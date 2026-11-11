package xyz.tiresia.matkon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.Edits
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Offline
import xyz.tiresia.matkon.data.Recipe

private class EdIng(
    amount: String,
    item: String,
    note: String,
) {
    var amount by mutableStateOf(amount)
    var item by mutableStateOf(item)
    var note by mutableStateOf(note)
}

private class EdGroup(
    name: String,
    items: List<EdIng>,
) {
    var name by mutableStateOf(name)
    val items = mutableStateListOf<EdIng>().apply { addAll(items.ifEmpty { listOf(EdIng("", "", "")) }) }
}

private class EdStep(
    text: String,
    val orig: String?,
    val timers: List<Int>,
) {
    var text by mutableStateOf(text)
}

/** The whole recipe as a form, from the recipe as it is now. */
private class Form(r: Recipe) {
    var title by mutableStateOf(r.title.orEmpty())
    var summary by mutableStateOf(r.summary.orEmpty())
    var servings by mutableStateOf(r.servings.orEmpty())
    var prep by mutableStateOf(r.time?.prep.orEmpty())
    var cook by mutableStateOf(r.time?.cook.orEmpty())
    var total by mutableStateOf(r.time?.total.orEmpty())
    var oven by mutableStateOf(r.oven.orEmpty())
    val groups =
        mutableStateListOf<EdGroup>().apply {
            val gs = r.groups.map { g -> EdGroup(g.name.orEmpty(), g.items.orEmpty().map { EdIng(it.amount.orEmpty(), it.item.orEmpty(), it.note.orEmpty()) }) }
            addAll(gs.ifEmpty { listOf(EdGroup("", emptyList())) })
        }
    val steps =
        mutableStateListOf<EdStep>().apply {
            val ss = r.steps.orEmpty().map { EdStep(it.text.orEmpty(), it.text.orEmpty(), it.timers.orEmpty()) }
            addAll(ss.ifEmpty { listOf(EdStep("", null, emptyList())) })
        }
    var tips by mutableStateOf(r.tips.orEmpty().joinToString("\n"))
    var missing by mutableStateOf(r.missing.orEmpty().joinToString("\n"))
    val edited = r.edited != null

    fun body(): Map<String, Any?> =
        Edits.recipe(
            title, summary, servings, prep, cook, total, oven,
            groups.map { g -> Edits.Group(g.name, g.items.map { Edits.Ing(it.amount, it.item, it.note) }) },
            steps.map { Edits.StepIn(it.text, it.orig, it.timers) },
            tips, missing,
        )
}

