package sh.zeron.zerona.transcript

interface TranscriptActions {
    fun toggleRow(key: ULong)

    fun toggleDetail(
        row: ULong,
        detail: ULong,
        open: Boolean,
    )

    fun openUrl(url: String)
}
