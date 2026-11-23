package com.example.docs

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fullscreen-first native PDF Reader viewport:
 * - Single tap toggles Immersive Mode (hides top bar, bottom page scrubber, Ask-AI FAB & root bottom nav)
 * - Double-tap quick zoom (1x <-> 2.1x) + 2-finger pinch-to-zoom (1x..3.5x) & smooth panning
 * - Compact Top Bar: TOC Sheet opener, Page/Chapter indicator (tap to jump), Bookmark, Night Filter
 * - Compact Bottom-End FAB ("Hỏi AI") + slim 1-row Page Scrubber above Bottom Navigation Bar
 */
@Composable
fun PdfReaderView(
    renderer: PdfBookRenderer,
    initialPage1Based: Int,
    externalJumpPage: Int = initialPage1Based,
    externalJumpToken: Int = 0,
    isImmersive: Boolean = false,
    onToggleImmersive: () -> Unit = {},
    bookmarkedPages: Set<Int>,
    nightFilterEnabled: Boolean,
    onPageChanged: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onToggleNightFilter: () -> Unit,
    onAskAiAboutPage: (Int) -> Unit = {},
    onOpenToc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var totalPages by remember { mutableIntStateOf(renderer.pageCount) }
    var isOpening by remember { mutableStateOf(!renderer.isOpen) }
    var openError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(renderer) {
        if (renderer.isOpen) {
            totalPages = renderer.pageCount.coerceAtLeast(1)
            isOpening = false
            return@LaunchedEffect
        }
        isOpening = true
        openError = null
        renderer.open()
            .onSuccess { count ->
                totalPages = count.coerceAtLeast(1)
                isOpening = false
            }
            .onFailure { err ->
                openError = err.localizedMessage ?: "Không thể mở tài liệu PDF."
                isOpening = false
            }
    }

    val startIndex = (initialPage1Based - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    val currentPage1Based by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                (listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages)
            } else {
                val viewportMid = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centered = visible.minByOrNull { abs((it.offset + it.size / 2) - viewportMid) }
                ((centered?.index ?: listState.firstVisibleItemIndex) + 1).coerceIn(1, totalPages)
            }
        }
    }

    // Debounce SharedPreferences persistence so fast flinging never blocks the main thread
    LaunchedEffect(currentPage1Based) {
        delay(300)
        onPageChanged(currentPage1Based)
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize(1080, 1600)) }
    var showJumpDialog by remember { mutableStateOf(false) }

    fun clampOffset(raw: Offset, scale: Float): Offset {
        if (scale <= 1.01f) return Offset.Zero
        val maxX = (viewportSize.width * (scale - 1f)) * 0.52f
        val maxY = (viewportSize.height * (scale - 1f)) * 0.52f
        return Offset(
            x = raw.x.coerceIn(-maxX, maxX),
            y = raw.y.coerceIn(-maxY, maxY)
        )
    }

    fun updateZoom(newScale: Float) {
        val clampedScale = newScale.coerceIn(1f, 3.5f)
        zoom = clampedScale
        panOffset = clampOffset(panOffset, clampedScale)
    }

    fun jumpToPage(page1Based: Int) {
        val idx = (page1Based - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        zoom = 1f
        panOffset = Offset.Zero
        coroutineScope.launch {
            listState.scrollToItem(idx)
        }
    }

    // Respond to external jumps (TOC Sheet selection or AI "Mở trang X")
    LaunchedEffect(externalJumpToken) {
        if (externalJumpToken > 0) {
            jumpToPage(externalJumpPage)
        }
    }

    val currentSectionLabel = remember(currentPage1Based) {
        PdfCurriculumRepository.sectionLabelForPage(currentPage1Based)
    }
    val isCurrentBookmarked = currentPage1Based in bookmarkedPages
    val latestToggleImmersive by rememberUpdatedState(onToggleImmersive)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_reader_viewport")
    ) {
        when {
            openError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PdfEmptyStateCard(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = "Không thể tải tệp PDF",
                        subtitle = openError ?: "Đã xảy ra lỗi khi đọc tài liệu."
                    )
                }
            }

            isOpening -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                cornerRadiusDp = 18.dp,
                                elevated = true
                            )
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        CircularProgressIndicator(color = CyanAccent, strokeWidth = 3.dp)
                        Text(
                            text = "Đang chuẩn bị tài liệu PDF ($PDF_TOTAL_PAGES trang)...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            else -> {
                // Full-bleed continuous vertical PDF reader with 1-tap Immersive toggle, double-tap zoom, pinch zoom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { latestToggleImmersive() },
                                onDoubleTap = {
                                    if (zoom > 1.15f) {
                                        zoom = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoom = 2.1f
                                        panOffset = Offset.Zero
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val activeCount = event.changes.count { it.pressed }
                                    if (activeCount >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        if (centroidSize > 0f && (zoomChange != 1f || panChange != Offset.Zero)) {
                                            val nextScale = (zoom * zoomChange).coerceIn(1f, 3.5f)
                                            zoom = nextScale
                                            panOffset = clampOffset(panOffset + panChange, nextScale)
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } else if (activeCount == 1 && zoom > 1.03f) {
                                        val panChange = event.calculatePan()
                                        if (panChange != Offset.Zero) {
                                            panOffset = clampOffset(panOffset + panChange, zoom)
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    val isScrollEnabled by remember { derivedStateOf { zoom <= 1.03f } }
                    PdfPagesLazyColumn(
                        renderer = renderer,
                        totalPages = totalPages,
                        listState = listState,
                        zoomProvider = { zoom },
                        panOffsetProvider = { panOffset },
                        nightFilterEnabled = nightFilterEnabled,
                        isImmersive = isImmersive,
                        userScrollEnabled = isScrollEnabled
                    )
                }
            }
        }

        // Compact Floating Top Bar (auto-hides smoothly in Immersive Mode)
        AnimatedVisibility(
            visible = !isImmersive,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PdfReaderTopBar(
                    currentPage1Based = currentPage1Based,
                    totalPages = totalPages,
                    sectionLabel = currentSectionLabel,
                    isBookmarked = isCurrentBookmarked,
                    nightFilterEnabled = nightFilterEnabled,
                    onOpenToc = onOpenToc,
                    onOpenJumpDialog = { showJumpDialog = true },
                    onToggleNightFilter = onToggleNightFilter,
                    onToggleBookmark = { onToggleBookmark(currentPage1Based) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                )
            }
        }

        // Compact Bottom-End Floating Action Pill ("Hỏi AI trang này") — never blocks reading
        AnimatedVisibility(
            visible = !isImmersive && !isOpening && openError == null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 14.dp, bottom = 136.dp)
        ) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        pillShape = CircleShape,
                        elevated = true,
                        fallbackBg = PurpleAccent,
                        accentBorder = PurpleAccent.copy(alpha = 0.7f)
                    )
                    .clip(CircleShape)
                    .clickable { onAskAiAboutPage(currentPage1Based) }
                    .padding(horizontal = 13.dp, vertical = 9.dp)
                    .testTag("pdf_ask_ai_page_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Hỏi AI về trang $currentPage1Based",
                    tint = if (ActiveThemeState.current.liquidGlass.enabled) PurpleAccent else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Hỏi AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ActiveThemeState.current.liquidGlass.enabled) SlateTextPrimary else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Slim 1-Row Bottom Page Scrubber (sits cleanly above the root Floating Bottom Navigation Bar)
        AnimatedVisibility(
            visible = !isImmersive && !isOpening && openError == null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 78.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PdfReaderBottomScrubber(
                    currentPage1Based = currentPage1Based,
                    totalPages = totalPages,
                    zoom = zoom,
                    onPrevPage = { if (currentPage1Based > 1) jumpToPage(currentPage1Based - 1) },
                    onNextPage = { if (currentPage1Based < totalPages) jumpToPage(currentPage1Based + 1) },
                    onSeekPage = { jumpToPage(it) },
                    onResetZoom = { updateZoom(if (zoom > 1.05f) 1f else 1.8f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                )
            }
        }
    }

    if (showJumpDialog) {
        PdfJumpToPageDialog(
            currentPage1Based = currentPage1Based,
            totalPages = totalPages,
            onDismiss = { showJumpDialog = false },
            onJump = { page ->
                showJumpDialog = false
                jumpToPage(page)
            }
        )
    }
}

