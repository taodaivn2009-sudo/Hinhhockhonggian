package com.example.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Thread-safe, memory-bounded native Android [PdfRenderer] engine for the bundled 388-page PDF.
 * - Lazily extracts the asset once into app cache using an atomic rename so [ParcelFileDescriptor]
 *   always opens reliably regardless of APK asset compression.
 * - Serializes all [PdfRenderer] page open/render/close calls via [Mutex] on [Dispatchers.IO].
 * - Caches recently rendered bitmaps inside a ~36 MB [LruCache] for smooth 60fps scrolling without OOM.
 */
class PdfBookRenderer(
    private val appContext: Context,
    private val assetPath: String = PDF_ASSET_PATH,
    private val cacheFileName: String = "toan_10_11_12_dpad_v1.pdf"
) {
    private val mutex = Mutex()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var cachedPageCount: Int = 0
    private var defaultAspectRatio: Float = 210f / 297f
    private var isClosed: Boolean = false

    // Cap decoded PDF page cache at ~36 MB so 388 pages never trigger OutOfMemoryError.
    private val bitmapCache = object : LruCache<String, Bitmap>(36 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    val pageCount: Int get() = if (cachedPageCount > 0) cachedPageCount else PDF_TOTAL_PAGES

    val aspectRatio: Float get() = defaultAspectRatio

    suspend fun open(): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isClosed) return@withContext Result.failure(IllegalStateException("Trình đọc PDF đã đóng"))
            if (renderer != null) return@withContext Result.success(cachedPageCount)

            runCatching {
                val cachedFile = ensureCachedPdfFile()
                val pfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                renderer = pdfRenderer
                cachedPageCount = pdfRenderer.pageCount
                if (cachedPageCount > 0) {
                    pdfRenderer.openPage(0).use { first ->
                        if (first.height > 0) {
                            defaultAspectRatio = (first.width.toFloat() / first.height.toFloat()).coerceIn(0.45f, 2.2f)
                        }
                    }
                }
                cachedPageCount
            }
        }
    }

    /**
     * Renders a 0-based page index at [targetWidthPx] (bucketed for clean cache reuse).
     */
    suspend fun renderPage(pageIndex0Based: Int, targetWidthPx: Int): Bitmap? {
        val bucketWidth = bucketizeWidth(targetWidthPx)
        val cacheKey = "${pageIndex0Based}_$bucketWidth"
        synchronized(bitmapCache) {
            bitmapCache.get(cacheKey)?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (isClosed) return@withLock null
                synchronized(bitmapCache) {
                    bitmapCache.get(cacheKey)?.let { return@withLock it }
                }

                val activeRenderer = renderer ?: run {
                    val cachedFile = ensureCachedPdfFile()
                    val pfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(pfd)
                    fileDescriptor = pfd
                    renderer = pdfRenderer
                    cachedPageCount = pdfRenderer.pageCount
                    pdfRenderer
                }

                if (pageIndex0Based !in 0 until activeRenderer.pageCount) return@withLock null

                runCatching {
                    activeRenderer.openPage(pageIndex0Based).use { page ->
                        val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else (297f / 210f)
                        val width = bucketWidth.coerceIn(480, 1800)
                        val height = (width * ratio).roundToInt().coerceIn(480, 2800)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        synchronized(bitmapCache) {
                            bitmapCache.put(cacheKey, bitmap)
                        }
                        bitmap
                    }
                }.getOrNull()
            }
        }
    }

    fun getCachedPage(pageIndex0Based: Int, targetWidthPx: Int): Bitmap? {
        val cacheKey = "${pageIndex0Based}_${bucketizeWidth(targetWidthPx)}"
        return synchronized(bitmapCache) { bitmapCache.get(cacheKey) }
    }

    fun close() {
        isClosed = true
        synchronized(bitmapCache) {
            bitmapCache.evictAll()
        }
        runCatching { renderer?.close() }
        runCatching { fileDescriptor?.close() }
        renderer = null
        fileDescriptor = null
    }

    private fun bucketizeWidth(rawWidthPx: Int): Int {
        val clamped = rawWidthPx.coerceIn(640, 1600)
        return ((clamped + 159) / 160) * 160
    }

    private fun ensureCachedPdfFile(): File {
        val target = File(appContext.cacheDir, cacheFileName)
        if (target.exists() && target.length() > 100_000L) {
            return target
        }
        val tmp = File(appContext.cacheDir, "$cacheFileName.tmp")
        appContext.assets.open(assetPath).use { input ->
            FileOutputStream(tmp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return target
    }
}
