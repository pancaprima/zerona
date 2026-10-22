package sh.zeron.zerona.transcript

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.math.max

/**
 * Lab-only fixture path (absolute). Embedded in debug entry trailer syntax from
 * `attachments.rs` (`- /path` lines). Never sent to [uniffi.zeron_core.CoreClient.readAttachment].
 */
const val LAB_DEMO_IMAGE_REFERENCE = "/__zerona_android_lab__/fixture.png"

private const val MAX_PREVIEW_URL_LENGTH = 8_192
private const val MAX_ATTACHMENT_REFERENCE_LENGTH = 16_384

data class ViewportClip(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object ImageAttachmentSupport {
    const val MAX_DECODE_EDGE_PX = 2048
    const val MAX_DECODED_BYTES = 16 * 1024 * 1024
    const val MAX_READ_BYTES = 24 * 1024 * 1024
    const val MAX_SOURCE_EDGE_PX = 100_000
    const val MAX_SOURCE_PIXELS = 268_000_000L

    val DEFAULT_MAX_ATTACHMENT_BYTES: ULong = 24uL * 1024uL * 1024uL

    fun maxReadBytes(coreMaxAttachmentBytes: ULong): Int {
        if (coreMaxAttachmentBytes == 0uL) return 0
        val capped =
            if (coreMaxAttachmentBytes > MAX_READ_BYTES.toULong()) {
                MAX_READ_BYTES.toULong()
            } else {
                coreMaxAttachmentBytes
            }
        val asLong = capped.toLong()
        return asLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun cacheKey(
        hostDeviceId: String,
        reference: String,
    ): String = "$hostDeviceId\u0000$reference"

    fun isLabDemoReference(reference: String): Boolean = reference == LAB_DEMO_IMAGE_REFERENCE

    /**
     * Paths allowed for host `readAttachment` (opaque host paths, `pending://`, etc.).
     * Rejects URI protocols used for links, not attachment storage.
     */
    fun isAllowedAttachmentReference(
        reference: String,
        allowLabFixture: Boolean = false,
    ): Boolean {
        if (reference.isBlank() || reference.length > MAX_ATTACHMENT_REFERENCE_LENGTH) return false
        if (referenceHasControlChar(reference)) return false
        if (isLabDemoReference(reference)) return allowLabFixture
        val scheme = attachmentUriScheme(reference) ?: return true
        return when (scheme) {
            "pending" -> reference.startsWith("pending://")
            "http", "https", "file", "content", "zeron-preview" -> false
            else -> false
        }
    }

    /** `zeron-preview://image?ref=…` internal preview only — never ACTION_VIEW. */
    fun previewImageReferenceFromUrl(url: String): String? {
        if (url.length > MAX_PREVIEW_URL_LENGTH) return null
        val uri =
            try {
                URI(url)
            } catch (_: URISyntaxException) {
                return null
            }
        if (uri.scheme?.lowercase() != "zeron-preview") return null
        if (uri.rawUserInfo != null) return null
        if (uri.port != -1) return null
        if (uri.rawFragment != null) return null
        val rawPath = uri.rawPath
        if (!rawPath.isNullOrEmpty() && rawPath != "/") return null
        val host = uri.host ?: return null
        if (host.lowercase() != "image") return null
        val query = uri.rawQuery ?: return null
        val decoded = parseSingleRefQueryValue(query) ?: return null
        if (decoded.length > MAX_ATTACHMENT_REFERENCE_LENGTH) return null
        if (referenceHasControlChar(decoded)) return null
        return decoded
    }

    fun sniffImageMime(bytes: ByteArray): String? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 6 &&
            bytes[0] == 0x47.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x38.toByte() &&
            (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) &&
            bytes[5] == 0x61.toByte()
        ) {
            return "image/gif"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) {
            return "image/webp"
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0x42.toByte() &&
            bytes[1] == 0x4D.toByte()
        ) {
            return "image/bmp"
        }
        return null
    }

    data class DecodeBounds(
        val width: Int,
        val height: Int,
    )

    data class SamplePlan(
        val inSampleSize: Int,
        val estimatedDecodedBytes: Long,
    )

    fun planSampledDecode(
        bounds: DecodeBounds,
        maxEdge: Int = MAX_DECODE_EDGE_PX,
        maxDecodedBytes: Int = MAX_DECODED_BYTES,
    ): SamplePlan? {
        if (maxEdge <= 0 || maxDecodedBytes <= 0) return null
        val w = bounds.width.toLong()
        val h = bounds.height.toLong()
        if (w <= 0 || h <= 0) return null
        val longEdge = max(bounds.width, bounds.height)
        if (longEdge > MAX_SOURCE_EDGE_PX) return null
        if (w > MAX_SOURCE_EDGE_PX || h > MAX_SOURCE_EDGE_PX) return null
        if (w * h > MAX_SOURCE_PIXELS) return null

        var sample = 1L
        while (longEdge / sample > maxEdge) {
            sample *= 2
            if (sample > 256) return null
        }
        var estimated = decodedArgbBytes(bounds.width, bounds.height, sample)
        while (estimated > maxDecodedBytes.toLong() && sample < 256) {
            sample *= 2
            estimated = decodedArgbBytes(bounds.width, bounds.height, sample)
        }
        if (estimated > maxDecodedBytes.toLong()) return null
        val sampleInt = sample.coerceAtMost(256).toInt().coerceAtLeast(1)
        return SamplePlan(inSampleSize = sampleInt, estimatedDecodedBytes = estimated)
    }

    fun shouldApplyLoad(
        expectedHostId: String,
        expectedReference: String,
        expectedGeneration: Long,
        loaderGeneration: Long,
        actualHostId: String?,
        actualReference: String?,
        actualBoundGeneration: Long,
    ): Boolean =
        expectedGeneration == loaderGeneration &&
            expectedGeneration == actualBoundGeneration &&
            expectedHostId == actualHostId &&
            expectedReference == actualReference

    fun bitmapByteSize(
        width: Int,
        height: Int,
    ): Long = width.toLong() * height.toLong() * 4L

    /** Clip rect in nested scroll child coordinates (accounts for scroll offset). */
    fun scrollViewportClipRect(
        scrollX: Int,
        scrollY: Int,
        width: Int,
        height: Int,
    ): ViewportClip =
        ViewportClip(scrollX, scrollY, scrollX + width, scrollY + height)

    private fun referenceHasControlChar(reference: String): Boolean =
        reference.any { it.code in 0..0x1F || it.code == 0x7F }

    private fun decodedArgbBytes(
        width: Int,
        height: Int,
        sample: Long,
    ): Long {
        val sw = ((width + sample - 1) / sample).coerceAtLeast(1)
        val sh = ((height + sample - 1) / sample).coerceAtLeast(1)
        return sw * sh * 4L
    }

    /**
     * Returns a lowercase scheme when [reference] is a URI with scheme chars
     * `[A-Za-z0-9.-]+`; `null` when the ref is an opaque host path (including
     * Windows `C:\…` and absolute paths like `/tmp/a:b.png`).
     */
    internal fun attachmentUriScheme(reference: String): String? {
        if (isWindowsDrivePath(reference)) return null
        val colon = reference.indexOf(':')
        if (colon <= 0) return null
        val scheme = reference.substring(0, colon)
        if (scheme.isEmpty() || !scheme.all { isUriSchemeChar(it) }) return null
        return scheme.lowercase()
    }

    private fun isUriSchemeChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '.' || c == '-'

    internal fun isWindowsDrivePath(reference: String): Boolean {
        if (reference.length < 3) return false
        val first = reference[0]
        if (!first.isLetter()) return false
        return reference[1] == ':' && (reference[2] == '\\' || reference[2] == '/')
    }

    /**
     * Parses `ref` from a raw query string (no leading `?`). Percent-decodes once,
     * preserving literal `+` (matches `rows.rs` `image_preview_link` / desktop path encoder).
     */
    internal fun parseSingleRefQueryValue(rawQuery: String): String? {
        if (rawQuery.isEmpty()) return null
        var refCount = 0
        var refValue: String? = null
        var start = 0
        while (start <= rawQuery.length) {
            val amp = rawQuery.indexOf('&', start).let { if (it < 0) rawQuery.length else it }
            val segment = rawQuery.substring(start, amp)
            if (segment.isNotEmpty()) {
                val eq = segment.indexOf('=')
                if (eq < 0) return null
                val name = segment.substring(0, eq)
                if (name == "ref") {
                    refCount++
                    if (refCount > 1) return null
                    refValue = segment.substring(eq + 1)
                } else if (name.isNotEmpty()) {
                    return null
                }
            }
            start = amp + 1
        }
        val encoded = refValue ?: return null
        return percentDecodeRef(encoded)
    }

    internal fun percentDecodeRef(encoded: String): String? {
        val out = ByteArrayOutputStream(encoded.length.coerceAtLeast(16))
        var i = 0
        while (i < encoded.length) {
            when (val c = encoded[i]) {
                '%' -> {
                    if (i + 2 >= encoded.length) return null
                    val hi = hexValue(encoded[i + 1]) ?: return null
                    val lo = hexValue(encoded[i + 2]) ?: return null
                    out.write((hi shl 4) or lo)
                    i += 3
                }
                else -> {
                    if (c.isLowSurrogate()) return null
                    if (c.isHighSurrogate()) {
                        if (i + 1 >= encoded.length) return null
                        val low = encoded[i + 1]
                        if (!low.isLowSurrogate()) return null
                        val chars = charArrayOf(c, low)
                        out.write(String(chars).toByteArray(Charsets.UTF_8))
                        i += 2
                    } else {
                        out.write(c.toString().toByteArray(Charsets.UTF_8))
                        i++
                    }
                }
            }
        }
        val decoder =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(out.toByteArray())).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun hexValue(c: Char): Int? =
        when (c) {
            in '0'..'9' -> c.code - '0'.code
            in 'a'..'f' -> c.code - 'a'.code + 10
            in 'A'..'F' -> c.code - 'A'.code + 10
            else -> null
        }
}
