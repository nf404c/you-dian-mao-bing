package com.fuellog.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionHistoryEasterEggTest {
    @Test fun sevenRapidTapsReachVersionHistory() {
        val state = (1..7).fold(VersionTapState()) { current, index ->
            nextVersionTapState(current, index * 200L)
        }
        assertTrue(state.reachedVersionHistory())
    }

    @Test fun fewerThanSevenRapidTapsDoNotReachVersionHistory() {
        val state = (1..6).fold(VersionTapState()) { current, index ->
            nextVersionTapState(current, index * 200L)
        }
        assertFalse(state.reachedVersionHistory())
    }

    @Test fun delayedTapResetsTheSequence() {
        val initial = nextVersionTapState(VersionTapState(), 100L)
        val reset = nextVersionTapState(initial, 100L + VersionTapWindowMillis + 1)
        assertFalse(reset.reachedVersionHistory())
        assertTrue(reset.count == 1)
    }

    @Test fun outsideDismissWaitsForTheShortOpeningCooldown() {
        val openedAt = 10_000L
        assertFalse(isVersionHistoryOutsideDismissAllowed(openedAt, openedAt + VersionHistoryDismissCooldownMillis - 1))
        assertTrue(isVersionHistoryOutsideDismissAllowed(openedAt, openedAt + VersionHistoryDismissCooldownMillis))
    }
}
