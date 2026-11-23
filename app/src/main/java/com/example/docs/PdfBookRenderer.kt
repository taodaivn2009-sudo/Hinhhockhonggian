package com.example.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Immutable
data class CachedPdfPage(
    val bitmap: Bitmap,
    val imageBitmap: ImageBitmap,
    val aspectRatio: Float
)

/**
 * Thread-safe, memory-bounded native Android [PdfRenderer] engine for the bundled 388-page PDF.
 * - Renders pages into Android-required [Bitmap.Config.ARGB_8888] bitmaps serialized via [Mutex].
 * - Pre-wraps [ImageBitmap] once per decoded page to avoid repeated conversions during recomposition.
 * - Caches decoded pages inside a ~48 MB [LruCache] with background neighbor prefetching.
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

    // Cap decoded PDF page cache at ~48 MB for instant 60fps scrolling without OOM.
    private val pageCache = object : LruCache<String, CachedPdfPage>(48 * 1024) {
        override fun sizeOf(key: String, value: CachedPdfPage): Int =
            (value.bitmap.allocationByteCount / 1024).coerceAtLeast(1)
    }

    val pageCount: Int get() = if (cachedPageCount > 0) cachedPageCount else PDF_TOTAL_PAGES

    val aspectRatio: Float get() = defaultAspectRatio

    val isOpen: Boolean get() = renderer != null && !isClosed

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
     * Renders a 0-based page index at [targetWidthPx] into a pre-wrapped [CachedPdfPage].
     */
    suspend fun renderPageAsset(pageIndex0Based: Int, targetWidthPx: Int): CachedPdfPage? {
        val bucketWidth = bucketizeWidth(targetWidthPx)
        val cacheKey = "${pageIndex0Based}_$bucketWidth"
        synchronized(pageCache) {
            pageCache.get(cacheKey)?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (isClosed) return@withLock null
                synchronized(pageCache) {
                    pageCache.get(cacheKey)?.let { return@withLock it }
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
                        val width = bucketWidth.coerceIn(480, 1440)
                        val height = (width * ratio).roundToInt().coerceIn(480, 2400)
                        // Android PdfRenderer.Page.render strictly requires Bitmap.Config.ARGB_8888
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val aspect = if (height > 0) (width.toFloat() / height.toFloat()).coerceIn(0.45f, 2.2f) else defaultAspectRatio
                        val asset = CachedPdfPage(
                            bitmap = bitmap,
                            imageBitmap = bitmap.asImageBitmap(),
                            aspectRatio = aspect
                        )
                        synchronized(pageCache) {
                            pageCache.put(cacheKey, asset)
                        }
                        asset
                    }
                }.getOrNull()
            }
        }
    }

    /**
     * Backwards-compatible raw [Bitmap] render API (used by AI page vision attachment).
     */
    suspend fun renderPage(pageIndex0Based: Int, targetWidthPx: Int): Bitmap? =
        renderPageAsset(pageIndex0Based, targetWidthPx)?.bitmap

    fun getCachedPageAsset(pageIndex0Based: Int, targetWidthPx: Int): CachedPdfPage? {
        val cacheKey = "${pageIndex0Based}_${bucketizeWidth(targetWidthPx)}"
        return synchronized(pageCache) { pageCache.get(cacheKey) }
    }

    fun getCachedPage(pageIndex0Based: Int, targetWidthPx: Int): Bitmap? =
        getCachedPageAsset(pageIndex0Based, targetWidthPx)?.bitmap

    /**
     * Opportunistically prefetches adjacent pages on [Dispatchers.IO] so turning pages is instant.
     */
    suspend fun prefetchPages(pageIndices: List<Int>, targetWidthPx: Int) {
        val bucketWidth = bucketizeWidth(targetWidthPx)
        for (idx in pageIndices) {
            if (isClosed) return
            if (idx !in 0 until pageCount) continue
            val cacheKey = "${idx}_$bucketWidth"
            val alreadyCached = synchronized(pageCache) { pageCache.get(cacheKey) != null }
            if (!alreadyCached) {
                renderPageAsset(idx, targetWidthPx)
            }
        }
    }

    fun close() {
        isClosed = true
        synchronized(pageCache) {
            pageCache.evictAll()
        }
        runCatching { renderer?.close() }
        runCatching { fileDescriptor?.close() }
        renderer = null
        fileDescriptor = null
    }

    private fun bucketizeWidth(rawWidthPx: Int): Int {
        val clamped = rawWidthPx.coerceIn(640, 1440)
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
