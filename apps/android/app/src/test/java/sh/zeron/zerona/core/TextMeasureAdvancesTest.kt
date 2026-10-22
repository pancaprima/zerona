package sh.zeron.zerona.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextMeasureAdvancesTest {
    @Test
    fun aggregatesSurrogatePairIntoOneScalar() {
        val text = "a😀b"
        val perUnit = floatArrayOf(10f, 20f, 5f, 15f)
        val advances = advancesPerUnicodeScalar(text, perUnit, text.length)!!
        assertEquals(3, advances.size)
        assertEquals(10f, advances[0])
        assertEquals(25f, advances[1])
        assertEquals(15f, advances[2])
    }

    @Test
    fun doesNotMergeCombiningScalars() {
        val text = "e\u0301"
        val perUnit = floatArrayOf(12f, 0f)
        val advances = advancesPerUnicodeScalar(text, perUnit, text.length)!!
        assertEquals(2, advances.size)
        assertEquals(12f, advances[0])
        assertEquals(0f, advances[1])
    }

    @Test
    fun rejectsMismatchedUtf16Count() {
        assertNull(advancesPerUnicodeScalar("ab", floatArrayOf(1f), 1))
    }
}
