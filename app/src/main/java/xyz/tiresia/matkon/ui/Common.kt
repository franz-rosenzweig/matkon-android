package xyz.tiresia.matkon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import xyz.tiresia.matkon.data.IndexEntry
import xyz.tiresia.matkon.data.Memory
import xyz.tiresia.matkon.data.Net
import xyz.tiresia.matkon.timer.RunningTimer
import xyz.tiresia.matkon.timer.Timers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun wide() = LocalConfiguration.current.screenWidthDp >= 600

@Composable
fun landscape() = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

fun shownTitle(
    e: IndexEntry,
    m: Memory,
) = (if (m.english() && e.title_en != null) e.title_en else e.title) ?: e.code

@Composable
fun Photo(
    path: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (path != null) {
            AsyncImage(
                model = Net.url(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun RecipeCard(
    e: IndexEntry,
    m: Memory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    under: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Column {
            Box {
                Photo(e.photo, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                Marks(e.code, m, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    shownTitle(e, m),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val by = under ?: e.user?.let { "@$it" }
                if (!by.isNullOrBlank()) {
                    Text(
                        by,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun Marks(
    code: String,
    m: Memory,
    modifier: Modifier = Modifier,
) {
    val saved = m.saved.orEmpty().containsKey(code)
    val made = (m.cooked_count.orEmpty()[code] ?: 0) > 0
    val rating = m.ratings.orEmpty()[code]
    if (!saved && !made && rating == null) return
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f)).padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saved) Icon(Icons.Default.Bookmark, "Saved", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
        if (made) Icon(Icons.Default.Check, "Made", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.done)
        if (rating != null) Text("$rating", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun now(): Long {
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            t = System.currentTimeMillis()
        }
    }
    return t
}

/** The running (and ringing) timers, over whatever screen is open. */
@Composable
fun TimersBar(
    running: List<RunningTimer>,
    ringing: List<RunningTimer>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (running.isEmpty() && ringing.isEmpty()) return
    val ctx = LocalContext.current
    val t = now()
    Column(modifier) {
        (ringing + running).forEach { r ->
            val up = r in ringing
            Surface(
                color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (up) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(r.code) },
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (up) "Time's up" else clock(r.endAt - t),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        r.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { Timers.stop(ctx, r.id) }) { Icon(Icons.Default.Close, "Stop") }
                }
            }
        }
    }
}

// ---- days ----

fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong()) // weeks start on Sunday

fun dayName(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
}

fun shortDate(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth

