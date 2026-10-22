package com.vaydelux.bousygram.core.observability

import android.util.Log

object CrashReporter {
    private const val TAG = "CrashReporter"

    fun setUserId(userId: String?) {
        invokeCrashlytics("setUserId", arrayOf(String::class.java), arrayOf(userId.orEmpty()))
    }

    fun setKey(key: String, value: String) {
        if (!invokeCrashlytics("setCustomKey", arrayOf(String::class.java, String::class.java), arrayOf(key, value.take(256)))) {
            Log.d(TAG, "crash-key $key=${value.take(256)}")
        }
    }

    fun setKey(key: String, value: Long) {
        if (!invokeCrashlytics("setCustomKey", arrayOf(String::class.java, java.lang.Long.TYPE), arrayOf(key, value))) {
            Log.d(TAG, "crash-key $key=$value")
        }
    }

    fun setKey(key: String, value: Int) {
        if (!invokeCrashlytics("setCustomKey", arrayOf(String::class.java, Integer.TYPE), arrayOf(key, value))) {
            Log.d(TAG, "crash-key $key=$value")
        }
    }

    fun log(message: String) {
        if (!invokeCrashlytics("log", arrayOf(String::class.java), arrayOf(message.take(512)))) {
            Log.d(TAG, message)
        }
    }

    fun recordException(error: Throwable) {
        if (!invokeCrashlytics("recordException", arrayOf(Throwable::class.java), arrayOf(error))) {
            Log.e(TAG, error.message.orEmpty(), error)
        }
    }

    private fun invokeCrashlytics(
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any?>
    ): Boolean = runCatching {
        val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
        val instance = clazz.getMethod("getInstance").invoke(null)
        clazz.getMethod(methodName, *parameterTypes).invoke(instance, *args)
        true
    }.getOrDefault(false)
}
