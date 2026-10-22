package sh.zeron.zerona.transcript

import uniffi.zeron_core.DebugEntry
import uniffi.zeron_core.layoutFixtureMarkdown

private const val LAB_MARKDOWN_IMAGE_ALT = "Lab Markdown image"

/**
 * Offline transcript lab (`setDebugEntries`, no session `attach`).
 *
 * [DebugEntry] text is laid out like live markdown (widgets/links from layout), but does not
 * simulate host-driven tool-call streams; those require an attached session.
 */
object TranscriptLab {
    const val CHAT_ID = "__zerona_transcript_lab__"
    const val STREAM_ASSISTANT_ID = "a-stream"

    private val prompts =
        listOf(
            "Summarize the fixture below.",
            "What block types are in the lab markdown?",
            "Show me links and code in the transcript.",
        )

    private val fixtureMarkdown: String by lazy { layoutFixtureMarkdown() }

    fun initialEntries(): List<DebugEntry> {
        val entries = mutableListOf<DebugEntry>()
        entries +=
            DebugEntry(
                id = "u-demo-image",
                user = true,
                text =
                    "Image preview lab fixture (local bitmap — not a host read)\n\n" +
                        "Attached images (local files — open them to view):\n" +
                        "- $LAB_DEMO_IMAGE_REFERENCE",
                streaming = false,
            )
        entries +=
            DebugEntry(
                id = "a-demo-image",
                user = false,
                text = "The thumbnail above uses the Android lab demo fixture only.",
                streaming = false,
            )
        entries +=
            DebugEntry(
                id = "a-markdown-image",
                user = false,
                text =
                    "Markdown image (lab fixture path — not a host read):\n\n" +
                        "![$LAB_MARKDOWN_IMAGE_ALT]($LAB_DEMO_IMAGE_REFERENCE)\n\n" +
                        "Copy-link diagnostic (plain link, not promoted to a widget): " +
                        "[.png]($LAB_DEMO_IMAGE_REFERENCE)",
                streaming = false,
            )
        for (i in 0 until 3) {
            entries +=
                DebugEntry(
                    id = "u$i",
                    user = true,
                    text = prompts[i],
                    streaming = false,
                )
            entries +=
                DebugEntry(
                    id = "a$i",
                    user = false,
                    text = fixtureMarkdown,
                    streaming = false,
                )
        }
        return entries
    }

    /** Deterministic UTF-16-safe growing prefixes of the fixture (exact markdown bytes). */
    fun fixtureStreamingTargets(stepChars: Int = 8): List<String> {
        val source = fixtureMarkdown
        return streamingPrefixTargets(source, stepChars)
    }
}

/**
 * UTF-16-safe prefix targets for scripted streaming demos (pure Kotlin; safe in JVM unit tests).
 */
internal fun streamingPrefixTargets(
    source: String,
    stepChars: Int = 8,
): List<String> {
    if (source.isEmpty()) return listOf("")
    val step = stepChars.coerceAtLeast(1)
    val targets = mutableListOf<String>()
    var end = 0
    while (end < source.length) {
        end = minOf(end + step, source.length)
        end = advanceUtf16Boundary(source, end)
        targets.add(source.substring(0, end))
    }
    return targets
}

private fun advanceUtf16Boundary(
    text: String,
    end: Int,
): Int {
    var safe = end
    if (safe > 0 && safe < text.length && text[safe - 1].isHighSurrogate() && text[safe].isLowSurrogate()) {
        safe++
    }
    return safe
}
