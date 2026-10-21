package sh.zeron.zerona.core

import android.graphics.Paint
import android.graphics.Typeface
import uniffi.zeron_core.FaceRole
import uniffi.zeron_core.PlatformMeasurer

class AndroidPlatformMeasurer : PlatformMeasurer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun measure(
        face: FaceRole,
        size: Float,
        ligatures: Boolean,
        text: String,
    ): Float {
        configure(face, size, ligatures)
        return paint.measureText(text)
    }

    override fun measureRun(
        face: FaceRole,
        size: Float,
        ligatures: Boolean,
        text: String,
    ): List<Float> {
        configure(face, size, ligatures)
        val advances = FloatArray(text.length)
        val count = paint.getTextWidths(text, advances)
        if (count != text.length) return emptyList()
        val out = mutableListOf<Float>()
        var i = 0
        while (i < text.length) {
            out.add(advances[i])
            i++
            while (i < text.length && advances[i] == 0f) {
                out.add(0f)
                i++
            }
        }
        return out
    }

    private fun configure(face: FaceRole, size: Float, ligatures: Boolean) {
        paint.textSize = size
        paint.typeface =
            when (face) {
                FaceRole.MONO,
                FaceRole.MONO_MEDIUM,
                FaceRole.MONO_SEMIBOLD,
                FaceRole.MONO_ITALIC,
                -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
        paint.isFakeBoldText =
            face == FaceRole.SANS_BOLD ||
                face == FaceRole.SANS_SEMIBOLD ||
                face == FaceRole.SANS_BOLD_ITALIC ||
                face == FaceRole.SANS_SEMIBOLD_ITALIC ||
                face == FaceRole.MONO_SEMIBOLD
        paint.textSkewX =
            if (
                face == FaceRole.SANS_ITALIC ||
                    face == FaceRole.SANS_MEDIUM_ITALIC ||
                    face == FaceRole.SANS_SEMIBOLD_ITALIC ||
                    face == FaceRole.SANS_BOLD_ITALIC ||
                    face == FaceRole.MONO_ITALIC
            ) {
                -0.25f
            } else {
                0f
            }
        // Ligatures are not modeled on Android Paint; Rust uses bundled faces first.
        @Suppress("UNUSED_VARIABLE")
        val _lig = ligatures
    }
}
