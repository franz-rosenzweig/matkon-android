package xyz.tiresia.matkon.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The changes made on the phone first, as the server makes them. */
class LocalTest {
    private val m = Memory.EMPTY

    @Test fun units() {
        val m2 = Local.apply(m, "setting", mapOf("key" to "units", "value" to "metric"))
        assertEquals("metric", m2.units())
        assertEquals("", Local.apply(m2, "setting", mapOf("key" to "units", "value" to "")).units())
        assertEquals(true, Local.apply(m2, "setting", mapOf("key" to "english", "value" to true)).english())
    }

    @Test fun collections() {
        var x = Local.apply(m, "coll-new", mapOf("id" to "c1", "name" to " Shabbat "))
        x = Local.apply(x, "coll-new", mapOf("id" to "c1", "name" to "again"))
        x = Local.apply(x, "coll-add", mapOf("id" to "c1", "code" to "demo-ramen"))
        x = Local.apply(x, "coll-add", mapOf("id" to "c1", "code" to "demo-ramen"))
        assertEquals(1, x.collections!!.size)
        assertEquals("Shabbat", x.collections!![0].name)
        assertEquals(listOf("demo-ramen"), x.collections!![0].codes)
        x = Local.apply(x, "coll-rename", mapOf("id" to "c1", "name" to "Friday"))
        x = Local.apply(x, "coll-remove", mapOf("id" to "c1", "code" to "demo-ramen"))
        assertEquals("Friday", x.collections!![0].name)
        assertEquals(emptyList<String>(), x.collections!![0].codes)
        assertEquals(0, Local.apply(x, "coll-delete", mapOf("id" to "c1")).collections!!.size)
    }

    @Test fun cookingLog() {
        var x = Local.apply(m, "cooked", mapOf("code" to "demo-ramen", "id" to "a1", "factor" to 2.0, "note" to " more ginger "))
        assertEquals(2.0, x.cooked!![0].factor)
        assertEquals("more ginger", x.cooked!![0].note)
        assertEquals(1, x.cooked_count!!["demo-ramen"])
        x = Local.apply(x, "cook-note", mapOf("id" to "a1", "note" to ""))
        assertEquals(null, x.cooked!![0].note)
    }

    @Test fun pantryAndAisles() {
        val x = Local.apply(m, "pantry", mapOf("items" to listOf("Eggs", "eggs", " feta ", "")))
        assertEquals(listOf("Eggs", "feta"), x.pantry)
        val y = Local.apply(m, "aisle", mapOf("key" to "lemon", "aisle" to "drinks"))
        assertEquals(mapOf("lemon" to "drinks"), y.aisles)
        assertEquals(emptyMap<String, String>(), Local.apply(y, "aisle", mapOf("key" to "lemon", "aisle" to "")).aisles)
    }
}
