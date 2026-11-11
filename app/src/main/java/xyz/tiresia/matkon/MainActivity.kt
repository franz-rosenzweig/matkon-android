package xyz.tiresia.matkon

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.ui.MatkonRoot
import xyz.tiresia.matkon.ui.opening

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { MatkonRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            // a reel shared from Instagram, or a recipe page from the browser: the text holds its link
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { Kitchen.shared.value = it }
        }
        if ((intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) && intent.type?.startsWith("image/") == true) {
            // photos of a recipe, shared from the gallery or the camera
            val uris =
                if (intent.action == Intent.ACTION_SEND) {
                    listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                } else {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                }
            if (uris.isNotEmpty()) Kitchen.sharedPhotos.value = uris.take(4)
        }
        intent.getStringExtra("cook")?.let { opening.value = it }
    }
}
