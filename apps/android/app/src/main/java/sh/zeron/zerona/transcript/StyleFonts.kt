package sh.zeron.zerona.transcript

import android.graphics.Paint
import android.graphics.Typeface
import sh.zeron.zerona.core.GeistTypefaces
import uniffi.zeron_core.FaceRole
import uniffi.zeron_core.StyleDesc

class StyleFonts {
    data class Entry(
        val typeface: Typeface,
        val size: Float,
        val ligatures: Boolean,
    )

    private val fonts = mutableMapOf<UShort, Entry>()
    var count: Int = 0
        private set

    /**
     * Rebuilds the table when descriptors change. Returns true if paints must be invalidated.
     */
    fun wouldChange(styles: List<StyleDesc>): Boolean {
        if (fonts.size != styles.size) return true
        for (s in styles) {
            val entry =
                Entry(
                    typeface = GeistTypefaces.typeface(s.face),
                    size = s.size,
                    ligatures = s.ligatures,
                )
            if (fonts[s.id] != entry) return true
        }
        return false
    }

    fun update(styles: List<StyleDesc>): Boolean {
        val next = mutableMapOf<UShort, Entry>()
        for (s in styles) {
            next[s.id] =
                Entry(
                    typeface = GeistTypefaces.typeface(s.face),
                    size = s.size,
                    ligatures = s.ligatures,
                )
        }
        val changed =
            fonts.size != next.size ||
                fonts.any { (id, entry) -> next[id] != entry }
        if (changed) {
            fonts.clear()
            fonts.putAll(next)
        }
        count = fonts.size
        return changed
    }

    fun entry(id: UShort): Entry? = fonts[id]

    fun paintFor(
        id: UShort,
        color: Int,
    ): Paint? {
        val e = fonts[id] ?: return null
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = e.typeface
            textSize = e.size
            this.color = color
            setTranscriptLigatures(e.ligatures)
        }
    }

}

internal fun Paint.setTranscriptLigatures(enabled: Boolean) {
    fontFeatureSettings =
        if (enabled) {
            "'liga' 1, 'clig' 1"
        } else {
            "'liga' 0, 'clig' 0"
        }
}
