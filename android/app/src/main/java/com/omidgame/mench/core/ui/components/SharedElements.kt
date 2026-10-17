package com.omidgame.mench.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Phase 3C — shared-element transitions. The navigation graph provides these two scopes; any composable can then mark
 * an element that should glide between two screens by giving both sides the same key. Without the scopes (previews,
 * tests, dialogs) the modifier does nothing, so it is always safe to use.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Phase 3B: ids of conversations with unseen stories; the conversation list provides it and UserAvatar draws the ring. */
val LocalUnseenStories = compositionLocalOf<Set<String>> { emptySet() }

/** Marks a person's avatar as a shared element: it flies from the conversation list row into the chat header and back. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAvatar(key: String?): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    if (key == null || sharedScope == null || visibilityScope == null) return this
    val self = this
    return with(sharedScope) {
        self.sharedElement(rememberSharedContentState(key = "avatar-$key"), visibilityScope)
    }
}
