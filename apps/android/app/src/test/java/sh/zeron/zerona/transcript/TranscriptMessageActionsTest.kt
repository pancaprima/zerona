package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMessageActionsTest {
    private fun menuState(
        fallbackRowText: String,
        wholeMessageText: String? = null,
        links: List<MessageLinkDescriptor> = emptyList(),
        preferredLinkUrl: String? = null,
        rowCopyText: String = fallbackRowText,
        rowDisplayText: String = fallbackRowText,
    ) = TranscriptMessageActionsSupport.buildMenuState(
        fallbackRowText = fallbackRowText,
        rowCopyText = rowCopyText,
        rowDisplayText = rowDisplayText,
        wholeMessageText = wholeMessageText,
        links = links,
        preferredLinkUrl = preferredLinkUrl,
    )

    @Test
    fun buildMenuState_prefersWholeMessageForCopy() {
        val state =
            menuState(
                fallbackRowText = "visible block",
                wholeMessageText = "# Title\n\nvisible block",
                rowCopyText = "# Title\n\nvisible block",
                rowDisplayText = "visible block",
            )
        assertEquals("# Title\n\nvisible block", state.copyMessageText)
        assertTrue(state.viewSourceEnabled)
        assertTrue(state.copyMessageEnabled)
        assertTrue(state.selectTextEnabled)
        assertFalse(state.copyLinkEnabled)
    }

    @Test
    fun buildMenuState_plainCopyTextDisablesViewSource() {
        val state =
            menuState(
                fallbackRowText = "visible block",
                wholeMessageText = "intro\n\nvisible block",
                rowCopyText = "visible block",
                rowDisplayText = "visible block",
            )
        assertFalse(state.viewSourceEnabled)
        assertEquals("intro\n\nvisible block", state.selectionText)
    }

    @Test
    fun buildMenuState_fallsBackWhenWholeMessageMissing() {
        val state =
            menuState(
                fallbackRowText = "row only",
                wholeMessageText = null,
            )
        assertEquals("row only", state.copyMessageText)
        assertEquals("row only", state.selectionText)
        assertFalse(state.viewSourceEnabled)
    }

    @Test
    fun buildMenuState_fallsBackWhenWholeMessageEmpty() {
        val state =
            menuState(
                fallbackRowText = "fallback",
                wholeMessageText = "",
            )
        assertEquals("fallback", state.copyMessageText)
    }

    @Test
    fun dedupeLinks_preservesExactTokenAndPreferredFirst() {
        val a = MessageLinkDescriptor("https://a.test/x", "a")
        val b = MessageLinkDescriptor("https://b.test/y", "b")
        val c = MessageLinkDescriptor("https://a.test/x?ref=1", "c")
        val out =
            TranscriptMessageActionsSupport.dedupeLinksPreserveToken(
                listOf(a, b, c),
                preferredUrl = "https://a.test/x?ref=1",
            )
        assertEquals(3, out.size)
        assertEquals("https://a.test/x?ref=1", out.first().url)
        assertEquals("https://a.test/x", out[1].url)
    }

    @Test
    fun dedupeLinks_preservesWhitespaceDistinctUrls() {
        val spaced = MessageLinkDescriptor("https://a.test/x ", "spaced")
        val tight = MessageLinkDescriptor("https://a.test/x", "tight")
        val out =
            TranscriptMessageActionsSupport.dedupeLinksPreserveToken(
                listOf(spaced, tight),
                preferredUrl = null,
            )
        assertEquals(2, out.size)
        assertEquals("https://a.test/x ", out[0].url)
        assertEquals("https://a.test/x", out[1].url)
    }

    @Test
    fun pickExactToken_rejectsTrimmedFallback() {
        val link = MessageLinkDescriptor("https://a.test/x", "a")
        assertNull(
            TranscriptMessageActionsSupport.pickExactToken(
                listOf(link),
                preferredUrl = " https://a.test/x ",
            ),
        )
        assertEquals(
            link,
            TranscriptMessageActionsSupport.pickExactToken(
                listOf(link),
                preferredUrl = "https://a.test/x",
            ),
        )
    }

    @Test
    fun buildMenuState_singleLinkResolvesDirectCopy() {
        val link = TranscriptMessageActionsSupport.linkDescriptor("zeron-preview:img")
        val state =
            menuState(
                fallbackRowText = "text",
                links = listOf(link),
            )
        assertTrue(state.copyLinkEnabled)
        assertEquals("zeron-preview:img", state.resolvedCopyLinkUrl)
    }

    @Test
    fun buildMenuState_manyLinksNeedChooserUnlessPreferred() {
        val links =
            listOf(
                TranscriptMessageActionsSupport.linkDescriptor("https://one"),
                TranscriptMessageActionsSupport.linkDescriptor("https://two"),
            )
        val noPreferred =
            menuState(
                fallbackRowText = "t",
                links = links,
            )
        assertNull(noPreferred.resolvedCopyLinkUrl)

        val withPreferred =
            menuState(
                fallbackRowText = "t",
                links = links,
                preferredLinkUrl = "https://two",
            )
        assertEquals("https://two", withPreferred.resolvedCopyLinkUrl)
    }

    @Test
    fun prepareClipboardText_truncatesWithNotice() {
        val huge = "x".repeat(TranscriptMessageActionsSupport.MAX_CLIPBOARD_CODE_UNITS + 10)
        val clip = TranscriptMessageActionsSupport.prepareClipboardText(huge)
        assertTrue(clip.truncated)
        assertTrue(clip.text.contains("UTF-16 code units"))
        assertTrue(clip.text.length <= TranscriptMessageActionsSupport.MAX_CLIPBOARD_CODE_UNITS)
    }

    @Test
    fun prepareClipboardText_preservesUtf16Supplementary() {
        val emoji = "\uD83D\uDE00"
        val clip = TranscriptMessageActionsSupport.prepareClipboardText("hi $emoji")
        assertFalse(clip.truncated)
        assertEquals("hi $emoji", clip.text)
    }

    @Test
    fun prepareClipboardText_truncatesOnSupplementaryBoundary() {
        val emoji = "\uD83D\uDE00"
        val prefix = "a".repeat(TranscriptMessageActionsSupport.MAX_CLIPBOARD_CODE_UNITS - 1)
        val text = prefix + emoji
        val clip = TranscriptMessageActionsSupport.prepareClipboardText(text)
        assertTrue(clip.truncated)
        assertFalse(clip.text.contains(emoji))
        assertTrue(clip.text.startsWith("a"))
    }

    @Test
    fun prepareClipboardText_unchangedUnderCap() {
        val text = "short message"
        val clip = TranscriptMessageActionsSupport.prepareClipboardText(text)
        assertEquals(text, clip.text)
        assertFalse(clip.truncated)
    }

    @Test
    fun prepareLinkClipboard_rejectsOversize() {
        val huge = "h".repeat(TranscriptMessageActionsSupport.MAX_CLIPBOARD_CODE_UNITS + 1)
        assertTrue(TranscriptMessageActionsSupport.prepareLinkClipboard(huge) is ClipboardWritePlan.LinkTooLarge)
    }

    @Test
    fun prepareSelectionDisplayText_noNoticeWhenUnderCap() {
        val text = "hello"
        val display = TranscriptMessageActionsSupport.prepareSelectionDisplayText(text)
        assertEquals(text, display.text)
        assertFalse(display.truncated)
    }

    @Test
    fun emptyLinks_disablesCopyLink() {
        val state =
            menuState(
                fallbackRowText = "x",
                preferredLinkUrl = "https://ignored",
            )
        assertFalse(state.copyLinkEnabled)
        assertNull(state.resolvedCopyLinkUrl)
    }
}
