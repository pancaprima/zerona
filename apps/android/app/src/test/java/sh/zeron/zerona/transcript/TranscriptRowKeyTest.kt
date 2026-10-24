package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptRowKeyTest {
    @Test
    fun userMessageKeyMatchesRustFnv1a() {
        assertEquals(5609040118007553040u, TranscriptRowKey.userMessage("msg-1"))
    }
}
