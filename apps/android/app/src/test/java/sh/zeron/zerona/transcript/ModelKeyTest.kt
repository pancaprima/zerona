package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModelKeyTest {
    @Test
    fun equalityUsesAllFields() {
        val a = ModelKey(1u, 2u, 400f)
        val b = ModelKey(1u, 2u, 400f)
        val c = ModelKey(1u, 3u, 400f)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
