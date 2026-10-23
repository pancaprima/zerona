package sh.zeron.zerona.transcript

/**
 * Rust display lists index row text in UTF-16 code units (same as [String.length]).
 */
object TranscriptUtf16 {
    fun slice(
        text: String,
        start: Int,
        len: Int,
    ): String {
        if (len <= 0 || start < 0) return ""
        val s = start.coerceAtMost(text.length)
        val end =
            if (len > text.length - s) {
                text.length
            } else {
                val rawEnd = s.toLong() + len.toLong()
                if (rawEnd > Int.MAX_VALUE) text.length else rawEnd.toInt()
            }
        if (s >= end) return ""
        var safeStart = s
        var safeEnd = end
        if (safeStart > 0 && safeStart < text.length && isLowSurrogate(text[safeStart])) {
            safeStart--
        }
        if (safeEnd > safeStart && safeEnd < text.length && isHighSurrogate(text[safeEnd - 1])) {
            safeEnd++
        }
        return text.substring(safeStart, safeEnd.coerceAtMost(text.length))
    }

    fun isValidRange(
        text: String,
        start: Int,
        len: Int,
    ): Boolean {
        if (start < 0 || len < 0) return false
        val end = start.toLong() + len.toLong()
        if (end > Int.MAX_VALUE) return false
        if (end > text.length) return false
        if (len == 0) return true
        val endIdx = end.toInt()
        if (start > 0 && isLowSurrogate(text[start])) return false
        if (endIdx < text.length && isHighSurrogate(text[endIdx - 1]) && isLowSurrogate(text[endIdx])) {
            return false
        }
        return true
    }

    private fun isHighSurrogate(c: Char): Boolean = c.code in 0xD800..0xDBFF

    private fun isLowSurrogate(c: Char): Boolean = c.code in 0xDC00..0xDFFF
}
