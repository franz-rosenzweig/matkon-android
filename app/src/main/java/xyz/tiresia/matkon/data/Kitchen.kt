package xyz.tiresia.matkon.data

import android.content.Context
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Everything the app shows, kept on the phone so it works without a connection: the recipe list, each
 * recipe (both languages), the account's memory (saved, ticks, notes, ratings, the week plan, the
 * shopping list…) and the changes made offline, which go to the server in order once it answers.
 */
object Kitchen {
    private lateinit var dir: File
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson get() = Net.gson

    val signedIn = MutableStateFlow(false)
    val recipes = MutableStateFlow<List<IndexEntry>>(emptyList())
    val waiting = MutableStateFlow(0)
    val me = MutableStateFlow<Me?>(null)
    val memory = MutableStateFlow(Memory.EMPTY)
    val suggest = MutableStateFlow<List<Suggestion>>(emptyList())
    val syncing = MutableStateFlow(false)
    val offline = MutableStateFlow(false)
    val stored = MutableStateFlow(0)

    /** Text another app shared (a reel or a recipe page's link), waiting to be added; "" opens the Add sheet. */
    val shared = MutableStateFlow<String?>(null)

    /** Photos of a recipe another app shared, waiting to be sent. */
    val sharedPhotos = MutableStateFlow<List<Uri>?>(null)

    /** Amounts ×, per recipe, while the app is open. */
    val factors = MutableStateFlow<Map<String, Double>>(emptyMap())

