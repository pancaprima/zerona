package sh.zeron.zerona.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.zeron.zerona.app.AppModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    appModel: AppModel,
    onSessionClick: (String) -> Unit,
) {
    val state by appModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zerona") },
                actions = {
                    Text(
                        state.coreVersion,
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(
                            if (state.isDemo) "Demo" else "Live",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            state.sections.forEach { section ->
                item(key = "h-${section.id}") {
                    SectionHeader(section.header)
                }
                items(section.sessions, key = { it.id }) { row ->
                    SessionRowItem(row, onClick = { onSessionClick(row.id) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SessionRowItem(
    row: sh.zeron.zerona.app.SessionRowUi,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            row.timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}
