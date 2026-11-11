package xyz.tiresia.matkon.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.Amounts
import xyz.tiresia.matkon.data.Ingredient
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Local
import xyz.tiresia.matkon.data.Recipe
import xyz.tiresia.matkon.data.Step
import xyz.tiresia.matkon.timer.Timers

private fun stem(w: String) = w.replace(Regex("(es|s)$", RegexOption.IGNORE_CASE), "")

/** The ingredients a step names, with their amounts, so they're there while cooking. A name counts at the
 * start of a word ("oil" isn't in "boil"); Hebrew words can carry a prefix (ה, ו, ב…). */
fun usesOf(
    r: Recipe,
    s: Step,
): List<Ingredient> {
    val text = Amounts.norm(s.text.orEmpty())
    val pre = if (r.he) "[והבלמשכ]{0,2}" else ""
    val out = mutableListOf<Ingredient>()
    r.groups.forEach { g ->
        g.items.orEmpty().forEach { i ->
            if (i.amount.isNullOrBlank()) return@forEach
            val words = Amounts.norm(i.item.orEmpty()).split(' ').filter { it.length > 2 }
            if (words.isEmpty()) return@forEach
            val key = stem(words.last())
            if (key.length > 2 &&
                Regex("(^| )" + pre + Regex.escape(key)).containsMatchIn(text) &&
                out.none { stem(Amounts.norm(it.item.orEmpty()).split(' ').last()) == key }
            ) {
                out.add(i)
            }
        }
    }
    return out.take(6)
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun CookScreen(code: String) {
    val nav = LocalNav.current
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val factors by Kitchen.factors.collectAsStateWithLifecycle()
    val running by Timers.running.collectAsStateWithLifecycle()
    val ringing by Timers.ringing.collectAsStateWithLifecycle()
    val lang = remember(code) { Kitchen.langFor(code) }
    val r = rememberRecipe(code, lang)
    KeepScreenOn()
    if (r == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val l = labelsFor(r.lang)
    val factor = factors[code] ?: 1.0
    val steps = r.stepList
    val startAt = remember(code) { ((m.steps.orEmpty()[code] ?: 1) - 1).coerceIn(0, steps.size) }
    val pager = rememberPagerState(initialPage = startAt) { steps.size + 1 }
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(false) }
    val startTimer = rememberTimerStarter()
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.debounce(700).collect { p ->
            if (p < steps.size) Kitchen.op("step", mapOf("code" to code, "n" to p + 1))
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (r.he) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            val split = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
            Column(Modifier.fillMaxSize()) {
                // the bar
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Default.Close, l.close) }
                    Text(
                        r.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (pager.currentPage < steps.size) {
                        Text(
                            l.step(pager.currentPage + 1, steps.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    if (!split && r.groups.isNotEmpty()) {
                        IconButton(onClick = { sheet = true }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, l.ingredients) }
                    }
                }
                Row(Modifier.weight(1f)) {
                    if (split && r.groups.isNotEmpty()) {
                        Column(Modifier.weight(0.38f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                            Ingredients(r, m, l, factor, big = true)
                            Spacer(Modifier.height(24.dp))
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    HorizontalPager(state = pager, modifier = Modifier.weight(0.62f).fillMaxSize()) { page ->
                        if (page < steps.size) {
                            StepPage(r, page, steps[page], l, factor, onTimer = { step, min -> startTimer(r, step, min) })
                        } else {
                            DonePage(r, l, onMade = {
                                Kitchen.op("cooked", mapOf("code" to code, "id" to Local.newId()))
                                nav.back()
                            }, onAgain = { scope.launch { pager.animateScrollToPage(0) } }, onRecipe = { nav.back() })
                        }
                    }
                }
                TimersBar(running, ringing, onOpen = { })
                if (pager.currentPage < steps.size) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(l.back, fontSize = 17.sp) }
                        Button(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(if (pager.currentPage == steps.size - 1) l.done else l.next, fontSize = 17.sp) }
                    }
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CompositionLocalProvider(LocalLayoutDirection provides if (r.he) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
                    Ingredients(r, m, l, factor, big = true)
                }
            }
        }
    }
}

@Composable
private fun StepPage(
    r: Recipe,
    page: Int,
    s: Step,
    l: Labels,
    factor: Double,
    onTimer: (Int, Int) -> Unit,
) {
    val uses = remember(r, page) { usesOf(r, s) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp)) {
            Text(s.text.orEmpty(), fontSize = if (wide()) 30.sp else 25.sp, lineHeight = if (wide()) 42.sp else 35.sp)
            TimerChips(r, page + 1, s, l, onTimer)
            if (uses.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(l.uses, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                uses.forEach { i ->
                    Text(amountAnd(Amounts.scale(i.amount.orEmpty(), factor), i.item.orEmpty()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DonePage(
    r: Recipe,
    l: Labels,
    onMade: () -> Unit,
    onAgain: () -> Unit,
    onRecipe: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.done)
        Spacer(Modifier.height(16.dp))
        Text(l.finished, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onMade, modifier = Modifier.widthIn(min = 220.dp).height(52.dp)) { Text(l.madeIt, fontSize = 17.sp) }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onAgain) { Text(l.again) }
        TextButton(onClick = onRecipe) { Text(l.toRecipe) }
    }
}

