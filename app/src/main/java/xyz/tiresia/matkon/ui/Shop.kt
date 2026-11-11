package xyz.tiresia.matkon.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tiresia.matkon.data.Aisles
import xyz.tiresia.matkon.data.Amounts
import xyz.tiresia.matkon.data.Units
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Local

@Composable
fun ShopScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val recipes by Kitchen.recipes.collectAsStateWithLifecycle()
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val byCode = recipes.associateBy { it.code }
    val lines = Amounts.merge(m.shop.orEmpty())
    val open = lines.filter { !it.done }
    val done = lines.filter { it.done }
    val units = m.units()
    val aisles = m.aisles.orEmpty()
    val byAisle = open.groupBy { aisles[it.key] ?: Aisles.of(it.item) }
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var aisleFor by remember { mutableStateOf<Amounts.Line?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(nav.again) { if (nav.again > 0) list.animateScrollToItem(0) }
    val add = {
        val t = text.trim()
        if (t.isNotEmpty()) Kitchen.op("shop-add", mapOf("items" to listOf(mapOf("id" to Local.newId(), "item" to t, "amount" to ""))))
        text = ""
    }

    Scaffold(
        topBar = {
            Bar("List") {
                if (open.isNotEmpty()) {
                    IconButton(onClick = {
                        val body = open.joinToString("\n") { "• " + (if (it.amount.isNotBlank()) shownAmount(it, units) + " " else "") + it.item }
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), null))
                    }) { Icon(Icons.Default.Share, "Share") }
                }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "More")
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Print") }, leadingIcon = { Icon(Icons.Default.Print, null) }, onClick = {
                            menu = false
                            Printing.list(ctx, lines, recipes, m)
                        })
                        DropdownMenuItem(text = { Text("Clear done") }, enabled = done.isNotEmpty(), onClick = {
                            menu = false
                            Kitchen.op("shop-clear", mapOf("done" to true))
                        })
                        DropdownMenuItem(text = { Text("Clear all") }, enabled = lines.isNotEmpty(), onClick = {
                            menu = false
                            confirm = true
                        })
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        // as narrow as the web's list on a wide screen, so a Hebrew line on the right stays near its box
        Column(Modifier.padding(pad).fillMaxSize().wrapContentWidth().widthIn(max = 760.dp)) {
            WithLine(m, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
            OutlinedTextField(
                text,
                { text = it },
                placeholder = { Text("Add") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = { IconButton(onClick = add, enabled = text.isNotBlank()) { Icon(Icons.Default.Add, "Add") } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(state = list, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                Aisles.ORDER.filter { byAisle.containsKey(it) }.forEach { a ->
                    item(key = "a$a") {
                        Text(
                            Aisles.NAMES[a] ?: a,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(byAisle.getValue(a), key = { "o" + it.key }) { line ->
                        Line(line, byCode.mapValues { shownTitle(it.value, m) }, units, onAisle = { aisleFor = line })
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done") {
                        Text(
                            "Done",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
                        )
                    }
                    items(done, key = { "d" + it.key }) { line ->
                        Line(line, byCode.mapValues { shownTitle(it.value, m) }, units, onAisle = null)
                    }
                }
            }
        }
    }

    aisleFor?.let { line ->
        AisleSheet(line, aisles[line.key] ?: Aisles.of(line.item), onDismiss = { aisleFor = null }) { a ->
            Kitchen.op("aisle", mapOf("key" to line.key, "aisle" to if (a == Aisles.of(line.item)) "" else a))
            aisleFor = null
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear the whole list?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    Kitchen.op("shop-clear", mapOf("done" to false))
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private val HEBREW = Regex("[\u0590-\u05FF]")

/** The amount on the list in the account's units (Hebrew units for a Hebrew line). */
fun shownAmount(
    line: Amounts.Line,
    units: String,
) = Units.amount(line.amount, units, if (HEBREW.containsMatchIn(line.amount + line.item)) "he" else "en")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AisleSheet(
    line: Amounts.Line,
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(line.item, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Aisles.ORDER.forEach { a ->
                val on = a == current
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(a) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Aisles.NAMES[a] ?: a, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Line(
    line: Amounts.Line,
    titles: Map<String, String>,
    units: String,
    onAisle: (() -> Unit)?,
) {
    val alpha = if (line.done) 0.45f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { Kitchen.op("shop-tick", mapOf("ids" to line.ids, "done" to !line.done)) }
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (line.done) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            null,
            Modifier.size(24.dp),
            tint = if (line.done) MaterialTheme.colorScheme.done else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // full width, so a Hebrew line sits on the right, as the web's dir="auto" does
            Text(amountAnd(shownAmount(line, units), line.item), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), modifier = Modifier.fillMaxWidth())
            val from = line.codes.mapNotNull { titles[it] }
            if (from.isNotEmpty()) {
                Text(
                    from.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (onAisle != null) {
            IconButton(onClick = onAisle) {
                Icon(Icons.AutoMirrored.Outlined.Label, "Aisle", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { Kitchen.op("shop-remove", mapOf("ids" to line.ids)) }) {
            Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
