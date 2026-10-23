package com.vaydelux.bousygram.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BousygramRouterTest {

    @Test
    fun testResumeRequestIsOneShotAndBreaksLoop() {
        val router = BousygramRouter(AppScreen.Home)
        assertFalse(router.resumeRequested)

        router.requestResume()
        assertTrue(router.resumeRequested)
        assertEquals(AppScreen.Home, router.currentScreen)

        // First consumption returns true
        assertTrue(router.consumeResume())
        // Immediately reset: subsequent consumption returns false, ensuring zero loop
        assertFalse(router.consumeResume())
        assertFalse(router.resumeRequested)
    }

    @Test
    fun testPopBackNavigatesCorrectly() {
        val router = BousygramRouter(AppScreen.Home)
        assertFalse(router.popBack()) // Already on Home

        router.navigateTo(AppScreen.Library)
        assertEquals(AppScreen.Library, router.currentScreen)
        assertTrue(router.popBack())
        assertEquals(AppScreen.Home, router.currentScreen)

        router.navigateTo(AppScreen.Settings(returnTo = AppScreen.Home))
        assertTrue(router.popBack())
        assertEquals(AppScreen.Home, router.currentScreen)
    }
}
