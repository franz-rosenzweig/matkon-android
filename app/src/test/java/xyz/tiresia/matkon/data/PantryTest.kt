package xyz.tiresia.matkon.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PantryTest {
    private fun entry(
        code: String,
        main: List<String>?,
        items: List<String> = emptyList(),
    ) = IndexEntry(
        code, code, null, null, "en", null, null, null, true, true, 0.0, null, null, null, null, null, main, null, items, null, null, null, null, null,
    )

    @Test fun saltAndSpicesAreNotNeeded() {
        val e = entry("a", listOf("Eggs", "salt", "Black pepper", "ground cumin", "tomatoes", "eggs"))
        assertEquals(listOf("Eggs", "tomatoes", "eggs"), Pantry.needsOf(e, false))
    }

    @Test fun haveItByTheWholeWord() {
        assertEquals(true, Pantry.haveIt("cherry tomatoes", listOf("tomato")))
        assertEquals(true, Pantry.haveIt("egg", listOf("eggs")))
        assertEquals(false, Pantry.haveIt("eggplant", listOf("egg")))
        assertEquals(false, Pantry.haveIt("flour", emptyList()))
    }

    @Test fun fewestMissingFirst() {
        val a = entry("a", listOf("eggs", "tomatoes", "feta"))
        val b = entry("b", listOf("eggs", "tomatoes"))
        val c = entry("c", listOf("flour", "butter"))
        val ranked = Pantry.ranked(listOf(a, b, c), listOf("eggs", "tomato"), false)
        assertEquals(listOf("b", "a"), ranked.map { it.e.code })
        assertEquals(listOf("feta"), ranked[1].miss)
        assertEquals(listOf("feta", "flour", "butter"), Pantry.offer(listOf(a, b, c), listOf("eggs", "tomato"), false))
    }
}
