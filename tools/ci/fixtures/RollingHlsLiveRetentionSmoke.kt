package com.vaydelux.bousygram.core.cast

fun main() {
    val second = 1_000_000L
    val mib = 1024L * 1024
    var now = 0L
    fun segment(n: Long) = RollingHlsLiveRetention.Segment(n, 6 * second, mib)
    val existing = RollingHlsLiveRetention(12 * second, 6, 32 * mib) { now }
    repeat(6) { check(existing.append(segment(it + 1L))) }
    check(existing.advertise()?.size == 6)
    check(!existing.withdrawOldest() && !existing.append(segment(7)))
    println("CURRENT_WINDOW_STALL_PASS")

    val planner = RollingHlsLiveRetention(6 * second, 8, 32 * mib) { now }
    repeat(2) { check(planner.append(segment(it + 1L))) }
    check(planner.advertise() == null && !planner.acquire(1))
    check(planner.append(segment(3)))
    check(planner.advertise()?.size == 3 && !planner.withdrawOldest())
    check(planner.append(segment(4)))
    check(planner.advertise()?.size == 4 && planner.withdrawOldest())
    check(planner.acquire(1) && !planner.acquire(1))
    now = 29 * second
    check(planner.eligible().isEmpty() && !planner.confirmDeleted(1))
    now = 30 * second
    check(planner.eligible().isEmpty() && !planner.acquire(1))
    check(planner.release(1) && planner.eligible() == listOf(1L))
    check(planner.retainedBytes() == 4 * mib)
    check(planner.confirmDeleted(1) && planner.retainedBytes() == 3 * mib)
    println("GRACE_AND_LEASE_PASS")

    now = 0
    val steady = RollingHlsLiveRetention(6 * second, 8, 32 * mib) { now }
    repeat(3) { check(steady.append(segment(it + 1L))) }
    check(steady.advertise()?.size == 3)
    var peak = 0
    for (seq in 4L..600L) {
        now = (seq - 4) * 6 * second
        steady.eligible().forEach { check(steady.confirmDeleted(it)) }
        check(steady.append(segment(seq)))
        peak = maxOf(peak, steady.retainedCount())
        check(steady.advertise()?.size == 4 && steady.withdrawOldest())
    }
    check(peak == 8 && steady.retainedBytes() <= 8 * mib)
    println("STEADY_STATE_PASS fragments=600 peak_slots=$peak")
    now--
    check(steady.advertise() == null && !steady.append(segment(601)))
    now += 100 * second
    check(steady.advertise() == null)
    println("CLOCK_ROLLBACK_PASS")

    now = 0
    val cap = RollingHlsLiveRetention(6 * second, 8, 32 * mib) { now }
    repeat(4) { check(cap.append(RollingHlsLiveRetention.Segment(it + 1L, 6 * second, 8 * mib))) }
    check(cap.advertise()?.size == 4 && cap.withdrawOldest())
    check(!cap.append(segment(5)) && cap.retainedBytes() == 32 * mib)
    println("BYTE_CAP_PASS")
}
