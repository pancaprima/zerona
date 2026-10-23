package sh.zeron.zerona.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelLabelPresentationTest {
    @Test
    fun prefersCatalogModelLabel() {
        assertEquals(
            "Claude Sonnet",
            formatModelLabel("Claude Sonnet", "raw-id", "Harness", null),
        )
    }

    @Test
    fun fallsBackToRawModelId() {
        assertEquals("gpt-4.1", formatModelLabel(null, "gpt-4.1", null, null))
    }

    @Test
    fun fallsBackToHarnessLabel() {
        assertEquals("Local host", formatModelLabel(null, null, "Local host", null))
    }

    @Test
    fun blankFieldsYieldNull() {
        assertNull(formatModelLabel("  ", null, null, null))
        assertNull(formatModelLabel(null, null, null, null))
    }

    @Test
    fun reasoningOnlyDoesNotCreateLabel() {
        assertNull(formatModelLabel(null, null, null, "high"))
    }

    @Test
    fun appendsTrimmedReasoningWhenBasePresent() {
        assertEquals(
            "Claude Sonnet · high",
            formatModelLabel("Claude Sonnet", null, null, " high "),
        )
    }

    @Test
    fun trimsWhitespaceOnBaseAndReasoning() {
        assertEquals(
            "My Model · medium",
            formatModelLabel("  My Model  ", null, null, " medium "),
        )
    }

    @Test
    fun unicodeModelIdentifierPreserved() {
        assertEquals("模型-α", formatModelLabel(null, "模型-α", null, null))
    }
}
