package xyz.tiresia.matkon.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Amounts and temperatures in the units a person reads in: Units.amount("1 cup", "metric") is "240 ml",
 * Units.text("Bake at 350°F", "metric") is "Bake at 180°C". The mode "" (as written) changes nothing. Spoons stay
 * spoons in metric (every kitchen has them), and an amount that already gives both ("3 cups (720ml)") stays as
 * written. The same rules as the web app's units.js.
 */
object Units {
    private val GL = mapOf('½' to 0.5, '⅓' to 1 / 3.0, '⅔' to 2 / 3.0, '¼' to 0.25, '¾' to 0.75, '⅕' to 0.2, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875)
    private const val G = "½⅓⅔¼¾⅕⅛⅜⅝⅞"
    private const val D = "[0-9]"
    private const val Q = """(?:$D+\s+$D+\s*/\s*$D+|$D+\s*/\s*$D+|$D+(?:[.,]$D+)?\s*[$G]?|[$G])"""
    private val NICE = listOf(0.0 to "", 0.125 to "⅛", 0.25 to "¼", 1 / 3.0 to "⅓", 0.5 to "½", 2 / 3.0 to "⅔", 0.75 to "¾", 1.0 to "")

    /** A number as JavaScript writes it: 240, 2.5. */
    private fun js(x: Double): String = if (x == floor(x) && abs(x) < 1e15) x.toLong().toString() else x.toString()

    private fun round(x: Double): Double = Math.round(x).toDouble()

    private fun roundTo(
        x: Double,
        step: Double,
    ) = round(x / step) * step

    fun value(q: String): Double? {
        val s = q.trim().replace(',', '.')
        Regex("""^($D+)\s+($D+)\s*/\s*($D+)$""").find(s)?.let { m ->
            val (w, n, d) = m.destructured
            return if (d.toDouble() == 0.0) null else w.toDouble() + n.toDouble() / d.toDouble()
        }
        Regex("""^($D+)\s*/\s*($D+)$""").find(s)?.let { m ->
            val (n, d) = m.destructured
            return if (d.toDouble() == 0.0) null else n.toDouble() / d.toDouble()
        }
        Regex("""^($D+(?:\.$D+)?)?\s*([$G])?$""").find(s)?.let { m ->
            val whole = m.groupValues[1]
            val frac = m.groupValues[2]
            if (whole.isEmpty() && frac.isEmpty()) return null
            return (if (whole.isEmpty()) 0.0 else whole.toDouble()) + (if (frac.isEmpty()) 0.0 else GL.getValue(frac[0]))
        }
        return null
    }

    /** 1.5 → "1½", 0.33 → "⅓". */
    private fun frac(x: Double): String {
        val whole = floor(x)
        val f = x - whole
        var near = NICE[0]
        for (b in NICE) if (abs(b.first - f) < abs(near.first - f)) near = b
        if (abs(near.first - f) < 0.07) {
            val w = if (near.first == 1.0) whole + 1 else whole
            return if (near.second.isEmpty()) js(w) else if (w == 0.0) near.second else js(w) + near.second
        }
        return js(round(x * 10) / 10)
    }

    private data class Unit(val kind: String, val system: String, val size: Double)

    // what each unit is: kind, system, size in ml / g / cm
    private val U: Map<String, Unit> =
        linkedMapOf<String, Unit>().apply {
            fun put(
                kind: String,
                sys: String,
                size: Double,
                vararg names: String,
            ) = names.forEach { put(it, Unit(kind, sys, size)) }
            put("vol", "spoon", 5.0, "tsp", "teaspoon", "teaspoons", "כפית", "כפיות")
            put("vol", "spoon", 15.0, "tbsp", "tbs", "tablespoon", "tablespoons", "כף", "כפות")
            put("vol", "us", 240.0, "cup", "cups", "c", "כוס", "כוסות")
            put("vol", "us", 29.57, "fl oz")
            put("vol", "us", 473.0, "pint", "pints")
            put("vol", "us", 946.0, "quart", "quarts")
            put("vol", "us", 3785.0, "gallon")
            put("vol", "metric", 1.0, "ml", "מ\"ל", "מ״ל")
            put("vol", "metric", 10.0, "cl")
            put("vol", "metric", 100.0, "dl")
            put("vol", "metric", 1000.0, "l", "liter", "liters", "litre", "litres", "ליטר")
            put("wt", "us", 28.35, "oz", "ounce", "ounces")
            put("wt", "us", 453.6, "lb", "lbs", "pound", "pounds")
            put("wt", "metric", 1.0, "g", "gr", "gram", "grams", "גרם")
            put("wt", "metric", 1000.0, "kg", "kilo", "ק\"ג", "ק״ג", "קילו")
            put("len", "us", 2.54, "inch", "inches")
            put("len", "metric", 1.0, "cm")
        }

