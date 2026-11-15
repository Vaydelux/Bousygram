package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionTrackExitTest {
    private fun episode(fileId: Int) = PlaybackQueueItem(
        chatId = 1L, messageId = fileId.toLong(), title = "Episode $fileId", fileId = fileId
    )

    @Test fun trackExitOnlyRunsOnActualQueueTransitionsNotRelease() {
        val cancelled = mutableListOf<Int>()
        val exited = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = listOf(episode(101), episode(102), episode(103)),
            onCancelDownloads = { cancelled.add(it) },
            onTrackExit = { exited.add(it) }
        )
        assertTrue(session.next())
        session.finishTransition()
        assertTrue(session.goToIndex(2))
        session.finishTransition()
        assertTrue(session.previous())
        session.release()
        assertEquals(listOf(101, 102, 103), exited)
        // Owner release cancels the active file but does not trigger a track-exit
        // callback, so recovery-stop cleanup remains under controller control.
        assertEquals(listOf(101, 102, 103, 102), cancelled)
    }
}
