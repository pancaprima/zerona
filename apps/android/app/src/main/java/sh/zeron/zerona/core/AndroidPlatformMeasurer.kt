package sh.zeron.zerona.core

import android.content.Context
import android.graphics.Paint
import sh.zeron.zerona.transcript.setTranscriptLigatures
import uniffi.zeron_core.FaceRole
import uniffi.zeron_core.PlatformMeasurer

class AndroidPlatformMeasurer(
    context: Context,
) : PlatformMeasurer {
    private val paints = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG) }

    init {
        GeistTypefaces.ensureLoaded(context)
    }

    override fun measure(
        face: FaceRole,
        size: Float,
        ligatures: Boolean,
        text: String,
    ): Float {
        val paint = configure(face, size, ligatures)
        return paint.measureText(text)
    }

    override fun measureRun(
        face: FaceRole,
        size: Float,
        ligatures: Boolean,
        text: String,
    ): List<Float> {
        val paint = configure(face, size, ligatures)
        val advances = FloatArray(text.length)
        val count = paint.getTextWidths(text, advances)
        return advancesPerUnicodeScalar(text, advances, count) ?: emptyList()
    }

    private fun configure(
        face: FaceRole,
        size: Float,
        ligatures: Boolean,
    ): Paint {
        val paint = paints.get()
        paint.textSize = size
        paint.typeface = GeistTypefaces.typeface(face)
        paint.isFakeBoldText = false
        paint.textSkewX = 0f
        paint.setTranscriptLigatures(ligatures)
        return paint
    }
}
