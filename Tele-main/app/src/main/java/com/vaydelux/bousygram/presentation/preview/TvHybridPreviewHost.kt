package com.vaydelux.bousygram.presentation.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vaydelux.bousygram.presentation.layout.TvHybridHomeDashboard

@Composable
fun TvHybridPreviewHost() {
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
        offlineFileIds = emptySet(),
        resumeFileIds = emptySet(),
        safeMode = false,
        modifier = Modifier.fillMaxSize()
    )
}
