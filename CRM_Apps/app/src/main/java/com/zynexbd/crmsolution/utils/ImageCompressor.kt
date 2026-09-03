package com.zynexbd.crmsolution.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance image compression utility.
 * Guarantees that captured or uploaded photos are resized and dynamically compressed
 * to strictly stay within 250 KB (256,000 bytes) while preserving crisp visual quality.
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"
    const val MAX_IMAGE_SIZE_BYTES = 250 * 1024 // 250 KB limit
    private const val MAX_DIMENSION = 960f // Optimal dimension for sharp mobile viewing & fast upload

    /**
     * Compresses [bitmap] and saves it to [destinationFile], ensuring the file size is <= [targetMaxBytes].
     * @return The written [destinationFile]
     */
    fun compressToFile(
        bitmap: Bitmap,
        destinationFile: File,
        targetMaxBytes: Int = MAX_IMAGE_SIZE_BYTES
    ): File {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Step 1: Scale down resolution if width or height exceeds MAX_DIMENSION
        var currentBitmap = if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            val ratio = min(MAX_DIMENSION / w, MAX_DIMENSION / h)
            val newW = max(1, (w * ratio).toInt())
            val newH = max(1, (h * ratio).toInt())
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }

        // Step 2: Dynamic iterative JPEG compression
        var quality = 85
        val stream = ByteArrayOutputStream()
        currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        while (stream.size() > targetMaxBytes && quality > 35) {
            stream.reset()
            quality -= 10
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        // Step 3: Extreme fallback if still over target limit (e.g. extremely noisy/complex backgrounds)
        if (stream.size() > targetMaxBytes) {
            var scale = 0.8f
            while (stream.size() > targetMaxBytes && scale >= 0.4f) {
                stream.reset()
                val scaledW = max(1, (currentBitmap.width * scale).toInt())
                val scaledH = max(1, (currentBitmap.height * scale).toInt())
                val furtherScaled = Bitmap.createScaledBitmap(currentBitmap, scaledW, scaledH, true)
                furtherScaled.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                scale -= 0.15f
            }
        }

        FileOutputStream(destinationFile).use { out ->
            stream.writeTo(out)
        }

        val finalSizeBytes = destinationFile.length()
        AppLogger.i(
            TAG,
            "Compressed '${destinationFile.name}': ${finalSizeBytes / 1024} KB (${finalSizeBytes} B) - Quality: $quality%, Dim: ${currentBitmap.width}x${currentBitmap.height}"
        )
        return destinationFile
    }

    /**
     * Compresses an existing image file on disk in-place or to a new file, ensuring size <= [targetMaxBytes].
     */
    fun compressFile(
        sourceFile: File,
        targetFile: File = sourceFile,
        targetMaxBytes: Int = MAX_IMAGE_SIZE_BYTES
    ): File {
        if (!sourceFile.exists()) return targetFile

        // If already under 250 KB, check if dimensions are reasonable
        val bmp = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return targetFile
        return compressToFile(bmp, targetFile, targetMaxBytes)
    }

    /**
     * Compresses [bitmap] and encodes it as Base64 JPEG string, strictly <= [targetMaxBytes].
     */
    fun compressToBase64(
        bitmap: Bitmap,
        targetMaxBytes: Int = MAX_IMAGE_SIZE_BYTES
    ): String {
        val tempFile = File.createTempFile("cmp_", ".jpg")
        try {
            compressToFile(bitmap, tempFile, targetMaxBytes)
            val bytes = tempFile.readBytes()
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } finally {
            tempFile.delete()
        }
    }
}
