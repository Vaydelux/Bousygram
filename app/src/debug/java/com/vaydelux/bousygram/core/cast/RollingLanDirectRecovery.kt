package com.vaydelux.bousygram.core.cast

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Only transport loss can retry a read. Never retry a pin failure, protocol
 * refusal, authorization loss or source mutation. A partial read is discarded. */
internal object RollingLanDirectRecovery {
    fun retryable(error: IOException): Boolean = error !is SSLException &&
        (error is EOFException || error is SocketException || error is SocketTimeoutException)
}
