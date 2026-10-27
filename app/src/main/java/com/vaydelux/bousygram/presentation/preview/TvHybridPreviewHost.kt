package com.vaydelux.bousygram.presentation.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.components.BousygramAdaptiveShell
import com.vaydelux.bousygram.presentation.components.ShellDestination
import com.vaydelux.bousygram.presentation.layout.TvHybridHomeDashboard

@Composable
fun TvHybridPreviewHost() {
    BousygramAdaptiveShell(
        selected = ShellDestination.Home,
        onSelect = {},
        showResume = false
    ) {
        TvHybridHomeDashboard(
            featuredVideos = emptyList<MediaItem>(),
            channelLabel = "Saved Messages",
            isLoading = false,
            errorMessage = null,
            onVideoSelected = {},
            onOpenSearch = {},
            onOpenLibrary = {},
            onOpenFavorites = {},
            onOpenSettings = {},
            onOpenLastPlayed = {},
            lastPlayedFileId = null,
            lastPlayedTitle = null,
            onToggleFavorite = {},
            onToggleOffline = {},
            favoriteKeys = emptySet<String>(),
            sourceChats = emptyList(),
            savedMessagesSelected = true,
            selectedChatId = null,
            pinnedChatIds = emptySet<Long>(),
            onSelectSavedMessages = {},
            onSelectChat = {},
            onOpenAllChats = {},
            offlineFileIds = emptySet<Int>(),
            resumeMessageKeys = emptySet<String>(),
            safeMode = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}
