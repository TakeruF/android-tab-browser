package com.takeruf.nagi.browser.newtab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Imports a static, bounded copy once; the picker URI never needs a persistent grant. */
class NewTabPhotoStore(private val context: Context) {
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { requireNotNull(it); BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri).use {
            requireNotNull(it)
            requireNotNull(BitmapFactory.decodeStream(it, null, options)) { "Unsupported image" }
        }
        var output: Bitmap? = null
        var oriented: Bitmap? = null
        val name = "${UUID.randomUUID()}.jpg"
        val destination = file(context, name)!!
        try {
            val orientation = resolver.openInputStream(uri).use { stream ->
                if (stream == null) ExifInterface.ORIENTATION_NORMAL else runCatching {
                    ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
                val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
                postScale(scale, scale)
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            output = createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
            Canvas(output).apply { drawColor(Color.WHITE); drawBitmap(oriented, 0f, 0f, null) }
            check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
            destination.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
            name
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            output?.recycle()
            if (oriented !== decoded) oriented?.recycle()
            decoded.recycle()
        }
    }

    fun delete(name: String?) { file(context, name)?.delete() }

    companion object {
        const val MAX_EDGE = 1920
        // Decode near the final dimensions instead of allocating the full camera image.
        internal fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            while (maxOf(width, height) / sample > MAX_EDGE) sample *= 2
            return sample
        }
        fun file(context: Context, name: String?): File? = name
            ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}\\.jpg")) }
            ?.let { File(context.filesDir, "new-tab-backgrounds/$it") }
    }
}
