package com.vaydelux.bousygram.presentation.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Navigation signal emitted only by an explicit View Favorites UI action. */
object CloudFavoritesNavigation {
    private val _openRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openRequests = _openRequests.asSharedFlow()
    fun viewFavorites() { _openRequests.tryEmit(Unit) }
}
