package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteButton
import com.vaydelux.bousygram.R

/**
 * MediaRouter uses AppCompat colorPrimary and android:colorBackground for contrast calculations.
 * The hosting activity uses a platform theme while the player is Compose Material3; absent
 * AppCompat colors can resolve to transparent and crash MediaRouteButton's constructor.
 *
 * Apply a scoped, fully opaque view theme before constructing the button rather than
 * changing the user's Compose color scheme or disabling Cast.
 */
object CastRouteButtonFactory {
    fun create(context: Context): MediaRouteButton =
        MediaRouteButton(ContextThemeWrapper(context, R.style.BousygramCastButtonThemeOverlay))
}
