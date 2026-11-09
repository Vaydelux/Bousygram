package com.vaydelux.bousygram.core.cache

import java.util.Locale

/**
 * Cache metadata belongs to a file until TDLib explicitly confirms that its
 * ID is permanently invalid. GetFile may fail during connectivity loss,
 * account migration, temporary TDLib faults or rate limiting. Deleting the
 * access timestamp for those failures can strand otherwise cacheable media
 * outside every future bounded eviction sweep.
 *
 * Fail closed for unknown/new TDLib error messages: retain the record and
 * let a later sweep retry. No untracked private file is deleted here.
 */
internal fun shouldDiscardCacheAccessRecordOnGetFileError(
    code: Int,
    message: String?
): Boolean {
    if (code != 400 && code != 404) return false
    val text = message?.trim()?.uppercase(Locale.ROOT) ?: return false
    return text == "FILE_NOT_FOUND" ||
        text == "FILE NOT FOUND" ||
        text == "FILE_ID_INVALID" ||
        text == "FILE ID INVALID" ||
        text == "INVALID FILE ID"
}
