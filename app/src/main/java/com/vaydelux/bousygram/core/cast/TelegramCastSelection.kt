package com.vaydelux.bousygram.core.cast

/** A TDLib file ID alone does not identify the authorized message. */
internal data class TelegramCastTarget(val chatId: Long, val messageId: Long, val fileId: Int)

internal object TelegramCastSelection {
    enum class Decision { PREPARE, KEEP, DISCONNECT }

    /** Pin the first source before asynchronous preparation, not after LOAD. */
    fun decide(pinned: TelegramCastTarget?, selected: TelegramCastTarget?): Decision = when {
        selected == null -> Decision.DISCONNECT
        pinned == null -> Decision.PREPARE
        pinned == selected -> Decision.KEEP
        else -> Decision.DISCONNECT
    }
}
