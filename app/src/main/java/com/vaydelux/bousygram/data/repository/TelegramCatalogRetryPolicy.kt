package com.vaydelux.bousygram.data.repository

import kotlinx.coroutines.delay
import org.drinkless.tdlib.TdApi

/**
 * Only retry TDLib's transient readiness/transport failures. Do not retry
 * authorization, inaccessible chats, bad parameters, or end-of-list (404).
 * Authentication must never be faked by hiding a permanent error.
 */
internal fun isTransientTelegramCatalogFailure(response: TdApi.Object?): Boolean =
    response == null || (response is TdApi.Error && response.code in setOf(
        408, 500, 502, 503, 504
    ))

/**
 * Bounded retries are deliberately serial and cancellable, so an unstable
 * TDLib connection after login does not force two or three manual Retry taps.
 * The caller provides an account-scoped, timeout-bounded request.
 */
internal suspend fun retryTransientTelegramCatalogRequest(
    query: TdApi.Function<*>,
    attempts: Int = 3,
    request: suspend (TdApi.Function<*>) -> TdApi.Object?
): TdApi.Object? {
    require(attempts in 1..3)
    var response: TdApi.Object? = null
    repeat(attempts) { attempt ->
        response = request(query)
        if (!isTransientTelegramCatalogFailure(response) || attempt == attempts - 1)
            return response
        delay(if (attempt == 0) 300L else 750L)
    }
    return response
}
