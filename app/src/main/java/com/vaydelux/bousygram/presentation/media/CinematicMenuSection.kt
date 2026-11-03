package com.vaydelux.bousygram.presentation.media

/** One logical navigation level. Never reuse PlayerMenuType: it controls real playback drawers. */
internal enum class CinematicMenuSection {
    ROOT,
    PLAYBACK,
    LIBRARY,
    ADVANCED
}

/** A submenu goes back to categories; Back on categories closes the existing More panel. */
internal fun cinematicMenuBackDestination(current: CinematicMenuSection): CinematicMenuSection? =
    if (current == CinematicMenuSection.ROOT) null else CinematicMenuSection.ROOT

internal fun cinematicMenuTitle(section: CinematicMenuSection): String = when (section) {
    CinematicMenuSection.ROOT -> "MORE OPTIONS"
    CinematicMenuSection.PLAYBACK -> "PLAYBACK"
    CinematicMenuSection.LIBRARY -> "LIBRARY"
    CinematicMenuSection.ADVANCED -> "ADVANCED"
}
