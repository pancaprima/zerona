package sh.zeron.zerona.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TranscriptColorTokensProseCardTest {
    @Test
    fun proseCardFillContrastsWithBoardBackground() {
        assertEquals(0xFFFFFFFF.toInt(), TranscriptColorTokens.LIGHT_PROSE_CARD)
        assertEquals(0xFF1C1B22.toInt(), TranscriptColorTokens.DARK_PROSE_CARD)
        assertNotEquals(ZeronaColorTokens.LIGHT_BACKGROUND, TranscriptColorTokens.LIGHT_PROSE_CARD)
        assertNotEquals(ZeronaColorTokens.DARK_BACKGROUND, TranscriptColorTokens.DARK_PROSE_CARD)
    }

    @Test
    fun proseCardBorderIsScopedHairlineNotOutlineVariant() {
        assertEquals(0xFFE2DEE8.toInt(), TranscriptColorTokens.LIGHT_PROSE_CARD_BORDER)
        assertEquals(0xFF3A3844.toInt(), TranscriptColorTokens.DARK_PROSE_CARD_BORDER)
        assertNotEquals(TranscriptColorTokens.LIGHT_PROSE_CARD, TranscriptColorTokens.LIGHT_PROSE_CARD_BORDER)
    }
}
