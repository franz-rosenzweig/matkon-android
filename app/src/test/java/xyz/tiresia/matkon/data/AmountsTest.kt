package xyz.tiresia.matkon.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountsTest {
    private fun s(
        a: String,
        f: Double,
    ) = Amounts.scale(a, f)

    @Test fun everyNumberScales() = assertEquals("4 portions (about 400g)", s("2 portions (about 200g)", 2.0))

    @Test fun fractions() {
        assertEquals("1 cup", s("1/2 cup", 2.0))
        assertEquals("3 tbsp", s("1½ tbsp", 2.0))
        assertEquals("¾ tsp", s("1 1/2 tsp", 0.5))
        assertEquals("⅓ cup", s("2/3 cup", 0.5))
    }

    @Test fun unitsAgree() {
        assertEquals("2 cups", s("1 cup", 2.0))
        assertEquals("½ lb", s("1 lbs", 0.5))
        assertEquals("2 thumbs", s("1 thumb", 2.0))
        assertEquals("1 to 1½ cloves", s("2 to 3 cloves", 0.5))
    }

    @Test fun ranges() = assertEquals("8-10", s("4-5", 2.0))

    @Test fun noNumberStays() {
        assertEquals("to taste", s("to taste", 2.0))
        assertEquals("", s("", 2.0))
        assertEquals("1 cup", s("1 cup", 1.0))
    }

    @Test fun hebrew() {
        assertEquals("1 כוס", s("חצי כוס", 2.0))
        assertEquals("15 פיתות", s("10 פיתות", 1.5))
    }

    @Test fun grams() = assertEquals("300g", s("200g", 1.5))

    @Test fun listAddsUp() {
        assertEquals("1½ cups", Amounts.total(listOf("1 cup", "1/2 cup")))
        assertEquals("2 tbsp + 1 tsp", Amounts.total(listOf("2 tbsp", "1 tsp")))
        assertEquals("5", Amounts.total(listOf("2", "3")))
        assertEquals("2 cups + 1 can (14 oz)", Amounts.total(listOf("1 can (14 oz)", "2 cups")))
    }

    @Test fun sameThing() {
        assertEquals(Amounts.key("Tomatoes"), Amounts.key("tomato"))
        assertEquals(Amounts.key("Cherries"), Amounts.key("cherry"))
        assertEquals(Amounts.key("Garlic"), Amounts.key("garlic"))
    }

    @Test fun mergedLines() {
        val items =
            listOf(
                ShopItem("1", "Garlic", "2 cloves", "a", false, null),
                ShopItem("2", "garlic", "1 clove", "b", false, null),
                ShopItem("3", "Lemon", "1", "a", true, null),
            )
        val lines = Amounts.merge(items)
        assertEquals(2, lines.size)
        assertEquals("3 cloves", lines[0].amount)
        assertEquals(listOf("1", "2"), lines[0].ids)
        assertEquals(listOf("a", "b"), lines[0].codes)
        assertEquals(true, lines[1].done)
    }
}
