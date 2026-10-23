package sh.zeron.zerona.transcript

import uniffi.zeron_core.LinkHit

object TranscriptHitTest {
    fun linkAtInRow(
        links: List<LinkHit>,
        rowX: Float,
        rowY: Float,
        scrollerFrames: List<ScrollerFrame>,
    ): LinkHit? {
        for (link in links) {
            var lx = rowX
            var ly = rowY
            val scroller = link.scroller
            if (scroller != null) {
                val idx = scroller.toInt()
                if (idx < 0 || idx >= scrollerFrames.size) continue
                val sf = scrollerFrames[idx]
                if (!sf.visible) continue
                if (rowX < sf.viewportLeft || rowX > sf.viewportLeft + sf.viewportWidth) continue
                if (rowY < sf.viewportTop || rowY > sf.viewportTop + sf.viewportHeight) continue
                lx = rowX - sf.viewportLeft + sf.scrollX
                ly = rowY - sf.viewportTop
            }
            val left = link.x - 4f
            val top = link.y - 2f
            val right = link.x + link.w + 4f
            val bottom = link.y + link.h + 2f
            if (lx in left..right && ly in top..bottom) {
                return link
            }
        }
        return null
    }
}

data class ScrollerFrame(
    val viewportLeft: Float,
    val viewportTop: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val scrollX: Float,
    val visible: Boolean,
)
