package com.fuellog.app

import com.fuellog.app.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSelectionTest {
    @Test fun deletingNonCurrentKeepsCurrent() {
        assertEquals(1, nextActiveVehicleId(2, 1, listOf(Vehicle(1, "A"))))
    }

    @Test fun deletingCurrentSelectsRemainingVehicle() {
        assertEquals(2, nextActiveVehicleId(1, 1, listOf(Vehicle(2, "B"))))
    }

    @Test fun deletingLastVehicleClearsSelection() {
        assertEquals(-1, nextActiveVehicleId(1, 1, emptyList()))
    }

    @Test fun homeVehicleSwipeNeedsTheFullDistanceThreshold() {
        assertFalse(hasReachedHomeVehicleSwipeThreshold(655f, 1_000f))
        assertTrue(hasReachedHomeVehicleSwipeThreshold(656f, 1_000f))
        assertFalse(hasReachedHomeSwipeThreshold(-655f, 1_000f))
        assertTrue(hasReachedHomeSwipeThreshold(-656f, 1_000f))
    }

    @Test fun sidePagesNeedTheSameDistanceToReturnHome() {
        assertFalse(hasReachedSidePageReturnSwipeThreshold(-655f, 1_000f, -1f))
        assertTrue(hasReachedSidePageReturnSwipeThreshold(-656f, 1_000f, -1f))
        assertFalse(hasReachedSidePageReturnSwipeThreshold(655f, 1_000f, 1f))
        assertTrue(hasReachedSidePageReturnSwipeThreshold(656f, 1_000f, 1f))
    }

    @Test fun homeVerticalNavigationUsesTheSameThreshold() {
        assertFalse(hasReachedHomeVerticalSwipeThreshold(655f, 1_000f))
        assertTrue(hasReachedHomeVerticalSwipeThreshold(656f, 1_000f))
        assertTrue(hasReachedHomeVerticalSwipeThreshold(-656f, 1_000f))
    }

    @Test fun addRecordVerticalReturnUsesTheSamePhysicalThreshold() {
        assertEquals(656f, verticalPageReturnThresholdPx(1_000f), 0.001f)
    }

    @Test fun verticalReturnsTravelTheFullVisiblePageHeightAfterTriggering() {
        assertEquals(-2_400f, verticalPageFullExitOffsetPx(2_400f, -1f), 0.001f)
        assertEquals(2_400f, verticalPageFullExitOffsetPx(2_400f, 1f), 0.001f)
    }

    @Test fun historyReturnRequiresTheLazyListToBeStrictlyAtTop() {
        assertTrue(isHistoryListAtTop(0, 0, canScrollBackward = false))
        assertFalse(isHistoryListAtTop(0, 1, canScrollBackward = false))
        assertFalse(isHistoryListAtTop(1, 0, canScrollBackward = false))
        assertFalse(isHistoryListAtTop(0, 0, canScrollBackward = true))
    }

    @Test fun navigationHintsFadeInAndOutBeforeThePageSwitches() {
        assertEquals(0f, navigationHintAlpha(0.08f), 0.001f)
        assertEquals(1f, navigationHintAlpha(0.30f), 0.001f)
        assertEquals(1f, navigationHintAlpha(0.75f), 0.001f)
        assertEquals(0f, navigationHintAlpha(1f), 0.001f)
    }

    @Test fun verticalReturnHintKeepsItsLateFadeForTheSettlePhase() {
        assertEquals(0.864f, verticalReturnHintAlpha(0.62f), 0.001f)
        assertEquals(0.341f, verticalReturnHintAlpha(0.85f), 0.001f)
        assertEquals(0f, verticalReturnHintAlpha(1f), 0.001f)
    }

    @Test fun verticalHomeNavigationHintAlsoUsesALongerLateFade() {
        assertEquals(0.735f, verticalNavigationHintAlpha(0.75f), 0.001f)
        assertEquals(0f, verticalNavigationHintAlpha(1f), 0.001f)
    }

    @Test fun hintProgressUsesTheFullSettleDistanceNotTheTriggerDistance() {
        assertEquals(0.5f, navigationSwipeProgress(656f, 1_000f), 0.001f)
        assertEquals(1f, navigationSwipeProgress(1_312f, 1_000f), 0.001f)
    }

    @Test fun historyRecordSwipeThresholdRemainsIndependent() {
        assertEquals(0.82f, SwipeTriggerFraction, 0.001f)
        assertEquals(0.656f, NavigationSwipeTriggerFraction, 0.001f)
        assertEquals(656f, homeVerticalEntryThresholdPx(1_000f), 0.001f)
    }

    @Test fun vehicleRenameCopyKeepsItsIdentityAndCreationTime() {
        val original = Vehicle(id = 42, name = "旧名称", createdAt = 123L)
        val updated = original.copy(name = "新名称")

        assertEquals(42L, updated.id)
        assertEquals(123L, updated.createdAt)
        assertEquals("新名称", updated.name)
    }
}
