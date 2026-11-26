#ifndef BOUSYGRAM_AAC_PRIMING_H
#define BOUSYGRAM_AAC_PRIMING_H
#include <stdint.h>
#include <stddef.h>

/* Strict LC subset: either no priming or exactly one initial 1024-sample
 * frame. No trailing/interior trim. Keep the access unit for decoder history;
 * the muxer edit list must exclude it from the presented timeline. */
static inline int aac_priming_anchor(int padding, int packet_number,
                                    const uint8_t *side, size_t size,
                                    int64_t observed, int64_t tolerance,
                                    int64_t *anchor) {
    if (!anchor || tolerance < 1 || tolerance > 128 ||
        (padding != 0 && padding != 1024) || packet_number < 1) return -1;
    uint32_t skip = 0, discard = 0;
    if (side) {
        if (size != 10) return -1;
        for (int i = 0; i < 4; i++) {
            skip |= (uint32_t)side[i] << (i * 8);
            discard |= (uint32_t)side[i + 4] << (i * 8);
        }
    } else if (size != 0) return -1;
    if (discard || skip != (packet_number == 1 ? (uint32_t)padding : 0)) return -1;
    if (packet_number == 1 && padding) {
        if (observed < -1024 - tolerance || observed > -1024 + tolerance) return -1;
        *anchor = -1024;
    } else *anchor = observed;
    return 0;
}
#endif
