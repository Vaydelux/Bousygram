package com.vaydelux.bousygram.presentation.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CinematicMenuSectionTest {
    @Test fun categoryHeadersAreStableAndReadable() {
        assertEquals("MORE OPTIONS", cinematicMenuTitle(CinematicMenuSection.ROOT))
        assertEquals("PLAYBACK", cinematicMenuTitle(CinematicMenuSection.PLAYBACK))
        assertEquals("LIBRARY", cinematicMenuTitle(CinematicMenuSection.LIBRARY))
        assertEquals("ADVANCED", cinematicMenuTitle(CinematicMenuSection.ADVANCED))
    }

    @Test fun aNestedBackReturnsToCategoriesButRootBackClosesTheMenu() {
        assertNull(cinematicMenuBackDestination(CinematicMenuSection.ROOT))
        for (section in listOf(
            CinematicMenuSection.PLAYBACK,
            CinematicMenuSection.LIBRARY,
            CinematicMenuSection.ADVANCED
        )) {
            assertEquals(CinematicMenuSection.ROOT, cinematicMenuBackDestination(section))
        }
    }
}
