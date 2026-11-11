package xyz.tiresia.matkon.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.tiresia.matkon.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

class SignedOut : Exception("signed out")

class Offline(cause: Throwable) : Exception("offline", cause)

class Refused(message: String, val status: Int) : Exception(message)

/** The way to the Matkon server: its address, the sign-in cookie, and plain JSON calls. */
object Net {
    private lateinit var prefs: SharedPreferences
    private val JSON = "application/json; charset=utf-8".toMediaType()
    val gson = Gson()

    @Volatile var server: String = ""
        private set

    @Volatile var cookie: String? = null
        private set

    var username: String = ""
        private set

    val signedIn get() = server.isNotEmpty() && cookie != null

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder().header("User-Agent", "Matkon-Android/" + BuildConfig.VERSION_NAME)
                val c = cookie
                if (c != null && isServer(req.url)) b.header("Cookie", "matkon=$c")
                if (req.method != "GET" && req.method != "HEAD") b.header("X-Matkon", "1")
                chain.proceed(b.build())
            }.connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** For the calls that take long: a question to Claude (up to ~90 s), photos going up. */
    private val slow: OkHttpClient by lazy {
        client
            .newBuilder()
            .readTimeout(150, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("matkon", Context.MODE_PRIVATE)
        server = prefs.getString("server", "") ?: ""
        cookie = prefs.getString("cookie", null)
        username = prefs.getString("username", "") ?: ""
    }

    private fun isServer(u: HttpUrl): Boolean {
        val s = server.toHttpUrlOrNull() ?: return false
        return u.host == s.host && u.port == s.port
    }

    fun url(path: String) = server.trimEnd('/') + "/" + path.trimStart('/')

    /** "matkon.example.com" → https, a bare IP or host:port → http. */
    fun normalize(input: String): String {
        var s = input.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            val hostPort = s.substringBefore('/')
            val host = hostPort.substringBefore(':')
            val plain = Regex("""^\d+\.\d+\.\d+\.\d+$""").matches(host) || host == "localhost" || hostPort.contains(':')
            s = (if (plain) "http://" else "https://") + s
        }
        return s
    }

    suspend fun get(path: String): String = call(Request.Builder().url(url(path)).build())

    suspend fun post(
        path: String,
        body: Any,
        long: Boolean = false,
    ): String = call(Request.Builder().url(url(path)).post(gson.toJson(body).toRequestBody(JSON)).build(), long)

    /** A file from the server (a photo), or null. */
    suspend fun bytes(path: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url(path)).build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }
            } catch (_: IOException) {
                null
            }
        }

    /** A GET written straight into `out` (the export), without holding it all in memory. */
    suspend fun download(
        path: String,
        out: java.io.OutputStream,
    ) = withContext(Dispatchers.IO) {
        val res =
            try {
                slow.newCall(Request.Builder().url(url(path)).build()).execute()
            } catch (e: IOException) {
                throw Offline(e)
            }
        res.use {
            if (it.code == 401) throw SignedOut()
            if (!it.isSuccessful) throw Refused(errorOf(it.body?.string().orEmpty()) ?: "Matkon answered ${it.code}", it.code)
            try {
                it.body?.byteStream()?.use { input -> input.copyTo(out) }
            } catch (e: IOException) {
                throw Offline(e)
            }
        }
    }

    private suspend fun call(
        req: Request,
        long: Boolean = false,
    ): String =
        withContext(Dispatchers.IO) {
            val res =
                try {
                    (if (long) slow else client).newCall(req).execute()
                } catch (e: IOException) {
                    throw Offline(e)
                }
            res.use {
                val text =
                    try {
                        it.body?.string() ?: ""
                    } catch (e: IOException) {
                        throw Offline(e)
                    }
                if (it.code == 401) throw SignedOut()
                if (!it.isSuccessful) throw Refused(errorOf(text) ?: "Matkon answered ${it.code}", it.code)
                text
            }
        }

    private fun errorOf(text: String): String? =
        try {
            gson.fromJson(text, JsonObject::class.java)?.get("error")?.asString
        } catch (_: Exception) {
            null
        }

    suspend fun signIn(
        address: String,
        user: String,
        password: String,
    ) = withContext(Dispatchers.IO) {
        val base = normalize(address)
        if (base.toHttpUrlOrNull() == null) throw Refused("That isn't a server address.", 0)
        val req =
            Request
                .Builder()
                .url("$base/api/signin")
                .post(gson.toJson(mapOf("username" to user, "password" to password)).toRequestBody(JSON))
                .build()
        val res =
            try {
                client.newCall(req).execute()
            } catch (e: IOException) {
                throw Offline(e)
            }
        res.use {
            val text = it.body?.string() ?: ""
            if (!it.isSuccessful) throw Refused(errorOf(text) ?: "Matkon answered ${it.code}", it.code)
            val c =
                it.headers("Set-Cookie").firstNotNullOfOrNull { h -> Regex("^matkon=([^;]+)").find(h)?.groupValues?.get(1) }
                    ?: throw Refused("That isn't a Matkon server.", 0)
            server = base
            cookie = c
            username = user
            prefs.edit().putString("server", base).putString("cookie", c).putString("username", user).apply()
        }
    }

    fun forget() {
        cookie = null
        prefs.edit().remove("cookie").apply()
    }
}
