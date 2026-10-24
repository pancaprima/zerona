package sh.zeron.zerona.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.zeron.zerona.app.AppModel
import uniffi.zeron_core.AuthOrg

@Composable
fun SignInScreen(appModel: AppModel) {
    val state by appModel.state.collectAsState()
    val context = LocalContext.current

    val orgChoices = state.orgChoices
    if (orgChoices != null) {
        OrgPickerDialog(
            orgs = orgChoices,
            onPick = { appModel.completeOrgSelection(it) },
            onDismiss = { appModel.dismissOrgPicker() },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZeronaDecorMark()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Zerona",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Sign in with your Zeron account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (state.authInProgress) {
            ZeronaInitialLoading(label = "Signing in…")
        } else {
            Button(
                onClick = {
                    appModel.beginWorkOsSignIn { url ->
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { appModel.startDemo() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Try demo")
            }
        }

        state.authError?.let { message ->
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (state.coreVersion.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                state.coreVersion,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OrgPickerDialog(
    orgs: List<AuthOrg>,
    onPick: (AuthOrg) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an organization") },
        text = {
            Column {
                for (org in orgs) {
                    TextButton(
                        onClick = { onPick(org) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(org.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
