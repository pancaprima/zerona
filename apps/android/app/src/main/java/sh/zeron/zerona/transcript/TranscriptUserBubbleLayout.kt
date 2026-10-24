package sh.zeron.zerona.transcript

import uniffi.zeron_core.ColorRole
import uniffi.zeron_core.RowDisplay

object TranscriptUserBubbleLayout {
    data class BubbleRect(
        val leftDp: Float,
        val topDp: Float,
        val widthDp: Float,
        val heightDp: Float,
    )

    /** Rightmost user bubble box in row coordinates (logical dp). */
    fun primaryBubble(display: RowDisplay): BubbleRect? {
        var best: BubbleRect? = null
        var bestRight = Float.NEGATIVE_INFINITY
        for (box in display.boxes) {
            if (box.color != ColorRole.USER_BUBBLE) continue
            val right = box.x + box.w
            if (right > bestRight) {
                bestRight = right
                best = BubbleRect(box.x, box.y, box.w, box.h)
            }
        }
        return best
    }
}
