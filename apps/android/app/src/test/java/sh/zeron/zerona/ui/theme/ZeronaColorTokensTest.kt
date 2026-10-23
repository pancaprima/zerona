package sh.zeron.zerona.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ZeronaColorTokensTest {
    @Test
    fun lightPrimaryMatchesCatalog() {
        assertEquals(0xFF5C45A8.toInt(), ZeronaColorTokens.LIGHT_PRIMARY)
    }

    @Test
    fun darkOnPrimaryIsDarkInkNotWhite() {
        assertEquals(0xFF1A1228.toInt(), ZeronaColorTokens.DARK_ON_PRIMARY)
    }

    @Test
    fun composerDockUsesSurfaceContainer() {
        assertEquals(ZeronaColorTokens.LIGHT_SURFACE_CONTAINER, ZeronaColorTokens.LIGHT_COMPOSER_DOCK)
        assertEquals(ZeronaColorTokens.DARK_SURFACE_CONTAINER, ZeronaColorTokens.DARK_COMPOSER_DOCK)
    }

    @Test
    fun backgroundAlignsWithCatalogBoard() {
        assertEquals(0xFFEBE8F0.toInt(), ZeronaColorTokens.LIGHT_BACKGROUND)
        assertEquals(0xFF141218.toInt(), ZeronaColorTokens.DARK_BACKGROUND)
    }
}
