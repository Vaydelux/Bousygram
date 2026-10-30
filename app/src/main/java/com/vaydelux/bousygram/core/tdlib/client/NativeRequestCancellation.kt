package com.vaydelux.bousygram.core.tdlib.client

import java.util.concurrent.atomic.AtomicLong

/**
 * Race-safe registration for a native TDLib response handler.
 *
 * A coroutine may be cancelled while Client.sendCancellable is still submitting a request,
 * so the native handler ID is not always available at cancellation time.
 */
internal class NativeRequestCancellation(
    private val forgetNativeHandler: (Long) -> Unit
) {
    private val nativeId = AtomicLong(0L)

    fun attach(requestId: Long) {
        require(requestId >= 0L)
        if (!nativeId.compareAndSet(0L, requestId) && requestId > 0L) {
            forgetNativeHandler(requestId)
        }
    }

    fun cancel() {
        val requestId = nativeId.getAndSet(-1L)
        if (requestId > 0L) forgetNativeHandler(requestId)
    }
}
