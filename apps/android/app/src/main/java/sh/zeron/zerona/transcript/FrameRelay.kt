package sh.zeron.zerona.transcript

import android.os.Handler
import android.os.Looper
import uniffi.zeron_core.LayoutListener

/**
 * Coalesces layout-thread [LayoutListener.frameReady] onto the main looper.
 */
class FrameRelay(
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val onFrame: () -> Unit,
) : LayoutListener {
    private val lock = Any()
    private var scheduled = false
    @Volatile
    private var alive = true

    fun shutdown() {
        alive = false
        synchronized(lock) { scheduled = false }
    }

    override fun frameReady(revision: ULong) {
        if (!alive) return
        synchronized(lock) {
            if (scheduled) return
            scheduled = true
        }
        mainHandler.post {
            synchronized(lock) { scheduled = false }
            if (alive) onFrame()
        }
    }
}
