//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2026
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
package org.drinkless.tdlib;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main class for interaction with the TDLib.
 */
public final class Client {
    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("tdjson_bridge");
            loaded = TdJsonNative.diagnostics().isEmpty();
        } catch (Throwable e) {
            loaded = false;
        }
        NATIVE_AVAILABLE = loaded;
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    /**
     * Interface for handler for results of queries to TDLib and incoming updates from TDLib.
     */
    public interface ResultHandler {
        /**
         * Callback called on result of query to TDLib or incoming update from TDLib.
         *
         * @param object Result of query or update of type TdApi.Update about new events.
         */
        void onResult(TdApi.Object object);
    }

    /**
     * Interface for handler of exceptions thrown while invoking ResultHandler.
     * By default, all such exceptions are ignored.
     * All exceptions thrown from ExceptionHandler are ignored.
     */
    public interface ExceptionHandler {
        /**
         * Callback called on exceptions thrown while invoking ResultHandler.
         *
         * @param e Exception thrown by ResultHandler.
         */
        void onException(Throwable e);
    }

    /**
     * Interface for handler of messages that are added to the internal TDLib log.
     */
    public interface LogMessageHandler {
        /**
         * Callback called on messages that are added to the internal TDLib log.
         *
         * @param verbosityLevel Log verbosity level with which the message was added from -1 up to 1024.
         *                       If 0, then TDLib will crash as soon as the callback returns.
         *                       None of the TDLib methods can be called from the callback.
         * @param message        The message added to the internal TDLib log.
         */
        void onLogMessage(int verbosityLevel, String message);
    }

    /**
     * Exception class thrown when TDLib error occurred while performing {@link #execute(TdApi.Function)}.
     */
    public static class ExecutionException extends Exception {
        /**
         * Original TDLib error occurred when performing one of the synchronous functions.
         */
        public final TdApi.Error error;

        /**
         * @param error TDLib error occurred while performing {@link #execute(TdApi.Function)}.
         */
        ExecutionException (TdApi.Error error) {
            super(error.code + ": " + error.message);
            this.error = error;
        }
    }

    /**
     * Sends a request to the TDLib.
     *
     * @param query            Object representing a query to the TDLib.
     * @param resultHandler    Result handler with onResult method which will be called with result
     *                         of the query or with TdApi.Error as parameter. If it is null, nothing
     *                         will be called.
     * @param exceptionHandler Exception handler with onException method which will be called on
     *                         exception thrown from resultHandler. If it is null, then
     *                         defaultExceptionHandler will be called.
     */
    public void send(TdApi.Function query, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        if (!NATIVE_AVAILABLE) {
            if (resultHandler != null) {
                resultHandler.onResult(new TdApi.Error(503, "TDLib JSON native runtime unavailable"));
            }
            return;
        }
        long queryId = currentQueryId.incrementAndGet();
        if (resultHandler != null) {
            handlers.put(queryId, new Handler(resultHandler, exceptionHandler));
        }
        try { TdJsonNative.send(nativeClientId, TdJsonCodec.encode(query, Long.toString(queryId))); }
        catch (RuntimeException error) {
            Handler failed = handlers.remove(queryId);
            if (failed != null) dispatchException(failed, nativeClientId, error);
        }
    }

    /**
     * Sends a request to the TDLib with an empty ExceptionHandler.
     *
     * @param query         Object representing a query to the TDLib.
     * @param resultHandler Result handler with onResult method which will be called with result
     *                      of the query or with TdApi.Error as parameter. If it is null, then
     *                      defaultExceptionHandler will be called.
     */
    public void send(TdApi.Function query, ResultHandler resultHandler) {
        send(query, resultHandler, null);
    }

    /**
     * Synchronously executes a TDLib request. Only a few marked accordingly requests can be executed synchronously.
     *
     * @param query Object representing a query to the TDLib.
     * @param <T> Automatically deduced return type of the query.
     * @return request result.
     * @throws ExecutionException if query execution fails.
     */
    @SuppressWarnings("unchecked")
    public static <T extends TdApi.Object> T execute(TdApi.Function<T> query) throws ExecutionException {
        if (!NATIVE_AVAILABLE) {
            throw new ExecutionException(new TdApi.Error(503, "TDLib JSON native runtime unavailable"));
        }
        TdApi.Object object;
        try { object = TdJsonCodec.decodeEnvelope(TdJsonNative.execute(TdJsonCodec.encode(query, null))).object; }
        catch (RuntimeException e) { object = new TdApi.Error(500, "JSON transport failure: " + e.getClass().getSimpleName()); }
        if (object instanceof TdApi.Error) {
            throw new ExecutionException((TdApi.Error) object);
        }
        return (T) object;
    }

    /**
     * Creates new Client.
     *
     * @param updateHandler           Handler for incoming updates.
     * @param updateExceptionHandler  Handler for exceptions thrown from updateHandler. If it is null, exceptions will be ignored.
     * @param defaultExceptionHandler Default handler for exceptions thrown from all ResultHandler. If it is null, exceptions will be ignored.
     * @return created Client
     */
    public static Client create(ResultHandler updateHandler, ExceptionHandler updateExceptionHandler, ExceptionHandler defaultExceptionHandler) {
        Client client = new Client(updateHandler, updateExceptionHandler, defaultExceptionHandler);
        if (NATIVE_AVAILABLE) {
            synchronized (responseReceiver) {
                if (!responseReceiver.isRun) {
                    responseReceiver.isRun = true;

                    Thread receiverThread = new Thread(responseReceiver, "TDLib thread");
                    receiverThread.setDaemon(true);
                    receiverThread.start();
                }
            }
        }
        return client;
    }

    /**
     * Sets the handler for messages that are added to the internal TDLib log.
     * None of the TDLib methods can be called from the callback.
     *
     * @param maxVerbosityLevel The maximum verbosity level of messages for which the callback will be called.
     * @param logMessageHandler Handler for messages that are added to the internal TDLib log. Pass null to remove the handler.
     */
    public static void setLogMessageHandler(int maxVerbosityLevel, Client.LogMessageHandler logMessageHandler) {
        if (!NATIVE_AVAILABLE) {
            return;
        }
        // JSON bridge does not expose TDLib log callbacks; no-op until safely supported.
    }

    private static class ResponseReceiver implements Runnable {
        public boolean isRun = false;

        @Override
        public void run() {
            while (true) {
                String response;
                try { response = TdJsonNative.receive(1.0); }
                catch (Throwable error) {
                    isRun = false;
                    reportTransportFailure(error);
                    return;
                }
                if (response == null) continue;
                try {
                    TdJsonCodec.Envelope envelope = TdJsonCodec.decodeEnvelope(response);
                    int clientId = envelope.clientId;
                    if (clientId == 0) clientId = activeClientId;
                    long correlation = 0L;
                    if (envelope.extra != null) correlation = Long.parseLong(envelope.extra);
                    processResult(clientId, correlation, envelope.object);
                } catch (Throwable error) {
                    reportTransportFailure(error);
                }
            }
        }

        private void processResult(int clientId, long id, TdApi.Object object) {
            boolean isClosed = false;
            if (id == 0 && object instanceof TdApi.UpdateAuthorizationState) {
                TdApi.AuthorizationState authorizationState = ((TdApi.UpdateAuthorizationState) object).authorizationState;
                if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
                    isClosed = true;
                }
            }

            Handler handler = id == 0 ? updateHandlers.get(clientId) : handlers.remove(id);
            if (handler != null) {
                try {
                    handler.resultHandler.onResult(object);
                } catch (Throwable cause) {
                    ExceptionHandler exceptionHandler = handler.exceptionHandler;
                    if (exceptionHandler == null) {
                        exceptionHandler = defaultExceptionHandlers.get(clientId);
                    }
                    if (exceptionHandler != null) {
                        try {
                            exceptionHandler.onException(cause);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            if (isClosed) {
                updateHandlers.remove(clientId);           // there will be no more updates
                defaultExceptionHandlers.remove(clientId); // ignore further exceptions
                clientCount.decrementAndGet();
            }
        }

        private static final int MAX_EVENTS = 1000;
        private final int[] clientIds = new int[MAX_EVENTS];
        private final long[] eventIds = new long[MAX_EVENTS];
        private final TdApi.Object[] events = new TdApi.Object[MAX_EVENTS];
    }

    private final int nativeClientId;

    private static final ConcurrentHashMap<Integer, ExceptionHandler> defaultExceptionHandlers = new ConcurrentHashMap<Integer, ExceptionHandler>();
    private static final ConcurrentHashMap<Integer, Handler> updateHandlers = new ConcurrentHashMap<Integer, Handler>();
    private static final ConcurrentHashMap<Long, Handler> handlers = new ConcurrentHashMap<Long, Handler>();
    private static final AtomicLong currentQueryId = new AtomicLong();
    private static final AtomicLong clientCount = new AtomicLong();
    private static volatile int activeClientId;
    private static void dispatchException(Handler handler, int clientId, Throwable error) {
        ExceptionHandler callback = handler.exceptionHandler != null ? handler.exceptionHandler : defaultExceptionHandlers.get(clientId);
        if (callback != null) try { callback.onException(error); } catch (Throwable ignored) {}
    }
    private static void reportTransportFailure(Throwable error) {
        for (java.util.Map.Entry<Integer, Handler> entry : updateHandlers.entrySet()) dispatchException(entry.getValue(), entry.getKey(), error);
        for (java.util.Map.Entry<Long, Handler> entry : handlers.entrySet()) {
            Handler removed = handlers.remove(entry.getKey());
            if (removed != null) dispatchException(removed, activeClientId, error);
        }
    }

    private static final ResponseReceiver responseReceiver = new ResponseReceiver();

    private static class Handler {
        final ResultHandler resultHandler;
        final ExceptionHandler exceptionHandler;

        Handler(ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
            this.resultHandler = resultHandler;
            this.exceptionHandler = exceptionHandler;
        }
    }

    private Client(ResultHandler updateHandler, ExceptionHandler updateExceptionHandler, ExceptionHandler defaultExceptionHandler) {
        int nextId = (int) clientCount.incrementAndGet();
        if (NATIVE_AVAILABLE) {
            nativeClientId = TdJsonNative.create();
            activeClientId = nativeClientId;
        } else {
            nativeClientId = nextId;
        }
        if (updateHandler != null) {
            updateHandlers.put(nativeClientId, new Handler(updateHandler, updateExceptionHandler));
        }
        if (defaultExceptionHandler != null) {
            defaultExceptionHandlers.put(nativeClientId, defaultExceptionHandler);
        }
        if (NATIVE_AVAILABLE) {
            send(new TdApi.GetOption("version"), null, null);
        } else {
            if (updateHandler != null) updateHandler.onResult(new TdApi.Error(503, "TDLib JSON native runtime unavailable"));
        }
    }

}