/** Fixing a recipe (when this account may: it added the recipe, or it's the kitchen's owner). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    code: String,
    lang: String,
) {
    val nav = LocalNav.current
    val l = labelsFor(lang)
    var form by remember(code, lang) { mutableStateOf<Form?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reverting by remember { mutableStateOf(false) }
    var titleMissing by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(code, lang) {
        val r =
            try {
                Kitchen.fetchRecipe(code, lang)
            } catch (_: Exception) {
                Kitchen.cachedRecipe(code, lang)
            }
        if (r != null) form = Form(r)
    }
    val save = { body: Map<String, Any?> ->
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    Kitchen.saveEdit(code, lang, body)
                    nav.back()
                } catch (_: Offline) {
                    snack.showSnackbar("No connection")
                } catch (e: Exception) {
                    snack.showSnackbar(e.message ?: "Not saved")
                } finally {
                    busy = false
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (lang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Scaffold(
            topBar = {
                Bar("", onBack = { nav.back() }) {
                    val f = form
                    if (f != null && f.edited) TextButton(onClick = { reverting = true }, enabled = !busy) { Text(l.revert) }
                    Button(
                        onClick = {
                            val f2 = form ?: return@Button
                            if (f2.title.isBlank()) {
                                titleMissing = true
                            } else {
                                save(mapOf("recipe" to f2.body()))
                            }
                        },
                        enabled = !busy && form != null,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(l.save) }
                }
            },
            snackbarHost = { SnackbarHost(snack) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
        ) { pad ->
            val f = form
            if (f == null) {
                Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
                val roomy = maxWidth >= 560.dp
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.widthIn(max = 820.dp)) {
                        OutlinedTextField(
                            f.title,
                            {
                                f.title = it.take(200)
                                titleMissing = false
                            },
                            label = { Text(l.title) },
                            isError = titleMissing,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(f.summary, { f.summary = it }, label = { Text(l.summary) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val small = Modifier.width(if (roomy) 150.dp else 160.dp)
                            OutlinedTextField(f.servings, { f.servings = it.take(100) }, label = { Text(l.servings) }, singleLine = true, modifier = small)
                            OutlinedTextField(f.prep, { f.prep = it.take(60) }, label = { Text(l.prep) }, singleLine = true, modifier = small)
                            OutlinedTextField(f.cook, { f.cook = it.take(60) }, label = { Text(l.cook) }, singleLine = true, modifier = small)
                            OutlinedTextField(f.total, { f.total = it.take(60) }, label = { Text(l.total) }, singleLine = true, modifier = small)
                            OutlinedTextField(f.oven, { f.oven = it.take(100) }, label = { Text(l.oven) }, singleLine = true, modifier = small)
                        }

                        Head(l.ing)
                        f.groups.forEach { g -> key(g) { GroupEditor(g, l, roomy, onDelete = { f.groups.remove(g) }) } }
                        TextButton(onClick = { f.groups.add(EdGroup("", emptyList())) }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(l.addGroup)
                        }

                        Head(l.steps)
                        f.steps.forEachIndexed { i, s ->
                            key(s) {
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.padding(top = 14.dp).size(28.dp).clip(CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(6.dp))
                                OutlinedTextField(s.text, { s.text = it.take(2000) }, minLines = 2, modifier = Modifier.weight(1f))
                                IconButton(onClick = { f.steps.remove(s) }, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.Default.Close, "Remove") }
                            }
                            }
                        }
                        TextButton(onClick = { f.steps.add(EdStep("", null, emptyList())) }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(l.addStep)
                        }

                        Head(l.tips)
                        OutlinedTextField(f.tips, { f.tips = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Head(l.miss)
                        OutlinedTextField(f.missing, { f.missing = it }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    if (reverting) {
        AlertDialog(
            onDismissRequest = { reverting = false },
            title = { Text(l.revert + "?") },
            confirmButton = {
                TextButton(onClick = {
                    reverting = false
                    save(mapOf("revert" to true))
                }) { Text(l.revert) }
            },
            dismissButton = { TextButton(onClick = { reverting = false }) { Text(l.cancel) } },
        )
    }
}

@Composable
private fun Head(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

@Composable
private fun GroupEditor(
    g: EdGroup,
    l: Labels,
    roomy: Boolean,
    onDelete: () -> Unit,
) {
    val style = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(g.name, { g.name = it.take(100) }, placeholder = { Text(l.group) }, singleLine = true, textStyle = style, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remove") }
        }
        g.items.forEach { i ->
            key(i) {
            val amount = @Composable { m: Modifier ->
                OutlinedTextField(i.amount, { i.amount = it.take(100) }, placeholder = { Text(l.amount) }, singleLine = true, textStyle = style, modifier = m)
            }
            val item = @Composable { m: Modifier ->
                OutlinedTextField(i.item, { i.item = it.take(200) }, placeholder = { Text(l.item) }, singleLine = true, textStyle = style, modifier = m)
            }
            val note = @Composable { m: Modifier ->
                OutlinedTextField(i.note, { i.note = it.take(200) }, placeholder = { Text(l.note) }, singleLine = true, textStyle = style, modifier = m)
            }
            if (roomy) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    amount(Modifier.weight(0.24f))
                    item(Modifier.weight(0.44f))
                    note(Modifier.weight(0.32f))
                    IconButton(onClick = { g.items.remove(i) }) { Icon(Icons.Default.Close, "Remove") }
                }
            } else {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        amount(Modifier.weight(0.36f))
                        item(Modifier.weight(0.64f))
                        IconButton(onClick = { g.items.remove(i) }) { Icon(Icons.Default.Close, "Remove") }
                    }
                    note(Modifier.padding(top = 4.dp, end = 54.dp).fillMaxWidth())
                }
            }
            }
        }
        TextButton(onClick = { g.items.add(EdIng("", "", "")) }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(l.addIng)
        }
    }
}
