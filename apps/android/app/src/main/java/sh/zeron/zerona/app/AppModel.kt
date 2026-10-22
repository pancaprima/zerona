package sh.zeron.zerona.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.zeron.zerona.core.AndroidPlatformMeasurer
import sh.zeron.zerona.core.FontLoader
import sh.zeron.zerona.core.ZeronCoreLoader
import uniffi.zeron_core.AuthCallback
import uniffi.zeron_core.AuthExchange
import uniffi.zeron_core.AuthOrg
import uniffi.zeron_core.AuthTokens
import uniffi.zeron_core.ClientEvent
import uniffi.zeron_core.ConnectivityState
import uniffi.zeron_core.CoreClient
import uniffi.zeron_core.CoreConfig
import uniffi.zeron_core.CoreException
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
import uniffi.zeron_core.authExchangeCode
import uniffi.zeron_core.authListOrgs
import uniffi.zeron_core.authProductionEdgeUrl
import uniffi.zeron_core.authRefresh
import uniffi.zeron_core.coreVersion
import uniffi.zeron_core.parseAuthCallback
import uniffi.zeron_core.workosAuthorizeUrl
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
    val signedIn: Boolean = false,
    val isDemo: Boolean = false,
    val authInProgress: Boolean = false,
    val authError: String? = null,
    val orgChoices: List<AuthOrg>? = null,
    val isOffline: Boolean = false,
    val sections: List<SessionSectionUi> = emptyList(),
)

