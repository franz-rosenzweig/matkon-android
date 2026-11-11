package xyz.tiresia.matkon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.IndexEntry
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Local
import xyz.tiresia.matkon.data.Memory
import xyz.tiresia.matkon.data.PlanEntry
import xyz.tiresia.matkon.data.Search
import java.time.LocalDate
import kotlin.math.roundToInt

/** A planned recipe being dragged to another day: where the finger is, and which day it's over. */
private class Drag {
    var entry by mutableStateOf<PlanEntry?>(null)
    var y by mutableFloatStateOf(0f)
    var grab = 0f
    var left = 0f
    var width = 0f
    var over by mutableStateOf<String?>(null)

    /** Each day's place on the screen (root coordinates), while it's on the screen. */
    val days = HashMap<String, Rect>()

    fun start(
        p: PlanEntry,
        at: Offset,
        row: Rect,
    ) {
        entry = p
        y = at.y
        grab = at.y - row.top
        left = row.left
        width = row.width
        over = p.day
    }

    fun move(at: Offset) {
        y = at.y
        findDay()
    }

    fun findDay() {
        over = days.entries.firstOrNull { y >= it.value.top && y < it.value.bottom }?.key ?: over
    }

    fun drop() {
        val p = entry ?: return
        val to = over
        entry = null
        over = null
        if (to != null && to != p.day) Kitchen.op("plan-move", mapOf("id" to p.id, "day" to to))
    }

    fun cancel() {
        entry = null
        over = null
    }
}

