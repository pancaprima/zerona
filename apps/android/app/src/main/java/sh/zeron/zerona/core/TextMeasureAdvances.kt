package sh.zeron.zerona.core

/**
 * Fold UTF-16 [getTextWidths] advances into one width per Unicode scalar (matches core FFI contract).
 */
internal fun advancesPerUnicodeScalar(
    text: String,
    perUtf16Unit: FloatArray,
    utf16Count: Int,
): List<Float>? {
    if (utf16Count != text.length || perUtf16Unit.size < utf16Count) return null
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<Float>(text.codePointCount(0, text.length))
    var unit = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val width = Character.charCount(cp)
        var advance = 0f
        for (j in unit until unit + width) {
            if (j < utf16Count) advance += perUtf16Unit[j]
        }
        out.add(advance)
        unit += width
        i += width
    }
    return out
}
