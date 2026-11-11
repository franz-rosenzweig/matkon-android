package xyz.tiresia.matkon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Pantry

/** What can I make? What's in the kitchen (kept for the account), and the recipes it goes furthest towards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryScreen() {
    val nav = LocalNav.current
    val all by Kitchen.recipes.collectAsStateWithLifecycle()
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val recipes = all.filter { it.is_recipe != false }
    val have = m.pantry.orEmpty()
    val english = m.english()
    val offer = remember(recipes, have, english) { Pantry.offer(recipes, have, english) }
    val ranked = remember(recipes, have, english) { if (have.isEmpty()) emptyList() else Pantry.ranked(recipes, have, english) }
    var text by remember { mutableStateOf("") }
    val set = { items: List<String> -> Kitchen.op("pantry", mapOf("items" to items)) }
    val add = {
        val t = text.trim()
        if (t.isNotEmpty() && have.none { it.equals(t, ignoreCase = true) }) set(have + t)
        text = ""
    }

    Scaffold(
        topBar = { Bar("What can I make?", onBack = if (nav.stack.size > 1) ({ nav.back() }) else null) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "add") {
                OutlinedTextField(
                    text,
                    { if (it.length <= 60) text = it },
                    placeholder = { Text("Add") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = { IconButton(onClick = add, enabled = text.isNotBlank()) { Icon(Icons.Default.Add, "Add") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).widthIn(max = 720.dp),
                )
            }
            if (have.isNotEmpty()) {
                item(key = "have") {
                    Column {
                        SectionTitle("In the kitchen")
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            have.forEach { x ->
                                InputChip(
                                    selected = false,
                                    onClick = { set(have - x) },
                                    label = { Text(x) },
                                    trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
            }
            if (offer.isNotEmpty()) {
                item(key = "offer") {
                    Column {
                        SectionTitle("From your recipes")
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            offer.forEach { x ->
                                AssistChip(
                                    onClick = { set(have + x) },
                                    label = { Text(x) },
                                    leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
            }
            if (have.isNotEmpty()) {
                if (ranked.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "Nothing yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    item(key = "can") { SectionTitle("You can make") }
                    items(ranked, key = { it.e.code }) { c ->
                        Surface(
                            onClick = { nav.go(Screen.Recipe(c.e.code)) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).widthIn(max = 720.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Photo(c.e.photo, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(shownTitle(c.e, m), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (c.miss.isEmpty()) "Everything" else "Missing: " + c.miss.take(6).joinToString(", ") + (if (c.miss.size > 6) "…" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
