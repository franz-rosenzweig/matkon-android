package xyz.tiresia.matkon

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.data.Net
import xyz.tiresia.matkon.timer.Timers

class MatkonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Net.init(this)
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader
                .Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory(Net.client)) }
                .diskCache {
                    DiskCache
                        .Builder()
                        .directory(filesDir.resolve("photos").toOkioPath())
                        .maxSizeBytes(300L * 1024 * 1024)
                        .build()
                }.build()
        }
        Kitchen.init(this)
        Timers.init(this)
    }
}
