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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tiresia.matkon.data.Amounts
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
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
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
                        val body = open.joinToString("\n") { "• " + (if (it.amount.isNotBlank()) it.amount + " " else "") + it.item }
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), null))
                    }) { Icon(Icons.Default.Share, "Share") }
                }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "More")
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
        Column(Modifier.padding(pad).fillMaxSize()) {
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
                items(open, key = { "o" + it.key }) { line ->
                    Line(line, byCode.mapValues { shownTitle(it.value, m) })
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
                        Line(line, byCode.mapValues { shownTitle(it.value, m) })
                    }
                }
            }
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

@Composable
private fun Line(
    line: Amounts.Line,
    titles: Map<String, String>,
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
            Text(amountAnd(line.amount, line.item), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            val from = line.codes.mapNotNull { titles[it] }
            if (from.isNotEmpty()) {
                Text(
                    from.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { Kitchen.op("shop-remove", mapOf("ids" to line.ids)) }) {
            Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
