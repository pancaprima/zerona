package sh.zeron.zerona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.ui.ZeronaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appModel: AppModel = viewModel(factory = AppModel.factory(applicationContext))
            ZeronaApp(appModel = appModel)
        }
    }
}
