package xyz.tiresia.matkon.data

import kotlin.random.Random

/** The same changes the server's memory.apply makes, done on the phone first so the app answers at once
 * (and keeps working offline). The server's answer replaces this copy when it comes. */
object Local {
    fun newId(): String = (1..8).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

    private fun now() = System.currentTimeMillis() / 1000.0

    @Suppress("UNCHECKED_CAST")
    fun apply(
        m: Memory,
        op: String,
        a: Map<String, Any?>,
    ): Memory {
        val code = a["code"] as String?
        return when (op) {
            "save" ->
                m.copy(saved = if (a["on"] == true) m.saved.orEmpty() + (code!! to now()) else m.saved.orEmpty() - code!!)
            "ticks" -> m.copy(ticks = m.ticks.orEmpty() + (code!! to (a["keys"] as List<String>)))
            "step" -> {
                val n = (a["n"] as Number?)?.toInt()
                m.copy(steps = if (n == null) m.steps.orEmpty() - code!! else m.steps.orEmpty() + (code!! to n))
            }
            "cooked" ->
                counted(
                    m.copy(
                        cooked = m.cooked.orEmpty() + Cooked(a["id"] as String, code!!, now()),
                        steps = m.steps.orEmpty() - code,
                        ticks = m.ticks.orEmpty() - code,
                    ),
                )
            "uncook" -> counted(m.copy(cooked = m.cooked.orEmpty().filter { it.id != a["id"] }))
            "note" -> {
                val t = (a["text"] as String?).orEmpty()
                m.copy(notes = if (t.isBlank()) m.notes.orEmpty() - code!! else m.notes.orEmpty() + (code!! to t))
            }
            "rate" -> {
                val r = (a["rating"] as Number?)?.toInt() ?: 0
                m.copy(ratings = if (r == 0) m.ratings.orEmpty() - code!! else m.ratings.orEmpty() + (code!! to r))
            }
            "setting" -> m.copy(settings = m.settings.orEmpty() + ((a["key"] as String) to (a["value"] == true)))
            "plan-add" ->
                m.copy(
                    plan =
                        (m.plan.orEmpty() + PlanEntry(a["id"] as String, code!!, a["day"] as String, now()))
                            .sortedWith(compareBy({ it.day }, { it.at ?: 0.0 })),
                )
            "plan-move" ->
                m.copy(plan = m.plan.orEmpty().map { if (it.id == a["id"]) it.copy(day = a["day"] as String) else it }.sortedBy { it.day })
            "plan-remove" -> m.copy(plan = m.plan.orEmpty().filter { it.id != a["id"] })
            "shop-add" -> {
                val items =
                    (a["items"] as List<Map<String, Any?>>).mapNotNull {
                        val item = (it["item"] as String?)?.trim().orEmpty()
                        if (item.isEmpty()) null else ShopItem(it["id"] as String, item, it["amount"] as String?, code, false, now())
                    }
                m.copy(shop = m.shop.orEmpty() + items)
            }
            "shop-tick" -> {
                val ids = (a["ids"] as List<String>).toSet()
                m.copy(shop = m.shop.orEmpty().map { if (it.id in ids) it.copy(done = a["done"] == true) else it })
            }
            "shop-remove" -> {
                val ids = (a["ids"] as List<String>).toSet()
                m.copy(shop = m.shop.orEmpty().filter { it.id !in ids })
            }
            "shop-clear" -> m.copy(shop = if (a["done"] == true) m.shop.orEmpty().filter { !it.done } else emptyList())
            else -> m
        }
    }

    private fun counted(m: Memory): Memory {
        val count = mutableMapOf<String, Int>()
        val last = mutableMapOf<String, Double>()
        m.cooked.orEmpty().forEach {
            count[it.code] = (count[it.code] ?: 0) + 1
            last[it.code] = maxOf(last[it.code] ?: 0.0, it.at)
        }
        return m.copy(cooked_count = count, cooked_last = last)
    }
}