    private val pending = mutableListOf<Map<String, Any?>>()
    private val flushing = Mutex()
    private val refreshing = Mutex()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        dir = File(ctx.filesDir, "kitchen").apply { mkdirs() }
        signedIn.value = Net.signedIn
        read<Index>("index")?.let {
            recipes.value = it.recipes.orEmpty()
            waiting.value = it.waiting ?: 0
        }
        read<Me>("me")?.let {
            me.value = it
            memory.value = it.memory ?: Memory.EMPTY
        }
        read<SuggestResponse>("suggest")?.let { suggest.value = it.suggest.orEmpty() }
        try {
            val f = File(dir, "pending.json")
            if (f.exists()) {
                val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                pending.addAll(gson.fromJson<List<Map<String, Any?>>>(f.readText(), type).orEmpty())
            }
        } catch (_: Exception) {
        }
        countStored()
    }

    // ---- files ----

    private inline fun <reified T> read(name: String): T? =
        try {
            val f = File(dir, "$name.json")
            if (f.exists()) gson.fromJson(f.readText(), T::class.java) else null
        } catch (_: Exception) {
            null
        }

    private fun write(
        name: String,
        value: Any,
    ) {
        val f = File(dir, "$name.json")
        val tmp = File(dir, "$name.json.tmp")
        tmp.writeText(gson.toJson(value))
        tmp.renameTo(f)
    }

    private fun writePending() = synchronized(pending) { write("pending", pending.toList()) }

    private fun persistMemory() {
        val m = me.value ?: Me(null, null, null, null)
        val next = m.copy(memory = memory.value)
        me.value = next
        write("me", next)
    }

    private fun countStored() {
        stored.value = dir.listFiles { f -> f.name.startsWith("r_") && f.name.endsWith(".json") }
            ?.map { it.name.removePrefix("r_").substringBeforeLast('_') }?.distinct()?.size ?: 0
    }

    // ---- signing in and out ----

    suspend fun signIn(
        server: String,
        user: String,
        password: String,
    ) {
        Net.signIn(server, user, password)
        signedIn.value = true
        refresh()
    }

    fun signOut() {
        Net.forget()
        synchronized(pending) { pending.clear() }
        dir.listFiles()?.forEach { it.delete() }
        recipes.value = emptyList()
        me.value = null
        memory.value = Memory.EMPTY
        suggest.value = emptyList()
        stored.value = 0
        signedIn.value = false
    }

    private fun lapsed() {
        Net.forget()
        signedIn.value = false
    }

    // ---- reading ----

    fun refresh() {
        scope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        if (!Net.signedIn || refreshing.isLocked) return
        refreshing.withLock {
            syncing.value = true
            try {
                flush()
                val idx = gson.fromJson(Net.get("api/recipes"), Index::class.java)
                write("index", idx)
                recipes.value = idx.recipes.orEmpty()
                waiting.value = idx.waiting ?: 0
                val m = gson.fromJson(Net.get("api/me"), Me::class.java)
                if (synchronized(pending) { pending.isEmpty() }) {
                    memory.value = m.memory ?: Memory.EMPTY
                }
                me.value = m.copy(memory = memory.value)
                write("me", me.value!!)
                try {
                    val s = gson.fromJson(Net.get("api/suggest"), SuggestResponse::class.java)
                    write("suggest", s)
                    suggest.value = s.suggest.orEmpty()
                } catch (_: Refused) {
                }
                offline.value = false
                keepAll(idx.recipes.orEmpty())
            } catch (_: SignedOut) {
                lapsed()
            } catch (_: Offline) {
                offline.value = true
            } catch (_: Exception) {
            } finally {
                syncing.value = false
            }
        }
    }

    /** Every recipe (and its English copy) and its photo, so the kitchen works offline. A recipe is
     * fetched again only when its line in the list changed. */
    private suspend fun keepAll(list: List<IndexEntry>) {
        val sigs = read<HashMap<*, *>>("sigs")?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() }?.toMutableMap() ?: mutableMapOf()
        val loader = SingletonImageLoader.get(app)
        for (e in list) {
            if (e.is_recipe == false) continue
            e.photo?.let { loader.enqueue(ImageRequest.Builder(app).data(Net.url(it)).build()) }
            val sig = gson.toJson(e).hashCode().toString()
            val langs = listOfNotNull(e.lang ?: "en", if (e.title_en != null && e.lang != "en") "en" else null)
            if (sigs[e.code] == sig && langs.all { File(dir, "r_${e.code}_$it.json").exists() }) continue
            try {
                for (l in langs) fetchRecipe(e.code, l)
                sigs[e.code] = sig
            } catch (_: Offline) {
                break
            } catch (_: SignedOut) {
                lapsed()
                break
            } catch (_: Refused) {
            }
        }
        val codes = list.map { it.code }.toSet()
        dir.listFiles { f -> f.name.startsWith("r_") }?.forEach { f ->
            if (f.name.removePrefix("r_").substringBeforeLast('_') !in codes) f.delete()
        }
        write("sigs", sigs)
        countStored()
    }

    fun langFor(code: String): String {
        val e = recipes.value.firstOrNull { it.code == code }
        return if (memory.value.english() && e?.title_en != null) "en" else e?.lang ?: "en"
    }

    fun cachedRecipe(
        code: String,
        lang: String,
    ): Recipe? = read("r_${code}_$lang")

    suspend fun fetchRecipe(
        code: String,
        lang: String,
    ): Recipe {
        val text = Net.get("api/r/$code?lang=$lang")
        val r = gson.fromJson(text, Recipe::class.java)
        File(dir, "r_${code}_$lang.json").writeText(text)
        return r
    }

    /** The recipe from the phone, else from the server. */
    suspend fun recipe(
        code: String,
        lang: String = langFor(code),
    ): Recipe? = cachedRecipe(code, lang) ?: try {
        fetchRecipe(code, lang)
    } catch (_: Exception) {
        null
    }

    fun entry(code: String) = recipes.value.firstOrNull { it.code == code }

    // ---- changing ----

    private val collapse = setOf("ticks", "step", "note", "rate", "save", "setting", "pantry", "aisle")

    /** One change: made here at once, sent to the server in the background (later, if offline). */
    fun op(
        op: String,
        args: Map<String, Any?> = emptyMap(),
    ) {
        val body = args + ("op" to op)
        memory.value = Local.apply(memory.value, op, body)
        synchronized(pending) {
            if (op in collapse) {
                val same = { p: Map<String, Any?> -> p["op"] == op && p["code"] == body["code"] && p["key"] == body["key"] }
                val keepFirst = flushing.isLocked && pending.isNotEmpty() && same(pending[0])
                val first = if (keepFirst) pending.removeAt(0) else null
                pending.removeAll(same)
                first?.let { pending.add(0, it) }
            }
            pending.add(body)
        }
        writePending()
        persistMemory()
        scope.launch { flush() }
    }

    suspend fun flush() =
        flushing.withLock {
            while (true) {
                val next = synchronized(pending) { pending.firstOrNull() } ?: break
                try {
                    val m = gson.fromJson(Net.post("api/me", next), MemoryResponse::class.java).memory
                    val empty =
                        synchronized(pending) {
                            if (pending.firstOrNull() === next) pending.removeAt(0)
                            pending.isEmpty()
                        }
                    writePending()
                    if (m != null && empty) {
                        memory.value = m
                        persistMemory()
                    }
                    offline.value = false
                } catch (_: Offline) {
                    offline.value = true
                    break
                } catch (_: SignedOut) {
                    lapsed()
                    break
                } catch (_: Refused) {
                    synchronized(pending) { if (pending.firstOrNull() === next) pending.removeAt(0) }
                    writePending()
                } catch (_: Exception) {
                    break
                }
            }
        }

    fun setFactor(
        code: String,
        f: Double,
    ) {
        factors.value = if (f == 1.0) factors.value - code else factors.value + (code to f)
    }

    fun factor(code: String) = factors.value[code] ?: 1.0

    suspend fun addReel(text: String): AddResult {
        val r = gson.fromJson(Net.post("api/settings", mapOf("op" to "add-reel", "url" to text)), AddResult::class.java)
        refresh()
        return r
    }

    private val INSTA = Regex("""(^|\.)(instagram\.com|instagr\.am)/""", RegexOption.IGNORE_CASE)

    /** Any link, as the web's Add does it: an Instagram reel goes to the reel queue, any other page is read
     * for its recipe. Text with a link in it (what a share sends) works too. */
    suspend fun addLink(text: String): AddResult {
        val url = Regex("""https?://\S+""").find(text)?.value ?: text.trim()
        if (INSTA.containsMatchIn(url.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")) ||
            Regex("instagram\\.com|instagr\\.am", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            return addReel(text)
        }
        val full = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(url)) url else "https://$url"
        val r = gson.fromJson(Net.post("api/add-link", mapOf("url" to full)), AddResult::class.java)
        refresh()
        return r
    }

    /** Photos of a recipe (up to four, each down to 2000 px), read into a recipe on the server. */
    suspend fun addPhotos(
        ctx: Context,
        uris: List<Uri>,
    ): AddResult {
        val images =
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                uris.take(4).map { Photos.jpegDataUrl(ctx, it) }
            }
        val r = gson.fromJson(Net.post("api/add-photo", mapOf("images" to images), long = true), AddResult::class.java)
        refresh()
        return r
    }

    /** A fix of the recipe (or {revert: true}), kept on the server. Its answer is the recipe as it is now, kept
     * on the phone with its other language and the list. */
    suspend fun saveEdit(
        code: String,
        lang: String,
        body: Map<String, Any?>,
    ): Recipe {
        val text =
            try {
                Net.post("api/r/$code/edit", body + ("lang" to lang))
            } catch (e: SignedOut) {
                lapsed()
                throw e
            }
        val r = gson.fromJson(text, Recipe::class.java)
        File(dir, "r_${code}_$lang.json").writeText(text)
        for (l in r.langs.orEmpty()) {
            if (l != lang) {
                try {
                    fetchRecipe(code, l)
                } catch (_: Exception) {
                }
            }
        }
        refresh()
        return r
    }

    /** A question about a recipe, answered by Claude on the server. */
    suspend fun ask(
        code: String,
        lang: String?,
        question: String,
        history: List<Pair<String, String>>,
    ): String {
        val body =
            mapOf(
                "code" to code,
                "lang" to lang,
                "question" to question,
                "history" to history.takeLast(6).map { mapOf("q" to it.first, "a" to it.second) },
            )
        val text =
            try {
                Net.post("api/ask", body, long = true)
            } catch (e: SignedOut) {
                lapsed()
                throw e
            }
        return gson.fromJson(text, AskResponse::class.java).answer.orEmpty()
    }

    /** A link anyone can open (on) or none (off). The phone's copies of the recipe are fetched again. */
    suspend fun share(
        code: String,
        on: Boolean,
    ): String? {
        val r = gson.fromJson(Net.post("api/settings", mapOf("op" to if (on) "share" else "unshare", "code" to code)), ShareResponse::class.java)
        scope.launch {
            for (l in listOf("en", "he")) {
                if (File(dir, "r_${code}_$l.json").exists()) {
                    try {
                        fetchRecipe(code, l)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return if (on) r.url else null
    }

    /** Every recipe as one .jsonld file, written where the person picked. */
    suspend fun export(
        ctx: Context,
        uri: Uri,
    ) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("Can't write there")
            out.use { Net.download("api/export", it) }
        }
    }

    /** A recipe for tonight: from Up next, nothing made in the last five days (else from the whole kitchen). */
    fun surprise(): String? {
        val now = System.currentTimeMillis() / 1000.0
        val last = memory.value.cooked_last.orEmpty()
        val recent = { c: String -> (last[c] ?: 0.0) > now - 5 * 86400 }
        val codes = recipes.value.filter { it.is_recipe != false }.map { it.code }.toSet()
        var pool = suggest.value.map { it.code }.filter { it in codes && !recent(it) }
        if (pool.isEmpty()) pool = codes.filter { !recent(it) }
        return pool.randomOrNull()
    }

    /** A recipe's ingredients onto the shopping list, at the amounts shown. */
    fun addToList(
        r: Recipe,
        factor: Double,
    ) {
        val items =
            r.groups.flatMap { it.items.orEmpty() }.filter { !it.item.isNullOrBlank() }.map {
                mapOf("id" to Local.newId(), "item" to it.item!!.trim(), "amount" to Amounts.scale(it.amount.orEmpty(), factor))
            }
        if (items.isNotEmpty()) op("shop-add", mapOf("code" to r.code, "items" to items))
    }
}
