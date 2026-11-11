package xyz.tiresia.matkon.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.tiresia.matkon.data.AddResult
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Offline
import xyz.tiresia.matkon.data.Refused
import xyz.tiresia.matkon.data.SignedOut

/** Add a recipe: a reel or any recipe page by its link (typed here, or shared from another app, then it goes in at
 * once), or photos of a recipe (picked here, or shared). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet() {
    val shared by Kitchen.shared.collectAsStateWithLifecycle()
    val sharedPhotos by Kitchen.sharedPhotos.collectAsStateWithLifecycle()
    if (shared == null && sharedPhotos == null) return
    val text0 = shared.orEmpty()
    val photos0 = sharedPhotos
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val visit = text0 to photos0
    var text by remember(visit) { mutableStateOf(text0) }
    var busy by remember(visit) { mutableStateOf(false) }
    var result by remember(visit) { mutableStateOf<AddResult?>(null) }
    var error by remember(visit) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val close = {
        Kitchen.shared.value = null
        Kitchen.sharedPhotos.value = null
    }
    val go: (suspend () -> AddResult) -> Unit = { call ->
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    result = call()
                } catch (_: Offline) {
                    error = "No connection"
                } catch (_: SignedOut) {
                    error = "Signed out"
                } catch (e: Refused) {
                    error = e.message
                } catch (e: Exception) {
                    error = e.message ?: "Something went wrong."
                } finally {
                    busy = false
                }
            }
        }
    }
    val send = { if (text.isNotBlank()) go { Kitchen.addLink(text) } }
    val sendPhotos = { uris: List<Uri> -> if (uris.isNotEmpty()) go { Kitchen.addPhotos(ctx, uris) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { sendPhotos(it.take(4)) }
    LaunchedEffect(visit) {
        if (photos0 != null) {
            sendPhotos(photos0)
        } else if (text0.isNotBlank()) {
            send()
        }
    }

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
                    Text("Add", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        text,
                        { text = it },
                        label = { Text("Reel or recipe page") },
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
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose photos")
                    }
                }
            }
        }
    }
}
