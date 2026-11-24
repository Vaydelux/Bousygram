package com.vaydelux.bousygram.core.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class BoundedOrderedWindowTest {
    private data class Item(val id: Int, val revision: Int)

    @Test fun matchesFullMapReferenceAcrossRandomOverlappingPages() {
        val random = Random(233)
        repeat(400) {
            val limit = random.nextInt(1, 241)
            val existing = List(random.nextInt(0, 400)) { Item(random.nextInt(300), it) }
            val incoming = List(random.nextInt(0, 600)) { Item(random.nextInt(300), it + 1000) }
            val reference = linkedMapOf<Int, Item>()
            (existing + incoming).forEach { reference[it.id] = it }
            val actual = mergeBoundedOrderedWindow(existing, incoming, limit) { it.id }
            assertEquals(reference.values.toList().takeLast(limit), actual.items)
            assertEquals((reference.size - limit).coerceAtLeast(0), actual.removedFromFront)
        }
    }

    @Test fun evictedDuplicateDoesNotReenterWindow() {
        val actual = mergeBoundedOrderedWindow(
            listOf(Item(1, 0), Item(2, 0)), listOf(Item(3, 1), Item(1, 2), Item(2, 3)), 2
        ) { it.id }
        assertEquals(listOf(Item(2, 3), Item(3, 1)), actual.items)
        assertEquals(1, actual.removedFromFront)
    }
}
