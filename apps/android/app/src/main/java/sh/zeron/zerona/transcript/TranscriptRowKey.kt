package sh.zeron.zerona.transcript

/**
 * Stable row keys matching `crates/mobile/src/layout/rows.rs::row_key` (FNV-1a).
 */
object TranscriptRowKey {
    fun rowKey(id: String): ULong {
        var h: ULong = 0xcbf2_9ce4_8422_2325u
        for (b in id.encodeToByteArray()) {
            h = h xor b.toUByte().toULong()
            h *= 0x0100_0000_01b3u
        }
        return h
    }

    fun userMessage(messageId: String): ULong = rowKey("$messageId#u")
}
