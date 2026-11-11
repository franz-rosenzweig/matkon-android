package xyz.tiresia.matkon.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.Amounts
import xyz.tiresia.matkon.data.Ingredient
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Local
import xyz.tiresia.matkon.data.Memory
import xyz.tiresia.matkon.data.Offline
import xyz.tiresia.matkon.data.Recipe
import xyz.tiresia.matkon.data.Refused
import xyz.tiresia.matkon.data.SignedOut
import xyz.tiresia.matkon.data.Step
import xyz.tiresia.matkon.data.Units
import xyz.tiresia.matkon.timer.Timers
import java.time.LocalDate

val FACTORS = listOf(0.5 to "½×", 1.0 to "1×", 1.5 to "1½×", 2.0 to "2×", 3.0 to "3×")

/** The language picked for a recipe on this visit (else the account's own), as on the web. */
val langChoice = mutableMapOf<String, String>()

/** Loads a recipe: the phone's copy first, then the server's when it answers. */
@Composable
fun rememberRecipe(
    code: String,
    lang: String,
): Recipe? {
    var r by remember(code, lang) { mutableStateOf(Kitchen.cachedRecipe(code, lang)) }
    LaunchedEffect(code, lang) {
        try {
            r = Kitchen.fetchRecipe(code, lang)
        } catch (_: Exception) {
            if (r == null) r = Kitchen.recipe(code, lang)
        }
    }
    return r
}

/** Starts a step timer, asking once for notifications so the alarm can show. */
@Composable
fun rememberTimerStarter(): (Recipe, Int, Int) -> Unit {
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) Timers.repost(ctx) }
    return { r, step, minutes ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Timers.start(ctx, r.code, r.title ?: "", step, minutes)
    }
}

fun openLink(
    ctx: Context,
    url: String,
) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

fun tickKey(
    g: Int,
    i: Int,
) = "$g.$i"

