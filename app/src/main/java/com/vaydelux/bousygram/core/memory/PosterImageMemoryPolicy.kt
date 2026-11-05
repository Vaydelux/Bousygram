package com.vaydelux.bousygram.core.memory

/** A bounded share of the app heap for Coil's decoded artwork cache.
 * Native player allocations and TDLib buffers are outside this budget.
 */
internal fun decodedPosterCacheHeapFraction(lowMemoryOrArm32: Boolean): Double =
    if (lowMemoryOrArm32) 0.08 else 0.16
