package com.vaydelux.bousygram.core.tdlib.server

import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Bounds accepted clients as well as workers, and owns every accepted socket until close. */
internal class BoundedSocketExecutor(workers: Int = 4, queuedClients: Int = 8) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val executor = ThreadPoolExecutor(
        workers, workers, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(queuedClients),
        { task -> Thread(task, "Bousygram-LoopbackClient").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    fun execute(socket: Socket, handler: (Socket) -> Unit): Boolean {
        clients.add(socket)
        if (closed.get()) { closeClient(socket); return false }
        return try {
            executor.execute {
                try { if (!closed.get()) handler(socket) } finally { closeClient(socket) }
            }
            true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            closeClient(socket)
            false
        }
    }

    private fun closeClient(socket: Socket) {
        runCatching { socket.close() }
        clients.remove(socket)
    }

    override fun close() {
        closed.set(true)
        clients.toList().forEach(::closeClient)
        executor.shutdownNow()
    }
}
