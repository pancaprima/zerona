package sh.zeron.zerona.transcript

import android.content.Context
import android.provider.Settings
import android.view.View
import sh.zeron.zerona.ui.theme.ZeronaMotionPolicy
import uniffi.zeron_core.RowKind

object ZeronaNativeMotion {
    private const val STREAM_REVEAL_MS = 220L
    private const val ROW_ENTRANCE_MS = 200L
    private const val USER_ENTRANCE_TRANSLATION_DP = 10f
    private const val INCOMING_ENTRANCE_TRANSLATION_DP = 6f
    private const val ENTRANCE_START_ALPHA = 0.78f

    fun ornamentalEnabled(context: Context): Boolean =
        ZeronaMotionPolicy.nativeOrnamentalMotionEnabled(readAnimatorDurationScale(context))

    fun streamRevealDurationMs(context: Context): Long =
        if (ornamentalEnabled(context)) STREAM_REVEAL_MS else 0L

    fun clearRowEntrance(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.translationY = 0f
    }

    fun playRowEntrance(
        view: View,
        rowKind: RowKind,
    ) {
        if (!ornamentalEnabled(view.context)) {
            clearRowEntrance(view)
            return
        }
        val density = view.resources.displayMetrics.density
        view.animate().cancel()
        val translationDp =
            when (rowKind) {
                RowKind.USER -> USER_ENTRANCE_TRANSLATION_DP
                else -> INCOMING_ENTRANCE_TRANSLATION_DP
            }
        view.alpha = ENTRANCE_START_ALPHA
        view.translationY = translationDp * density
        view
            .animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ROW_ENTRANCE_MS)
            .start()
    }

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
}
