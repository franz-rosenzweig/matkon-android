package xyz.tiresia.matkon.data

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Amounts as the reels give them ("1/2 cup", "4-5", "1½ tbsp", "חצי כוס", "to taste"): scaled by a
 * factor, and added up on the shopping list when the same thing comes from more than one recipe. */
object Amounts {
    private val GLYPHS =
        mapOf('½' to 0.5, '⅓' to 1 / 3.0, '⅔' to 2 / 3.0, '¼' to 0.25, '¾' to 0.75, '⅕' to 0.2, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875)
    private const val G = "½⅓⅔¼¾⅕⅛⅜⅝⅞"
    private const val Q = """(?:\d+\s+\d+\s*/\s*\d+|\d+\s*/\s*\d+|\d+(?:[.,]\d+)?\s*[$G]?|[$G])"""
    private val HEBREW = mapOf("חצי" to 0.5, "רבע" to 0.25, "שליש" to 1 / 3.0)
    private val NICE = listOf(0.0 to "", 0.125 to "⅛", 0.25 to "¼", 1 / 3.0 to "⅓", 0.5 to "½", 2 / 3.0 to "⅔", 0.75 to "¾", 1.0 to "")

    fun value(q: String): Double? {
        val s = q.trim().replace(',', '.')
        Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)$""").find(s)?.let { m ->
            val (w, n, d) = m.destructured
            return if (d.toDouble() == 0.0) null else w.toDouble() + n.toDouble() / d.toDouble()
        }
        Regex("""^(\d+)\s*/\s*(\d+)$""").find(s)?.let { m ->
            val (n, d) = m.destructured
            return if (d.toDouble() == 0.0) null else n.toDouble() / d.toDouble()
        }
        Regex("""^(\d+(?:\.\d+)?)?\s*([$G])?$""").find(s)?.let { m ->
            val whole = m.groupValues[1].toDoubleOrNull()
            val frac = m.groupValues[2].firstOrNull()?.let { GLYPHS[it] }
            if (whole == null && frac == null) return null
            return (whole ?: 0.0) + (frac ?: 0.0)
        }
        return null
    }

    fun fmt(x: Double): String {
        if (x >= 20) return x.roundToInt().toString()
        val whole = floor(x).toInt()
        val frac = x - whole
        val near = NICE.minBy { abs(it.first - frac) }
        if (abs(near.first - frac) < 0.04) {
            val w = if (near.first == 1.0) whole + 1 else whole
            return when {
                near.second.isEmpty() -> w.toString()
                w == 0 -> near.second
                else -> "$w${near.second}"
            }
        }
        return ((x * 10).roundToInt() / 10.0).toString().removeSuffix(".0")
    }

    private val UNITS =
        listOf(
            "cup" to "cups", "clove" to "cloves", "can" to "cans", "tablespoon" to "tablespoons", "teaspoon" to "teaspoons",
            "pound" to "pounds", "ounce" to "ounces", "slice" to "slices", "piece" to "pieces", "stick" to "sticks",
            "bunch" to "bunches", "pinch" to "pinches", "handful" to "handfuls", "sprig" to "sprigs", "package" to "packages",
            "jar" to "jars", "bag" to "bags", "head" to "heads", "lb" to "lbs", "egg" to "eggs", "sheet" to "sheets",
            "thumb" to "thumbs", "portion" to "portions", "fillet" to "fillets", "bowl" to "bowls",
        )

    private fun agree(
        rest: String,
        x: Double,
    ): String {
        val m = Regex("""^(\s*)([A-Za-z]+)(.*)$""", RegexOption.DOT_MATCHES_ALL).find(rest) ?: return rest
        val (sp, word, tail) = m.destructured
        val lower = word.lowercase()
        val pair = UNITS.firstOrNull { it.first == lower || it.second == lower } ?: return rest
        val want = if (x > 1.0) pair.second else pair.first
        val cased = if (word.first().isUpperCase()) want.replaceFirstChar { it.uppercase() } else want
        return sp + cased + tail
    }

    private val COMBO = Regex("""($Q)(?:(\s*(?:-|–|—|to|עד)\s*)($Q))?""")

    private fun ws(x: String) = x.takeLastWhile { it == ' ' }

    /** The amount × factor, in the same words: every number in it ("2 cups (480ml)" → "4 cups (960ml)").
     * Amounts without a number ("to taste") stay as they are. */
    fun scale(
        amount: String,
        factor: Double,
    ): String {
        if (factor == 1.0 || amount.isBlank()) return amount
        val sb = StringBuilder()
        var last = 0
        var first: Double? = null
        var firstEnd = 0
        for (m in COMBO.findAll(amount)) {
            val a = value(m.groupValues[1]) ?: continue
            val b = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { value(it) }
            sb.append(amount, last, m.range.first)
            sb.append(fmt(a * factor)).append(ws(m.groupValues[1]))
            if (b != null) sb.append(m.groupValues[2]).append(fmt(b * factor)).append(ws(m.groupValues[3]))
            else if (m.groupValues[3].isNotEmpty()) sb.append(m.groupValues[2]).append(m.groupValues[3])
            last = m.range.last + 1
            if (first == null) {
                first = (b ?: a) * factor
                firstEnd = sb.length
            }
        }
        if (first == null) {
            for ((w, v) in HEBREW) {
                if (Regex("""(^|\s)$w(\s|$)""").containsMatchIn(amount)) return amount.replaceFirst(w, fmt(v * factor))
            }
            return amount
        }
        sb.append(amount, last, amount.length)
        val out = sb.toString()
        return out.substring(0, firstEnd) + agree(out.substring(firstEnd), first)
    }

    // ---- the shopping list ----

    private val UNIT_KEY =
        mapOf(
            "cup" to "cup", "cups" to "cup", "c" to "cup",
            "tbsp" to "tbsp", "tbs" to "tbsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp", "כף" to "tbsp", "כפות" to "tbsp",
            "tsp" to "tsp", "teaspoon" to "tsp", "teaspoons" to "tsp", "כפית" to "tsp", "כפיות" to "tsp",
            "g" to "g", "gr" to "g", "gram" to "g", "grams" to "g", "גרם" to "g",
            "kg" to "kg", "ml" to "ml", "l" to "l", "liter" to "l", "liters" to "l", "litre" to "l",
            "lb" to "lb", "lbs" to "lb", "pound" to "lb", "pounds" to "lb", "oz" to "oz", "ounce" to "oz", "ounces" to "oz",
            "clove" to "clove", "cloves" to "clove", "can" to "can", "cans" to "can", "כוס" to "cup", "כוסות" to "cup",
        )
    private val UNIT_SHOW = mapOf("cup" to ("cup" to "cups"), "clove" to ("clove" to "cloves"), "can" to ("can" to "cans"))

    fun norm(s: String): String =
        Normalizer
            .normalize(s.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^\p{L}\p{N} ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun key(item: String): String =
        norm(item).split(' ').joinToString(" ") { w ->
            when {
                w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
                w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
                w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
                else -> w
            }
        }

    data class Line(
        val key: String,
        val item: String,
        val amount: String,
        val ids: List<String>,
        val done: Boolean,
        val codes: List<String>,
    )

    /** One line per thing to buy, its amounts added up where the units agree. */
    fun merge(items: List<ShopItem>): List<Line> =
        items
            .groupBy { key(it.item) }
            .map { (k, xs) ->
                Line(k, xs.first().item, total(xs.mapNotNull { it.amount?.trim()?.takeIf { a -> a.isNotEmpty() } }), xs.map { it.id }, xs.all { it.done }, xs.mapNotNull { it.code }.distinct())
            }

    fun total(amounts: List<String>): String {
        if (amounts.size <= 1) return amounts.firstOrNull().orEmpty()
        val sums = linkedMapOf<String, Double>()
        val other = mutableListOf<String>()
        for (a in amounts) {
            val m = Regex("""^\s*($Q)\s*(.*)$""").find(a)
            val v = m?.let { value(it.groupValues[1]) }
            val rest = m?.groupValues?.get(2)?.trim()?.lowercase().orEmpty()
            val unit = if (rest.isEmpty()) "" else UNIT_KEY[rest.split(' ').first()]
            if (v == null || unit == null || (unit.isNotEmpty() && rest.contains(' '))) {
                if (a !in other) other.add(a)
            } else {
                sums[unit] = (sums[unit] ?: 0.0) + v
            }
        }
        val parts =
            sums.map { (u, v) ->
                val shown = UNIT_SHOW[u]?.let { if (v > 1) it.second else it.first } ?: u
                if (u.isEmpty()) fmt(v) else "${fmt(v)} $shown"
            }
        return (parts + other).joinToString(" + ")
    }

    fun minutes(m: Int): String = if (m < 60) "$m min" else "${m / 60} hr" + (if (m % 60 != 0) " ${m % 60} min" else "")
}
