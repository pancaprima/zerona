package sh.zeron.zerona

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.ui.ZeronaApp

class MainActivity : ComponentActivity() {
    private lateinit var appModel: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appModel =
            ViewModelProvider(this, AppModel.factory(applicationContext))[AppModel::class.java]
        if (savedInstanceState == null) {
            deliverAuthIntent(intent)
        }
        setContent {
            ZeronaApp(appModel = appModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deliverAuthIntent(intent)
        intent.data = null
        setIntent(intent)
    }

    private fun deliverAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "zeron") return
        appModel.handleAuthCallback(uri)
        intent.data = null
        if (intent === getIntent()) {
            setIntent(Intent(intent))
        }
    }
}
