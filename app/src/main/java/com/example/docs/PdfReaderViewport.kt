package com.example.docs

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-featured native PDF Reader viewport:
 * - Renders the 388-page bundled DPAD PDF lazily on Dispatchers.IO via [PdfBookRenderer]
 * - Smooth vertical page scrolling at 100% zoom, 2-finger pinch-to-zoom (1x..3.5x),
 *   double-tap quick zoom (1x <-> 2.1x), and 1-finger panning when zoomed in
 * - Floating Liquid Glass top bar (Back to Mục lục, Chapter Title, Page Pill, Night Filter, Bookmark ★)
 *   and bottom dock (Prev/Next page, Interactive 1..388 Slider, Zoom - / 100% / +)
 */
@Composable
fun PdfReaderView(
    renderer: PdfBookRenderer,
    initialPage1Based: Int,
    bookmarkedPages: Set<Int>,
    nightFilterEnabled: Boolean,
    onPageChanged: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onToggleNightFilter: () -> Unit,
    onAskAiAboutPage: (Int) -> Unit = {},
    onBackToToc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var totalPages by remember { mutableIntStateOf(renderer.pageCount) }
    var isOpening by remember { mutableStateOf(true) }
    var openError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(renderer) {
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

    // Track currently centered/visible 1-based page number cleanly
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

    LaunchedEffect(currentPage1Based) {
        onPageChanged(currentPage1Based)
    }

    // Zoom & pan state
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

    val currentSectionLabel = remember(currentPage1Based) {
        PdfCurriculumRepository.sectionLabelForPage(currentPage1Based)
    }
    val isCurrentBookmarked = currentPage1Based in bookmarkedPages

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
                // Continuous vertical PDF reader with smooth 2-finger pinch-to-zoom & pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures(
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
                    PdfPagesLazyColumn(
                        renderer = renderer,
                        totalPages = totalPages,
                        listState = listState,
                        zoom = zoom,
                        panOffset = panOffset,
                        nightFilterEnabled = nightFilterEnabled,
                        userScrollEnabled = zoom <= 1.03f
                    )
                }
            }
        }

        // Top Reader Control Pill (sits right under the top WorkspaceModeSwitch bar)
        PdfReaderTopBar(
            currentPage1Based = currentPage1Based,
            totalPages = totalPages,
            sectionLabel = currentSectionLabel,
            isBookmarked = isCurrentBookmarked,
            nightFilterEnabled = nightFilterEnabled,
            onBackToToc = onBackToToc,
            onOpenJumpDialog = { showJumpDialog = true },
            onAskAiAboutPage = { onAskAiAboutPage(currentPage1Based) },
            onToggleNightFilter = onToggleNightFilter,
            onToggleBookmark = { onToggleBookmark(currentPage1Based) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        // Bottom Reader Navigation & Zoom Dock
        PdfReaderBottomDock(
            currentPage1Based = currentPage1Based,
            totalPages = totalPages,
            zoom = zoom,
            onPrevPage = { if (currentPage1Based > 1) jumpToPage(currentPage1Based - 1) },
            onNextPage = { if (currentPage1Based < totalPages) jumpToPage(currentPage1Based + 1) },
            onSeekPage = { jumpToPage(it) },
            onZoomOut = { updateZoom(zoom - 0.35f) },
            onResetZoom = { updateZoom(1f) },
            onZoomIn = { updateZoom(zoom + 0.35f) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
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
    zoom: Float,
    panOffset: Offset,
    nightFilterEnabled: Boolean,
    userScrollEnabled: Boolean
) {
    val density = LocalDensity.current
    val nightColorFilter = remember(nightFilterEnabled) {
        if (!nightFilterEnabled) null
        else ColorFilter.colorMatrix(
            // Soft warm-dark inversion for comfortable night studying
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
            with(density) { (maxWidth.coerceAtMost(840.dp).toPx() * 1.45f).roundToInt().coerceIn(720, 1600) }
        }

        LazyColumn(
            state = listState,
            userScrollEnabled = userScrollEnabled,
            contentPadding = PaddingValues(top = 64.dp, bottom = 126.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panOffset.x
                    translationY = panOffset.y
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
    var bitmap by remember(pageIndex0Based, targetWidthPx) {
        mutableStateOf<Bitmap?>(renderer.getCachedPage(pageIndex0Based, targetWidthPx))
    }

    LaunchedEffect(pageIndex0Based, targetWidthPx) {
        if (bitmap == null) {
            bitmap = renderer.renderPage(pageIndex0Based, targetWidthPx)
        }
    }

    val aspectRatio = remember(bitmap, renderer.aspectRatio) {
        bitmap?.let { b ->
            if (b.height > 0) (b.width.toFloat() / b.height.toFloat()).coerceIn(0.45f, 2.2f) else renderer.aspectRatio
        } ?: renderer.aspectRatio
    }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(10.dp))
            .background(if (nightColorFilter != null) Color(0xFF151922) else Color.White)
            .border(1.dp, SlateBorder.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
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

        // Subtle corner page badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
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
    onBackToToc: () -> Unit,
    onOpenJumpDialog: () -> Unit,
    onAskAiAboutPage: () -> Unit,
    onToggleNightFilter: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                pillShape = RoundedCornerShape(50),
                cornerRadiusDp = 16.dp,
                elevated = true,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onBackToToc,
            modifier = Modifier.testTag("pdf_back_to_toc_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại Mục lục",
                tint = SlateTextPrimary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenJumpDialog)
                .padding(horizontal = 6.dp, vertical = 4.dp)
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
                text = "Trang $currentPage1Based / $totalPages · Chạm để nhảy trang",
                style = MaterialTheme.typography.labelSmall,
                color = CyanAccent,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onAskAiAboutPage,
            modifier = Modifier.testTag("pdf_ask_ai_page_btn")
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Hỏi AI về trang này",
                tint = PurpleAccent
            )
        }

        IconButton(
            onClick = onToggleNightFilter,
            modifier = Modifier.testTag("pdf_night_filter_toggle")
        ) {
            Icon(
                imageVector = if (nightFilterEnabled) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = if (nightFilterEnabled) "Tắt chế độ đọc ban đêm" else "Bật chế độ đọc ban đêm",
                tint = if (nightFilterEnabled) AmberAccent else SlateTextSecondary
            )
        }

        IconButton(
            onClick = onToggleBookmark,
            modifier = Modifier.testTag("pdf_bookmark_toggle")
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (isBookmarked) "Bỏ đánh dấu trang này" else "Đánh dấu trang này",
                tint = if (isBookmarked) AmberAccent else SlateTextSecondary
            )
        }
    }
}

@Composable
private fun PdfReaderBottomDock(
    currentPage1Based: Int,
    totalPages: Int,
    zoom: Float,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onSeekPage: (Int) -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember(currentPage1Based, sliderDragging) {
        mutableFloatStateOf(currentPage1Based.toFloat())
    }

    Column(
        modifier = modifier
            .liquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                cornerRadiusDp = 18.dp,
                elevated = true,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onPrevPage,
                enabled = currentPage1Based > 1,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("pdf_prev_page_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Trang trước",
                    tint = if (currentPage1Based > 1) SlateTextPrimary else SlateTextSecondary.copy(alpha = 0.4f)
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
                    .testTag("pdf_page_slider")
            )

            IconButton(
                onClick = onNextPage,
                enabled = currentPage1Based < totalPages,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("pdf_next_page_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Trang sau",
                    tint = if (currentPage1Based < totalPages) SlateTextPrimary else SlateTextSecondary.copy(alpha = 0.4f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (sliderDragging) {
                    "Đang chọn: Trang ${sliderValue.roundToInt()} / $totalPages"
                } else {
                    "Thu phóng: ${(zoom * 100).roundToInt()}% (chạm đúp hoặc chụm 2 ngón)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallZoomChip(
                    onClick = onZoomOut,
                    enabled = zoom > 1.01f,
                    testTag = "pdf_zoom_out_btn"
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Thu nhỏ", tint = SlateTextPrimary, modifier = Modifier.size(16.dp))
                }
                SmallZoomChip(
                    onClick = onResetZoom,
                    enabled = true,
                    testTag = "pdf_zoom_reset_btn"
                ) {
                    Text(
                        text = "${(zoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                SmallZoomChip(
                    onClick = onZoomIn,
                    enabled = zoom < 3.45f,
                    testTag = "pdf_zoom_in_btn"
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Phóng to", tint = SlateTextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallZoomChip(
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 34.dp, minHeight = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SlateDark.copy(alpha = if (enabled) 0.72f else 0.35f))
            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
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

            Text(
                text = "Nhảy nhanh mốc chính:",
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary
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
