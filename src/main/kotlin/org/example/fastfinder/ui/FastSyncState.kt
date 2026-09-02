package org.example.fastfinder.ui

/**
 * Bundles the opt-in "fast update tracking" (elevation) UI state as one value rather than five
 * loose parameters threaded through FastFinderApp -> FilterRail -> FastSyncRow - see FastSyncRow
 * in FilterRail.kt for what each field means and why turning it on/off are asymmetric operations.
 */
data class FastSyncState(
    val isElevated: Boolean,
    val fastSyncEnabled: Boolean,
    val isAwaitingElevation: Boolean,
    val onEnable: () -> Unit,
    val onDisable: () -> Unit,
)
