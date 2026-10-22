package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptUtf16Test {
    @Test
    fun slice_respectsUtf16Length() {
        val text = "a\uD83D\uDE00b"
        assertEquals(4, text.length)
        assertEquals("\uD83D\uDE00", TranscriptUtf16.slice(text, 1, 2))
    }

    @Test
    fun slice_clampsOutOfRange() {
        val text = "hello"
        assertEquals("", TranscriptUtf16.slice(text, 10, 2))
        assertEquals("lo", TranscriptUtf16.slice(text, 3, 10))
    }

    @Test
    fun slice_doesNotSplitSurrogate() {
        val text = "x\uD83D\uDE00y"
        val slice = TranscriptUtf16.slice(text, 1, 2)
        assertEquals("\uD83D\uDE00", slice)
    }

    @Test
    fun slice_handlesOverflowLen() {
        val text = "ab"
        assertEquals("ab", TranscriptUtf16.slice(text, 0, Int.MAX_VALUE))
    }

    @Test
    fun isValidRange() {
        val text = "abc"
        assertTrue(TranscriptUtf16.isValidRange(text, 0, 3))
        assertFalse(TranscriptUtf16.isValidRange(text, 1, 3))
        assertTrue(TranscriptUtf16.isValidRange(text, 2, 1))
        assertFalse(TranscriptUtf16.isValidRange(text, 3, 1))
        assertTrue(TranscriptUtf16.isValidRange(text, 3, 0))
        assertTrue(TranscriptUtf16.isValidRange("", 0, 0))
    }

    @Test
    fun isValidRange_rejectsSplitSurrogate() {
        val text = "a\uD83D\uDE00"
        assertFalse(TranscriptUtf16.isValidRange(text, 2, 1))
    }
}
