import com.vaydelux.bousygram.core.account.*
import com.vaydelux.bousygram.core.tdlib.server.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok) { "Assertion ${checks + 1} failed" }; checks++ }
    val session = AccountSessionController()
    val owner = session.activate(101, session.generation())!!
    val first = LinkageError("fixture")
    val second = IllegalStateException("fixture")
    var fail = true
    var cleaned = 0
    session.registerInvalidationHandler { if (fail) throw first }
    session.registerInvalidationHandler { if (fail) throw second }
    session.registerInvalidationHandler { if (fail) throw first }
    session.registerInvalidationHandler { cleaned++ }
    val failure = runCatching { session.invalidate() }.exceptionOrNull()
    verify(cleaned == 1)
    verify(failure?.cause === first)
    verify(first.suppressed.single() === second)
    verify(!session.isCurrent(owner))
    verify(session.current() == null)
    verify(session.activate(202, session.generation()) == null)
    verify(!session.runIfCurrent(owner) { error("stale publication") })
    fail = false
    val generation = session.invalidate()
    verify(cleaned == 2)
    val next = session.activate(202, generation)!!
    verify(next.accountId == 202L)
    verify(!session.isCurrent(owner))
    verify(session.isCurrent(next))
    val entered = CountDownLatch(1)
    val released = CountDownLatch(1)
    session.registerInvalidationHandler {
        entered.countDown()
        check(released.await(2, TimeUnit.SECONDS))
    }
    val thread = Thread { session.invalidate() }
    thread.start()
    try {
        verify(entered.await(2, TimeUnit.SECONDS))
        verify(!session.runIfCurrent(next) {})
        verify(session.activate(303, session.generation()) == null)
    } finally {
        released.countDown()
        thread.join(3000)
    }
    verify(!thread.isAlive)
    verify(session.activate(303, session.generation()) != null)
    verify(advancePrefetchOffset(100, 200, 10, 50) == 110L)
    verify(advancePrefetchOffset(100, 200, 10, 5) == 105L)
    verify(advancePrefetchOffset(100, 200, 10, 0) == 100L)
    verify(advancePrefetchOffset(Long.MAX_VALUE - 2, Long.MAX_VALUE, 20, 20) == Long.MAX_VALUE)
    verify(trackedPrefetchRange(10, 0).isEmpty())
    verify(trackedPrefetchRange(10, 5) == 10L..14L)
    verify(trackedPrefetchRange(Long.MAX_VALUE - 2, 10) == (Long.MAX_VALUE - 2)..(Long.MAX_VALUE - 1))
    val random = Random(233)
    repeat(1000) {
        val start = Long.MAX_VALUE - random.nextLong(0, 100000)
        val end = saturatingPrefetchEnd(start, random.nextLong(0, 100000))
        val request = random.nextInt(0, 100000)
        val result = random.nextInt(0, 100000)
        val advanced = advancePrefetchOffset(start, end, request, result)
        verify(advanced in start..end)
        verify(advanced - start == minOf(request.toLong(), result.toLong(), end - start))
    }
    verify(savedTailSliceBounds(100L, 64, 100L + (1L shl 32), 8) == null)
    verify(savedTailSliceBounds(100L, 64, 164L, 8) == null)
    verify(savedTailSliceBounds(100L, 64, 99L, 8) == null)
    verify(savedTailSliceBounds(100L, 64, 102L, 8) == 2..9)
    verify(savedTailSliceBounds(100L, 64, 160L, 8) == 60..63)
    verify(savedTailSliceBounds(0L, Int.MAX_VALUE, Int.MAX_VALUE.toLong() - 1, Int.MAX_VALUE) ==
        (Int.MAX_VALUE - 1)..(Int.MAX_VALUE - 1))
    verify(savedTailSliceBounds(-1L, 64, 0L, 8) == null)
    verify(savedTailSliceBounds(0L, 64, Long.MAX_VALUE, 8) == null)
    verify(savedTailSliceBounds(0L, 64, 0L, 0) == null)
    println("PASS $checks assertions: account cleanup/activation barrier, prefetch arithmetic, and saved-tail slices")
}
