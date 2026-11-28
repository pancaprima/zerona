package sh.zeron.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDeepLinkTest {
    @Test
    fun parsesValidConversationLink() {
        val link = ConversationDeepLinkParser.parse(
            "zeron://open/chat/02a5ed0b-2e43-458a-a269-bd10bd048703?workspace=0460d2d6633300e1",
        )
        assertEquals(
            ConversationDeepLink("02a5ed0b-2e43-458a-a269-bd10bd048703", "0460d2d6633300e1"),
            link,
        )
    }

    @Test
    fun parsesWorkspaceAsSecondParamAndPercentEncodedChatId() {
        val link = ConversationDeepLinkParser.parse(
            "zeron://open/chat/chat%2D123?from=widget&workspace=0460d2d6633300e1#frag",
        )
        assertEquals(ConversationDeepLink("chat-123", "0460d2d6633300e1"), link)
    }

    @Test
    fun rejectsMalformedAndForeignLinks() {
        val bad = listOf(
            null,
            "",
            "https://example.com",
            "zeron://callback?code=1",
            "zeron://open/chat/abc",
            "zeron://open/chat/a/b?workspace=0460d2d6633300e1",
            "zeron://open/chat/%GG?workspace=0460d2d6633300e1",
            "zeron://open/chat/abc?workspace=ZZZZZZZZ",
            "zeron://open/chat/abc?workspace=short",
            "zeron://open/chat/abc def?workspace=0460d2d6633300e1",
            "zeron://open/chat/bad_id?workspace=0460d2d6633300e1",
        )
        for (url in bad) {
            assertNull("expected null for: $url", ConversationDeepLinkParser.parse(url))
        }
    }

    @Test
    fun workspaceLocatorMatchesRustReferenceVectors() {
        assertEquals("0460d2d6633300e1", ConversationDeepLinkParser.workspaceLocator("Synced", "u1", "org_1"))
        assertEquals("0ddcd6f86c3d02d2", ConversationDeepLinkParser.workspaceLocator("Synced", "u1", null))
        assertEquals("0ddcd6f86c3d02d2", ConversationDeepLinkParser.workspaceLocator("Synced", "u1", "  "))
        assertEquals("364e1cb3983b0d4e", ConversationDeepLinkParser.workspaceLocator("Development", "u1", "org_1"))
    }

    @Test
    fun matchesWorkspaceRespectsScopeAndIdentity() {
        assertTrue(
            ConversationDeepLinkParser.matchesWorkspace("0460d2d6633300e1", "u1", "org_1", isDev = false),
        )
        assertFalse(
            ConversationDeepLinkParser.matchesWorkspace("364e1cb3983b0d4e", "u1", "org_1", isDev = false),
        )
        assertFalse(
            ConversationDeepLinkParser.matchesWorkspace("0ddcd6f86c3d02d2", "u1", "org_1", isDev = false),
        )

        assertTrue(
            ConversationDeepLinkParser.matchesWorkspace("0460d2d6633300e1", "u1", "org_1", isDev = true),
        )
        assertTrue(
            ConversationDeepLinkParser.matchesWorkspace("364e1cb3983b0d4e", "u1", "org_1", isDev = true),
        )
        assertFalse(
            ConversationDeepLinkParser.matchesWorkspace("0460d2d6633300e1", "u2", "org_1", isDev = true),
        )
        assertFalse(
            ConversationDeepLinkParser.matchesWorkspace("0460d2d6633300e1", "u1", "org_2", isDev = true),
        )
    }
}