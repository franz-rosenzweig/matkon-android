package xyz.tiresia.matkon.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A phone photo, down to 2000 px on its long side and a JPEG, before it goes up (as the web does it). */
object Photos {
    private const val MAX = 2000

    fun jpegDataUrl(
        ctx: Context,
        uri: Uri,
    ): String {
        val bmp = decode(ctx, uri)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decode(
        ctx: Context,
        uri: Uri,
    ): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder turns the photo the way the camera held it
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val k = min(1.0, MAX.toDouble() / max(w, h))
                if (k < 1) dec.setTargetSize((w * k).roundToInt().coerceAtLeast(1), (h * k).roundToInt().coerceAtLeast(1))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val res = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        res.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX) sample *= 2
        val raw =
            res.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: throw IOException("Not a photo")
        val k = min(1.0, MAX.toDouble() / max(raw.width, raw.height))
        val turn =
            try {
                res.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) {
                0f
            }
        if (k >= 1 && turn == 0f) return raw
        val m = Matrix().apply {
            postScale(k.toFloat(), k.toFloat())
            postRotate(turn)
        }
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out !== raw) raw.recycle()
        return out
    }
}
