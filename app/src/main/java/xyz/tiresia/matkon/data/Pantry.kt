package xyz.tiresia.matkon.data

/** "What can I make?": what's in the kitchen against the main things each recipe needs, as on the web. */
object Pantry {
    private val STAPLE =
        listOf("salt", "pepper", "black pepper", "salt and pepper", "water", "oil", "olive oil", "vegetable oil", "sugar", "ice", "מלח", "פלפל שחור", "מים", "שמן", "שמן זית", "סוכר")
            .map { Amounts.norm(it) }
            .toSet()

    /** The main things a recipe needs, not salt and spices. */
    fun needsOf(
        e: IndexEntry,
        english: Boolean,
    ): List<String> {
        val useEn = english && e.lang != "en" && !e.items_en.isNullOrEmpty()
        val list = if (useEn) e.items_en.orEmpty() else if (!e.main_ingredients.isNullOrEmpty()) e.main_ingredients else e.items.orEmpty()
        val out =
            list
                .map { it.orEmpty().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .filter { Amounts.norm(it) !in STAPLE && Aisles.of(it) != "spices" }
        return if (useEn) out.take(8) else out
    }

    fun haveIt(
        x: String,
        have: List<String>,
    ): Boolean {
        val kx = " " + Amounts.key(x) + " "
        return have.any { p ->
            val kp = Amounts.key(p)
            kp.isNotEmpty() && (kx.contains(" $kp ") || " $kp ".contains(kx))
        }
    }

    /** Things to add from the recipes, the most used first. */
    fun offer(
        recipes: List<IndexEntry>,
        have: List<String>,
        english: Boolean,
    ): List<String> {
        val common = LinkedHashMap<String, Pair<Int, String>>()
        recipes.forEach { r ->
            needsOf(r, english).forEach { x ->
                val k = Amounts.key(x)
                val c = common[k]
                common[k] = if (c == null) 1 to x else (c.first + 1) to c.second
            }
        }
        return common.values.sortedByDescending { it.first }.map { it.second }.filter { !haveIt(it, have) }.take(30)
    }

    data class Can(val e: IndexEntry, val got: List<String>, val miss: List<String>)

    /** The recipes that use something in the kitchen, the fewest missing first. */
    fun ranked(
        recipes: List<IndexEntry>,
        have: List<String>,
        english: Boolean,
    ): List<Can> =
        recipes
            .map { r ->
                val need = needsOf(r, english)
                Can(r, need.filter { haveIt(it, have) }, need.filter { !haveIt(it, have) })
            }.filter { it.got.isNotEmpty() }
            .sortedWith(compareBy<Can> { it.miss.size }.thenByDescending { it.got.size })
            .take(40)
}
