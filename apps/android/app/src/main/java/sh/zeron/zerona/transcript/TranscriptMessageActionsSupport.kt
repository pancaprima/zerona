package sh.zeron.zerona.transcript

/**
 * Pure helpers for transcript long-press copy / link / selection menus (JVM-testable).
 */
data class MessageLinkDescriptor(
    val url: String,
    val label: String,
)

data class ClipboardText(
    val text: String,
    val truncated: Boolean,
)

sealed class ClipboardWritePlan {
    data class Ready(val clipboard: ClipboardText) : ClipboardWritePlan()

    data object LinkTooLarge : ClipboardWritePlan()
}

data class MessageActionMenuState(
    val copyMessageEnabled: Boolean,
    val selectTextEnabled: Boolean,
    val copyLinkEnabled: Boolean,
    val viewSourceEnabled: Boolean,
    val copyMessageText: String,
    val selectionText: String,
    val selectionDisplayText: String,
    val selectionDisplayTruncated: Boolean,
    val clipboard: ClipboardText,
    val links: List<MessageLinkDescriptor>,
    val preferredLinkUrl: String?,
    val resolvedCopyLinkUrl: String?,
)

object TranscriptMessageActionsSupport {
    /**
     * Upper bound for clipboard payload (UTF-16 code units). Keeps Binder clip under ~1 MiB.
     */
    const val MAX_CLIPBOARD_CODE_UNITS: Int = 131_072

    /**
     * Upper bound for selection-panel TextView content (UTF-16 code units) before layout.
     */
    const val MAX_SELECTION_DISPLAY_CODE_UNITS: Int = 131_072

    private val TRUNCATION_NOTICE: String
        get() =
            "\n\n[Truncated: message exceeded the clipboard limit ($MAX_CLIPBOARD_CODE_UNITS UTF-16 code units).]"

    private val SELECTION_DISPLAY_NOTICE: String
        get() =
            "\n\n[Truncated for display: exceeded $MAX_SELECTION_DISPLAY_CODE_UNITS UTF-16 code units. Copy message uses the full bounded clipboard limit.]"

    fun linkDescriptor(url: String): MessageLinkDescriptor {
        val trimmed = url.trim()
        val label =
            when {
                trimmed.isEmpty() -> "Link"
                trimmed.length <= 72 -> trimmed
                else -> trimmed.take(69) + "…"
            }
        return MessageLinkDescriptor(url = url, label = label)
    }

    fun dedupeLinksPreserveToken(
        links: List<MessageLinkDescriptor>,
        preferredUrl: String?,
    ): List<MessageLinkDescriptor> {
        if (links.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        val out = ArrayList<MessageLinkDescriptor>(links.size)
        val preferred = preferredUrl?.let { pickExactToken(links, it) }
        if (preferred != null && seen.add(preferred.url)) {
            out.add(preferred)
        }
        for (link in links) {
            if (seen.add(link.url)) {
                out.add(link)
            }
        }
        return out
    }

    fun pickExactToken(
        links: List<MessageLinkDescriptor>,
        preferredUrl: String,
    ): MessageLinkDescriptor? = links.firstOrNull { it.url == preferredUrl }

    fun truncateUtf16CodeUnits(
        text: String,
        maxUnits: Int,
    ): String {
        if (maxUnits <= 0) return ""
        if (text.length <= maxUnits) return text
        var end = maxUnits
        if (end < text.length && isLowSurrogate(text[end])) {
            end--
        }
        if (end > 0 && isHighSurrogate(text[end - 1])) {
            end--
        }
        return text.substring(0, end.coerceAtLeast(0))
    }

    fun prepareClipboardText(text: String): ClipboardText {
        if (text.length <= MAX_CLIPBOARD_CODE_UNITS) {
            return ClipboardText(text = text, truncated = false)
        }
        val notice = TRUNCATION_NOTICE
        val budget = (MAX_CLIPBOARD_CODE_UNITS - notice.length).coerceAtLeast(0)
        val body = if (budget > 0) truncateUtf16CodeUnits(text, budget) else ""
        return ClipboardText(text = body + notice, truncated = true)
    }

    fun prepareLinkClipboard(url: String): ClipboardWritePlan {
        if (url.length > MAX_CLIPBOARD_CODE_UNITS) {
            return ClipboardWritePlan.LinkTooLarge
        }
        return ClipboardWritePlan.Ready(ClipboardText(text = url, truncated = false))
    }

    fun prepareSelectionDisplayText(text: String): ClipboardText {
        if (text.length <= MAX_SELECTION_DISPLAY_CODE_UNITS) {
            return ClipboardText(text = text, truncated = false)
        }
        val notice = SELECTION_DISPLAY_NOTICE
        val budget = (MAX_SELECTION_DISPLAY_CODE_UNITS - notice.length).coerceAtLeast(0)
        val body = if (budget > 0) truncateUtf16CodeUnits(text, budget) else ""
        return ClipboardText(text = body + notice, truncated = true)
    }

    fun resolveSelectionText(
        fallbackRowText: String,
        wholeMessageText: String?,
    ): String {
        val whole = wholeMessageText?.takeIf { it.isNotEmpty() }
        if (whole != null) return whole
        return fallbackRowText
    }

    fun resolveCopyMessageText(
        fallbackRowText: String,
        wholeMessageText: String?,
    ): String {
        val whole = wholeMessageText?.takeIf { it.isNotEmpty() }
        return whole ?: fallbackRowText
    }

    fun buildMenuState(
        fallbackRowText: String,
        rowCopyText: String,
        rowDisplayText: String,
        wholeMessageText: String?,
        links: List<MessageLinkDescriptor>,
        preferredLinkUrl: String?,
    ): MessageActionMenuState {
        val block = fallbackRowText
        val selectionText = resolveSelectionText(block, wholeMessageText)
        val copyMessageText = resolveCopyMessageText(block, wholeMessageText)
        val clipboard = prepareClipboardText(copyMessageText)
        val selectionDisplay = prepareSelectionDisplayText(selectionText)
        val deduped = dedupeLinksPreserveToken(links, preferredLinkUrl)
        val resolvedLink =
            when {
                deduped.isEmpty() -> null
                deduped.size == 1 -> deduped.first().url
                preferredLinkUrl != null -> pickExactToken(deduped, preferredLinkUrl)?.url
                else -> null
            }
        val whole = wholeMessageText?.takeIf { it.isNotEmpty() }
        val wholeDiffersFromBlock = whole != null && whole != block
        val copyTextIsDistinctSource =
            rowCopyText.isNotEmpty() && rowCopyText != rowDisplayText
        val viewSourceEnabled = wholeDiffersFromBlock && copyTextIsDistinctSource
        return MessageActionMenuState(
            copyMessageEnabled = copyMessageText.isNotEmpty(),
            selectTextEnabled = selectionText.isNotEmpty(),
            copyLinkEnabled = deduped.isNotEmpty(),
            viewSourceEnabled = viewSourceEnabled,
            copyMessageText = copyMessageText,
            selectionText = selectionText,
            selectionDisplayText = selectionDisplay.text,
            selectionDisplayTruncated = selectionDisplay.truncated,
            clipboard = clipboard,
            links = deduped,
            preferredLinkUrl = preferredLinkUrl,
            resolvedCopyLinkUrl = resolvedLink,
        )
    }

    private fun isHighSurrogate(c: Char): Boolean = c.code in 0xD800..0xDBFF

    private fun isLowSurrogate(c: Char): Boolean = c.code in 0xDC00..0xDFFF
}
