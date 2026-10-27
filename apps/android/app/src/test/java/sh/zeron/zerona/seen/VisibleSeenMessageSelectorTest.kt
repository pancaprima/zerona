package sh.zeron.zerona.seen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibleSeenMessageSelectorTest {
    private val entryIds = mapOf(0 to "a", 1 to "b", 2 to "c")
    private val times = mapOf("a" to 100L, "b" to 300L, "c" to 200L)

    @Test
    fun picksNewestByMessageAt() {
        val result =
            VisibleSeenMessageSelector.newest(
                placementIndices = listOf(0, 1, 2),
                entryIdForIndex = { entryIds[it] },
                createdAtMs = { times[it] },
            )
        assertEquals(VisibleSeenMessage("b", 300L), result)
    }

    @Test
    fun skipsRowsMarkedByPredicate() {
        val result =
            VisibleSeenMessageSelector.newest(
                placementIndices = listOf(0),
                entryIdForIndex = { entryIds[it] },
                createdAtMs = { times[it] },
            )
        assertEquals(VisibleSeenMessage("a", 100L), result)
    }

    @Test
    fun returnsNullWhenNothingResolvable() {
        assertNull(
            VisibleSeenMessageSelector.newest(
                placementIndices = emptyList(),
                entryIdForIndex = { entryIds[it] },
                createdAtMs = { times[it] },
            ),
        )
    }

    @Test
    fun filterVisible_includesRowInsideViewport() {
        val indices =
            VisibleSeenMessageSelector.filterVisible(
                placements = listOf(PlacementBounds(0, 100f, 40f)),
                viewportTop = 80f,
                viewportBottom = 200f,
            )
        assertEquals(listOf(0), indices)
    }

    @Test
    fun filterVisible_excludesRowFullyBelowViewport() {
        val indices =
            VisibleSeenMessageSelector.filterVisible(
                placements = listOf(PlacementBounds(1, 900f, 40f)),
                viewportTop = 100f,
                viewportBottom = 500f,
            )
        assertEquals(emptyList<Int>(), indices)
    }

    @Test
    fun filterVisible_excludesRowFullyAboveViewport() {
        val indices =
            VisibleSeenMessageSelector.filterVisible(
                placements = listOf(PlacementBounds(2, 10f, 30f)),
                viewportTop = 100f,
                viewportBottom = 500f,
            )
        assertEquals(emptyList<Int>(), indices)
    }

    @Test
    fun filterVisible_includesPartiallyVisibleTallRow() {
        val indices =
            VisibleSeenMessageSelector.filterVisible(
                placements = listOf(PlacementBounds(3, 460f, 80f)),
                viewportTop = 100f,
                viewportBottom = 500f,
                minVisibleDp = 24f,
            )
        assertEquals(listOf(3), indices)
    }

    @Test
    fun filterVisible_excludesSliverBelowMinVisible() {
        val indices =
            VisibleSeenMessageSelector.filterVisible(
                placements = listOf(PlacementBounds(4, 495f, 80f)),
                viewportTop = 100f,
                viewportBottom = 500f,
                minVisibleDp = 24f,
            )
        assertEquals(emptyList<Int>(), indices)
    }

    @Test
    fun filterVisible_includesShortRowWhenFullyVisible() {
        val indices =
            VisibleSeenMessageSelector.filterVisible(
                placements = listOf(PlacementBounds(5, 120f, 12f)),
                viewportTop = 100f,
                viewportBottom = 500f,
                minVisibleDp = 24f,
            )
        assertEquals(listOf(5), indices)
    }

    @Test
    fun newestAfterFilter_ignoresOverscanBelowViewport() {
        val bounds =
            listOf(
                PlacementBounds(0, 200f, 40f),
                PlacementBounds(1, 1200f, 40f),
            )
        val visible =
            VisibleSeenMessageSelector.filterVisible(
                placements = bounds,
                viewportTop = 100f,
                viewportBottom = 500f,
            )
        val result =
            VisibleSeenMessageSelector.newest(
                placementIndices = visible,
                entryIdForIndex = { entryIds[it] },
                createdAtMs = { times[it] },
            )
        assertEquals(VisibleSeenMessage("a", 100L), result)
    }
}
