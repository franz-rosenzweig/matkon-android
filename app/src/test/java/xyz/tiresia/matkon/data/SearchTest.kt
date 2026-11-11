package xyz.tiresia.matkon.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Search and the editor's timers, as the web app has them. */
class SearchTest {
    private fun hay(text: String): Search.Hay {
        val s = Amounts.norm(text)
        return Search.Hay(s, s.split(' ').distinct())
    }

    @Test fun near() {
        assertEquals(true, Search.near("tomato", "tomatoe", 1))
        assertEquals(true, Search.near("ramen", "remen", 1))
        assertEquals(true, Search.near("shakshuka", "shakshouka", 2))
        assertEquals(false, Search.near("abc", "xyz", 1))
        assertEquals(false, Search.near("pasta", "pizza", 1))
        assertEquals(true, Search.near("", "ab", 2))
        assertEquals(true, Search.near("chocolate", "chocolat", 1))
    }

    @Test fun typosForgiven() {
        assertEquals("ramen", true, Search.wordHit(Amounts.norm("ramen"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("remen", true, Search.wordHit(Amounts.norm("remen"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("ramn", true, Search.wordHit(Amounts.norm("ramn"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("chocolat", true, Search.wordHit(Amounts.norm("chocolat"), hay("brown butter chocolate chip cookies bake at 180")))
        assertEquals("choclate", true, Search.wordHit(Amounts.norm("choclate"), hay("brown butter chocolate chip cookies bake at 180")))
        assertEquals("cokies", true, Search.wordHit(Amounts.norm("cokies"), hay("brown butter chocolate chip cookies bake at 180")))
        assertEquals("egg", true, Search.wordHit(Amounts.norm("egg"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("eg", true, Search.wordHit(Amounts.norm("eg"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("bok", true, Search.wordHit(Amounts.norm("bok"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("shakshuka", false, Search.wordHit(Amounts.norm("shakshuka"), hay("שקשוקה ביצים ברוטב עגבניות")))
        assertEquals("עגבניה", true, Search.wordHit(Amounts.norm("עגבניה"), hay("שקשוקה ביצים ברוטב עגבניות")))
        assertEquals("ביצה", true, Search.wordHit(Amounts.norm("ביצה"), hay("שקשוקה ביצים ברוטב עגבניות")))
        assertEquals("pizza", false, Search.wordHit(Amounts.norm("pizza"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("jamy", true, Search.wordHit(Amounts.norm("jamy"), hay("miso ramen with jammy egg and bok choy")))
        assertEquals("butte", true, Search.wordHit(Amounts.norm("butte"), hay("brown butter chocolate chip cookies bake at 180")))
    }

    @Test fun everyWordMustBeThere() {
        val h = hay("miso ramen with jammy egg")
        assertEquals(true, Search.matches(h, Search.words("Ramen egg")))
        assertEquals(false, Search.matches(h, Search.words("ramen pizza")))
        assertEquals(true, Search.matches(h, emptyList()))
    }

    @Test fun timersIn() {
        assertEquals(listOf<Int>(20, 25), Edits.timersIn("Bake 20 to 25 minutes"))
        assertEquals(listOf<Int>(60), Edits.timersIn("Simmer for 1 hour"))
        assertEquals(listOf<Int>(120, 180), Edits.timersIn("cook 2-3 hrs"))
        assertEquals(listOf<Int>(15), Edits.timersIn("מבשלים 15 דקות"))
        assertEquals(listOf<Int>(40, 45), Edits.timersIn("אופים 40-45 דקות"))
        assertEquals(listOf<Int>(5, 10, 90), Edits.timersIn("Rest 5 min, then 10 mins more, then 1.5 hours"))
        assertEquals(listOf<Int>(), Edits.timersIn("no timer here"))
        assertEquals(listOf<Int>(), Edits.timersIn("Cook 6½ minutes"))
        assertEquals(listOf<Int>(3), Edits.timersIn("boil 2,5 minutes"))
        assertEquals(listOf<Int>(), Edits.timersIn("wait 3000 minutes"))
        assertEquals(listOf<Int>(5, 7, 9), Edits.timersIn("5 min 5 min 7 min 9 min"))
    }
}
