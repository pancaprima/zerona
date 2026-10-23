package sh.zeron.zerona.transcript

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.zeron_core.LinkHit

class TranscriptHitTestTest {
    @Test
    fun linkAtInRow_hitsInsideRect() {
        val link = LinkHit(10f, 20f, 30f, 12f, "https://example.com", null)
        val hit = TranscriptHitTest.linkAtInRow(listOf(link), 25f, 25f, emptyList())
        assertNotNull(hit)
    }

    @Test
    fun linkAtInRow_missesOutside() {
        val link = LinkHit(10f, 20f, 30f, 12f, "https://example.com", null)
        assertNull(TranscriptHitTest.linkAtInRow(listOf(link), 0f, 0f, emptyList()))
    }

    @Test
    fun linkAtInRow_requiresScrollerViewport() {
        val link = LinkHit(10f, 20f, 30f, 12f, "https://example.com", 0u)
        val frame =
            ScrollerFrame(
                viewportLeft = 100f,
                viewportTop = 0f,
                viewportWidth = 50f,
                viewportHeight = 40f,
                scrollX = 0f,
                visible = true,
            )
        assertNull(TranscriptHitTest.linkAtInRow(listOf(link), 25f, 25f, listOf(frame)))
        val hit =
            TranscriptHitTest.linkAtInRow(
                listOf(link),
                110f,
                25f,
                listOf(frame),
            )
        assertNotNull(hit)
    }

    @Test
    fun linkAtInRow_ignoresHiddenScroller() {
        val link = LinkHit(10f, 20f, 30f, 12f, "https://example.com", 0u)
        val frame =
            ScrollerFrame(
                viewportLeft = 0f,
                viewportTop = 0f,
                viewportWidth = 200f,
                viewportHeight = 40f,
                scrollX = 0f,
                visible = false,
            )
        assertNull(TranscriptHitTest.linkAtInRow(listOf(link), 25f, 25f, listOf(frame)))
    }

    @Test
    fun linkAtInRow_invalidScrollerIndexSkipped() {
        val link = LinkHit(10f, 20f, 30f, 12f, "https://example.com", 5u)
        assertNull(TranscriptHitTest.linkAtInRow(listOf(link), 25f, 25f, emptyList()))
    }
}
