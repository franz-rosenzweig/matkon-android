package xyz.tiresia.matkon.data

import kotlin.math.abs
import kotlin.math.min

/** Search, as on the web: every word must be there (in the title, the creator, the ingredients, the steps and
 * tips too), and a typo is forgiven: one letter in a word of four to six letters, two from seven on. */
object Search {
    class Hay(val s: String, val words: List<String>)

    /** Everything a recipe can be found by, in one lowercase line. */
    fun hay(
        e: IndexEntry,
        names: Map<String, String>?,
    ): Hay {
        val s =
            Amounts.norm(
                listOf(
                    e.title, e.title_en, e.user, e.cuisine, e.kind, e.added_by?.let { names?.get(it) ?: it },
                    e.tags.orEmpty().joinToString(" "), e.items.orEmpty().joinToString(" "), e.items_en.orEmpty().joinToString(" "),
                    e.text, e.text_en, e.summary,
                ).joinToString(" ") { it.orEmpty() },
            )
        return Hay(s, s.split(' ').distinct())
    }

    @Volatile private var cache: Triple<List<IndexEntry>, Map<String, String>?, Map<String, Hay>>? = null

    /** The hays of a whole list, kept while the list is the same. */
    fun hays(
        list: List<IndexEntry>,
        names: Map<String, String>?,
    ): Map<String, Hay> {
        cache?.let { (l, n, h) -> if (l === list && n === names) return h }
        val h = list.associate { it.code to hay(it, names) }
        cache = Triple(list, names, h)
        return h
    }

    fun words(query: String) = Amounts.norm(query).split(' ').filter { it.isNotEmpty() }

    /** At most `max` letters different (Levenshtein), giving up early. */
    fun near(
        a: String,
        b: String,
        max: Int,
    ): Boolean {
        if (abs(a.length - b.length) > max) return false
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val row = IntArray(b.length + 1)
            row[0] = i
            var best = i
            for (j in 1..b.length) {
                row[j] = min(min(prev[j] + 1, row[j - 1] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                best = min(best, row[j])
            }
            if (best > max) return false
            prev = row
        }
        return prev[b.length] <= max
    }

    fun wordHit(
        w: String,
        h: Hay,
    ): Boolean {
        if (h.s.contains(w)) return true
        if (w.length < 4) return false
        val max = if (w.length >= 7) 2 else 1
        return h.words.any { x -> x.length >= 3 && (near(w, x, max) || (x.length > w.length && near(w, x.substring(0, w.length), max))) }
    }

    fun matches(
        h: Hay?,
        words: List<String>,
    ) = words.isEmpty() || (h != null && words.all { wordHit(it, h) })
}
