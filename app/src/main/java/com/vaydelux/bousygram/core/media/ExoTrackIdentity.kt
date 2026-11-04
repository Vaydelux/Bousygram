package com.vaydelux.bousygram.core.media

/**
 * The visible Media3 track list can contain multiple groups of the same type
 * (for example separate embedded subtitle groups). Format.id and local index
 * are not globally unique; encode the group+index instead.
 */
internal fun exoTrackUiId(groupIndex: Int, trackIndex: Int): Int {
    require(groupIndex in 0..65535 && trackIndex in 0..65535)
    return (groupIndex shl 16) or trackIndex
}

internal fun decodeExoTrackUiId(id: Int): Pair<Int, Int>? {
    if (id < 0) return null
    return (id ushr 16) to (id and 0xFFFF)
}
