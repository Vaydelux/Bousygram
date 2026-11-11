package com.vaydelux.mediaforge.core

import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaForgeEmbedNavigationPolicyTest {
    private fun allows(selected: String, requested: String) =
        MediaForgeEmbedNavigationPolicy.allows(URI(selected), requested)

    @Test fun pathBoundMovieAndEpisodeDoNotChangeUnderProviderNavigation() {
        val movie = "https://video.example.org/embed/movie/550"
        assertTrue(allows(movie, "$movie?server=2"))
        assertFalse(allows(movie, "https://video.example.org/embed/movie/551"))
        assertFalse(allows(movie, "https://video.example.org/embed/tv/550"))

        val episode = "https://video.example.org/embed/tv/550?s=1&e=2"
        assertTrue(allows(episode, "$episode&server=alternative"))
        assertFalse(allows(episode, "https://video.example.org/embed/tv/550?s=1&e=3"))
        assertFalse(allows(episode, "https://video.example.org/embed/tv/550?s=1"))
    }

    @Test fun queryBoundMovieRoutesCannotChangeTitle() {
        val movie = "https://video.example.org/embed/movie?tmdb=550"
        assertTrue(allows(movie, "$movie&server=2&startAt=40"))
        assertFalse(allows(movie, "https://video.example.org/embed/movie?tmdb=551"))
        assertFalse(allows(movie, "https://video.example.org/embed/movie?tmdb=550&id=551"))
        assertFalse(allows(movie, "https://video.example.org/embed/movie?tmdb=550&TMDB=551"))
        assertFalse(allows(movie, "https://video.example.org/embed/movie?tmdb=550&%74mdb=551"))
    }

    @Test fun queryBoundTvRoutesCannotChangeEpisodeOrInjectIdentity() {
        val episode = "https://video.example.org/embed/tv?tmdb=550&season=1&episode=2"
        assertTrue(allows(episode, "$episode&subtitle=en"))
        assertFalse(allows(episode, "https://video.example.org/embed/tv?tmdb=551&season=1&episode=2"))
        assertFalse(allows(episode, "https://video.example.org/embed/tv?tmdb=550&season=1&episode=3"))
        assertFalse(allows(episode, "https://video.example.org/embed/tv?tmdb=550&season=1&episode=2&e=3"))
        assertFalse(allows(episode, "https://other.example.org/embed/tv?tmdb=550&season=1&episode=2"))
    }

    @Test fun previouslyBroadEmbedPathCannotSilentlySwitchTitle() {
        val movie = "https://video.example.org/embed/550"
        assertTrue(allows(movie, "$movie?server=2"))
        assertFalse(allows(movie, "https://video.example.org/embed/551"))
        assertFalse(allows(movie, "https://video.example.org/embed/550/another-title"))
        assertFalse(allows(movie, "https://video.example.org/embed/%35%35%30"))
    }

    @Test fun superEmbedAndLegacyIdentityRemainLocked() {
        val superEmbed = "https://video.example.org/?video_id=550&tmdb=1"
        assertTrue(allows(superEmbed, "$superEmbed&server=2"))
        assertFalse(allows(superEmbed, "https://video.example.org/?video_id=551&tmdb=1"))
        assertFalse(allows(superEmbed, "https://video.example.org/?video_id=550&tmdb=0"))

        val legacy = "https://video.example.org/embed?id=550&type=movie"
        assertTrue(allows(legacy, "$legacy&server=2"))
        assertFalse(allows(legacy, "https://video.example.org/embed?id=551&type=movie"))
        assertFalse(allows(legacy, "https://video.example.org/embed?id=550&type=tv"))
        assertFalse(allows(legacy, "https://video.example.org/embed?id=550&id=551&type=movie"))
    }
}
