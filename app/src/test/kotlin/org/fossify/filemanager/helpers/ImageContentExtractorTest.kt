package org.fossify.filemanager.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM-only tests for [ImageContentExtractor.calculateSampleSize] and
 * [ImageContentExtractor.isSupportedImage].
 *
 * Bitmap decoding itself requires the Android framework, so we test only the
 * pure-logic helpers here (sample-size math and extension checking).
 */
class ImageContentExtractorTest {

    // --- calculateSampleSize ---

    @Test
    fun smallImageReturnsNoDownsampling() {
        // 256×256 is below the 512 max — should stay at sampleSize 1
        assertEquals(1, ImageContentExtractor.calculateSampleSize(256, 256))
    }

    @Test
    fun exactMaxDimensionReturnsNoDownsampling() {
        assertEquals(1, ImageContentExtractor.calculateSampleSize(512, 512))
    }

    @Test
    fun slightlyAboveMaxReturnsNoDownsampling() {
        // 800×600 — longest is 800. 800/2 = 400 < 512 so sampleSize stays 1
        assertEquals(1, ImageContentExtractor.calculateSampleSize(800, 600))
    }

    @Test
    fun twelveMegapixelImageIsDownsampled() {
        // 4000×3000 — longest 4000. 4000/2=2000 >=512, 4000/4=1000 >=512, 4000/8=500 <512
        // so sampleSize should be 4
        assertEquals(4, ImageContentExtractor.calculateSampleSize(4000, 3000))
    }

    @Test
    fun veryLargeImageIsHeavilyDownsampled() {
        // 8192×6144 — 8192/2=4096, /4=2048, /8=1024, /16=512 (still >=512), /32=256 <512
        // so sampleSize should be 16 (8192/16 = 512, which meets the MAX_DIMENSION threshold)
        assertEquals(16, ImageContentExtractor.calculateSampleSize(8192, 6144))
    }

    @Test
    fun tallPortraitImageUsesLongestSide() {
        // 1080×1920 — longest 1920. 1920/2=960 >=512, 1920/4=480 <512 → sampleSize 2
        assertEquals(2, ImageContentExtractor.calculateSampleSize(1080, 1920))
    }

    @Test
    fun exactDoubleMaxDimensionReturnsTwo() {
        // 1024×1024 — 1024/2=512 >=512, 1024/4=256 <512 → sampleSize 2
        assertEquals(2, ImageContentExtractor.calculateSampleSize(1024, 1024))
    }

    // --- isSupportedImage ---

    @Test
    fun jpgIsSupported() {
        val file = java.io.File("/tmp/test.jpg")
        assertEquals(true, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun jpegIsSupported() {
        val file = java.io.File("/tmp/test.jpeg")
        assertEquals(true, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun pngIsSupported() {
        val file = java.io.File("/tmp/test.png")
        assertEquals(true, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun webpIsSupported() {
        val file = java.io.File("/tmp/test.webp")
        assertEquals(true, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun uppercaseExtensionIsSupported() {
        val file = java.io.File("/tmp/test.JPG")
        assertEquals(true, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun gifIsNotSupported() {
        val file = java.io.File("/tmp/test.gif")
        assertEquals(false, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun txtIsNotSupported() {
        val file = java.io.File("/tmp/test.txt")
        assertEquals(false, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun pdfIsNotSupported() {
        val file = java.io.File("/tmp/test.pdf")
        assertEquals(false, ImageContentExtractor.isSupportedImage(file))
    }

    @Test
    fun noExtensionIsNotSupported() {
        val file = java.io.File("/tmp/Makefile")
        assertEquals(false, ImageContentExtractor.isSupportedImage(file))
    }
}
