package com.omidgame.mench.core.ui.adaptive

/**
 * Window-width breakpoints (Phase 4A). Matches Material's "expanded" width class (>= 840 dp),
 * so tablets, unfolded foldables and landscape tablets get list + chat side by side.
 * Pure so it is trivially testable; screens read the width from LocalConfiguration, which
 * also updates on rotation and fold changes.
 */
const val TWO_PANE_MIN_WIDTH_DP = 840
const val LIST_PANE_WIDTH_DP = 380

fun isTwoPane(screenWidthDp: Int): Boolean = screenWidthDp >= TWO_PANE_MIN_WIDTH_DP
