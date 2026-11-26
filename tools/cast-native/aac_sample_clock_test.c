#include "aac_sample_clock.h"
#include "aac_priming.h"
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
        /* TS copied from quantized MKV can be eight samples early. */
        int64_t observed = exact - (i % 3 == 0 && i != 0 ? 8 : 0);
        assert(aac_sample_clock_step(&ts, observed, 48, &corrected) == 0);
        assert(corrected == exact);
    }
    AacSampleClock overflow = {0};
    assert(aac_sample_clock_step(&overflow, INT64_MAX, 1, &corrected) < 0);
    assert(aac_sample_clock_step(&overflow, INT64_MIN, 1, &corrected) < 0);
    assert(aac_sample_clock_step(&overflow, 0, 129, &corrected) < 0);
    uint8_t skip[10] = {0, 4};
    int64_t anchor = 0;
    assert(aac_priming_anchor(1024, 1, skip, sizeof(skip), -1008, 48, &anchor) == 0);
    assert(anchor == -1024);
    assert(aac_priming_anchor(1024, 2, NULL, 0, 0, 48, &anchor) == 0);
    assert(anchor == 0);
    assert(aac_priming_anchor(1024, 2, skip, sizeof(skip), 0, 48, &anchor) < 0);
    assert(aac_priming_anchor(1024, 1, NULL, 0, -1008, 48, &anchor) < 0);
    assert(aac_priming_anchor(1024, 1, skip, sizeof(skip), -800, 48, &anchor) < 0);
    skip[4] = 1;
    assert(aac_priming_anchor(1024, 1, skip, sizeof(skip), -1008, 48, &anchor) < 0);
    assert(aac_priming_anchor(0, 1, NULL, 0, 67200, 48, &anchor) == 0);
    assert(anchor == 67200);
    puts("AAC_SAMPLE_CLOCK_PASS");
    return 0;
}
