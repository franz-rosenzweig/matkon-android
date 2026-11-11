package xyz.tiresia.matkon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tiresia.matkon.data.Cooked
import xyz.tiresia.matkon.data.IndexEntry
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Memory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private fun dayOf(t: Double): LocalDate = Instant.ofEpochMilli((t * 1000).toLong()).atZone(ZoneId.systemDefault()).toLocalDate()

/** What was cooked: a month to a page, with each day's dishes, and the log with its notes. */
@Composable
fun HistoryScreen() {
    val nav = LocalNav.current
    val recipes by Kitchen.recipes.collectAsStateWithLifecycle()
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val byCode = recipes.associateBy { it.code }
    val log = m.cooked.orEmpty().sortedByDescending { it.at }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var noting by remember { mutableStateOf<Cooked?>(null) }

    Scaffold(
        topBar = { Bar("History", onBack = if (nav.stack.size > 1) ({ nav.back() }) else null) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "cal") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Calendar(month, log, byCode, m, onMonth = { month = it }, onOpen = { nav.go(Screen.Recipe(it)) }, modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 12.dp))
                }
            }
            val rows = log.mapNotNull { c -> byCode[c.code]?.let { c to it } }
            if (rows.isEmpty()) {
                item(key = "none") { Text("Nothing yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(rows, key = { it.first.id }) { (c, e) ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    LogRow(c, e, m, onOpen = { nav.go(Screen.Recipe(e.code)) }, onNote = { noting = c }, modifier = Modifier.widthIn(max = 720.dp))
                }
            }
        }
    }

    noting?.let { c ->
        var note by remember(c.id) { mutableStateOf(c.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { noting = null },
            title = { Text("Note") },
            text = { OutlinedTextField(note, { if (it.length <= 500) note = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    Kitchen.op("cook-note", mapOf("id" to c.id, "note" to note.trim()))
                    noting = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { noting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LogRow(
    c: Cooked,
    e: IndexEntry,
    m: Memory,
    onOpen: () -> Unit,
    onNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Photo(e.photo, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(shownTitle(e, m), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!c.note.isNullOrBlank()) Text(c.note, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    day(c.at) + (if (c.factor != null && c.factor != 1.0) " · " + factorName(c.factor) else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNote) { Icon(Icons.Default.Edit, "Note", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = { Kitchen.op("uncook", mapOf("code" to c.code, "id" to c.id)) }) {
                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Calendar(
    month: YearMonth,
    log: List<Cooked>,
    byCode: Map<String, IndexEntry>,
    m: Memory,
    onMonth: (YearMonth) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val first = month.atDay(1)
    val start = weekStart(first)
    var cells = (0L until 42L).map { start.plusDays(it) }
    if (cells.drop(35).all { it.month != first.month }) cells = cells.take(35)
    val byDay = log.groupBy { dayOf(it.at) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (0L until 7L).forEach { i ->
                Text(
                    start.plusDays(i).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { d ->
                    val out = d.month != first.month
                    val dishes = byDay[d].orEmpty().mapNotNull { byCode[it.code] }
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.82f)
                            .padding(vertical = 1.5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (out) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerLowest)
                            .then(if (d == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier),
                    ) {
                        val pic = (maxWidth - 6.dp) / 2
                        Column(Modifier.fillMaxSize().padding(3.dp)) {
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (out) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                dishes.take(2).forEach { e ->
                                    Photo(e.photo, Modifier.size(pic).clip(RoundedCornerShape(4.dp)).clickable { onOpen(e.code) })
                                }
                            }
                            if (dishes.size > 2) {
                                Text("+${dishes.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