    private fun reEsc(x: String) = x.replace(Regex("""[.*+?^${'$'}{}()|\[\]\\]""")) { "\\" + it.value }

    private val NAMES = U.keys.sortedByDescending { it.length }.joinToString("|") { reEsc(it) }

    // a number (or range) and a unit right after it, at the start of an amount
    private val LEAD =
        Regex(
            """^(\s*(?:about|approx\.?|~|כ-?)?\s*)($Q)(?:(\s*(?:-|–|—|to|עד)\s*)($Q))?\s*($NAMES)(?![\p{L}])""",
            RegexOption.IGNORE_CASE,
        )
    private val PAREN = Regex("""\(([^)]*)\)""")
    private val IN_PAREN = Regex("""($Q)\s*($NAMES)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    private fun he(lang: String?) = lang == "he"

    /** base ml / g / cm → the number and the unit in the other system. */
    private fun show(
        base: Double,
        kind: String,
        mode: String,
        lang: String?,
    ): Pair<String, String> {
        if (mode == "metric") {
            if (kind == "vol") {
                return if (base >= 1000) {
                    js(round(base / 100) / 10) to (if (he(lang)) "ליטר" else "l")
                } else {
                    js(if (base < 20) round(base) else roundTo(base, 5.0)) to (if (he(lang)) "מ״ל" else "ml")
                }
            }
            if (kind == "wt") {
                return if (base >= 1000) {
                    js(round(base / 100) / 10) to (if (he(lang)) "ק״ג" else "kg")
                } else {
                    js(if (base < 20) round(base) else if (base < 100) roundTo(base, 5.0) else roundTo(base, 10.0)) to (if (he(lang)) "גרם" else "g")
                }
            }
            return js(round(base * 2) / 2) to "cm"
        }
        if (kind == "vol") {
            if (base >= 60) {
                val c = base / 240
                val n =
                    if (c < 1) {
                        val opts = listOf(0.25 to "¼", 1 / 3.0 to "⅓", 0.5 to "½", 2 / 3.0 to "⅔", 0.75 to "¾", 1.0 to "1")
                        var best = opts[0]
                        for (b in opts) if (abs(b.first - c) < abs(best.first - c)) best = b
                        best.second
                    } else {
                        frac(if (c >= 2) roundTo(c, 0.25) else c)
                    }
                val many = (value(n) ?: 0.0) > 1
                return n to (if (he(lang)) (if (many) "כוסות" else "כוס") else if (many) "cups" else "cup")
            }
            if (base >= 15) return frac(base / 15) to (if (he(lang)) "כפות" else "tbsp")
            return frac(base / 5) to (if (he(lang)) "כפיות" else "tsp")
        }
        if (kind == "wt") {
            if (base >= 408) return frac(roundTo(base / 453.6, 0.25)) to "lb"
            return frac(max(0.5, roundTo(base / 28.35, 0.5))) to "oz"
        }
        return frac(roundTo(base / 2.54, 0.25)) to (if (he(lang)) "אינץ׳" else "inch")
    }

    /** Does the amount already give it in the wanted system ("3 cups (720ml)")? */
    private fun alreadyBoth(
        a: String,
        mode: String,
    ): Boolean =
        PAREN.findAll(a).any { p ->
            val inner = p.value.substring(1, p.value.length - 1)
            val m = IN_PAREN.find(inner) ?: return@any false
            U[m.groupValues[2].lowercase()]?.system == mode
        }

    /** An amount ("2 cups", "1 lb", "about 3-4 cups") in the units of `mode`: "", "metric" or "us". */
    fun amount(
        a: String,
        mode: String?,
        lang: String? = null,
    ): String {
        if (mode.isNullOrEmpty() || a.isBlank()) return a
        if (alreadyBoth(a, mode)) return a
        val m = LEAD.find(a) ?: return a
        val g = m.groupValues
        val u = U[g[5].lowercase()] ?: U[g[5]] ?: return a
        if (u.system == mode || u.system == "spoon") return a // spoons are spoons everywhere
        val x = value(g[2]) ?: return a
        val y = if (g[4].isNotEmpty()) value(g[4]) else null
        val (nx, unit) = show(x * u.size, u.kind, mode, lang)
        val ny = if (y != null) show(y * u.size, u.kind, mode, lang).first else null
        val rest = a.substring(m.value.length)
        return g[1] + nx + (if (ny != null) g[3] + ny else "") + " " + unit + rest
    }

    // temperatures, and amounts inside a sentence ("add 2 cups of flour", "bake at 350°F")
    private const val END = "(?![A-Za-z0-9_])"
    private val T_DEG = Regex("""($D{2,3})\s*°\s*([CF])$END""", RegexOption.IGNORE_CASE)
    private val T_WORD = Regex("""($D{2,3})\s*(?:degrees?\s*)?(fahrenheit|celsius|F$END|C$END)""", RegexOption.IGNORE_CASE)
    private val T_BARE_EN = Regex("""($D{2,3})\s+degrees$END(?!\s*(?:F|C|fahrenheit|celsius)$END)""", RegexOption.IGNORE_CASE)
    private val T_BARE_HE = Regex("""($D{2,3})\s*מעלות(?!\s*(?:צלזיוס|פרנהייט))""")
    private val HAS_C = Regex("""°\s*C|celsius""", RegexOption.IGNORE_CASE)
    private val HAS_F = Regex("""°\s*F|fahrenheit""", RegexOption.IGNORE_CASE)
    private val IN_TEXT =
        Regex(
            """($Q)(?:(\s*(?:-|–|—|to|עד)\s*)($Q))?\s*(cups?|tablespoons?|tbsp|teaspoons?|tsp|fl oz|ounces?|oz|lbs?|pounds?|grams?|g|kg|ml|liters?|litres?|inch(?:es)?|cm|כוסות|כוס|גרם|ק"ג|ק״ג|מ"ל|מ״ל|ליטר)(?![\p{L}])""",
            RegexOption.IGNORE_CASE,
        )

    private fun toC(f: Double) = roundTo((f - 32) * 5 / 9, 10.0)

    private fun toF(c: Double): Double {
        val f = c * 9 / 5 + 32
        return if (f >= 200) roundTo(f, 25.0) else roundTo(f, 5.0)
    }

    private fun temp(
        n: Int,
        unit: String,
        mode: String,
    ): String =
        if (mode == "metric") {
            if (unit == "F") js(toC(n.toDouble())) + "°C" else "$n°C"
        } else {
            if (unit == "C") js(toF(n.toDouble())) + "°F" else "$n°F"
        }

    /** A sentence (a step, a tip, the oven line) with its temperatures and amounts in the units of `mode`. */
    fun text(
        t: String,
        mode: String?,
        lang: String? = null,
    ): String {
        if (mode.isNullOrEmpty() || t.isEmpty()) return t
        var s = t
        val both = HAS_C.containsMatchIn(s) && HAS_F.containsMatchIn(s)
        if (!both) {
            s = T_DEG.replace(s) { temp(it.groupValues[1].toInt(), it.groupValues[2].uppercase(), mode) }
            s = T_WORD.replace(s) { temp(it.groupValues[1].toInt(), if (it.groupValues[2].startsWith("f", ignoreCase = true)) "F" else "C", mode) }
            s = T_BARE_EN.replace(s) { val n = it.groupValues[1].toInt(); temp(n, if (n > 260) "F" else "C", mode) }
            s = T_BARE_HE.replace(s) { val n = it.groupValues[1].toInt(); temp(n, if (n > 260) "F" else "C", mode) }
        }
        return IN_TEXT.replace(s) { amount(it.value, mode, lang) }
    }
}
