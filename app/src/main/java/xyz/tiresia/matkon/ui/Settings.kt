package xyz.tiresia.matkon.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tiresia.matkon.BuildConfig
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Net

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    end: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        end?.invoke()
    }
}

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val me by Kitchen.me.collectAsStateWithLifecycle()
    val m by Kitchen.memory.collectAsStateWithLifecycle()
    val stored by Kitchen.stored.collectAsStateWithLifecycle()
    val syncing by Kitchen.syncing.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf(false) }
    var allowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    Scaffold(
        topBar = { Bar("Settings", onBack = if (nav.stack.size > 1) ({ nav.back() }) else null) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.widthIn(max = 720.dp)) {
                Group("Account")
                Item("Name", me?.account?.name ?: Net.username)
                Item("Server", Net.server.removePrefix("https://").removePrefix("http://"))
                Group("Recipes")
                Item("Hebrew recipes in English", onClick = { Kitchen.op("setting", mapOf("key" to "english", "value" to !m.english())) }) {
                    Switch(m.english(), { Kitchen.op("setting", mapOf("key" to "english", "value" to it)) })
                }
                Item("Calories and protein", onClick = { Kitchen.op("setting", mapOf("key" to "nutrition", "value" to !m.nutrition())) }) {
                    Switch(m.nutrition(), { Kitchen.op("setting", mapOf("key" to "nutrition", "value" to it)) })
                }
                Group("This device")
                Item("Saved for offline", "$stored")
                Item("Refresh", if (syncing) "…" else null, onClick = { Kitchen.refresh() })
                if (!allowed) {
                    Item("Timer alarms", onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        TextButton(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
                    }
                }
                Group("About")
                Item("Version", BuildConfig.VERSION_NAME)
                Item("Source", "github.com/franz-rosenzweig/matkon-android", onClick = { openLink(ctx, "https://github.com/franz-rosenzweig/matkon-android") })
                Item("Built on", "Nextcloud Cookbook for Android", onClick = { openLink(ctx, "https://github.com/lneugebauer/nextcloud-cookbook") })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { leaving = true }, modifier = Modifier.padding(16.dp)) { Text("Sign out") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    Kitchen.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
        )
    }
}
