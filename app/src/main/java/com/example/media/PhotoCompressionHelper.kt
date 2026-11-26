package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object PhotoCompressionHelper {

    suspend fun compressUriToBase64(context: Context, uri: Uri, maxDim: Int = 720, quality: Int = 68): String? =
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val rawBytes = input.use { it.readBytes() }
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@withContext null
                val rotated = try {
                    val exif = ExifInterface(rawBytes.inputStream())
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotateBitmapIfNeeded(decoded, orientation)
                } catch (_: Exception) {
                    decoded
                }
                encodeBitmapToBase64(rotated, maxDim, quality)
            } catch (_: Exception) {
                null
            }
        }

    suspend fun compressFileToBase64(file: File, maxDim: Int = 720, quality: Int = 68): String? =
        withContext(Dispatchers.IO) {
            try {
                val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                val rotated = try {
                    val exif = ExifInterface(file.absolutePath)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotateBitmapIfNeeded(decoded, orientation)
                } catch (_: Exception) {
                    decoded
                }
                encodeBitmapToBase64(rotated, maxDim, quality)
            } catch (_: Exception) {
                null
            }
        }

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap, maxDim: Int, quality: Int): String {
        val ratio = minOf(
            maxDim.toFloat() / bitmap.width.coerceAtLeast(1),
            maxDim.toFloat() / bitmap.height.coerceAtLeast(1),
            1f
        )
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        var currentQuality = quality
        scaled.compress(Bitmap.CompressFormat.JPEG, currentQuality, out)
        while (out.size() > 320_000 && currentQuality > 35) {
            out.reset()
            currentQuality -= 10
            scaled.compress(Bitmap.CompressFormat.JPEG, currentQuality, out)
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
