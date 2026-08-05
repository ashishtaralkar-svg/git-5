package com.hdfc.docupload.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Down-scales and re-encodes images before upload while keeping them readable.
 * PDFs are passed through untouched.
 */
@Singleton
class ImageCompressor @Inject constructor() {

    /** Returns a compressed copy, or the original file if compression is not applicable. */
    fun compress(source: File, mimeType: String): File {
        if (!mimeType.startsWith("image/")) return source

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return source

        val sampleSize = calculateInSampleSize(longest, Constants.MAX_IMAGE_DIMENSION)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = BitmapFactory.decodeFile(source.absolutePath, decodeOptions) ?: return source
        bitmap = applyExifRotation(source, bitmap)

        val target = File(source.parentFile, "cmp_${source.name}")
        target.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, Constants.JPEG_QUALITY, out)
        }
        bitmap.recycle()
        return target
    }

    private fun calculateInSampleSize(longestEdge: Int, maxDimension: Int): Int {
        var sample = 1
        var edge = longestEdge
        while (edge / 2 >= maxDimension) {
            edge /= 2
            sample *= 2
        }
        return sample
    }

    private fun applyExifRotation(source: File, bitmap: Bitmap): Bitmap {
        return runCatching {
            val exif = ExifInterface(source.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (degrees == 0) return bitmap
            val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    @Suppress("unused")
    private fun Int.scaledBy(factor: Float): Int = (this * factor).roundToInt()
}
