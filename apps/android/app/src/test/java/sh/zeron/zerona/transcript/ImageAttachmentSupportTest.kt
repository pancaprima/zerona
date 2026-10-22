package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAttachmentSupportTest {
    @Test
    fun cacheKeyIncludesHost() {
        val a = ImageAttachmentSupport.cacheKey("host-a", "/tmp/a.png")
        val b = ImageAttachmentSupport.cacheKey("host-b", "/tmp/a.png")
        assertFalse(a == b)
    }

    @Test
    fun allowsOpaqueHostPathsAndPending() {
        assertTrue(ImageAttachmentSupport.isAllowedAttachmentReference("/tmp/uploads/x.png"))
        assertTrue(ImageAttachmentSupport.isAllowedAttachmentReference("/tmp/a:b.png"))
        assertTrue(ImageAttachmentSupport.isAllowedAttachmentReference("/path with spaces/x.png"))
        assertTrue(ImageAttachmentSupport.isAllowedAttachmentReference("pending://up-1/file.png"))
        assertTrue(ImageAttachmentSupport.isAllowedAttachmentReference("C:\\Users\\x\\pic.png"))
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference("pending:broken"))
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference("custom-scheme://x"))
    }

    @Test
    fun rejectsControlCharsInReference() {
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference("/tmp/\u0007evil.png"))
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference("/tmp/x\u007F.png"))
    }

    @Test
    fun labFixtureOnlyWhenExplicitlyAllowed() {
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference(LAB_DEMO_IMAGE_REFERENCE))
        assertTrue(
            ImageAttachmentSupport.isAllowedAttachmentReference(
                LAB_DEMO_IMAGE_REFERENCE,
                allowLabFixture = true,
            ),
        )
    }

    @Test
    fun rejectsLinkProtocols() {
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference("https://example.com/x.png"))
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference("file:///sdcard/x.png"))
        assertFalse(ImageAttachmentSupport.isAllowedAttachmentReference("zeron-preview://image?ref=x"))
    }

    @Test
    fun parsesZeronPreviewRef() {
        val ref =
            ImageAttachmentSupport.previewImageReferenceFromUrl(
                "zeron-preview://image?ref=%2Ftmp%2Fuploads%2Fshot.png",
            )
        assertEquals("/tmp/uploads/shot.png", ref)
    }

    @Test
    fun previewPreservesLiteralPlus() {
        val ref =
            ImageAttachmentSupport.previewImageReferenceFromUrl(
                "zeron-preview://image?ref=foo%2Bbar",
            )
        assertEquals("foo+bar", ref)
    }

    @Test
    fun previewDecodesPercentOnce() {
        val ref =
            ImageAttachmentSupport.previewImageReferenceFromUrl(
                "zeron-preview://image?ref=%252Ftmp",
            )
        assertEquals("%2Ftmp", ref)
    }

    @Test
    fun previewRejectsMissingImageHost() {
        assertNull(
            ImageAttachmentSupport.previewImageReferenceFromUrl(
                "zeron-preview://?ref=%2Ftmp",
            ),
        )
    }

    @Test
    fun previewRejectsDuplicateRefParam() {
        assertNull(
            ImageAttachmentSupport.previewImageReferenceFromUrl(
                "zeron-preview://image?ref=a&ref=b",
            ),
        )
    }

    @Test
    fun previewRejectsMalformedPercent() {
        assertNull(
            ImageAttachmentSupport.previewImageReferenceFromUrl(
                "zeron-preview://image?ref=%2",
            ),
        )
    }

    @Test
    fun previewRejectsExtraQueryParams() {
        assertNull(
            ImageAttachmentSupport.previewImageReferenceFromUrl(
                "zeron-preview://image?ref=x&evil=1",
            ),
        )
    }

    @Test
    fun rejectsInvalidPreviewSchemes() {
        assertNull(ImageAttachmentSupport.previewImageReferenceFromUrl("http://evil.test/x"))
        assertNull(ImageAttachmentSupport.previewImageReferenceFromUrl("zeron-preview://other?ref=x"))
    }

    @Test
    fun sniffPngAndJpeg() {
        val png =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            )
        assertEquals("image/png", ImageAttachmentSupport.sniffImageMime(png))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0)
        assertEquals("image/jpeg", ImageAttachmentSupport.sniffImageMime(jpeg))
        assertNull(ImageAttachmentSupport.sniffImageMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(ImageAttachmentSupport.sniffImageMime(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun samplePlanDownscalesLargeEdges() {
        val plan =
            ImageAttachmentSupport.planSampledDecode(
                ImageAttachmentSupport.DecodeBounds(8000, 4000),
            )
        assertNotNull(plan)
        assertTrue(plan!!.inSampleSize >= 4)
        assertTrue(plan.estimatedDecodedBytes <= ImageAttachmentSupport.MAX_DECODED_BYTES)
    }

    @Test
    fun samplePlanRejectsInvalidCapsAndExtremeDimensions() {
        assertNull(
            ImageAttachmentSupport.planSampledDecode(
                ImageAttachmentSupport.DecodeBounds(100, 100),
                maxEdge = 0,
            ),
        )
        assertNull(
            ImageAttachmentSupport.planSampledDecode(
                ImageAttachmentSupport.DecodeBounds(Int.MAX_VALUE, 4),
            ),
        )
        assertNull(
            ImageAttachmentSupport.planSampledDecode(
                ImageAttachmentSupport.DecodeBounds(200_000, 200),
            ),
        )
    }

    @Test
    fun staleLoadGuardIncludesLoaderGeneration() {
        assertTrue(
            ImageAttachmentSupport.shouldApplyLoad(
                "h1",
                "r1",
                2L,
                2L,
                "h1",
                "r1",
                2L,
            ),
        )
        assertFalse(
            ImageAttachmentSupport.shouldApplyLoad(
                "h1",
                "r1",
                2L,
                3L,
                "h1",
                "r1",
                2L,
            ),
        )
        assertFalse(
            ImageAttachmentSupport.shouldApplyLoad(
                "h1",
                "r1",
                2L,
                2L,
                "h1",
                "r1",
                3L,
            ),
        )
        assertFalse(
            ImageAttachmentSupport.shouldApplyLoad(
                "h1",
                "r1",
                2L,
                2L,
                "h2",
                "r1",
                2L,
            ),
        )
    }

    @Test
    fun maxReadBytesUsesCoreCapAndZeroRefusal() {
        val cap = ImageAttachmentSupport.maxReadBytes(8uL * 1024uL * 1024uL)
        assertEquals(8 * 1024 * 1024, cap)
        val full = ImageAttachmentSupport.maxReadBytes(48uL * 1024uL * 1024uL)
        assertEquals(ImageAttachmentSupport.MAX_READ_BYTES, full)
        assertEquals(0, ImageAttachmentSupport.maxReadBytes(0uL))
    }

    @Test
    fun scrollViewportClipAccountsForScrollOffset() {
        val clip = ImageAttachmentSupport.scrollViewportClipRect(0, 120, 400, 800)
        assertEquals(0, clip.left)
        assertEquals(120, clip.top)
        assertEquals(400, clip.right)
        assertEquals(920, clip.bottom)
    }

    @Test
    fun percentDecodeRefHandlesUnicode() {
        assertEquals("café", ImageAttachmentSupport.percentDecodeRef("caf%C3%A9"))
        assertEquals("café", ImageAttachmentSupport.percentDecodeRef("café"))
    }

    @Test
    fun percentDecodeRefHandlesEmoji() {
        val emoji = "😀"
        assertEquals(emoji, ImageAttachmentSupport.percentDecodeRef(emoji))
        assertEquals(emoji, ImageAttachmentSupport.percentDecodeRef("%F0%9F%98%80"))
    }

    @Test
    fun percentDecodeRefRejectsMalformedSequences() {
        assertNull(ImageAttachmentSupport.percentDecodeRef("%C3%28"))
        assertNull(ImageAttachmentSupport.percentDecodeRef("%E9"))
        assertNull(ImageAttachmentSupport.percentDecodeRef("%"))
    }

    @Test
    fun percentDecodeRefPreservesDoubleEncodedSlash() {
        assertEquals("%2Ftmp", ImageAttachmentSupport.percentDecodeRef("%252Ftmp"))
    }

    @Test
    fun percentDecodeRefRejectsUnpairedSurrogate() {
        assertNull(ImageAttachmentSupport.percentDecodeRef("\uD800"))
    }
}
