package sh.zeron.zerona.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private fun assertAa(
        foreground: Int,
        background: Int,
    ) {
        val ratio = Contrast.contrastRatio(foreground, background)
        assertTrue(
            "expected AA normal text on bg ${background.toUInt()} fg ${foreground.toUInt()} ratio=$ratio",
            Contrast.meetsAaNormalText(ratio),
        )
    }

    @Test
    fun lightOnPrimaryOnPrimaryPassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_ON_PRIMARY, ZeronaColorTokens.LIGHT_PRIMARY)
    }

    @Test
    fun darkOnPrimaryOnPrimaryPassesAa() {
        assertAa(ZeronaColorTokens.DARK_ON_PRIMARY, ZeronaColorTokens.DARK_PRIMARY)
    }

    @Test
    fun lightOnSurfaceOnSurfacePassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_ON_SURFACE, ZeronaColorTokens.LIGHT_SURFACE)
    }

    @Test
    fun darkOnSurfaceOnSurfacePassesAa() {
        assertAa(ZeronaColorTokens.DARK_ON_SURFACE, ZeronaColorTokens.DARK_SURFACE)
    }

    @Test
    fun lightOnBackgroundOnBackgroundPassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_ON_SURFACE, ZeronaColorTokens.LIGHT_BACKGROUND)
    }

    @Test
    fun darkOnBackgroundOnBackgroundPassesAa() {
        assertAa(ZeronaColorTokens.DARK_ON_SURFACE, ZeronaColorTokens.DARK_BACKGROUND)
    }

    @Test
    fun lightOnSurfaceVariantOnSurfacePassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_ON_SURFACE_VARIANT, ZeronaColorTokens.LIGHT_SURFACE)
    }

    @Test
    fun darkOnSurfaceVariantOnSurfacePassesAa() {
        assertAa(ZeronaColorTokens.DARK_ON_SURFACE_VARIANT, ZeronaColorTokens.DARK_SURFACE)
    }

    @Test
    fun lightOnTertiaryOnTertiaryPassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_ON_TERTIARY, ZeronaColorTokens.LIGHT_TERTIARY)
    }

    @Test
    fun darkOnTertiaryOnTertiaryPassesAa() {
        assertAa(ZeronaColorTokens.DARK_ON_TERTIARY, ZeronaColorTokens.DARK_TERTIARY)
    }

    @Test
    fun lightSecondaryBadgeTextOnContainerPassesAa() {
        assertAa(
            ZeronaColorTokens.LIGHT_ON_SECONDARY_CONTAINER,
            ZeronaColorTokens.LIGHT_SECONDARY_CONTAINER,
        )
    }

    @Test
    fun liveBadgeLightPassesAa() {
        assertAa(
            ZeronaColorTokens.LIGHT_ON_TERTIARY_CONTAINER,
            ZeronaColorTokens.LIGHT_TERTIARY_CONTAINER,
        )
    }

    @Test
    fun liveBadgeDarkPassesAa() {
        assertAa(
            ZeronaColorTokens.DARK_ON_TERTIARY_CONTAINER,
            ZeronaColorTokens.DARK_TERTIARY_CONTAINER,
        )
    }

    @Test
    fun lightErrorPairsPassAa() {
        assertAa(ZeronaColorTokens.LIGHT_ON_ERROR, ZeronaColorTokens.LIGHT_ERROR)
        assertAa(ZeronaColorTokens.LIGHT_ON_ERROR_CONTAINER, ZeronaColorTokens.LIGHT_ERROR_CONTAINER)
        assertAa(ZeronaColorTokens.LIGHT_ERROR, ZeronaColorTokens.LIGHT_SURFACE)
    }

    @Test
    fun darkErrorPairsPassAa() {
        assertAa(ZeronaColorTokens.DARK_ON_ERROR, ZeronaColorTokens.DARK_ERROR)
        assertAa(ZeronaColorTokens.DARK_ON_ERROR_CONTAINER, ZeronaColorTokens.DARK_ERROR_CONTAINER)
        assertAa(ZeronaColorTokens.DARK_ERROR, ZeronaColorTokens.DARK_SURFACE)
    }

    @Test
    fun lightPreviewStagePassesAa() {
        assertAa(
            ZeronaColorTokens.previewOnSurfaceArgb(false),
            ZeronaColorTokens.previewBackdropArgb(false),
        )
        assertAa(
            ZeronaColorTokens.previewLinkArgb(false),
            ZeronaColorTokens.previewBackdropArgb(false),
        )
    }

    @Test
    fun darkPreviewStagePassesAa() {
        assertAa(
            ZeronaColorTokens.previewOnSurfaceArgb(true),
            ZeronaColorTokens.previewBackdropArgb(true),
        )
        assertAa(
            ZeronaColorTokens.previewLinkArgb(true),
            ZeronaColorTokens.previewBackdropArgb(true),
        )
    }

    @Test
    fun fencedCodeSyntaxOnBlockBackgroundPassesAa() {
        val bg = TranscriptColorTokens.CODE_BLOCK_BACKGROUND
        assertAa(TranscriptColorTokens.CODE_BLOCK_TEXT, bg)
        assertAa(TranscriptColorTokens.SYNTAX_KEYWORD, bg)
        assertAa(TranscriptColorTokens.SYNTAX_STRING, bg)
        assertAa(TranscriptColorTokens.SYNTAX_COMMENT, bg)
        assertAa(TranscriptColorTokens.SYNTAX_NUMBER, bg)
        assertAa(TranscriptColorTokens.SYNTAX_FUNCTION, bg)
        assertAa(TranscriptColorTokens.SYNTAX_TYPE, bg)
        assertAa(TranscriptColorTokens.SYNTAX_VARIABLE, bg)
        assertAa(TranscriptColorTokens.SYNTAX_PROPERTY, bg)
        assertAa(TranscriptColorTokens.SYNTAX_OPERATOR, bg)
        assertAa(TranscriptColorTokens.SYNTAX_TAG, bg)
        assertAa(TranscriptColorTokens.SYNTAX_ATTRIBUTE, bg)
        assertAa(TranscriptColorTokens.SYNTAX_ESCAPE, bg)
    }

    @Test
    fun inlineCodePassesAaInBothThemes() {
        assertAa(
            TranscriptColorTokens.LIGHT_INLINE_CODE_TEXT,
            TranscriptColorTokens.LIGHT_INLINE_CODE_BACKGROUND,
        )
        assertAa(
            TranscriptColorTokens.DARK_INLINE_CODE_TEXT,
            TranscriptColorTokens.DARK_INLINE_CODE_BACKGROUND,
        )
    }

    @Test
    fun dangerOnProseBackgroundPassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_ERROR, ZeronaColorTokens.LIGHT_BACKGROUND)
        assertAa(ZeronaColorTokens.DARK_ERROR, ZeronaColorTokens.DARK_BACKGROUND)
    }

    @Test
    fun successOnProseBackgroundPassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_SUCCESS, ZeronaColorTokens.LIGHT_BACKGROUND)
        assertAa(ZeronaColorTokens.DARK_SUCCESS, ZeronaColorTokens.DARK_BACKGROUND)
    }

    @Test
    fun lightPrimaryOnProseAndSectionContainerPassesAa() {
        assertAa(ZeronaColorTokens.LIGHT_PRIMARY, ZeronaColorTokens.LIGHT_BACKGROUND)
        assertAa(ZeronaColorTokens.LIGHT_PRIMARY, ZeronaColorTokens.LIGHT_SURFACE_CONTAINER)
    }

    @Test
    fun lightLinkAccentOnProseBackgroundPassesAa() {
        assertAa(ZeronaColorTokens.linkAccentArgb(false), ZeronaColorTokens.LIGHT_BACKGROUND)
    }

    @Test
    fun proseCardBodyTextPassesAaInBothThemes() {
        assertAa(ZeronaColorTokens.LIGHT_ON_SURFACE, TranscriptColorTokens.LIGHT_PROSE_CARD)
        assertAa(ZeronaColorTokens.DARK_ON_SURFACE, TranscriptColorTokens.DARK_PROSE_CARD)
    }

    @Test
    fun proseCardSecondaryAndLinkForegroundPassAaOnCardFill() {
        assertAa(
            ZeronaColorTokens.LIGHT_ON_SURFACE_VARIANT,
            TranscriptColorTokens.LIGHT_PROSE_CARD,
        )
        assertAa(
            ZeronaColorTokens.DARK_ON_SURFACE_VARIANT,
            TranscriptColorTokens.DARK_PROSE_CARD,
        )
        assertAa(
            ZeronaColorTokens.linkAccentArgb(false),
            TranscriptColorTokens.LIGHT_PROSE_CARD,
        )
        assertAa(
            ZeronaColorTokens.linkAccentArgb(true),
            TranscriptColorTokens.DARK_PROSE_CARD,
        )
    }

    /** Decorative hairline borders are not body text — AA 4.5:1 would force a heavy stroke. */
    @Test
    fun proseCardBorderIsVisibleButNotAaBodyTextOnFill() {
        fun assertHairlineSeparates(border: Int, fill: Int) {
            org.junit.Assert.assertNotEquals(border, fill)
            val ratio = Contrast.contrastRatio(border, fill)
            org.junit.Assert.assertTrue(
                "hairline should read on card fill without AA body-text contrast (ratio=$ratio)",
                ratio >= 1.15 && ratio < Contrast.AA_NORMAL_TEXT_MIN,
            )
        }
        assertHairlineSeparates(
            TranscriptColorTokens.LIGHT_PROSE_CARD_BORDER,
            TranscriptColorTokens.LIGHT_PROSE_CARD,
        )
        assertHairlineSeparates(
            TranscriptColorTokens.DARK_PROSE_CARD_BORDER,
            TranscriptColorTokens.DARK_PROSE_CARD,
        )
    }

    @Test
    fun transcriptTertiaryReadableOnProseAndChipPassesAa() {
        assertAa(
            TranscriptColorTokens.textTertiaryArgb(false),
            ZeronaColorTokens.LIGHT_BACKGROUND,
        )
        assertAa(
            TranscriptColorTokens.textTertiaryArgb(false),
            ZeronaColorTokens.LIGHT_SURFACE_VARIANT,
        )
        assertAa(
            TranscriptColorTokens.textTertiaryArgb(true),
            ZeronaColorTokens.DARK_BACKGROUND,
        )
        assertAa(
            TranscriptColorTokens.textTertiaryArgb(true),
            ZeronaColorTokens.DARK_SURFACE_CONTAINER_HIGH,
        )
    }

    @Test
    fun transcriptFaintReadableOnProseAndChipPassesAa() {
        assertAa(
            TranscriptColorTokens.textFaintArgb(false),
            ZeronaColorTokens.LIGHT_BACKGROUND,
        )
        assertAa(
            TranscriptColorTokens.textFaintArgb(false),
            ZeronaColorTokens.LIGHT_SURFACE_VARIANT,
        )
        assertAa(
            TranscriptColorTokens.textFaintArgb(true),
            ZeronaColorTokens.DARK_BACKGROUND,
        )
        assertAa(
            TranscriptColorTokens.textFaintArgb(true),
            ZeronaColorTokens.DARK_SURFACE_CONTAINER_HIGH,
        )
    }
}