@Composable
private fun PdfPagesLazyColumn(
    renderer: PdfBookRenderer,
    totalPages: Int,
    listState: LazyListState,
    zoomProvider: () -> Float,
    panOffsetProvider: () -> Offset,
    nightFilterEnabled: Boolean,
    isImmersive: Boolean,
    userScrollEnabled: Boolean
) {
    val density = LocalDensity.current
    val nightColorFilter = remember(nightFilterEnabled) {
        if (!nightFilterEnabled) null
        else ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.88f, 0f, 0f, 0f, 236f,
                    0f, -0.88f, 0f, 0f, 232f,
                    0f, 0f, -0.84f, 0f, 224f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val targetWidthPx = remember(maxWidth, density) {
            with(density) { (maxWidth.coerceAtMost(840.dp).toPx() * 1.25f).roundToInt().coerceIn(720, 1280) }
        }

        // Prefetch immediate neighbor pages once scrolling settles so next/prev pages appear instantly
        LaunchedEffect(listState.isScrollInProgress, targetWidthPx) {
            if (!listState.isScrollInProgress) {
                val center = listState.firstVisibleItemIndex
                renderer.prefetchPages(listOf(center + 1, center - 1, center + 2), targetWidthPx)
            }
        }

        LazyColumn(
            state = listState,
            userScrollEnabled = userScrollEnabled,
            contentPadding = PaddingValues(
                top = if (isImmersive) 12.dp else 74.dp,
                bottom = if (isImmersive) 20.dp else 156.dp,
                start = 8.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    val s = zoomProvider()
                    val pan = panOffsetProvider()
                    scaleX = s
                    scaleY = s
                    translationX = pan.x
                    translationY = pan.y
                }
        ) {
            items(count = totalPages, key = { it }) { pageIndex ->
                PdfSinglePageItem(
                    renderer = renderer,
                    pageIndex0Based = pageIndex,
                    targetWidthPx = targetWidthPx,
                    nightColorFilter = nightColorFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 820.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfSinglePageItem(
    renderer: PdfBookRenderer,
    pageIndex0Based: Int,
    targetWidthPx: Int,
    nightColorFilter: ColorFilter?,
    modifier: Modifier = Modifier
) {
    var pageAsset by remember(pageIndex0Based, targetWidthPx) {
        mutableStateOf<CachedPdfPage?>(renderer.getCachedPageAsset(pageIndex0Based, targetWidthPx))
    }

    LaunchedEffect(pageIndex0Based, targetWidthPx) {
        if (pageAsset == null) {
            pageAsset = renderer.renderPageAsset(pageIndex0Based, targetWidthPx)
        }
    }

    val aspectRatio = pageAsset?.aspectRatio ?: renderer.aspectRatio

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(10.dp))
            .background(if (nightColorFilter != null) Color(0xFF151922) else Color.White)
            .border(1.dp, SlateBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val loaded = pageAsset
        if (loaded != null) {
            Image(
                bitmap = loaded.imageBitmap,
                contentDescription = "Trang ${pageIndex0Based + 1} tài liệu Toán 10 11 12",
                contentScale = ContentScale.FillWidth,
                colorFilter = nightColorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = CyanAccent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Đang tải trang ${pageIndex0Based + 1}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nightColorFilter != null) SlateTextSecondary else Color(0xFF475569)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.52f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = "${pageIndex0Based + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PdfReaderTopBar(
    currentPage1Based: Int,
    totalPages: Int,
    sectionLabel: String,
    isBookmarked: Boolean,
    nightFilterEnabled: Boolean,
    onOpenToc: () -> Unit,
    onOpenJumpDialog: () -> Unit,
    onToggleNightFilter: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .liquidGlassSurface(
                shape = CircleShape,
                pillShape = CircleShape,
                elevated = true,
                fallbackBg = SlateDark.copy(alpha = 0.94f),
                fallbackBorder = SlateBorder
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onOpenToc,
            modifier = Modifier
                .size(44.dp)
                .testTag("pdf_back_to_toc_button")
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = "Mở Mục lục 59 chuyên đề",
                tint = CyanAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenJumpDialog)
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("pdf_jump_page_pill")
        ) {
            Text(
                text = sectionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Trang $currentPage1Based / $totalPages · Nhảy trang",
                style = MaterialTheme.typography.labelSmall,
                color = CyanAccent,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleBookmark,
            modifier = Modifier
                .size(44.dp)
                .testTag("pdf_bookmark_toggle")
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (isBookmarked) "Bỏ đánh dấu trang này" else "Lưu trang yêu thích",
                tint = if (isBookmarked) AmberAccent else SlateTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onToggleNightFilter,
            modifier = Modifier
                .size(44.dp)
                .testTag("pdf_night_filter_toggle")
        ) {
            Icon(
                imageVector = if (nightFilterEnabled) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = if (nightFilterEnabled) "Tắt lọc ánh sáng ban đêm" else "Bật lọc ánh sáng ban đêm",
                tint = if (nightFilterEnabled) AmberAccent else SlateTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PdfReaderBottomScrubber(
    currentPage1Based: Int,
    totalPages: Int,
    zoom: Float,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onSeekPage: (Int) -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember(currentPage1Based, sliderDragging) {
        mutableFloatStateOf(currentPage1Based.toFloat())
    }

    Row(
        modifier = modifier
            .liquidGlassSurface(
                shape = CircleShape,
                pillShape = CircleShape,
                elevated = true,
                fallbackBg = SlateDark.copy(alpha = 0.94f),
                fallbackBorder = SlateBorder
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onPrevPage,
            enabled = currentPage1Based > 1,
            modifier = Modifier
                .size(38.dp)
                .testTag("pdf_prev_page_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Trang trước",
                tint = if (currentPage1Based > 1) SlateTextPrimary else SlateTextSecondary.copy(alpha = 0.35f)
            )
        }

        Slider(
            value = if (sliderDragging) sliderValue else currentPage1Based.toFloat(),
            onValueChange = {
                sliderDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                sliderDragging = false
                onSeekPage(sliderValue.roundToInt().coerceIn(1, totalPages))
            },
            valueRange = 1f..totalPages.coerceAtLeast(2).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                inactiveTrackColor = SlateBorder
            ),
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .testTag("pdf_page_slider")
        )

        IconButton(
            onClick = onNextPage,
            enabled = currentPage1Based < totalPages,
            modifier = Modifier
                .size(38.dp)
                .testTag("pdf_next_page_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Trang sau",
                tint = if (currentPage1Based < totalPages) SlateTextPrimary else SlateTextSecondary.copy(alpha = 0.35f)
            )
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(SlateSurface.copy(alpha = 0.85f))
                .border(1.dp, SlateBorder, CircleShape)
                .clickable(onClick = onResetZoom)
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("pdf_zoom_reset_btn"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (sliderDragging) "Tr.${sliderValue.roundToInt()}" else "${(zoom * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = CyanAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun PdfJumpToPageDialog(
    currentPage1Based: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var inputText by remember { mutableStateOf(currentPage1Based.toString()) }
    val parsedPage = inputText.trim().toIntOrNull()
    val isValid = parsedPage != null && parsedPage in 1..totalPages

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    cornerRadiusDp = 20.dp,
                    elevated = true,
                    fallbackBg = SlateDark,
                    accentBorder = CyanAccent.copy(alpha = 0.55f)
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Đi đến trang (1 – $totalPages)",
                style = MaterialTheme.typography.titleMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = inputText,
                onValueChange = { v -> inputText = v.filter { it.isDigit() }.take(4) },
                singleLine = true,
                label = { Text("Số trang") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = { if (isValid && parsedPage != null) onJump(parsedPage) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary,
                    cursorColor = CyanAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_jump_page_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                JumpPresetButton("Mục lục", 3, Modifier.weight(1f), onJump)
                JumpPresetButton("Lớp 10", 18, Modifier.weight(1f), onJump)
                JumpPresetButton("Lớp 11", 111, Modifier.weight(1f), onJump)
                JumpPresetButton("Lớp 12", 256, Modifier.weight(1f), onJump)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Đóng", color = SlateTextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (isValid && parsedPage != null) onJump(parsedPage) },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                    modifier = Modifier.testTag("pdf_jump_confirm_btn")
                ) {
                    Text("Mở trang", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun JumpPresetButton(
    label: String,
    page: Int,
    modifier: Modifier,
    onJump: (Int) -> Unit
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CyanAccent.copy(alpha = 0.14f))
            .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { onJump(page) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CyanAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
