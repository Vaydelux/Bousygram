package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem

/** Guard and sequence all Next, Previous, and direct episode transitions identically. */
class PlaybackQueueCoordinator(private val session: PlaybackSession) {
    fun moveTo(
        index: Int,
        onLeaveCurrent: (PlaybackQueueItem) -> Unit,
        onResetAndStop: () -> Unit
    ): Boolean {
        if (session.isStopped() || session.isTransitioning ||
            index !in session.queue.indices || index == session.currentIndex.value
        ) return false
        val current = session.currentItem ?: return false
        onLeaveCurrent(current)
        onResetAndStop()
        return session.goToIndex(index)
    }
}
