package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPrefixTest {
    @Test
    fun prefixesPreserveExactTextAndSurrogates() {
        val source = "ab😀cd\n```\ncode\n```"
        val targets = streamingPrefixTargets(source, stepChars = 3)
        assertEquals(source, targets.last())
        for (target in targets) {
            assertEquals(source.substring(0, target.length), target)
        }
    }
}
