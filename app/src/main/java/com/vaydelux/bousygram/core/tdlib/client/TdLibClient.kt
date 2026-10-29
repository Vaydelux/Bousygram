package com.vaydelux.bousygram.core.tdlib.client

import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
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

    val client: Client
        get() = synchronized(this) {
            instance ?: createNewClient().also { instance = it }
        }

    fun restartClient(): Client = synchronized(this) {
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
