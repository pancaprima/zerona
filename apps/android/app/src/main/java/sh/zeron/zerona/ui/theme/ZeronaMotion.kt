package sh.zeron.zerona.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val LocalZeronaOrnamentalMotionEnabled = compositionLocalOf { true }

@Composable
fun ZeronaOrnamentalMotionProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ornamental by remember {
        mutableStateOf(
            ZeronaMotionPolicy.ornamentalMotionEnabled(readAnimatorDurationScale(context)),
        )
    }
    DisposableEffect(context, lifecycleOwner) {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    ornamental =
                        ZeronaMotionPolicy.ornamentalMotionEnabled(readAnimatorDurationScale(context))
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        ornamental =
            ZeronaMotionPolicy.ornamentalMotionEnabled(readAnimatorDurationScale(context))
        val lifecycleObserver =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    ornamental =
                        ZeronaMotionPolicy.ornamentalMotionEnabled(readAnimatorDurationScale(context))
                }
            }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    CompositionLocalProvider(LocalZeronaOrnamentalMotionEnabled provides ornamental) {
        content()
    }
}

@Composable
fun rememberOrnamentalMotionEnabled(): Boolean = LocalZeronaOrnamentalMotionEnabled.current

private fun readAnimatorDurationScale(context: Context): Float =
    try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            ZeronaMotionPolicy.NORMAL_SYSTEM_MOTION_SCALE,
        )
    } catch (_: Exception) {
        ZeronaMotionPolicy.NORMAL_SYSTEM_MOTION_SCALE
    }

/** Draw-only press compression on inner visuals; place outside [clickable] touch targets. */
@Composable
fun Modifier.zeronaTactileCompression(interactionSource: MutableInteractionSource): Modifier {
    val ornamental = rememberOrnamentalMotionEnabled()
    val pressed by interactionSource.collectIsPressedAsState()
    val target =
        when {
            !ornamental -> ZeronaMotionPolicy.TACTILE_REST_SCALE
            pressed -> ZeronaMotionPolicy.TACTILE_PRESSED_SCALE
            else -> ZeronaMotionPolicy.TACTILE_REST_SCALE
        }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "zeronaTactileCompression",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
