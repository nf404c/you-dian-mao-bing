package com.fuellog.app

internal const val VersionTapWindowMillis = 1_200L
internal const val VersionTapCount = 7
internal const val VersionHistoryDismissCooldownMillis = 700L

internal data class VersionTapState(val count: Int = 0, val lastTapMillis: Long = 0L)

internal fun nextVersionTapState(state: VersionTapState, nowMillis: Long): VersionTapState {
    val count = if (nowMillis - state.lastTapMillis <= VersionTapWindowMillis) state.count + 1 else 1
    return VersionTapState(count, nowMillis)
}

internal fun VersionTapState.reachedVersionHistory() = count >= VersionTapCount

internal fun isVersionHistoryOutsideDismissAllowed(openedAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - openedAtMillis >= VersionHistoryDismissCooldownMillis
