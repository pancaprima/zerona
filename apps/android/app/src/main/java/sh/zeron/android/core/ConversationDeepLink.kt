package sh.zeron.android.core

import sh.zeron.android.fcm.NotificationChatId
import java.security.MessageDigest

data class ConversationDeepLink(val chatId: String, val workspace: String)

object ConversationDeepLinkParser {
    private val WORKSPACE_PATTERN = Regex("^[0-9a-f]{8,64}$")

    fun parse(raw: String?): ConversationDeepLink? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.any { it.isWhitespace() }) return null
        if (!trimmed.startsWith("zeron://open/chat/")) return null
        val rest = trimmed.removePrefix("zeron://open/chat/")
        val qIdx = rest.indexOf('?')
        if (qIdx < 0) return null
        val rawChatId = rest.substring(0, qIdx)
        if (rawChatId.isEmpty() || '/' in rawChatId || '#' in rawChatId) return null
        val decodedChatId = decodePercent(rawChatId) ?: return null
        val chatId = NotificationChatId.validate(decodedChatId) ?: return null
        val rawQuery = rest.substring(qIdx + 1).substringBefore('#')
        val rawWorkspace = rawQuery.split('&').firstOrNull { it.startsWith("workspace=") }?.removePrefix("workspace=")
            ?: return null
        val workspace = decodePercent(rawWorkspace) ?: return null
        if (!WORKSPACE_PATTERN.matches(workspace)) return null
        return ConversationDeepLink(chatId, workspace)
    }

    fun workspaceLocator(scope: String, userId: String, orgId: String?): String {
        val org = orgId?.trim()?.takeIf { it.isNotEmpty() } ?: "personal"
        val input = "$scope\u0000user:$userId:org:$org"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(16)
    }

    fun matchesWorkspace(locator: String, userId: String, orgId: String?, isDev: Boolean): Boolean {
        if (locator == workspaceLocator("Synced", userId, orgId)) return true
        return isDev && locator == workspaceLocator("Development", userId, orgId)
    }

    private fun decodePercent(value: String): String? {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = ArrayList<Byte>(bytes.size)
        var index = 0
        while (index < bytes.size) {
            if (bytes[index] == '%'.code.toByte()) {
                if (index + 2 >= bytes.size) return null
                val hi = hexNibble(bytes[index + 1]) ?: return null
                val lo = hexNibble(bytes[index + 2]) ?: return null
                out.add(((hi shl 4) or lo).toByte())
                index += 3
            } else {
                out.add(bytes[index])
                index += 1
            }
        }
        return try {
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun hexNibble(byte: Byte): Int? {
        val c = byte.toInt() and 0xff
        return when {
            c in '0'.code..'9'.code -> c - '0'.code
            c in 'a'.code..'f'.code -> c - 'a'.code + 10
            c in 'A'.code..'F'.code -> c - 'A'.code + 10
            else -> null
        }
    }
}