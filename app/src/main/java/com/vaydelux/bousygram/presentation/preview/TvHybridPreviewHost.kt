package com.vaydelux.bousygram.presentation.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        featuredVideos = mockMediaItems(),
        channelLabel = mockChannelLabel(),
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
        favoriteKeys = emptySet(),
        sourceChats = emptyList(),
        savedMessagesSelected = true,
        selectedChatId = null,
        pinnedChatIds = emptySet(),
        onSelectSavedMessages = {},
        onSelectChat = {},
        onOpenAllChats = {},
        offlineFileIds = emptySet(),
        resumeMessageKeys = emptySet(),
        safeMode = false,
        modifier = Modifier.fillMaxSize()
        )
    }
}
