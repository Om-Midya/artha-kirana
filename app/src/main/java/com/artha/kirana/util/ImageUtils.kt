package com.artha.kirana.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Camera + image helpers for the bill/ledger scan slice. All I/O is best-effort and NEVER throws —
 * [uriToBase64]/[decodePreview] return null on any failure. Honors EXIF orientation and accepts a
 * manual [extraRotation] for untagged sideways photos.
 */
object ImageUtils {

    private val counter = AtomicInteger(0)

    /** Empty target file under cacheDir/images + a FileProvider content:// Uri (authority must match the manifest). */
    fun newImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "cap_${counter.incrementAndGet()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun uriToBase64(
        context: Context,
        uri: Uri,
        maxDim: Int = 1568,
        quality: Int = 90,
        extraRotation: Int = 0,
    ): String? {
        val upright = decodeScaledUpright(context, uri, maxDim) ?: run {
            Log.w("ArthaScan", "uriToBase64: decode failed for $uri")
            return null
        }
        val extra = ((extraRotation % 360) + 360) % 360
        val bmp = if (extra != 0) {
            val r = rotate(upright, extra)
            if (r !== upright) upright.recycle()
            r
        } else upright
        return try {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), baos)
            bmp.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w("ArthaScan", "uriToBase64: compress/encode failed", t)
            null
        }
    }

    fun decodePreview(context: Context, uri: Uri, maxDim: Int = 640): Bitmap? =
        decodeScaledUpright(context, uri, maxDim)

    private fun decodeScaledUpright(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        try {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longest = max(info.size.width, info.size.height)
                if (longest > maxDim) {
                    val ratio = maxDim.toFloat() / longest
                    decoder.setTargetSize(
                        max(1, (info.size.width * ratio).toInt()),
                        max(1, (info.size.height * ratio).toInt()),
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w("ArthaScan", "ImageDecoder failed for $uri, trying BitmapFactory", t)
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            var bmp = scaleToMaxDim(decoded, maxDim)
            if (bmp !== decoded) decoded.recycle()
            val deg = exifDegrees(context, uri)
            if (deg != 0) {
                val r = rotate(bmp, deg)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            bmp
        } catch (t: Throwable) {
            Log.w("ArthaScan", "BitmapFactory fallback also failed for $uri", t)
            null
        }
    }

    private fun exifDegrees(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Throwable) {
        0
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= maxDim) sample *= 2
        return sample
    }

    private fun scaleToMaxDim(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * ratio).toInt()),
            max(1, (src.height * ratio).toInt()),
            true,
        )
    }
}
