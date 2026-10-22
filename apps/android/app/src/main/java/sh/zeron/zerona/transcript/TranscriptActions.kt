package sh.zeron.zerona.transcript

import android.content.Context
import android.graphics.Bitmap

interface TranscriptActions {
    fun toggleRow(key: ULong)

    fun toggleDetail(
        row: ULong,
        detail: ULong,
        open: Boolean,
    )

    fun openUrl(
        context: Context,
        url: String,
    )

    fun imageLoader(): SessionImageLoader? = null

    fun showImagePreview(
        context: Context,
        reference: String,
        thumbnail: Bitmap?,
    ) {
    }
}