private enum class RecipeSheet { Collections, Share, Made }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(code: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val factors by Kitchen.factors.collectAsStateWithLifecycle()
    var lang by remember(code) { mutableStateOf(langChoice[code] ?: Kitchen.langFor(code)) }
    val r = rememberRecipe(code, lang)
    val factor = factors[code] ?: 1.0
    var playing by remember(code) { mutableStateOf(false) }
    var planning by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<RecipeSheet?>(null) }
    var shareUrl by remember(code, r?.share) { mutableStateOf(r?.share) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(code) { Kitchen.op("view", mapOf("code" to code)) }

    Scaffold(
        topBar = {
            Bar("", onBack = { nav.back() }) {
                if (r != null && r.langs.orEmpty().size > 1) {
                    TextButton(onClick = {
                        lang = if (lang == "he") "en" else "he"
                        langChoice[code] = lang
                    }) {
                        Text(if (lang == "he") "English" else "עברית")
                    }
                }
                val saved = m.saved.orEmpty().containsKey(code)
                IconButton(onClick = { Kitchen.op("save", mapOf("code" to code, "on" to !saved)) }) {
                    Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (saved) "Saved" else "Save", tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                if (r != null) {
                    val l = labelsFor(r.lang)
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, l.more)
                        CompositionLocalProvider(LocalLayoutDirection provides if (r.he) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val item = @Composable { label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, go: () -> Unit ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { Icon(icon, null) },
                                    onClick = {
                                        menu = false
                                        go()
                                    },
                                )
                            }
                            item(l.collections, Icons.Default.Folder) { sheet = RecipeSheet.Collections }
                            if (r.is_recipe != false) item(l.share, Icons.Default.Link) { sheet = RecipeSheet.Share }
                            item(l.print, Icons.Default.Print) { Printing.recipe(ctx, r, factor, m.units()) }
                            if (r.can_edit == true) item(l.edit, Icons.Default.Edit) { nav.go(Screen.Edit(code, r.lang ?: lang)) }
                        }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        if (r == null) {
            Box(Modifier.padding(pad).fillMaxSize())
            return@Scaffold
        }
        val l = labelsFor(r.lang)
        val startTimer = rememberTimerStarter()
        CompositionLocalProvider(LocalLayoutDirection provides if (r.he) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
                val two = maxWidth >= 720.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
                    val media: @Composable (Modifier) -> Unit = { mod ->
                        Media(r, playing, onPlay = { playing = true }, modifier = mod)
                    }
                    val head: @Composable () -> Unit = {
                        Head(
                            r, m, l, factor,
                            onCreator = { nav.fromCreator(it) },
                            onCook = { nav.go(Screen.Cook(code)) },
                            onPlan = { planning = true },
                            onList = {
                                Kitchen.addToList(r, factor)
                                scope.launch {
                                    if (snack.showSnackbar(l.onList, actionLabel = "Open", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                                        nav.go(Screen.Shop)
                                    }
                                }
                            },
                        )
                    }
                    if (two) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            media(Modifier.weight(0.42f).clip(RoundedCornerShape(16.dp)))
                            Column(Modifier.weight(0.58f)) { head() }
                        }
                    } else {
                        media(Modifier.fillMaxWidth())
                        Column(Modifier.padding(horizontal = 16.dp)) { head() }
                    }
                    val ingredients: @Composable (Modifier) -> Unit = { mod ->
                        Column(mod) { Ingredients(r, m, l, factor) }
                    }
                    val steps: @Composable (Modifier) -> Unit = { mod ->
                        Column(mod) { Steps(r, l, m.units(), onTimer = { step, min -> startTimer(r, step, min) }) }
                    }
                    if (two) {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            ingredients(Modifier.weight(0.42f))
                            steps(Modifier.weight(0.58f))
                        }
                    } else {
                        ingredients(Modifier.padding(horizontal = 16.dp))
                        steps(Modifier.padding(horizontal = 16.dp))
                    }
                    Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp)) {
                        Extras(r, l, m.units())
                        Mine(
                            r, m, l,
                            onMade = { sheet = RecipeSheet.Made },
                            onAgain = { f ->
                                Kitchen.setFactor(code, f)
                                nav.go(Screen.Cook(code, start = 1))
                            },
                        )
                        AskBox(r, l)
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    if (planning && r != null) {
        PlanSheet(onDismiss = { planning = false }) { d ->
            planning = false
            Kitchen.op("plan-add", mapOf("id" to Local.newId(), "code" to code, "day" to d.toString()))
            scope.launch {
                if (snack.showSnackbar(dayName(d) + " · " + shortDate(d), actionLabel = "Open", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                    nav.go(Screen.Week)
                }
            }
        }
    }
    if (r != null) {
        when (sheet) {
            RecipeSheet.Collections -> CollectionsSheet(code, labelsFor(r.lang)) { sheet = null }
            RecipeSheet.Share -> ShareLinkSheet(r, shareUrl, onUrl = { shareUrl = it }) { sheet = null }
            RecipeSheet.Made -> MadeSheet(r, factor) { sheet = null }
            null -> {}
        }
    }
}

@Composable
private fun Media(
    r: Recipe,
    playing: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier,
) {
    val h = LocalConfiguration.current.screenHeightDp
    if (playing && r.video != null) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            ReelVideo(r.video, Modifier.heightIn(max = (h * 0.72f).dp).aspectRatio(9f / 16f))
        }
        return
    }
    Box(modifier) {
        Photo(r.photo, Modifier.fillMaxWidth().aspectRatio(if (wide()) 1f else 4f / 3f))
        if (r.video != null) {
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(labelsFor(r.lang).watch, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun LinkText(
    text: String,
    onClick: () -> Unit,
    strong: Boolean = false,
) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (strong) FontWeight.Medium else null,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

/** Where the recipe came from: the creator and the reel, the site and its page, or nothing for photos. */
@Composable
private fun SourceLine(
    r: Recipe,
    l: Labels,
    onCreator: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val links = mutableListOf<@Composable () -> Unit>()
    when (r.source) {
        "photo" -> {}
        "web" -> {
            if (!r.user.isNullOrBlank()) links.add { LinkText(r.user, { onCreator(r.user) }, strong = true) }
            if (!r.reel.isNullOrBlank()) links.add { LinkText(l.page, { openLink(ctx, r.reel) }) }
        }
        else -> {
            if (!r.user.isNullOrBlank()) links.add { LinkText("@${r.user}", { onCreator(r.user) }, strong = true) }
            if (!r.reel.isNullOrBlank()) links.add { LinkText(l.insta, { openLink(ctx, r.reel) }) }
            if (!r.link.isNullOrBlank()) links.add { LinkText(l.page, { openLink(ctx, r.link) }) }
        }
    }
    if (links.isEmpty()) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            links.forEachIndexed { i, link ->
                if (i > 0) Text("  ·  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                link()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Head(
    r: Recipe,
    m: Memory,
    l: Labels,
    factor: Double,
    onCreator: (String) -> Unit,
    onCook: () -> Unit,
    onPlan: () -> Unit,
    onList: () -> Unit,
) {
    val me by Kitchen.me.collectAsStateWithLifecycle()
    Spacer(Modifier.height(14.dp))
    Text(r.title ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (!r.summary.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(r.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    SourceLine(r, l, onCreator)
    val who = r.added_by?.takeIf { it != me?.account?.id }?.let { me?.names?.get(it) ?: it }
    val dates =
        listOfNotNull(
            r.posted?.let { l.posted(day(it, r.lang)) },
            r.found?.let { l.added(day(it, r.lang), who) },
            r.edited?.at?.let { l.edited(day(it, r.lang)) },
        )
    if (dates.isNotEmpty()) {
        Text(dates.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val facts =
        listOfNotNull(
            r.servings?.takeIf { it.isNotBlank() }?.let { "${servesOrMakes(it, l)} $it" },
            r.time?.prep?.takeIf { it.isNotBlank() }?.let { "${l.prep} $it" },
            r.time?.cook?.takeIf { it.isNotBlank() }?.let { "${l.cook} $it" },
            r.time?.total?.takeIf { it.isNotBlank() }?.let { "${l.total} $it" },
            r.oven?.takeIf { it.isNotBlank() }?.let { "${l.oven} ${Units.text(it, m.units(), r.lang)}" },
        )
    if (facts.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            facts.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    val at = m.steps.orEmpty()[r.code]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r.stepList.isNotEmpty()) {
            Button(onClick = onCook) {
                Icon(Icons.Default.Restaurant, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (at != null && at > 1) l.cont(at) else l.start)
            }
        }
        OutlinedButton(onClick = onPlan) {
            Icon(Icons.Default.Event, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(l.plan)
        }
        if (r.groups.isNotEmpty()) {
            OutlinedButton(onClick = onList) {
                Icon(Icons.Default.AddShoppingCart, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(l.list)
            }
        }
    }
    if (r.groups.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.amounts, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            FactorChips(factor) { Kitchen.setFactor(r.code, it) }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** ½× to 3×, always left to right. */
@Composable
fun FactorChips(
    factor: Double,
    onPick: (Double) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FACTORS.forEach { (f, name) ->
                FilterChip(selected = factor == f, onClick = { onPick(f) }, label = { Text(name) })
            }
        }
    }
}

@Composable
fun IngredientRow(
    i: Ingredient,
    done: Boolean,
    factor: Double,
    l: Labels,
    units: String,
    lang: String?,
    big: Boolean = false,
    onTick: () -> Unit,
) {
    val amount = Units.amount(Amounts.scale(i.amount.orEmpty(), factor), units, lang)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onTick).padding(vertical = if (big) 10.dp else 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            null,
            Modifier.size(if (big) 24.dp else 20.dp).padding(top = 1.dp),
            tint = if (done) MaterialTheme.colorScheme.done else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        val style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
        val alpha = if (done) 0.45f else 1f
        Column(Modifier.weight(1f)) {
            Text(amountAnd(amount, i.item.orEmpty()), style = style, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            val note = listOfNotNull(i.note?.takeIf { it.isNotBlank() }, if (amount.isBlank()) l.noAmt else null)
            if (note.isNotEmpty()) {
                Text(note.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
    }
}

/** "2 cups flour", the amount in bold, as one line of text. */
fun amountAnd(
    amount: String,
    item: String,
) = buildAnnotatedString {
    if (amount.isNotBlank()) {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(amount.trim()) }
        append(" ")
    }
    append(item)
}

fun toggleTick(
    code: String,
    m: Memory,
    key: String,
) {
    val now = m.ticks.orEmpty()[code].orEmpty().toMutableSet()
    if (!now.add(key)) now.remove(key)
    Kitchen.op("ticks", mapOf("code" to code, "keys" to now.toList()))
}

@Composable
fun Ingredients(
    r: Recipe,
    m: Memory,
    l: Labels,
    factor: Double,
    big: Boolean = false,
) {
    if (r.groups.isEmpty()) return
    val ticked = m.ticks.orEmpty()[r.code].orEmpty().toSet()
    val units = m.units()
    Text(l.ing, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    r.groups.forEachIndexed { gi, g ->
        if (!g.name.isNullOrBlank() && (r.groups.size > 1 || g.name != r.title)) {
            Text(g.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        }
        g.items.orEmpty().forEachIndexed { ii, i ->
            val k = tickKey(gi, ii)
            IngredientRow(i, k in ticked, factor, l, units, r.lang, big) { toggleTick(r.code, m, k) }
        }
    }
}

@Composable
fun TimerChips(
    r: Recipe,
    step: Int,
    s: Step,
    l: Labels,
    onTimer: (Int, Int) -> Unit,
) {
    val timers = s.timers.orEmpty().filter { it > 0 }
    if (timers.isEmpty()) return
    val running by Timers.running.collectAsStateWithLifecycle()
    val mine = running.filter { it.code == r.code && it.step == step }
    val t = if (mine.isNotEmpty()) now() else 0L
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mine.isNotEmpty()) {
            val ctx = LocalContext.current
            mine.forEach { rt ->
                Surface(onClick = { Timers.stop(ctx, rt.id) }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(clock(rt.endAt - t), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Close, "Stop", Modifier.size(16.dp))
                    }
                }
            }
        } else {
            timers.forEach { min ->
                Surface(onClick = { onTimer(step, min) }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(l.timer(min), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Steps(
    r: Recipe,
    l: Labels,
    units: String,
    onTimer: (Int, Int) -> Unit,
) {
    val steps = r.stepList
    if (steps.isEmpty()) return
    Text(l.steps, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    steps.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 8.dp)) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Units.text(s.text.orEmpty(), units, r.lang), style = MaterialTheme.typography.bodyLarge)
                TimerChips(r, i + 1, s, l, onTimer)
            }
        }
    }
}

@Composable
private fun Bullets(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
    lines.forEach {
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Extras(
    r: Recipe,
    l: Labels,
    units: String,
) {
    Bullets(l.tips, r.tips.orEmpty().filter { it.isNotBlank() }.map { Units.text(it, units, r.lang) })
    Bullets(l.miss, r.missing.orEmpty().filter { it.isNotBlank() })
    val names = mapOf("calories" to l.calories, "protein" to l.protein, "carbs" to l.carbs, "fat" to l.fat)
    val nut = r.nutrition.orEmpty().filterValues { it != null && it.toString().isNotBlank() }
    if (nut.isNotEmpty()) {
        Text(l.nut, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
        nut.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(names[k] ?: k.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v.toString().removeSuffix(".0"), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Mine(
    r: Recipe,
    m: Memory,
    l: Labels,
    onMade: () -> Unit,
    onAgain: (Double) -> Unit,
) {
    Spacer(Modifier.height(22.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(16.dp))
    val log = m.cooked.orEmpty().filter { it.code == r.code }.sortedByDescending { it.at }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMade) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.done)
            Spacer(Modifier.width(8.dp))
            Text(l.madeIt)
        }
        if (log.isNotEmpty()) {
            Text(l.made(log.size, day(log.first().at, r.lang)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val prev = log.firstOrNull { it.factor != null && it.factor != 1.0 }
    if (prev?.factor != null && r.stepList.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onAgain(prev.factor) }) {
            Icon(Icons.Default.Replay, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(l.again2 + " · " + factorName(prev.factor))
        }
    }
    log.take(8).forEach { c ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    day(c.at, r.lang) + (if (c.factor != null && c.factor != 1.0) " · " + factorName(c.factor) else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!c.note.isNullOrBlank()) Text(c.note, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { Kitchen.op("uncook", mapOf("code" to r.code, "id" to c.id)) }) {
                Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(l.rating, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    val cur = m.ratings.orEmpty()[r.code] ?: 0
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..10).forEach { n ->
                val on = n <= cur
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { Kitchen.op("rate", mapOf("code" to r.code, "rating" to if (n == cur) 0 else n)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Text(l.notes, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    var note by remember(r.code) { mutableStateOf(m.notes.orEmpty()[r.code].orEmpty()) }
    LaunchedEffect(note) {
        delay(800)
        if (note != m.notes.orEmpty()[r.code].orEmpty()) Kitchen.op("note", mapOf("code" to r.code, "text" to note))
    }
    OutlinedTextField(note, { note = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
}

// ---- Ask about this recipe ----

data class AskItem(val id: Int, val q: String, val a: String?, val bad: Boolean = false)

/** The questions asked about each recipe, kept while the app is open. A question goes on when the screen is left. */
object Asks {
    val threads = mutableStateMapOf<String, List<AskItem>>()
    private val scope = MainScope()
    private var next = 0

    fun ask(
        code: String,
        lang: String?,
        q: String,
    ) {
        val t = threads[code].orEmpty()
        val history = t.filter { it.a != null && !it.bad }.takeLast(6).map { it.q to it.a!! }
        val item = AskItem(next++, q, null)
        threads[code] = t + item
        scope.launch {
            val (a, bad) =
                try {
                    Kitchen.ask(code, lang, q, history) to false
                } catch (_: Offline) {
                    "No connection" to true
                } catch (_: SignedOut) {
                    "Signed out" to true
                } catch (e: Refused) {
                    (e.message ?: "Something went wrong.") to true
                } catch (e: Exception) {
                    (e.message ?: "Something went wrong.") to true
                }
            threads[code] = threads[code].orEmpty().map { if (it.id == item.id) it.copy(a = a, bad = bad) else it }
        }
    }
}

@Composable
private fun AskBox(
    r: Recipe,
    l: Labels,
) {
    val thread = Asks.threads[r.code].orEmpty()
    var q by remember(r.code) { mutableStateOf("") }
    val send = {
        val t = q.trim()
        if (t.isNotEmpty()) {
            Asks.ask(r.code, r.lang, t)
            q = ""
        }
    }
    Text(l.ask, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
    thread.forEach { x ->
        Text(x.q, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(
            x.a ?: l.asking,
            style = MaterialTheme.typography.bodyLarge,
            color = if (x.bad) MaterialTheme.colorScheme.error else if (x.a == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
    }
    OutlinedTextField(
        q,
        { if (it.length <= 1000) q = it },
        placeholder = { Text(l.askPh) },
        trailingIcon = {
            IconButton(onClick = send, enabled = q.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, l.ask) }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

// ---- the sheets from More, and I made it ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionsSheet(
    code: String,
    l: Labels,
    onDismiss: () -> Unit,
) {
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    val add = {
        val n = name.trim()
        if (n.isNotEmpty()) {
            val id = Local.newId()
            Kitchen.op("coll-new", mapOf("id" to id, "name" to n))
            Kitchen.op("coll-add", mapOf("id" to id, "code" to code))
            name = ""
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text(l.collections, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            m.collections.orEmpty().forEach { c ->
                val on = code in c.codes.orEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Kitchen.op(if (on) "coll-remove" else "coll-add", mapOf("id" to c.id, "code" to code)) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text(c.name.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        if (on) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        null,
                        tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            OutlinedTextField(
                name,
                { if (it.length <= 60) name = it },
                placeholder = { Text("New collection") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = { IconButton(onClick = add, enabled = name.isNotBlank()) { Icon(Icons.Default.Add, "Add") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareLinkSheet(
    r: Recipe,
    url: String?,
    onUrl: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val l = labelsFor(r.lang)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val call = { on: Boolean ->
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    onUrl(Kitchen.share(r.code, on))
                } catch (_: Offline) {
                    error = "No connection"
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text(l.share, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
            if (url == null) {
                Button(onClick = { call(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Make a link")
                }
            } else {
                OutlinedTextField(url, {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                FlowRowButtons {
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, r.title)
                        ctx.startActivity(Intent.createChooser(send, null))
                    }) { Text("Share") }
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(r.title ?: "Matkon", url))
                        copied = true
                    }) { Text(if (copied) "Copied" else "Copy") }
                    TextButton(onClick = { call(false) }, enabled = !busy) { Text("Stop sharing") }
                }
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** "I made it": the amounts used and a note, for the cooking log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MadeSheet(
    r: Recipe,
    factor: Double,
    onDismiss: () -> Unit,
) {
    val l = labelsFor(r.lang)
    var f by remember { mutableStateOf(factor) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CompositionLocalProvider(LocalLayoutDirection provides if (r.he) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp)) {
                Text(l.madeIt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                if (r.groups.isNotEmpty()) {
                    Text(l.amounts, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                    FactorChips(f) { f = it }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(note, { if (it.length <= 500) note = it }, label = { Text(l.noteLbl) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        Kitchen.op("cooked", mapOf("code" to r.code, "id" to Local.newId(), "factor" to f, "note" to note.trim()))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(l.madeIt)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(
    onDismiss: () -> Unit,
    title: String = "Plan",
    onPick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            (0L until 14L).map { today.plusDays(it) }.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(d) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayName(d), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(shortDate(d), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
