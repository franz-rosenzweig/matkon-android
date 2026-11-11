package xyz.tiresia.matkon.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import xyz.tiresia.matkon.data.Net

/** The reel's own video, from Matkon (with the sign-in), with the usual controls. */
@OptIn(UnstableApi::class)
@Composable
fun ReelVideo(
    path: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val player =
        remember(path) {
            ExoPlayer
                .Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Net.client)))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Net.url(path)))
                    prepare()
                    playWhenReady = true
                }
        }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = modifier,
    )
}
