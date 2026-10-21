package sh.zeron.zerona.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.zeron.zerona.core.AndroidPlatformMeasurer
import sh.zeron.zerona.core.FontLoader
import sh.zeron.zerona.core.ZeronCoreLoader
import uniffi.zeron_core.ClientEvent
import uniffi.zeron_core.CoreClient
import uniffi.zeron_core.CoreConfig
import uniffi.zeron_core.Credentials
import uniffi.zeron_core.DemoFixture
import uniffi.zeron_core.DemoOptions
import uniffi.zeron_core.FrontPage
import uniffi.zeron_core.ProjectView
import uniffi.zeron_core.SessionRow
import uniffi.zeron_core.StreamSpeed
import uniffi.zeron_core.TextSystem
import uniffi.zeron_core.TranscriptScale
import uniffi.zeron_core.WorkspaceSnapshot
import uniffi.zeron_core.authProductionEdgeUrl
import uniffi.zeron_core.coreVersion
import java.util.UUID

data class SessionRowUi(
    val id: String,
    val title: String,
    val timeLabel: String,
)

data class SessionSectionUi(
    val id: String,
    val header: String,
    val sessions: List<SessionRowUi>,
)

data class AppUiState(
    val loadError: String? = null,
    val coreVersion: String = "",
    val isDemo: Boolean = false,
    val sections: List<SessionSectionUi> = emptyList(),
)

class AppModel(
    private val appContext: Context,
) : ViewModel() {
    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppModel(context.applicationContext) as T
            }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val refreshCounter = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = refreshCounter.asStateFlow()

    var client: CoreClient? = null
        private set

    private var textSystem: TextSystem? = null

    fun textSystemOrNull(): TextSystem? = textSystem

    init {
        viewModelScope.launch {
            val err = ZeronCoreLoader.ensureLoaded()
            if (err != null) {
                _state.update { it.copy(loadError = err) }
                return@launch
            }
            startDemoClient()
        }
    }

    private fun startDemoClient() {
        val faces = FontLoader.loadFaceData(appContext)
        if (faces.isEmpty()) {
            _state.update { it.copy(loadError = "Missing Geist fonts in assets/fonts") }
            return
        }
        val measurer = AndroidPlatformMeasurer()
        textSystem = TextSystem(faces, measurer)

        val demoDir = appContext.filesDir.resolve("demo")
        demoDir.mkdirs()
        val config =
            CoreConfig(
                edgeUrl = authProductionEdgeUrl(),
                dataDir = demoDir.absolutePath,
                deviceId = stableDeviceId(),
                deviceName = deviceName(),
                platform = "android",
                appVersion = "0.1.0-m1",
            )
        val demoOptions =
            DemoOptions(
                fixture = DemoFixture.STANDARD,
                transcriptScale = TranscriptScale.NORMAL,
                streamSpeed = StreamSpeed.REALISTIC,
                longReply = false,
            )
        val listener =
            ClientListenerBridge { event ->
                mainHandler.post { handleEvent(event) }
            }
        try {
            val core = CoreClient(config, Credentials.Demo(demoOptions), listener)
            client = core
            core.preloadSessions()
            refreshWorkspace(core)
            _state.update {
                it.copy(
                    loadError = null,
                    coreVersion = coreVersion(),
                    isDemo = core.isDemo(),
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(loadError = e.message ?: e.toString()) }
        }
    }

    private fun handleEvent(event: ClientEvent) {
        when (event) {
            is ClientEvent.WorkspaceChanged -> {
                client?.let { refreshWorkspace(it) }
            }
            is ClientEvent.SessionChanged,
            is ClientEvent.ComposerChanged,
            -> {
                refreshCounter.update { it + 1 }
            }
            else -> Unit
        }
    }

    private fun refreshWorkspace(core: CoreClient) {
        val ws = core.workspace()
        val sections = buildSections(ws)
        _state.update { it.copy(sections = sections) }
    }

    private fun buildSections(ws: WorkspaceSnapshot): List<SessionSectionUi> {
        val out = mutableListOf<SessionSectionUi>()
        val page = ws.front
        for (folder in folderHeaders(page)) {
            val sessions =
                when (folder.id) {
                    "pinned" -> page.pinned
                    else -> page.sections.find { it.id == folder.id }?.sessions ?: emptyList()
                }
            if (sessions.isNotEmpty()) {
                out +=
                    SessionSectionUi(
                        id = folder.id,
                        header = folder.name,
                        sessions = sessions.map { it.toUi() },
                    )
            }
        }
        val recent = page.recent
        if (recent.isNotEmpty()) {
            val groups = projectGroups(recent, ws.projects)
            for (g in groups) {
                out +=
                    SessionSectionUi(
                        id = g.id,
                        header = g.name,
                        sessions = g.sessions,
                    )
            }
        }
        return out
    }

    private data class FolderHeader(val id: String, val name: String)

    private fun folderHeaders(page: FrontPage): List<FolderHeader> {
        val folders = mutableListOf<FolderHeader>()
        if (page.pinned.isNotEmpty()) {
            folders += FolderHeader("pinned", "Pinned")
        }
        for (s in page.sections) {
            folders += FolderHeader(s.id, s.name)
        }
        return folders
    }

    private data class ProjectGroup(
        val id: String,
        val name: String,
        val sessions: List<SessionRowUi>,
    )

    private fun projectGroups(
        rows: List<SessionRow>,
        projects: List<ProjectView>,
    ): List<ProjectGroup> {
        val byId = projects.associateBy { it.id }
        val groups = mutableListOf<ProjectGroup>()
        val index = mutableMapOf<String, Int>()
        for (row in rows) {
            val id: String
            val name: String
            val ref = row.project
            if (ref != null) {
                val project = byId[ref.id]
                id = "project:" + (project?.groupKey ?: ref.id)
                name = project?.groupName ?: ref.name
            } else {
                id = "home:" + row.deviceId
                name = row.deviceName?.let { "Home · $it" } ?: "Home"
            }
            val ui = row.toUi()
            val i = index[id]
            if (i != null) {
                val g = groups[i]
                groups[i] = g.copy(sessions = g.sessions + ui)
            } else {
                index[id] = groups.size
                groups += ProjectGroup(id, name, listOf(ui))
            }
        }
        return groups
    }

    private fun SessionRow.toUi() =
        SessionRowUi(
            id = id,
            title = title,
            timeLabel = timeLabel,
        )

    private fun stableDeviceId(): String {
        val prefs = appContext.getSharedPreferences("zerona", Context.MODE_PRIVATE)
        val existing = prefs.getString("deviceId", null)
        if (existing != null) return existing
        val id = "android-" + UUID.randomUUID().toString().take(8).lowercase()
        prefs.edit().putString("deviceId", id).apply()
        return id
    }

    private fun deviceName(): String {
        val name = Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME)
        return name?.takeIf { it.isNotBlank() } ?: "Android device"
    }

    override fun onCleared() {
        client?.shutdown()
        client = null
        super.onCleared()
    }
}
