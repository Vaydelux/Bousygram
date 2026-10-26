package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import com.vaydelux.bousygram.presentation.navigation.AppScreen
import com.vaydelux.bousygram.presentation.navigation.BousygramRouter
import com.vaydelux.bousygram.presentation.navigation.unwrapNonPlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionEpisodeTransitionTest {

    private fun createQueue(count: Int = 4): List<PlaybackQueueItem> =
        (1..count).map { episodeNum ->
            PlaybackQueueItem(
                chatId = 777L,
                messageId = 1000L + episodeNum,
                title = "Episode $episodeNum",
                fileId = 500 + episodeNum,
                durationSeconds = 1800,
                fileSizeBytes = 350_000_000L
            )
        }

    @Test
    fun singlePlayerDestinationInPlaceEpisodeSwap() {
        val queue = createQueue(3)
        val session = PlaybackSession(queue = queue, initialIndex = 0)

        // Initial state: Episode 1
        assertEquals(0, session.currentIndex.value)
        assertEquals("Episode 1", session.currentItem?.title)

        // Episode 1 finishes playing -> onPlaying releases any transition lock
        session.onPlaying()
        assertFalse(session.isTransitioning)

        // Transition to Episode 2: media item is swapped in place inside existing session
        val nextTriggered = session.next()
        assertTrue(nextTriggered)
        assertEquals(1, session.currentIndex.value)
        assertEquals("Episode 2", session.currentItem?.title)
        assertEquals(PlaybackState.Preparing(queue[1]), session.state.value)
        assertTrue(session.isTransitioning)

        // Episode 2 becomes ready and starts playing
        session.onPlaying()
        assertFalse(session.isTransitioning)
        assertEquals(PlaybackState.Playing(queue[1]), session.state.value)

        // Transition to Episode 3: media item swapped in place again
        val nextTriggered2 = session.next()
        assertTrue(nextTriggered2)
        assertEquals(2, session.currentIndex.value)
        assertEquals("Episode 3", session.currentItem?.title)
    }

    @Test
    fun doubleTriggerAutoNextIsGuardedWhileTransitioning() {
        val queue = createQueue(4)
        val session = PlaybackSession(queue = queue, initialIndex = 0)

        session.onPlaying()
        assertFalse(session.isTransitioning)

        // First trigger: auto-next or user pressing Next
        val firstNext = session.next()
        assertTrue("First Next call must succeed", firstNext)
        assertEquals(1, session.currentIndex.value)
        assertTrue("Transition must be marked as in flight", session.isTransitioning)

        // Second trigger at the exact same moment (e.g. double tap, or EOF firing twice)
        val secondNext = session.next()
        assertFalse("Second Next call during transition must be guarded and rejected", secondNext)
        assertEquals("Queue index must NOT double advance", 1, session.currentIndex.value)

        // Third trigger via onAutoNext
        val autoNextAttempt = session.onAutoNext()
        assertFalse("onAutoNext during transition must also be guarded and rejected", autoNextAttempt)
        assertEquals("Queue index must remain at 1", 1, session.currentIndex.value)
    }

    @Test
    fun previousDownloadsAndTokensCancelledOnTrackChange() {
        val queue = createQueue(3)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 0,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        // Ep 1 is playing
        session.onPlaying()

        // Ep 1 -> Ep 2
        session.next()
        assertEquals(listOf(501), cancelledFileIds)

        // Ep 2 is playing
        session.onPlaying()

        // Ep 2 -> Ep 1 (previous)
        session.previous()
        assertEquals(listOf(501, 502), cancelledFileIds)
    }

    @Test
    fun transitionCompletesWhenReadyOrOnError() {
        val queue = createQueue(3)
        val session = PlaybackSession(queue = queue, initialIndex = 0)

        session.onPlaying()
        session.next()
        assertTrue(session.isTransitioning)

        // Error during stream prep releases the transition lock so retry or next is permitted
        session.onError("Stream prep timeout")
        assertFalse("Error must release transition lock", session.isTransitioning)

        // Can now transition or retry
        val canAdvance = session.next()
        assertTrue(canAdvance)
        assertEquals(2, session.currentIndex.value)
    }

    @Test
    fun unwrapNonPlayerScreenExtractsHomeFromNestedPlayer() {
        val request = PlaybackRequest(
            initialStreamUrl = "http://127.0.0.1:8080/stream",
            chatId = 777L,
            messageId = 1001L,
            fileId = 501,
            title = "Episode 1"
        )

        // Nested Player screens (e.g. if someone accidentally tried to chain player routes)
        val nestedPlayer = AppScreen.Player(
            request = request,
            returnTo = AppScreen.Player(
                request = request,
                returnTo = AppScreen.Player(
                    request = request,
                    returnTo = AppScreen.Home
                )
            )
        )

        val unwrapped = unwrapNonPlayerScreen(nestedPlayer)
        assertEquals("Unwrapping must recursively unwrap to Home", AppScreen.Home, unwrapped)
    }

    @Test
    fun backPopsDirectlyToOriginatingScreenRegardlessOfEpisodesPlayed() {
        val router = BousygramRouter(initialScreen = AppScreen.Home)
        val request = PlaybackRequest(
            initialStreamUrl = "http://127.0.0.1:8080/stream",
            chatId = 777L,
            messageId = 1001L,
            fileId = 501,
            title = "Episode 1"
        )

        // User opens player from Home
        router.openPlayer(request = request, returnTo = AppScreen.Home)
        assertTrue(router.currentScreen is AppScreen.Player)

        // Multiple episodes played in place (session advances index 0 -> 1 -> 2 -> 3)
        // Router currentScreen remains AppScreen.Player with returnTo = Home
        val playerScreen = router.currentScreen as AppScreen.Player
        assertEquals(AppScreen.Home, playerScreen.returnTo)

        // Even if openPlayer is called again while playing, returnTo remains Home (never nested)
        router.openPlayer(request = request, returnTo = router.currentScreen)
        val current = router.currentScreen as AppScreen.Player
        assertEquals("returnTo must never be Player", AppScreen.Home, current.returnTo)

        // Pressing Back once immediately lands on Home
        val popped = router.popBack()
        assertTrue("Back must pop out of player", popped)
        assertEquals("Must land directly on Home in one back press", AppScreen.Home, router.currentScreen)
    }
}
