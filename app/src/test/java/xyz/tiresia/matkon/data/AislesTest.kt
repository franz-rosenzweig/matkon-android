package xyz.tiresia.matkon.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/** The same aisles as the web app's aisles.js. */
class AislesTest {
    @Test fun longestPhraseWins() {
        assertEquals("spices", Aisles.of("black pepper"))
        assertEquals("produce", Aisles.of("red bell pepper"))
        assertEquals("produce", Aisles.of("cherry tomatoes"))
    }

    @Test fun hebrewPrefixes() {
        assertEquals("produce", Aisles.of("הבצל"))
        assertEquals("produce", Aisles.of("ובצל"))
    }

    /** The whole table, phrase by phrase in its order, is the web's (aisles.js gives 758 phrases with this hash). */
    @Test fun sameTableAsTheWeb() {
        assertEquals(758, Aisles.TABLE.size)
        val lines = Aisles.TABLE.joinToString("\n") { it.first + "\t" + it.second }
        val hash = MessageDigest.getInstance("SHA-256").digest(lines.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertEquals("b9dd1a98c35033cfd22b3a10e40777fee8c5e32c2606c0272435125136a78252", hash)
    }

    @Test fun items() {
        assertEquals("cherry tomatoes", "produce", Aisles.of("cherry tomatoes"))
        assertEquals("black pepper", "spices", Aisles.of("black pepper"))
        assertEquals("pepper", "other", Aisles.of("pepper"))
        assertEquals("red bell pepper", "produce", Aisles.of("red bell pepper"))
        assertEquals("olive oil", "pantry", Aisles.of("olive oil"))
        assertEquals("eggs", "dairy", Aisles.of("eggs"))
        assertEquals("chicken thighs", "meat", Aisles.of("chicken thighs"))
        assertEquals("flour", "pantry", Aisles.of("flour"))
        assertEquals("פלפל שחור", "spices", Aisles.of("פלפל שחור"))
        assertEquals("בצל", "produce", Aisles.of("בצל"))
        assertEquals("הבצל", "produce", Aisles.of("הבצל"))
        assertEquals("ובצל", "produce", Aisles.of("ובצל"))
        assertEquals("שמן זית", "pantry", Aisles.of("שמן זית"))
        assertEquals("frozen peas", "frozen", Aisles.of("frozen peas"))
        assertEquals("parchment paper", "household", Aisles.of("parchment paper"))
        assertEquals("white wine", "drinks", Aisles.of("white wine"))
        assertEquals("unicorn tears", "other", Aisles.of("unicorn tears"))
        assertEquals("salt", "spices", Aisles.of("salt"))
        assertEquals("ground cumin", "spices", Aisles.of("ground cumin"))
        assertEquals("tomatoes", "produce", Aisles.of("tomatoes"))
        assertEquals("lemons", "produce", Aisles.of("lemons"))
        assertEquals("garlic cloves", "produce", Aisles.of("garlic cloves"))
        assertEquals("baby bok choy", "produce", Aisles.of("baby bok choy"))
        assertEquals("fresh ramen noodles", "pantry", Aisles.of("fresh ramen noodles"))
        assertEquals("toasted sesame oil", "pantry", Aisles.of("toasted sesame oil"))
        assertEquals("white miso", "pantry", Aisles.of("white miso"))
        assertEquals("unsalted butter", "dairy", Aisles.of("unsalted butter"))
        assertEquals("dark chocolate", "pantry", Aisles.of("dark chocolate"))
        assertEquals("spring onions", "produce", Aisles.of("spring onions"))
        assertEquals("kosher salt", "spices", Aisles.of("kosher salt"))
        assertEquals("chicken or vegetable stock", "pantry", Aisles.of("chicken or vegetable stock"))
        assertEquals("ginger", "produce", Aisles.of("ginger"))
        assertEquals("feta", "dairy", Aisles.of("feta"))
        assertEquals("red onion", "produce", Aisles.of("red onion"))
        assertEquals("green pepper", "other", Aisles.of("green pepper"))
        assertEquals("kalamata olives", "pantry", Aisles.of("kalamata olives"))
        assertEquals("dried oregano", "spices", Aisles.of("dried oregano"))
        assertEquals("red wine vinegar", "drinks", Aisles.of("red wine vinegar"))
        assertEquals("ג׳ינג׳ר", "produce", Aisles.of("ג׳ינג׳ר"))
        assertEquals("עגבניות מרוסקות", "pantry", Aisles.of("עגבניות מרוסקות"))
        assertEquals("ביצים", "dairy", Aisles.of("ביצים"))
        assertEquals("פטרוזיליה", "produce", Aisles.of("פטרוזיליה"))
        assertEquals("paper towels", "household", Aisles.of("paper towels"))
        assertEquals("", "other", Aisles.of(""))
        assertEquals("Ice Cream", "frozen", Aisles.of("Ice Cream"))
        assertEquals("sweet potatoes", "produce", Aisles.of("sweet potatoes"))
        assertEquals("brown sugar", "pantry", Aisles.of("brown sugar"))
        assertEquals("פיתות", "bakery", Aisles.of("פיתות"))
        assertEquals("חזה עוף", "meat", Aisles.of("חזה עוף"))
        assertEquals("coriander seeds", "spices", Aisles.of("coriander seeds"))
        assertEquals("fresh coriander", "produce", Aisles.of("fresh coriander"))
        assertEquals("egg yolk", "dairy", Aisles.of("egg yolk"))
        assertEquals("lemon juice", "produce", Aisles.of("lemon juice"))
    }
}
