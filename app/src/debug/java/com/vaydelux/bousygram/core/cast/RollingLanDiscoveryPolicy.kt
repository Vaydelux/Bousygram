package com.vaydelux.bousygram.core.cast

/** Discovery is an untrusted availability hint, never a pairing credential. */
internal class RollingLanDiscoveryPolicy {
    private val names = linkedSetOf<String>()
    fun found(name: String): Boolean {
        if (!validName(name) || names.size >= MAX_PEERS) return false
        return names.add(name)
    }
    fun lost(name: String): Boolean = names.remove(name)
    fun snapshot(): List<String> = names.sorted()
    fun clear() = names.clear()

    companion object {
        const val TYPE = "_bousygram._tcp."
        const val MAX_PEERS = 16
        private val NAME = Regex("Bousygram-[0-9a-f]{12}( \\([1-9][0-9]{0,2}\\))?")
        fun validName(name: String): Boolean = NAME.matches(name)
        fun matchesType(type: String): Boolean = type.trimEnd('.').equals(TYPE.trimEnd('.'), ignoreCase = true)
    }
}