class AppModel(
    private val appContext: Context,
) : ViewModel() {
    companion object {
        private const val APP_VERSION = "0.1.0-m3a"

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppModel(context.applicationContext) as T
            }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val credentialsStore = CredentialsStore(appContext)
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val refreshCounter = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = refreshCounter.asStateFlow()

    private val workspaceCounter = MutableStateFlow(0L)
    val workspaceTick: StateFlow<Long> = workspaceCounter.asStateFlow()

    var client: CoreClient? = null
        private set

    private var textSystem: TextSystem? = null
    private var pendingOAuthState: String? = null
    private var pendingExchange: AuthExchange? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var processLifecycleRegistered = false

    fun textSystemOrNull(): TextSystem? = textSystem

    init {
        viewModelScope.launch {
            val err = ZeronCoreLoader.ensureLoaded()
            if (err != null) {
                _state.update { it.copy(loadError = err) }
                return@launch
            }
            ensureProcessLifecycle()
            val stored = credentialsStore.stored()
            if (stored != null) {
                startLive(stored)
            } else {
                _state.update { it.copy(coreVersion = coreVersion()) }
            }
        }
    }

    fun beginWorkOsSignIn(openUrl: (String) -> Unit) {
        val state = UUID.randomUUID().toString()
        pendingOAuthState = state
        _state.update { it.copy(authError = null, authInProgress = false) }
        openUrl(workosAuthorizeUrl(state))
    }

    fun handleAuthCallback(uri: Uri) {
        val parsed =
            parseAuthCallback(uri.toString()) ?: run {
                _state.update { it.copy(authError = "Sign-in didn't complete. Try again.") }
                return
            }
        when (parsed) {
            is AuthCallback.Error -> {
                val msg = parsed.description?.takeIf { it.isNotBlank() } ?: parsed.error
                _state.update { it.copy(authError = msg, authInProgress = false) }
            }
            is AuthCallback.Code -> {
                val expected = pendingOAuthState
                pendingOAuthState = null
                if (expected == null || parsed.state != expected) {
                    _state.update {
                        it.copy(
                            authError = "Sign-in didn't complete. Try again.",
                            authInProgress = false,
                        )
                    }
                    return
                }
                finishSignInWithCode(parsed.code)
            }
        }
    }

    fun completeOrgSelection(org: AuthOrg) {
        val exchange = pendingExchange ?: return
        pendingExchange = null
        _state.update { it.copy(orgChoices = null, authInProgress = true, authError = null) }
        viewModelScope.launch {
            try {
                val tokens =
                    withContext(Dispatchers.IO) {
                        authRefresh(
                            authProductionEdgeUrl(),
                            exchange.tokens.refreshToken,
                            org.organizationId,
                        )
                    }
                credentialsStore.store(exchange.user.id, org.organizationId, tokens)
                withContext(Dispatchers.Main) {
                    startLive(
                        StoredCredentials(
                            exchange.user.id,
                            org.organizationId,
                            tokens.accessToken,
                            tokens.refreshToken,
                        ),
                    )
                    _state.update { it.copy(authInProgress = false, authError = null) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(authInProgress = false, authError = formatCoreError(e))
                }
            }
        }
    }

    fun dismissOrgPicker() {
        pendingExchange = null
        _state.update { it.copy(orgChoices = null, authInProgress = false) }
    }

    fun startDemo() {
        credentialsStore.clear()
        startDemoClient()
    }

    fun signOut() {
        val wasDemo = _state.value.isDemo
        stopClient(keepLiveDataDir = !wasDemo)
        credentialsStore.clear()
        if (!wasDemo) {
            liveCoreDir().deleteRecursively()
        }
        _state.update {
            AppUiState(
                coreVersion = it.coreVersion.ifEmpty { coreVersion() },
                authError = null,
            )
        }
    }

    fun leaveDemoForSignIn() {
        stopClient(keepLiveDataDir = true)
        _state.update {
            AppUiState(
                coreVersion = it.coreVersion.ifEmpty { coreVersion() },
                authError = null,
            )
        }
    }

    private fun finishSignInWithCode(code: String) {
        _state.update { it.copy(authInProgress = true, authError = null, orgChoices = null) }
        viewModelScope.launch {
            try {
                val edge = authProductionEdgeUrl()
                val exchange =
                    withContext(Dispatchers.IO) { authExchangeCode(edge, code) }
                val orgs =
                    withContext(Dispatchers.IO) {
                        authListOrgs(edge, exchange.tokens.accessToken)
                    }
                if (orgs.isEmpty()) {
                    _state.update {
                        it.copy(
                            authInProgress = false,
                            authError = "This account isn't in an organization yet.",
                        )
                    }
                    return@launch
                }
                if (orgs.size == 1) {
                    val org = orgs.first()
                    val tokens =
                        withContext(Dispatchers.IO) {
                            authRefresh(edge, exchange.tokens.refreshToken, org.organizationId)
                        }
                    credentialsStore.store(exchange.user.id, org.organizationId, tokens)
                    withContext(Dispatchers.Main) {
                        startLive(
                            StoredCredentials(
                                exchange.user.id,
                                org.organizationId,
                                tokens.accessToken,
                                tokens.refreshToken,
                            ),
                        )
                        _state.update { it.copy(authInProgress = false, authError = null) }
                    }
                } else {
                    pendingExchange = exchange
                    _state.update {
                        it.copy(authInProgress = false, orgChoices = orgs, authError = null)
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(authInProgress = false, authError = formatCoreError(e))
                }
            }
        }
    }

    private fun startDemoClient() {
        if (!ensureTextSystem()) return
        val demoDir = appContext.filesDir.resolve("demo")
        demoDir.mkdirs()
        val config = buildCoreConfig(demoDir.absolutePath)
        val demoOptions =
            DemoOptions(
                fixture = DemoFixture.STANDARD,
                transcriptScale = TranscriptScale.Normal,
                streamSpeed = StreamSpeed.REALISTIC,
                longReply = false,
            )
        val listener =
            ClientListenerBridge { event ->
                mainHandler.post { handleEvent(event) }
            }
        try {
            shutdownClient()
            val core = CoreClient(config, Credentials.Demo(demoOptions), listener)
            client = core
            unregisterNetworkMonitor()
            core.preloadSessions()
            refreshWorkspace(core)
            _state.update {
                it.copy(
                    loadError = null,
                    coreVersion = coreVersion(),
                    signedIn = true,
                    isDemo = core.isDemo(),
                    isOffline = false,
                    sections = it.sections,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(loadError = e.message ?: e.toString()) }
        }
    }

    private fun startLive(stored: StoredCredentials) {
        if (!ensureTextSystem()) return
        claimCoreDir(stored.userId, stored.orgId)
        val coreDir = liveCoreDir()
        coreDir.mkdirs()
        val tokens =
            AuthTokens(
                accessToken = stored.accessToken,
                refreshToken = stored.refreshToken,
            )
        val credentials =
            Credentials.WorkOs(
                userId = stored.userId,
                orgId = stored.orgId,
                tokens = tokens,
            )
        val config = buildCoreConfig(coreDir.absolutePath)
        val listener =
            ClientListenerBridge { event ->
                mainHandler.post { handleEvent(event) }
            }
        try {
            shutdownClient()
            val core = CoreClient(config, credentials, listener)
            client = core
            credentialsStore.store(stored.userId, stored.orgId, tokens)
            registerNetworkMonitor()
            syncNetworkOnline()
            core.preloadSessions()
            refreshWorkspace(core)
            _state.update {
                it.copy(
                    loadError = null,
                    coreVersion = coreVersion(),
                    signedIn = true,
                    isDemo = false,
                    authError = null,
                    authInProgress = false,
                    orgChoices = null,
                )
            }
        } catch (e: Exception) {
            if (e is CoreException.Auth) {
                credentialsStore.clear()
            }
            _state.update {
                it.copy(
                    signedIn = false,
                    authError = formatCoreError(e),
                )
            }
        }
    }

    private fun buildCoreConfig(dataDir: String): CoreConfig =
        CoreConfig(
            edgeUrl = authProductionEdgeUrl(),
            dataDir = dataDir,
            deviceId = stableDeviceId(),
            deviceName = deviceName(),
            platform = "android",
            appVersion = APP_VERSION,
        )

    private fun liveCoreDir() = appContext.filesDir.resolve("core")

    private fun claimCoreDir(
        userId: String,
        orgId: String,
    ) {
        val coreDir = liveCoreDir()
        val owner = "$userId/$orgId"
        val marker = coreDir.resolve(".owner")
        val existing = marker.takeIf { it.isFile }?.readText()?.trim()
        if (existing != owner) {
            coreDir.deleteRecursively()
        }
        coreDir.mkdirs()
        marker.writeText(owner)
    }

    private fun ensureTextSystem(): Boolean {
        if (textSystem != null) return true
        val faces = FontLoader.loadFaceData(appContext)
        if (faces.isEmpty()) {
            _state.update { it.copy(loadError = "Missing Geist fonts in assets/fonts") }
            return false
        }
        val measurer = AndroidPlatformMeasurer()
        textSystem = TextSystem(faces, measurer)
        return true
    }

    private fun handleEvent(event: ClientEvent) {
        when (event) {
            is ClientEvent.WorkspaceChanged -> {
                client?.let { refreshWorkspace(it) }
                workspaceCounter.update { it + 1 }
            }
            is ClientEvent.SessionChanged,
            is ClientEvent.ComposerChanged,
            -> {
                refreshCounter.update { it + 1 }
            }
            is ClientEvent.ConnectivityChanged -> {
                val state = event.connectivity.state
                val offline =
                    state == ConnectivityState.OFFLINE || state == ConnectivityState.RECONNECTING
                _state.update { it.copy(isOffline = offline) }
            }
            is ClientEvent.AuthRefreshed -> {
                credentialsStore.updateTokens(event.tokens)
            }
            is ClientEvent.AuthExpired -> {
                handleAuthExpired()
            }
        }
    }

    private fun handleAuthExpired() {
        shutdownClient()
        credentialsStore.clear()
        _state.update {
            AppUiState(
                coreVersion = it.coreVersion.ifEmpty { coreVersion() },
                signedIn = false,
                authError = "Session expired. Sign in again.",
            )
        }
    }

    private fun shutdownClient() {
        unregisterNetworkMonitor()
        client?.shutdown()
        client = null
    }

    private fun stopClient(keepLiveDataDir: Boolean) {
        shutdownClient()
        if (!keepLiveDataDir) {
            liveCoreDir().deleteRecursively()
        }
        _state.update {
            it.copy(
                signedIn = false,
                isDemo = false,
                sections = emptyList(),
                isOffline = false,
                orgChoices = null,
                authInProgress = false,
            )
        }
    }

    private fun registerNetworkMonitor() {
        if (networkCallback != null) return
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    client?.setNetworkOnline(true)
                    _state.update { it.copy(isOffline = false) }
                }

                override fun onLost(network: Network) {
                    client?.setNetworkOnline(false)
                    _state.update { it.copy(isOffline = true) }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    syncNetworkOnline()
                }
            }
        networkCallback = callback
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        connectivityManager.registerNetworkCallback(request, callback)
        syncNetworkOnline()
    }

    private fun unregisterNetworkMonitor() {
        val callback = networkCallback ?: return
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    private fun syncNetworkOnline() {
        val active = connectivityManager.activeNetwork
        val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
        val online =
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        client?.setNetworkOnline(online)
        _state.update { s ->
            s.copy(isOffline = client != null && !online && !s.isDemo)
        }
    }

    private fun ensureProcessLifecycle() {
        if (processLifecycleRegistered) return
        processLifecycleRegistered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    val c = client
                    if (c != null && !c.isDemo()) {
                        c.onForeground()
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    val c = client
                    if (c != null && !c.isDemo()) {
                        c.onBackground()
                    }
                }
            },
        )
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

    private fun formatCoreError(e: Exception): String =
        when (e) {
            is CoreException.Auth -> e.detail
            is CoreException.NotFound -> e.detail
            is CoreException.InvalidArgument -> e.detail
            is CoreException.HostUnavailable -> e.detail
            is CoreException.Unsupported -> e.detail
            is CoreException.Network -> e.detail
            is CoreException.HostException -> e.detail
            is CoreException.Storage -> e.detail
            is CoreException.NotImplemented -> e.detail
            is CoreException.Closed -> "Client closed"
            is CoreException.Internal -> e.detail
            is CoreException -> e.message ?: e.toString()
            else -> e.message ?: e.toString()
        }

    override fun onCleared() {
        stopClient(keepLiveDataDir = true)
        super.onCleared()
    }
}
