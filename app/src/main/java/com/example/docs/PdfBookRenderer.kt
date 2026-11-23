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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

@Immutable
data class CachedPdfPage(
    val bitmap: Bitmap,
    val imageBitmap: ImageBitmap,
    val aspectRatio: Float,
    val isHd: Boolean = true
)

/**
 * Progressive 2-Stage Priority native Android [PdfRenderer] engine for the bundled 388-page PDF:
 * - Stage 1 (Fast Preview ~360px wide): ~12x fewer pixels -> renders in ~15–25ms so pages appear
 *   immediately while scrolling instead of blocking behind a spinner. Stored in a dedicated 10 MB
 *   preview LRU cache (~30–40 pages instant).
 * - Stage 2 (Crisp HD ~1080–1280px wide): Seamlessly sharpens the already-visible page in-place.
 *   Automatically yields to any waiting Stage-1 preview via [pendingPreviews] so fast scrolling
 *   never stalls behind off-screen HD renders.
 * - Always renders with Android-required [Bitmap.Config.ARGB_8888].
 */
class PdfBookRenderer(
    private val appContext: Context,
    private val assetPath: String = PDF_ASSET_PATH,
    private val cacheFileName: String = "toan_10_11_12_dpad_v1.pdf"
) {
    companion object {
        const val PREVIEW_WIDTH_PX = 360
    }

    private val mutex = Mutex()
    private val pendingPreviews = AtomicInteger(0)
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var cachedPageCount: Int = 0
    private var defaultAspectRatio: Float = 210f / 297f
    private var isClosed: Boolean = false

    // Fast Stage-1 preview cache (~10 MB -> holds ~30–40 preview pages for instant scrollback)
    private val previewCache = object : LruCache<Int, CachedPdfPage>(10 * 1024) {
        override fun sizeOf(key: Int, value: CachedPdfPage): Int =
            (value.bitmap.allocationByteCount / 1024).coerceAtLeast(1)
    }

    // Crisp Stage-2 HD cache (~44 MB -> holds ~6–8 full sharp pages without OOM)
    private val hdCache = object : LruCache<String, CachedPdfPage>(44 * 1024) {
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
     * Returns the best immediately available cached asset (HD preferred, else Fast Preview) in 0ms.
     */
    fun getBestCachedPageAsset(pageIndex0Based: Int, hdWidthPx: Int): CachedPdfPage? {
        val hdKey = "${pageIndex0Based}_${bucketizeWidth(hdWidthPx)}"
        synchronized(hdCache) {
            hdCache.get(hdKey)?.let { return it }
        }
        synchronized(previewCache) {
            previewCache.get(pageIndex0Based)?.let { return it }
        }
        return null
    }

    /**
     * Stage 1: High-priority fast preview (~360px wide, ~15–25ms).
     * Always preempts slower Stage-2 HD / prefetch work so visible pages appear immediately!
     */
    suspend fun renderPreviewPageAsset(pageIndex0Based: Int): CachedPdfPage? {
        synchronized(previewCache) {
            previewCache.get(pageIndex0Based)?.let { return it }
        }

        pendingPreviews.incrementAndGet()
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                mutex.withLock {
                    if (isClosed) return@withLock null
                    synchronized(previewCache) {
                        previewCache.get(pageIndex0Based)?.let { return@withLock it }
                    }
                    val activeRenderer = ensureOpenRendererLocked()
                    if (pageIndex0Based !in 0 until activeRenderer.pageCount) return@withLock null

                    renderPageInternalLocked(
                        activeRenderer = activeRenderer,
                        pageIndex0Based = pageIndex0Based,
                        targetWidth = PREVIEW_WIDTH_PX,
                        isHd = false
                    )?.also { asset ->
                        synchronized(previewCache) {
                            previewCache.put(pageIndex0Based, asset)
                        }
                    }
                }
            }
        } finally {
            pendingPreviews.decrementAndGet()
        }
    }

    /**
     * Stage 2: Crisp HD page render (~1080–1280px wide) that yields to any pending Stage-1 previews
     * so scrolling never stalls behind HD upgrades.
     */
    suspend fun renderHdPageAsset(pageIndex0Based: Int, hdWidthPx: Int): CachedPdfPage? {
        val bucketWidth = bucketizeWidth(hdWidthPx)
        val cacheKey = "${pageIndex0Based}_$bucketWidth"
        synchronized(hdCache) {
            hdCache.get(cacheKey)?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            // Let any waiting Stage-1 fast previews finish first (usually <25ms each)
            var yieldSpins = 0
            while (pendingPreviews.get() > 0 && yieldSpins < 15) {
                currentCoroutineContext().ensureActive()
                delay(18)
                yieldSpins++
            }
            currentCoroutineContext().ensureActive()

            mutex.withLock {
                if (isClosed) return@withLock null
                currentCoroutineContext().ensureActive()
                synchronized(hdCache) {
                    hdCache.get(cacheKey)?.let { return@withLock it }
                }

                val activeRenderer = ensureOpenRendererLocked()
                if (pageIndex0Based !in 0 until activeRenderer.pageCount) return@withLock null

                renderPageInternalLocked(
                    activeRenderer = activeRenderer,
                    pageIndex0Based = pageIndex0Based,
                    targetWidth = bucketWidth.coerceIn(640, 1440),
                    isHd = true
                )?.also { asset ->
                    synchronized(hdCache) {
                        hdCache.put(cacheKey, asset)
                    }
                }
            }
        }
    }

    /**
     * Progressive background prefetch:
     * 1. Pre-renders lightweight 360px previews for ±2..+3 neighbor pages (~80ms total)
     * 2. Then pre-renders HD for immediate neighbors (+1, -1) if no Stage-1 previews are waiting.
     */
    suspend fun prefetchAround(centerIndex0Based: Int, hdWidthPx: Int) {
        val previewTargets = listOf(
            centerIndex0Based + 1,
            centerIndex0Based - 1,
            centerIndex0Based + 2,
            centerIndex0Based - 2,
            centerIndex0Based + 3
        )
        for (idx in previewTargets) {
            if (isClosed) return
            currentCoroutineContext().ensureActive()
            if (idx !in 0 until pageCount) continue
            val hasPreview = synchronized(previewCache) { previewCache.get(idx) != null }
            if (!hasPreview) {
                renderPreviewPageAsset(idx)
            }
        }

        val hdTargets = listOf(centerIndex0Based + 1, centerIndex0Based - 1)
        val bucketWidth = bucketizeWidth(hdWidthPx)
        for (idx in hdTargets) {
            if (isClosed) return
            currentCoroutineContext().ensureActive()
            if (idx !in 0 until pageCount) continue
            val hdKey = "${idx}_$bucketWidth"
            val hasHd = synchronized(hdCache) { hdCache.get(hdKey) != null }
            if (!hasHd) {
                renderHdPageAsset(idx, hdWidthPx)
            }
        }
    }

    /**
     * Backwards-compatible raw [Bitmap] render API (used by AI page vision attachment).
     */
    suspend fun renderPage(pageIndex0Based: Int, targetWidthPx: Int): Bitmap? =
        renderHdPageAsset(pageIndex0Based, targetWidthPx)?.bitmap

    fun getCachedPageAsset(pageIndex0Based: Int, targetWidthPx: Int): CachedPdfPage? =
        getBestCachedPageAsset(pageIndex0Based, targetWidthPx)

    fun getCachedPage(pageIndex0Based: Int, targetWidthPx: Int): Bitmap? =
        getBestCachedPageAsset(pageIndex0Based, targetWidthPx)?.bitmap

    fun close() {
        isClosed = true
        synchronized(previewCache) { previewCache.evictAll() }
        synchronized(hdCache) { hdCache.evictAll() }
        runCatching { renderer?.close() }
        runCatching { fileDescriptor?.close() }
        renderer = null
        fileDescriptor = null
    }

    private fun ensureOpenRendererLocked(): PdfRenderer {
        renderer?.let { return it }
        val cachedFile = ensureCachedPdfFile()
        val pfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(pfd)
        fileDescriptor = pfd
        renderer = pdfRenderer
        cachedPageCount = pdfRenderer.pageCount
        return pdfRenderer
    }

    private fun renderPageInternalLocked(
        activeRenderer: PdfRenderer,
        pageIndex0Based: Int,
        targetWidth: Int,
        isHd: Boolean
    ): CachedPdfPage? = runCatching {
        activeRenderer.openPage(pageIndex0Based).use { page ->
            val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else (297f / 210f)
            val width = targetWidth.coerceIn(280, 1440)
            val height = (width * ratio).roundToInt().coerceIn(360, 2400)
            // Android PdfRenderer.Page.render strictly requires Bitmap.Config.ARGB_8888
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val aspect = if (height > 0) (width.toFloat() / height.toFloat()).coerceIn(0.45f, 2.2f) else defaultAspectRatio
            CachedPdfPage(
                bitmap = bitmap,
                imageBitmap = bitmap.asImageBitmap(),
                aspectRatio = aspect,
                isHd = isHd
            )
        }
    }.getOrNull()

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
