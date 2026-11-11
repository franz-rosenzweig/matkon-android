package xyz.tiresia.matkon.data

/** Fixing a recipe by hand: what the editor sends to the server (POST api/r/<code>/edit). */
object Edits {
    private val TIMER =
        Regex(
            """([0-9]+(?:[.,][0-9]+)?)(?:\s*(?:-|–|to|עד|או)\s*([0-9]+(?:[.,][0-9]+)?))?\s*(minutes?|mins?|דקות|דק׳|דקה|hours?|hrs?|שעות)""",
            RegexOption.IGNORE_CASE,
        )
    private val HOURS = Regex("^(h|שע)", RegexOption.IGNORE_CASE)

    /** "bake 20 to 25 minutes" → [20, 25], the same as Matkon's own reading (and the web's editor). */
    fun timersIn(text: String): List<Int> {
        val out = mutableListOf<Int>()
        for (m in TIMER.findAll(text)) {
            val h = if (HOURS.containsMatchIn(m.groupValues[3])) 60 else 1
            listOf(m.groupValues[1], m.groupValues[2]).filter { it.isNotEmpty() }.forEach { x ->
                val v = Math.round(x.replaceFirst(',', '.').toDouble() * h)
                if (v in 1L..1440L && v.toInt() !in out) out.add(v.toInt())
            }
        }
        return out.take(3)
    }

    data class Ing(val amount: String, val item: String, val note: String)

    data class Group(val name: String, val items: List<Ing>)

    /** A step as typed, with the text it started with and that text's timers. */
    data class StepIn(val text: String, val orig: String?, val timers: List<Int>)

    /** The recipe as the server keeps a fix: empty rows dropped, a changed step's timers read again. */
    fun recipe(
        title: String,
        summary: String,
        servings: String,
        prep: String,
        cook: String,
        total: String,
        oven: String,
        groups: List<Group>,
        steps: List<StepIn>,
        tips: String,
        missing: String,
    ): Map<String, Any?> {
        val lines = { t: String -> t.trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() } }
        return mapOf(
            "title" to title.trim(),
            "summary" to summary.trim(),
            "servings" to servings.trim(),
            "time" to mapOf("prep" to prep.trim(), "cook" to cook.trim(), "total" to total.trim()),
            "oven" to oven.trim(),
            "ingredient_groups" to
                groups
                    .map { g ->
                        mapOf(
                            "name" to g.name.trim(),
                            "items" to
                                g.items
                                    .map { mapOf("amount" to it.amount.trim(), "item" to it.item.trim(), "note" to it.note.trim()) }
                                    .filter { it["item"]!!.isNotEmpty() },
                        )
                    }.filter { (it["items"] as List<*>).isNotEmpty() },
            "steps" to
                steps
                    .map { s ->
                        val text = s.text.trim()
                        mapOf("text" to text, "timers" to if (text == s.orig) s.timers.filter { it != 0 } else timersIn(text))
                    }.filter { (it["text"] as String).isNotEmpty() },
            "tips" to lines(tips),
            "missing" to lines(missing),
        )
    }
}