private class Holder<T>(var value: T? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val recipes by Kitchen.recipes.collectAsStateWithLifecycle()
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val byCode = recipes.associateBy { it.code }
    val today = LocalDate.now()
    val start = weekStart(today)
    val days = (0L until 14L).map { start.plusDays(it) }
    val plan = m.plan.orEmpty().groupBy { it.day }
    var adding by remember { mutableStateOf<LocalDate?>(null) }
    var moving by remember { mutableStateOf<PlanEntry?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val drag = remember { Drag() }
    val box = remember { Holder<LayoutCoordinates>() }
    val density = LocalDensity.current
    LaunchedEffect(nav.again) { if (nav.again > 0) list.animateScrollToItem(0) }

    // near the top or the bottom while dragging: the list moves along
    val dragging = drag.entry != null
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val most = with(density) { 14.dp.toPx() }
        while (drag.entry != null) {
            withFrameNanos { }
            val b = box.value?.takeIf { it.isAttached }?.boundsInRoot() ?: continue
            val v =
                when {
                    drag.y < b.top + edge -> -most * ((b.top + edge - drag.y) / edge).coerceAtMost(1f)
                    drag.y > b.bottom - edge -> most * ((drag.y - (b.bottom - edge)) / edge).coerceAtMost(1f)
                    else -> 0f
                }
            if (v != 0f) list.scrollBy(v)
            drag.findDay()
        }
    }

    Scaffold(
        topBar = {
            Bar("Week") {
                IconButton(onClick = { Printing.week(ctx, recipes, m) }) { Icon(Icons.Default.Print, "Print") }
                IconButton(onClick = {
                    scope.launch {
                        val coming = m.plan.orEmpty().filter { it.day >= today.toString() && it.day < start.plusDays(7).toString() }
                        var n = 0
                        for (p in coming) {
                            val r = Kitchen.recipe(p.code) ?: continue
                            Kitchen.addToList(r, Kitchen.factor(p.code))
                            n++
                        }
                        if (n > 0 && snack.showSnackbar("On the list", actionLabel = "Open", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                            nav.go(Screen.Shop)
                        }
                    }
                }) { Icon(Icons.Default.AddShoppingCart, "Add the week to the list") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            WithLine(m, Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { box.value = it }) {
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                    days.forEachIndexed { i, d ->
                        if (i == 0 || i == 7) {
                            item(key = "w$i") {
                                Text(
                                    if (i == 0) "This week" else "Next week",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                        }
                        item(key = d.toString()) {
                            val key = d.toString()
                            val past = d < today
                            DisposableEffect(key) { onDispose { drag.days.remove(key) } }
                            val drop = drag.entry != null && drag.over == key
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { drag.days[key] = it.boundsInRoot() }
                                    .background(if (drop) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        dayName(d),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Medium,
                                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(shortDate(d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { adding = d }) { Icon(Icons.Default.Add, "Add") }
                                }
                                plan[key].orEmpty().forEach { p ->
                                    val e = byCode[p.code]
                                    if (e != null) PlannedRow(e, m, p, drag, onOpen = { nav.go(Screen.Recipe(p.code)) }, onMove = { moving = p })
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                // the recipe under the finger
                val p = drag.entry
                val e = p?.let { byCode[it.code] }
                val origin = box.value?.takeIf { it.isAttached }?.boundsInRoot()
                if (e != null && origin != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 8.dp,
                        modifier =
                            Modifier
                                .offset { IntOffset((drag.left - origin.left).roundToInt(), (drag.y - drag.grab - origin.top).roundToInt()) }
                                .width(with(density) { drag.width.toDp() }),
                    ) {
                        PlannedLook(e, m)
                    }
                }
            }
        }
    }

    adding?.let { d ->
        PickRecipe(recipes, m, title = dayName(d) + " · " + shortDate(d), onDismiss = { adding = null }) { code ->
            Kitchen.op("plan-add", mapOf("id" to Local.newId(), "code" to code, "day" to d.toString()))
            adding = null
        }
    }
    moving?.let { p ->
        PlanSheet(onDismiss = { moving = null }, title = "Move") { d ->
            Kitchen.op("plan-move", mapOf("id" to p.id, "day" to d.toString()))
            moving = null
        }
    }
}

@Composable
private fun PlannedLook(
    e: IndexEntry,
    m: Memory,
    end: @Composable () -> Unit = { Spacer(Modifier.width(48.dp)) },
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Photo(e.photo, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Text(
            shownTitle(e, m),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        end()
    }
}

/** A planned recipe: tap to open it, hold and drag it to another day, or Move from its menu. */
@Composable
private fun PlannedRow(
    e: IndexEntry,
    m: Memory,
    p: PlanEntry,
    drag: Drag,
    onOpen: () -> Unit,
    onMove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val coords = remember { Holder<LayoutCoordinates>() }
    val pin = remember { Holder<PinnableContainer.PinnedHandle>() }
    val pinnable = LocalPinnableContainer.current
    val haptic = LocalHapticFeedback.current
    val lifted = drag.entry?.id == p.id
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (lifted) 0.35f else 1f),
    ) {
        PlannedLook(
            e,
            m,
            modifier =
                Modifier
                    .onGloballyPositioned { coords.value = it }
                    .pointerInput(p.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { at ->
                                val c = coords.value
                                if (c != null && c.isAttached) {
                                    // kept alive while the list scrolls it away, so the drag goes on
                                    pin.value = pinnable?.pin()
                                    drag.start(p, c.localToRoot(at), c.boundsInRoot())
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, _ ->
                                val c = coords.value
                                if (c != null && c.isAttached && drag.entry?.id == p.id) drag.move(c.localToRoot(change.position))
                            },
                            onDragEnd = {
                                drag.drop()
                                pin.value?.release()
                                pin.value = null
                            },
                            onDragCancel = {
                                drag.cancel()
                                pin.value?.release()
                                pin.value = null
                            },
                        )
                    },
            end = {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.Event, "Move")
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Move") }, onClick = {
                            menu = false
                            onMove()
                        })
                        DropdownMenuItem(text = { Text("Remove") }, onClick = {
                            menu = false
                            Kitchen.op("plan-remove", mapOf("id" to p.id))
                        })
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickRecipe(
    recipes: List<IndexEntry>,
    m: Memory,
    title: String? = null,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val me by Kitchen.me.collectAsStateWithLifecycle()
    val words = Search.words(q)
    val hays = Search.hays(recipes, me?.names)
    val shown =
        recipes
            .filter { it.is_recipe != false }
            .filter { e -> Search.matches(hays[e.code], words) }
            .sortedWith(compareByDescending<IndexEntry> { m.saved.orEmpty().containsKey(it.code) }.thenByDescending { it.found ?: 0.0 })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            OutlinedTextField(
                q,
                { q = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "Clear") } },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.code }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(e.code) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Photo(e.photo, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(shownTitle(e, m), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
