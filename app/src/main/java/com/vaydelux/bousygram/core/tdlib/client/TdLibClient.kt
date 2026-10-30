package com.vaydelux.bousygram.core.tdlib.client

import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot

object TdLibClient {
    private const val TAG = "TdLibClient"

    private class HandlerRegistry<T>(
        private val name: String
    ) {
        private val handlers = CopyOnWriteArrayList<(T) -> Unit>()

        fun add(handler: (T) -> Unit) {
            handlers.add(handler)
        }

        fun remove(handler: (T) -> Unit) {
            handlers.remove(handler)
        }

        fun dispatch(value: T) {
            handlers.forEach { handler ->
                try { handler(value) } catch (e: Exception) {
                    Log.e(TAG, "Error in $name handler", e)
                }
            }
        }
    }

    private val updateHandlers = HandlerRegistry<TdApi.Object?>("update")
    private val errorHandlers = HandlerRegistry<Throwable?>("error")
    private val newMessageHandlers = HandlerRegistry<TdApi.UpdateNewMessage>("newMessage")
    private val deleteMessageHandlers = HandlerRegistry<TdApi.UpdateDeleteMessages>("deleteMessage")

    @Volatile
    private var instance: Client? = null
    private val clientGeneration = AtomicLong(0L)
    private val pendingResponses = PendingTdRequests()

    init {
        // AccountSession has already marked the old identity invalid when invoking handlers.
        // Cancel suspended callers synchronously so they cannot wait on abandoned native clients.
        AccountSession.registerInvalidationHandler { pendingResponses.invalidateAll() }
    }

    /** A repository binds this facade once; it can never silently become another account's client. */
    class AccountClient internal constructor(private val owner: AccountSnapshot?) {
        fun send(query: TdApi.Function<*>, handler: (TdApi.Object?) -> Unit) {
            val snapshot = owner ?: return
            if (!AccountSession.isCurrent(snapshot)) return
            sendCurrent(query) { result ->
                if (AccountSession.isCurrent(snapshot)) handler(result)
            }
        }
    }

    fun accountClient(): AccountClient = AccountClient(AccountSession.current())

    /** Authentication requests are also rejected when their native client has been replaced. */
    fun sendCurrent(query: TdApi.Function<*>, handler: (TdApi.Object?) -> Unit) {
        val connection: Client
        val generation: Long
        synchronized(this) {
            connection = client
            generation = clientGeneration.get()
        }
        connection.send(query) { result ->
            if (clientGeneration.get() == generation) handler(result)
        }
    }

    /**
     * A bounded, account-scoped native request. In contrast to sendCurrent (used for login),
     * this never follows a replacement client or permits a response from another account.
     *
     * TDLib callbacks cannot be cancelled at the JNI layer. Cancellation instead removes the
     * continuation, and a late native callback is ignored. The caller remains cancellable.
     */
    suspend fun awaitAccountResponse(
        query: TdApi.Function<*>,
        expectedOwner: AccountSnapshot? = AccountSession.current(),
        timeoutMs: Long = 15_000L
    ): TdApi.Object? {
        val owner = expectedOwner ?: throw CancellationException("No active Telegram account")
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val connection: Client
                val generation: Long
                val ticket: PendingTdRequests.Ticket
                synchronized(this@TdLibClient) {
                    connection = client
                    generation = clientGeneration.get()
                    ticket = pendingResponses.register(generation) {
                        continuation.cancel(CancellationException("TDLib client or account changed"))
                    }
                }
                // A coroutine can time out while Java is still returning from native send.
                // The negative sentinel makes that race safe in both possible orders:
                // after send -> unregister immediately; before send -> unregister in onCancel.
                val nativeRequest = NativeRequestCancellation(connection::forgetRequest)
                continuation.invokeOnCancellation {
                    pendingResponses.forget(ticket)
                    nativeRequest.cancel()
                }

                if (!AccountSession.isCurrent(owner) || clientGeneration.get() != generation ||
                    !pendingResponses.isPending(ticket)
                ) {
                    if (pendingResponses.finish(ticket)) {
                        continuation.cancel(CancellationException("Telegram account changed before native send"))
                    }
                    return@suspendCancellableCoroutine
                }

                try {
                    val nativeId = connection.sendCancellable(query) { result ->
                        if (!pendingResponses.finish(ticket)) return@sendCancellable
                        if (clientGeneration.get() != generation || !AccountSession.isCurrent(owner)) {
                            continuation.cancel(CancellationException("Stale TDLib callback discarded"))
                        } else if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                    nativeRequest.attach(nativeId)
                } catch (failure: Exception) {
                    if (pendingResponses.finish(ticket) && continuation.isActive) {
                        continuation.resumeWithException(failure)
                    }
                }
            }
        }
    }

    val client: Client
        get() = synchronized(this) {
            instance ?: createNewClient().also { instance = it }
        }

    fun restartClient(): Client = synchronized(this) {
        // Wake every suspended request before installing the next TDLib generation.
        pendingResponses.invalidateAll()
        instance = createNewClient().also { instance = it }
        instance!!
    }

    private fun createNewClient(): Client {
        val generation = clientGeneration.incrementAndGet()
        return Client.create(
            { update ->
                if (clientGeneration.get() != generation) return@create
                try {
                    updateHandlers.dispatch(update)
                    when (update) {
                        is TdApi.UpdateNewMessage -> newMessageHandlers.dispatch(update)
                        is TdApi.UpdateDeleteMessages -> deleteMessageHandlers.dispatch(update)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error dispatching update", e)
                }
            },
            { error ->
                if (clientGeneration.get() != generation) return@create
                try {
                    errorHandlers.dispatch(error)
                } catch (e: Exception) {
                    Log.e(TAG, "Error dispatching error", e)
                }
            },
            null
        )
    }

    fun addUpdateHandler(handler: (TdApi.Object?) -> Unit) = updateHandlers.add(handler)
    fun removeUpdateHandler(handler: (TdApi.Object?) -> Unit) = updateHandlers.remove(handler)

    fun addErrorHandler(handler: (Throwable?) -> Unit) = errorHandlers.add(handler)
    fun removeErrorHandler(handler: (Throwable?) -> Unit) = errorHandlers.remove(handler)

    fun addNewMessageHandler(handler: (TdApi.UpdateNewMessage) -> Unit) = newMessageHandlers.add(handler)
    fun removeNewMessageHandler(handler: (TdApi.UpdateNewMessage) -> Unit) = newMessageHandlers.remove(handler)

    fun addDeleteMessageHandler(handler: (TdApi.UpdateDeleteMessages) -> Unit) = deleteMessageHandlers.add(handler)
    fun removeDeleteMessageHandler(handler: (TdApi.UpdateDeleteMessages) -> Unit) = deleteMessageHandlers.remove(handler)
}
