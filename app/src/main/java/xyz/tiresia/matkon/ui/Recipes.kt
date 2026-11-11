package xyz.tiresia.matkon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tiresia.matkon.data.Amounts
import xyz.tiresia.matkon.data.IndexEntry
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Memory

enum class Sort(val label: String) { Added("Added"), Posted("Posted"), AZ("A–Z"), Rating("Rating"), Made("Made") }

// kept while the app is open
private var query by mutableStateOf("")
private var kind by mutableStateOf("All")
private var sortBy by mutableStateOf(Sort.Added)

fun haystack(e: IndexEntry) =
    Amounts.norm(
        listOfNotNull(e.title, e.title_en, e.user, e.kind, e.cuisine, e.summary).joinToString(" ") + " " +
            (e.items.orEmpty() + e.items_en.orEmpty() + e.tags.orEmpty()).joinToString(" "),
    )

fun visible(
    all: List<IndexEntry>,
    m: Memory,
    creator: String?,
): List<IndexEntry> {
    val words = Amounts.norm(query).split(' ').filter { it.isNotEmpty() }
    return all
        .asSequence()
        .filter { it.is_recipe != false }
        .filter { creator == null || it.user == creator }
        .filter {
            when (kind) {
                "All" -> true
                "Saved" -> m.saved.orEmpty().containsKey(it.code)
                "Made" -> (m.cooked_count.orEmpty()[it.code] ?: 0) > 0
                else -> it.kind == kind
            }
        }.filter { e -> words.isEmpty() || haystack(e).let { h -> words.all { h.contains(it) } } }
        .sortedWith(
            when (sortBy) {
                Sort.Added -> compareByDescending { it.found ?: 0.0 }
                Sort.Posted -> compareByDescending { it.posted ?: 0.0 }
                Sort.AZ -> compareBy { shownTitle(it, m).lowercase() }
                Sort.Rating -> compareByDescending<IndexEntry> { m.ratings.orEmpty()[it.code] ?: 0 }.thenByDescending { it.found ?: 0.0 }
                Sort.Made -> compareByDescending<IndexEntry> { m.cooked_last.orEmpty()[it.code] ?: 0.0 }.thenByDescending { it.found ?: 0.0 }
            },
        ).toList()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipesScreen() {
    val nav = LocalNav.current
    val all by Kitchen.recipes.collectAsStateWithLifecycle()
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    val creator = nav.creator
    val shown = visible(all, m, creator)
    val kinds =
        all
            .filter { it.is_recipe != false && !it.kind.isNullOrBlank() }
            .groupingBy { it.kind!! }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    val grid = rememberLazyGridState()
    LaunchedEffect(nav.again) { if (nav.again > 0) grid.animateScrollToItem(0) }
    LaunchedEffect(query, kind, sortBy, creator) { grid.scrollToItem(0) }

    Scaffold(
        topBar = {
            Bar("Recipes") {
                IconButton(onClick = { sheet = true }) {
                    Icon(Icons.Default.Tune, "Sort and filter", tint = if (sortBy != Sort.Added || creator != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query,
                { query = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (creator != null) {
                    item {
                        InputChip(
                            selected = true,
                            onClick = { nav.creator = null },
                            label = { Text("@$creator") },
                            trailingIcon = { Icon(Icons.Default.Close, "Clear", Modifier.padding(0.dp)) },
                        )
                    }
                }
                items(listOf("All", "Saved", "Made") + kinds) { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) })
                }
            }
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(if (wide()) 200.dp else 156.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.code }) { e ->
                    RecipeCard(
                        e,
                        m,
                        onClick = { nav.go(Screen.Recipe(e.code)) },
                        under =
                            when (sortBy) {
                                Sort.Posted -> e.posted?.let { day(it, e.lang) }
                                Sort.Made -> m.cooked_last.orEmpty()[e.code]?.let { "Made " + day(it) }
                                else -> null
                            },
                    )
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort and filter", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { sheet = false }) { Text("Done") }
                }
                Text("Sort", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sort.entries.forEach { s -> FilterChip(selected = sortBy == s, onClick = { sortBy = s }, label = { Text(s.label) }) }
                }
                Text("Creator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                val creators =
                    all
                        .filter { it.is_recipe != false && !it.user.isNullOrBlank() }
                        .groupingBy { it.user!! }
                        .eachCount()
                        .entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = creator == null, onClick = { nav.creator = null }, label = { Text("All") })
                    creators.forEach { (u, n) ->
                        FilterChip(selected = creator == u, onClick = { nav.creator = u }, label = { Text("@$u · $n") })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
