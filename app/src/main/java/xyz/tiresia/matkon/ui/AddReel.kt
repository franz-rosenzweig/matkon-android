package xyz.tiresia.matkon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.AddResult
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Offline
import xyz.tiresia.matkon.data.Refused

/** Add a reel: pasted here, or shared from Instagram (then it goes in at once). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReelSheet() {
    val shared by Kitchen.shared.collectAsStateWithLifecycle()
    val text0 = shared ?: return
    val nav = LocalNav.current
    var text by remember(text0) { mutableStateOf(text0) }
    var busy by remember(text0) { mutableStateOf(false) }
    var result by remember(text0) { mutableStateOf<AddResult?>(null) }
    var error by remember(text0) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val close = { Kitchen.shared.value = null }
    val send: () -> Unit = {
        if (text.isNotBlank() && !busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    result = Kitchen.addReel(text)
                } catch (_: Offline) {
                    error = "No connection"
                } catch (e: Refused) {
                    error = if (e.status == 400) "Not a reel link" else e.message
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
    }
    LaunchedEffect(text0) { if (text0.isNotBlank()) send() }

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            val r = result
            when {
                r != null -> {
                    Text(
                        when {
                            r.done == true -> "Already in Matkon"
                            r.new == false -> "Already on the way"
                            else -> "Added"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (r.done == true && r.code != null) {
                            Button(onClick = {
                                close()
                                nav.go(Screen.Recipe(r.code))
                            }) { Text("Open") }
                        }
                        OutlinedButton(onClick = close) { Text("Done") }
                    }
                }
                else -> {
                    Text("Add a reel", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        text,
                        { text = it },
                        placeholder = { Text("instagram.com/reel/…") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { send() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = send, enabled = !busy && text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(if (busy) "Adding…" else "Add")
                    }
                }
            }
        }
    }
}
