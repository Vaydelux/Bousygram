package com.vaydelux.bousygram.data.repository

/** Small, deterministic query budget for a video-library page. */
internal data class MediaSearchPlan(
    val pageLimit: Int,
    val documentLimit: Int,
    val filterCount: Int = 4
)

/**
 * Each specialized video filter only needs one page of candidates to form a globally sorted
 * page. Document search gets twice as many candidates because it also returns ordinary files
 * that are discarded by video-format detection. Keep every TDLib request within its 100 item cap.
 */
internal fun createMediaSearchPlan(requestedLimit: Int): MediaSearchPlan {
    val pageLimit = requestedLimit.coerceIn(1, 100)
    return MediaSearchPlan(
        pageLimit = pageLimit,
        documentLimit = (pageLimit * 2).coerceIn(pageLimit, 100)
    )
}
