package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.zeron.zerona.ui.theme.ZeronaColorTokens

class PaletteBackgroundTest {
    @Test
    fun lightBackgroundMatchesComposeRoot() {
        assertEquals(ZeronaColorTokens.backgroundArgb(false), Palette.background(false))
    }

    @Test
    fun darkBackgroundMatchesComposeRoot() {
        assertEquals(ZeronaColorTokens.backgroundArgb(true), Palette.background(true))
    }
}
