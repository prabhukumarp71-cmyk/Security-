package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ImageEnhancer applies an unsharp mask filter to boost fine details,
 * text readability, and edge crispness to match or exceed the native Motorola camera app.
 */
object ImageEnhancer {

    suspend fun applyDetailEnhancement(context: Context, uri: Uri, strength: Float = 0.6f) = withContext(Dispatchers.IO) {
        if (strength <= 0.05f) return@withContext
        try {
            val contentResolver = context.contentResolver
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext

            val width = bitmap.width
            val height = bitmap.height

            // High performance direct pixel buffer unsharp mask
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val output = IntArray(width * height)
            val k = strength.coerceIn(0.1f, 1.2f)

            for (y in 1 until height - 1) {
                val rowOffset = y * width
                val prevRowOffset = (y - 1) * width
                val nextRowOffset = (y + 1) * width

                for (x in 1 until width - 1) {
                    val idx = rowOffset + x
                    val c = pixels[idx]
                    val up = pixels[prevRowOffset + x]
                    val down = pixels[nextRowOffset + x]
                    val left = pixels[rowOffset + x - 1]
                    val right = pixels[rowOffset + x + 1]

                    val a = (c ushr 24) and 0xFF

                    // Red channel
                    val rC = (c ushr 16) and 0xFF
                    val rU = (up ushr 16) and 0xFF
                    val rD = (down ushr 16) and 0xFF
                    val rL = (left ushr 16) and 0xFF
                    val rR = (right ushr 16) and 0xFF
                    val rNew = (rC + k * (4 * rC - rU - rD - rL - rR)).toInt().coerceIn(0, 255)

                    // Green channel
                    val gC = (c ushr 8) and 0xFF
                    val gU = (up ushr 8) and 0xFF
                    val gD = (down ushr 8) and 0xFF
                    val gL = (left ushr 8) and 0xFF
                    val gR = (right ushr 8) and 0xFF
                    val gNew = (gC + k * (4 * gC - gU - gD - gL - gR)).toInt().coerceIn(0, 255)

                    // Blue channel
                    val bC = c and 0xFF
                    val bU = up and 0xFF
                    val bD = down and 0xFF
                    val bL = left and 0xFF
                    val bR = right and 0xFF
                    val bNew = (bC + k * (4 * bC - bU - bD - bL - bR)).toInt().coerceIn(0, 255)

                    output[idx] = (a shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                }
            }

            // Copy borders untouched
            System.arraycopy(pixels, 0, output, 0, width)
            System.arraycopy(pixels, (height - 1) * width, output, (height - 1) * width, width)
            for (y in 0 until height) {
                output[y * width] = pixels[y * width]
                output[y * width + width - 1] = pixels[y * width + width - 1]
            }

            val sharpenedBitmap = Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
            bitmap.recycle()

            contentResolver.openOutputStream(uri, "wt")?.use { out ->
                sharpenedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            sharpenedBitmap.recycle()
        } catch (e: OutOfMemoryError) {
            Log.w("ImageEnhancer", "Not enough memory for software unsharp mask, keeping hardware ISP capture", e)
        } catch (e: Exception) {
            Log.e("ImageEnhancer", "Failed to apply detail enhancement", e)
        }
    }
}
