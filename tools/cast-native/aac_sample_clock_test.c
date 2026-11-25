#include "aac_sample_clock.h"
#include <assert.h>
#include <stdio.h>

int main(void) {
    AacSampleClock clock = {0};
    int64_t corrected = 0;
    /* 48kHz, MKV milliseconds rounded to nearest integer, negative priming. */
    for (int i = 0; i < 3376; i++) {
        int64_t exact = -1008 + 1024LL * i;
        int64_t observed = ((exact + (exact >= 0 ? 24 : -24)) / 48) * 48;
        assert(aac_sample_clock_step(&clock, observed, 48, &corrected) == 0);
        assert(corrected == exact);
    }
    int64_t next = clock.next;
    assert(aac_sample_clock_step(&clock, next + 1024, 48, &corrected) < 0);
    assert(aac_sample_clock_step(&clock, next - 1024, 48, &corrected) < 0);
    assert(clock.next == next);
    assert(aac_sample_clock_step(&clock, next, 48, &corrected) == 0);
    AacSampleClock ts = {0};
    for (int i = 0; i < 20000; i++) {
        int64_t exact = 67200 + 1024LL * i;
        assert(aac_sample_clock_step(&ts, exact, 1, &corrected) == 0);
        assert(corrected == exact);
    }
    AacSampleClock overflow = {0};
    assert(aac_sample_clock_step(&overflow, INT64_MAX, 1, &corrected) < 0);
    assert(aac_sample_clock_step(&overflow, INT64_MIN, 1, &corrected) < 0);
    assert(aac_sample_clock_step(&overflow, 0, 129, &corrected) < 0);
    puts("AAC_SAMPLE_CLOCK_PASS");
    return 0;
}
