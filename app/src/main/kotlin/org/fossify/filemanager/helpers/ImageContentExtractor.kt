package org.fossify.filemanager.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max

/**
 * Extracts a scaled-down Bitmap from image files for AI (vision modality) analysis.
 * Uses [BitmapFactory.Options.inSampleSize] to avoid loading full-resolution camera
 * photos into memory.
 */
object ImageContentExtractor {

    private const val MAX_DIMENSION = 512

    private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Decodes the image file to a Bitmap scaled to at most [MAX_DIMENSION]×[MAX_DIMENSION].
     *
     * @return a scaled Bitmap, or `null` if the file is not a supported image format,
     *         does not exist, or cannot be decoded.
     */
    fun extractBitmapForAi(file: File): Bitmap? {
        val extension = file.extension.lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) return null
        if (!file.exists() || !file.canRead()) return null

        // First pass: decode bounds only to calculate the sample size
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        // Calculate inSampleSize — largest power-of-2 that keeps the longest side >= MAX_DIMENSION
        val sampleSize = calculateSampleSize(width, height)

        // Second pass: decode the actual (down-sampled) bitmap
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    /**
     * Returns `true` if [file] has a supported image extension for AI analysis.
     */
    fun isSupportedImage(file: File): Boolean {
        return file.extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    /**
     * Computes the largest power-of-2 sample size that keeps the decoded image
     * at or above [MAX_DIMENSION] on its longest side.
     */
    internal fun calculateSampleSize(width: Int, height: Int): Int {
        val longestSide = max(width, height)
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
