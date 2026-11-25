#ifndef BOUSYGRAM_AAC_SAMPLE_CLOCK_H
#define BOUSYGRAM_AAC_SAMPLE_CLOCK_H

#include <stdint.h>
#include <limits.h>

/* AAC-LC, 1024 samples per access unit. Values are already rescaled to
 * integer sample ticks; tolerate only source timestamp quantization.
 * Never insert/drop frames, conceal a discontinuity, or accumulate rounding. */
typedef struct {
    int initialized;
    int64_t next;
} AacSampleClock;

static inline int aac_sample_clock_step(AacSampleClock *clock, int64_t observed,
                                       int64_t tolerance, int64_t *corrected) {
    if (!clock || !corrected || tolerance < 0 || tolerance > 128) return -1;
    int64_t expected = clock->initialized ? clock->next : observed;
    if (expected < INT64_MIN + tolerance || expected > INT64_MAX - tolerance ||
        expected > INT64_MAX - 1024 || observed < expected - tolerance ||
        observed > expected + tolerance) return -1;
    *corrected = expected;
    clock->next = expected + 1024;
    clock->initialized = 1;
    return 0;
}

#endif
