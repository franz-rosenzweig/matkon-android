package xyz.tiresia.matkon.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.tiresia.matkon.data.Aisles
import xyz.tiresia.matkon.data.Amounts
import xyz.tiresia.matkon.data.IndexEntry
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Memory
import xyz.tiresia.matkon.data.Net
import xyz.tiresia.matkon.data.Recipe
import xyz.tiresia.matkon.data.Units
import java.time.LocalDate

/** Printing a recipe, the week or the shopping list: a clean page, handed to Android's printing (which can
 * also save it as a PDF). */
object Printing {
    private val scope = MainScope()
    private var keep: WebView? = null

    private fun esc(s: String?) =
        (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private const val STYLE = """
        @page { margin: 14mm; }
        body { font-family: sans-serif; color: #000; margin: 0; font-size: 11.5pt; line-height: 1.45; }
        h1 { font-size: 20pt; line-height: 1.2; margin: 0 0 4pt; }
        h2 { font-size: 14pt; margin: 14pt 0 4pt; }
        h3 { font-size: 11.5pt; margin: 10pt 0 2pt; color: #444; }
        .sub { color: #444; font-size: 10pt; margin: 2pt 0; }
        .facts span { display: inline-block; background: #eee; border-radius: 6px; padding: 2pt 7pt; margin: 4pt 4pt 0 0; font-size: 10pt; }
        img.photo { float: right; max-width: 7cm; max-height: 6cm; border-radius: 8px; margin: 0 0 8pt 14pt; }
        [dir=rtl] img.photo { float: left; margin: 0 14pt 8pt 0; }
        li[dir=rtl] { text-align: right; }
        ul.ings { list-style: none; padding: 0; margin: 0; }
        ul.ings li { padding: 2pt 0; break-inside: avoid; }
        .box { display: inline-block; width: 9pt; height: 9pt; border: 1px solid #000; border-radius: 2px; margin: 0 6pt; vertical-align: -1pt; }
        .done { color: #888; }
        .done .box { background: #888; border-color: #888; }
        .from { color: #666; font-size: 9.5pt; margin: 0 0 0 23pt; }
        [dir=rtl] .from { margin: 0 23pt 0 0; }
        ol.steps { padding-inline-start: 18pt; }
        ol.steps li { margin: 0 0 6pt; break-inside: avoid; }
        ul.list li { margin: 0 0 3pt; }
        .day { break-inside: avoid; border-bottom: 1px solid #ccc; padding: 5pt 0; }
        .day b { display: inline-block; min-width: 7em; }
        .dish { display: flex; align-items: center; gap: 8pt; margin: 4pt 0 0; }
        .dish img { width: 1.4cm; height: 1.4cm; object-fit: cover; border-radius: 5px; }
        .nut span { margin-inline-end: 12pt; }
    """

    private fun page(
        title: String,
        body: String,
        lang: String? = null,
    ) = "<!doctype html><html lang=\"${if (lang == "he") "he" else "en"}\" dir=\"${if (lang == "he") "rtl" else "ltr"}\">" +
        "<head><meta charset=\"utf-8\"><title>${esc(title)}</title><style>$STYLE</style></head><body>$body</body></html>"

    /** A photo as a data: address, from the phone's photo cache (so it prints offline) or the server. */
    private suspend fun photo(
        ctx: Context,
        path: String?,
    ): String? {
        if (path == null) return null
        val bytes =
            withContext(Dispatchers.IO) {
                try {
                    SingletonImageLoader.get(ctx).diskCache?.openSnapshot(Net.url(path))?.use { snap -> snap.data.toFile().readBytes() }
                } catch (_: Exception) {
                    null
                }
            } ?: Net.bytes(path) ?: return null
        val type =
            when {
                bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
                bytes.size > 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                else -> "image/jpeg"
            }
        return "data:$type;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun send(
        ctx: Context,
        name: String,
        html: String,
    ) {
        val wv = WebView(ctx)
        var sent = false
        wv.webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String?,
                ) {
                    if (sent) return
                    sent = true
                    val pm = ctx.getSystemService(PrintManager::class.java) ?: return
                    pm.print("Matkon · $name", view.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
                    keep = null
                }
            }
        keep = wv // until the page is handed to the printing
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun recipe(
        ctx: Context,
        r: Recipe,
        factor: Double,
        units: String,
    ) {
        scope.launch {
            val pic = photo(ctx, r.photo)
            val l = labelsFor(r.lang)
            val me = Kitchen.me.value
            val sb = StringBuilder()
            if (pic != null) sb.append("<img class=\"photo\" src=\"$pic\" alt=\"\">")
            sb.append("<h1>${esc(r.title)}</h1>")
            if (!r.summary.isNullOrBlank()) sb.append("<p class=\"sub\">${esc(r.summary)}</p>")
            val by =
                when (r.source) {
                    "photo" -> null
                    "web" -> r.user
                    else -> r.user?.takeIf { it.isNotBlank() }?.let { "@$it" }
                }
            if (!by.isNullOrBlank()) sb.append("<p class=\"sub\" dir=\"ltr\">${esc(by)}</p>")
            val who = r.added_by?.takeIf { it != me?.account?.id }?.let { me?.names?.get(it) ?: it }
            val dates =
                listOfNotNull(
                    r.posted?.let { l.posted(day(it, r.lang)) },
                    r.found?.let { l.added(day(it, r.lang), who) },
                    r.edited?.at?.let { l.edited(day(it, r.lang)) },
                )
            if (dates.isNotEmpty()) sb.append("<p class=\"sub\">${esc(dates.joinToString(" · "))}</p>")
            val facts =
                listOfNotNull(
                    r.servings?.takeIf { it.isNotBlank() }?.let { "${servesOrMakes(it, l)} $it" },
                    r.time?.prep?.takeIf { it.isNotBlank() }?.let { "${l.prep} $it" },
                    r.time?.cook?.takeIf { it.isNotBlank() }?.let { "${l.cook} $it" },
                    r.time?.total?.takeIf { it.isNotBlank() }?.let { "${l.total} $it" },
                    r.oven?.takeIf { it.isNotBlank() }?.let { "${l.oven} ${Units.text(it, units, r.lang)}" },
                )
            if (facts.isNotEmpty()) sb.append("<div class=\"facts\">${facts.joinToString("") { "<span>${esc(it)}</span>" }}</div>")
            if (r.groups.isNotEmpty()) {
                sb.append("<h2>${esc(l.ing)}</h2>")
                r.groups.forEach { g ->
                    if (!g.name.isNullOrBlank() && (r.groups.size > 1 || g.name != r.title)) sb.append("<h3>${esc(g.name)}</h3>")
                    sb.append("<ul class=\"ings\">")
                    g.items.orEmpty().forEach { i ->
                        val amount = Units.amount(Amounts.scale(i.amount.orEmpty(), factor), units, r.lang)
                        sb.append("<li><span class=\"box\"></span>")
                        if (amount.isNotBlank()) sb.append("<b>${esc(amount.trim())}</b> ")
                        sb.append(esc(i.item))
                        if (!i.note.isNullOrBlank()) sb.append("<span class=\"sub\">, ${esc(i.note)}</span>")
                        sb.append("</li>")
                    }
                    sb.append("</ul>")
                }
            }
            if (r.stepList.isNotEmpty()) {
                sb.append("<h2>${esc(l.steps)}</h2><ol class=\"steps\">")
                r.stepList.forEach { sb.append("<li>${esc(Units.text(it.text.orEmpty(), units, r.lang))}</li>") }
                sb.append("</ol>")
            }
            val tips = r.tips.orEmpty().filter { it.isNotBlank() }
            if (tips.isNotEmpty()) sb.append("<h2>${esc(l.tips)}</h2><ul class=\"list\">${tips.joinToString("") { "<li>${esc(Units.text(it, units, r.lang))}</li>" }}</ul>")
            val names = mapOf("calories" to l.calories, "protein" to l.protein, "carbs" to l.carbs, "fat" to l.fat)
            val nut = r.nutrition.orEmpty().filter { (k, v) -> k in names && v != null && v.toString().isNotBlank() }
            if (nut.isNotEmpty()) {
                sb.append("<h2>${esc(l.nut)}</h2><p class=\"nut\">")
                nut.forEach { (k, v) -> sb.append("<span>${esc(names[k])} ${esc(v.toString().removeSuffix(".0"))}</span>") }
                sb.append("</p>")
            }
            val miss = r.missing.orEmpty().filter { it.isNotBlank() }
            if (miss.isNotEmpty()) sb.append("<h2>${esc(l.miss)}</h2><ul class=\"list\">${miss.joinToString("") { "<li>${esc(it)}</li>" }}</ul>")
            send(ctx, r.title ?: "Recipe", page(r.title ?: "Recipe", sb.toString(), r.lang))
        }
    }

    fun week(
        ctx: Context,
        recipes: List<IndexEntry>,
        m: Memory,
    ) {
        scope.launch {
            val byCode = recipes.associateBy { it.code }
            val today = LocalDate.now()
            val start = weekStart(today)
            val plan = m.plan.orEmpty().groupBy { it.day }
            val pics = HashMap<String, String?>()
            val sb = StringBuilder("<h1>Week</h1>")
            val h = m.household.orEmpty()
            if (h.isNotEmpty()) sb.append("<p class=\"sub\">With ${esc(h.joinToString(", ") { Kitchen.me.value?.names?.get(it) ?: it })}</p>")
            for (w in 0..1) {
                sb.append("<h2>${if (w == 0) "This week" else "Next week"}</h2>")
                for (i in 0L until 7L) {
                    val d = start.plusDays(w * 7 + i)
                    sb.append("<div class=\"day\"><b>${esc(dayName(d))}</b> <span class=\"sub\">${esc(shortDate(d))}</span>")
                    plan[d.toString()].orEmpty().mapNotNull { byCode[it.code] }.forEach { e ->
                        val pic = if (pics.containsKey(e.code)) pics[e.code] else photo(ctx, e.photo).also { pics[e.code] = it }
                        sb.append("<div class=\"dish\">")
                        if (pic != null) sb.append("<img src=\"$pic\" alt=\"\">")
                        sb.append("<span dir=\"auto\">${esc(shownTitle(e, m))}</span></div>")
                    }
                    sb.append("</div>")
                }
            }
            send(ctx, "Week", page("Week", sb.toString()))
        }
    }

    fun list(
        ctx: Context,
        lines: List<Amounts.Line>,
        recipes: List<IndexEntry>,
        m: Memory,
    ) {
        val byCode = recipes.associateBy { it.code }
        val units = m.units()
        val aisles = m.aisles.orEmpty()
        val line = { x: Amounts.Line ->
            val amount = if (x.amount.isNotBlank()) shownAmount(x, units) else ""
            val from = x.codes.mapNotNull { byCode[it] }.joinToString(" · ") { shownTitle(it, m) }
            val rtl = Regex("[\u0590-\u05FF]").containsMatchIn(x.amount + x.item)
            "<li class=\"${if (x.done) "done" else ""}\" dir=\"${if (rtl) "rtl" else "ltr"}\"><span class=\"box\"></span>" +
                (if (amount.isNotBlank()) "<b>${esc(amount)}</b> " else "") + esc(x.item) +
                (if (from.isNotEmpty()) "<div class=\"from\">${esc(from)}</div>" else "") + "</li>"
        }
        val open = lines.filter { !it.done }
        val done = lines.filter { it.done }
        val groups = open.groupBy { aisles[it.key] ?: Aisles.of(it.item) }
        val sb = StringBuilder("<h1>List</h1>")
        val h = m.household.orEmpty()
        if (h.isNotEmpty()) sb.append("<p class=\"sub\">With ${esc(h.joinToString(", ") { Kitchen.me.value?.names?.get(it) ?: it })}</p>")
        Aisles.ORDER.filter { groups.containsKey(it) }.forEach { a ->
            sb.append("<h3>${esc(Aisles.NAMES[a])}</h3><ul class=\"ings\">${groups.getValue(a).joinToString("") { line(it) }}</ul>")
        }
        if (done.isNotEmpty()) sb.append("<h2>Done</h2><ul class=\"ings\">${done.joinToString("") { line(it) }}</ul>")
        send(ctx, "List", page("List", sb.toString()))
    }
}
