package com.vaydelux.bousygram.data.repository

/**
 * GetChats returns an accumulated prefix. A successful LoadChats acknowledgement
 * is not evidence of progress. Stop after three non-growing short prefixes;
 * retain visible sources and let an explicit refresh retry synchronization.
 */
internal class ChatDirectoryProgress(private val maxStalls: Int = 3) {
    init { require(maxStalls > 0) }
    private var largestPrefix = -1
    private var stalls = 0

    fun allowLoadMore(prefixSize: Int): Boolean {
        require(prefixSize >= 0)
        if (prefixSize > largestPrefix) {
            largestPrefix = prefixSize
            stalls = 0
        } else {
            stalls++
        }
        return stalls < maxStalls
    }
}
