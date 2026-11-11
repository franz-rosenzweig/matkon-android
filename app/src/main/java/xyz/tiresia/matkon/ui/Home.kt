package xyz.tiresia.matkon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.IndexEntry
import xyz.tiresia.matkon.data.Kitchen
import java.time.LocalDate

private data class HomeRow(val title: String, val cards: List<Pair<IndexEntry, String?>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val recipes by Kitchen.recipes.collectAsStateWithLifecycle()
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val suggest by Kitchen.suggest.collectAsStateWithLifecycle()
    val syncing by Kitchen.syncing.collectAsStateWithLifecycle()
    val offline by Kitchen.offline.collectAsStateWithLifecycle()
    val waiting by Kitchen.waiting.collectAsStateWithLifecycle()
    val wide = wide()
    val byCode = recipes.associateBy { it.code }
    val today = LocalDate.now()
    val todayS = today.toString()
    val weekEnd = today.plusDays(7).toString()
    val plan = m.plan.orEmpty()

    val rows =
        buildList {
            plan.filter { it.day == todayS }.mapNotNull { p -> byCode[p.code]?.let { it to null } }.let {
                if (it.isNotEmpty()) add(HomeRow("Today", it))
            }
            suggest.mapNotNull { s ->
                val e = byCode[s.code] ?: return@mapNotNull null
                val why =
                    when (s.why) {
                        "saved" -> "Saved"
                        "again" -> "Not made since " + day((s.about as? Number)?.toDouble())
                        "like" -> "Like " + (s.about ?: "")
                        "new" -> "New"
                        else -> null
                    }
                e to why
            }.let { if (it.isNotEmpty()) add(HomeRow("Up next", it)) }
            plan.filter { it.day > todayS && it.day < weekEnd }.mapNotNull { p ->
                byCode[p.code]?.let { it to dayName(LocalDate.parse(p.day)) }
            }.let { if (it.isNotEmpty()) add(HomeRow("This week", it)) }
            recipes.filter { it.is_recipe != false }.take(12).map { it to null }.let {
                if (it.isNotEmpty()) add(HomeRow("Added lately", it))
            }
            m.cooked.orEmpty().sortedByDescending { it.at }.distinctBy { it.code }.take(12).mapNotNull { c ->
                byCode[c.code]?.let { it to "Made " + day(c.at) }
            }.let { if (it.isNotEmpty()) add(HomeRow("Made lately", it)) }
        }

    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(nav.again) { if (nav.again > 0) list.animateScrollToItem(0) }

    Scaffold(
        topBar = {
            Bar("Matkon") {
                if (offline) Icon(Icons.Default.CloudOff, "Offline", Modifier.padding(horizontal = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { Kitchen.shared.value = "" }) { Icon(Icons.Default.Add, "Add a reel") }
                if (!wide) IconButton(onClick = { nav.go(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { scope.launch { Kitchen.refreshNow() } },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                if (waiting > 0) {
                    item {
                        Text(
                            "$waiting on the way",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                rows.forEach { row ->
                    item(key = row.title) {
                        Column {
                            SectionTitle(row.title)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(row.cards, key = { it.first.code + (it.second ?: "") }) { (e, under) ->
                                    RecipeCard(
                                        e,
                                        m,
                                        onClick = { nav.go(Screen.Recipe(e.code)) },
                                        under = under,
                                        modifier = Modifier.width(if (wide) 220.dp else 168.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (rows.isEmpty() && !syncing) {
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}
