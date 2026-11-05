package com.vaydelux.bousygram.core.media

/**
 * libmpv/LibVLC are both loaded in the application process. If the child
 * native player constructor fails, close its already-created parent exactly
 * once rather than leaking a native allocation on low-RAM ARM32 devices.
 *
 * Preserve the original allocation exception and attach cleanup failures as
 * suppressed diagnostics. A successful child remains owned by its caller.
 */
internal inline fun <Parent, Child> createNativeChildOrReleaseParent(
    parent: Parent,
    create: (Parent) -> Child,
    releaseParent: (Parent) -> Unit
): Child = try {
    create(parent)
} catch (failure: Throwable) {
    try {
        releaseParent(parent)
    } catch (cleanup: Throwable) {
        if (failure !== cleanup) failure.addSuppressed(cleanup)
    }
    throw failure
}
