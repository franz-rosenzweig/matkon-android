package xyz.tiresia.matkon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.Amounts
import xyz.tiresia.matkon.data.IndexEntry
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Local
import xyz.tiresia.matkon.data.Memory
import xyz.tiresia.matkon.data.PlanEntry
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen() {
    val nav = LocalNav.current
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
    LaunchedEffect(nav.again) { if (nav.again > 0) list.animateScrollToItem(0) }

    Scaffold(
        topBar = {
            Bar("Week") {
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
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.padding(pad).fillMaxSize()) {
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
                    val past = d < today
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                        plan[d.toString()].orEmpty().forEach { p ->
                            val e = byCode[p.code]
                            if (e != null) PlannedRow(e, m, p, onOpen = { nav.go(Screen.Recipe(p.code)) }, onMove = { moving = p })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    adding?.let { d ->
        PickRecipe(recipes, m, onDismiss = { adding = null }) { code ->
            Kitchen.op("plan-add", mapOf("id" to Local.newId(), "code" to code, "day" to d.toString()))
            adding = null
        }
    }
    moving?.let { p ->
        PlanSheet(onDismiss = { moving = null }) { d ->
            Kitchen.op("plan-move", mapOf("id" to p.id, "day" to d.toString()))
            moving = null
        }
    }
}

@Composable
private fun PlannedRow(
    e: IndexEntry,
    m: Memory,
    p: PlanEntry,
    onOpen: () -> Unit,
    onMove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Photo(e.photo, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Text(
                shownTitle(e, m),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickRecipe(
    recipes: List<IndexEntry>,
    m: Memory,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val words = Amounts.norm(q).split(' ').filter { it.isNotEmpty() }
    val shown =
        recipes
            .filter { it.is_recipe != false }
            .filter { e -> words.isEmpty() || haystack(e).let { h -> words.all { h.contains(it) } } }
            .sortedWith(compareByDescending<IndexEntry> { m.saved.orEmpty().containsKey(it.code) }.thenByDescending { it.found ?: 0.0 })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
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
