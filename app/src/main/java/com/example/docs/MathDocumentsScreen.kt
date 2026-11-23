package com.example.docs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geo2d.MathWorkspace
import com.example.ui.theme.*

/**
 * Fullscreen-first 388-page PDF Reference Library ("Tài liệu"):
 * - Opens directly onto the last read PDF page in distraction-free full-screen reader mode
 * - Single tap toggles Immersive Mode (hides top bar, bottom page scrubber, Ask-AI FAB & bottom nav)
 * - Hierarchical 59-topic Table of Contents (Lớp 10, 11, 12 + Bookmarks + diacritic-insensitive search)
 *   opens cleanly in an on-demand Modal Bottom Sheet from the compact top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathDocumentsScreen(
    isImmersive: Boolean = false,
    onToggleImmersive: () -> Unit = {},
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    pendingOpenPage: Int? = null,
    onConsumePendingPage: () -> Unit = {},
    onAskAiAboutPage: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember(appContext) { PdfDocsPreferences(appContext) }
    val pdfRenderer = remember(appContext) { PdfBookRenderer(appContext) }

    DisposableEffect(pdfRenderer) {
        onDispose { pdfRenderer.close() }
    }

    var selectedTab by remember { mutableStateOf(prefs.loadLastTab()) }
    var searchQuery by remember { mutableStateOf("") }
    var lastReadPage by remember { mutableIntStateOf(prefs.loadLastPage()) }
    var bookmarkedPages by remember { mutableStateOf(prefs.loadBookmarks()) }
    var nightFilterEnabled by remember { mutableStateOf(prefs.loadNightFilter()) }
    var showTocSheet by remember { mutableStateOf(false) }

    // Trigger counter so jumping to a page works reliably even if the same page is requested again
    var jumpTargetPage by remember { mutableIntStateOf(lastReadPage) }
    var jumpRequestToken by remember { mutableIntStateOf(0) }

    val searchHits = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList() else PdfCurriculumRepository.searchTopics(searchQuery)
    }

    val openPdfAt: (Int) -> Unit = { page1Based ->
        val safePage = page1Based.coerceIn(1, PDF_TOTAL_PAGES)
        lastReadPage = safePage
        prefs.saveLastPage(safePage)
        jumpTargetPage = safePage
        jumpRequestToken += 1
        showTocSheet = false
    }

    LaunchedEffect(pendingOpenPage) {
        val target = pendingOpenPage
        if (target != null) {
            openPdfAt(target)
            onConsumePendingPage()
        }
    }

    val toggleBookmark: (Int) -> Unit = { page1Based ->
        bookmarkedPages = prefs.toggleBookmark(page1Based)
    }

    val toggleNightFilter: () -> Unit = {
        val next = !nightFilterEnabled
        nightFilterEnabled = next
        prefs.saveNightFilter(next)
    }

    BackHandler {
        when {
            showTocSheet -> showTocSheet = false
            isImmersive -> onToggleImmersive()
            else -> onSwitchWorkspace(MathWorkspace.SPACE_3D)
        }
    }

    val bgBackdrop = rememberLiquidGlassBackdropState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("math_documents_screen")
    ) {
        val isWideScreen = maxWidth >= 600.dp

        LiquidGlassBackdrop(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(bgBackdrop)
        )

        CompositionLocalProvider(LocalLiquidGlassBackdrop provides bgBackdrop) {
            // Direct Fullscreen PDF Reader Viewport
            PdfReaderView(
                renderer = pdfRenderer,
                initialPage1Based = lastReadPage,
                externalJumpPage = jumpTargetPage,
                externalJumpToken = jumpRequestToken,
                isImmersive = isImmersive,
                onToggleImmersive = onToggleImmersive,
                bookmarkedPages = bookmarkedPages,
                nightFilterEnabled = nightFilterEnabled,
                onPageChanged = { currentPage ->
                    if (currentPage != lastReadPage) {
                        lastReadPage = currentPage
                        prefs.saveLastPage(currentPage)
                    }
                },
                onToggleBookmark = toggleBookmark,
                onToggleNightFilter = toggleNightFilter,
                onAskAiAboutPage = onAskAiAboutPage,
                onOpenToc = { showTocSheet = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Hierarchical TOC Modal Bottom Sheet (Lớp 10, 11, 12, 59 chuyên đề, Bookmarks & Diacritic-Insensitive Search)
        if (showTocSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                sheetState = sheetState,
                containerColor = SlateDark,
                contentColor = SlateTextPrimary,
                dragHandle = { BottomSheetDefaults.DragHandle(color = SlateMuted) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.86f)
                        .testTag("pdf_toc_sheet")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mục lục Toán THPT (388 trang)",
                                style = MaterialTheme.typography.titleMedium,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "59 chuyên đề Lớp 10 – 11 – 12 & Trang đã đánh dấu",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateTextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SlateSurface)
                                .border(1.dp, SlateBorder, CircleShape)
                                .clickable { showTocSheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng Mục lục",
                                tint = SlateTextLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    PdfTocOverviewContent(
                        selectedTab = selectedTab,
                        searchQuery = searchQuery,
                        searchHits = searchHits,
                        lastReadPage = lastReadPage,
                        bookmarkedPages = bookmarkedPages,
                        isWideScreen = isWideScreen,
                        onSearchChange = { searchQuery = it },
                        onSelectTab = { nextTab ->
                            selectedTab = nextTab
                            prefs.saveLastTab(nextTab)
                        },
                        onOpenPage = openPdfAt,
                        onRemoveBookmark = toggleBookmark
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfTocOverviewContent(
    selectedTab: PdfDocsTab,
    searchQuery: String,
    searchHits: List<PdfTopicSearchHit>,
    lastReadPage: Int,
    bookmarkedPages: Set<Int>,
    isWideScreen: Boolean,
    onSearchChange: (String) -> Unit,
    onSelectTab: (PdfDocsTab) -> Unit,
    onOpenPage: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cross-grade diacritic-insensitive search across all 59 PDF topics & subtopics
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    text = "Tìm mục lục không dấu: lượng giác, đạo hàm, oxyz, bayes...",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    maxLines = 1
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Xóa tìm kiếm",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SlateTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SlateDeepest,
                unfocusedContainerColor = SlateDeepest,
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = SlateBorder,
                cursorColor = CyanAccent
            ),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .testTag("docs_search_input")
        )

        if (searchQuery.isBlank()) {
            PdfGradeTabBar(
                selectedTab = selectedTab,
                bookmarkCount = bookmarkedPages.size,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (searchQuery.isNotBlank()) {
                item(key = "pdf_search_count") {
                    Text(
                        text = "Tìm thấy ${searchHits.size} chủ đề trong tài liệu PDF",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 4.dp)
                    )
                }

                if (searchHits.isEmpty()) {
                    item(key = "pdf_search_empty") {
                        PdfEmptyStateCard(
                            icon = Icons.Default.SearchOff,
                            title = "Không tìm thấy mục phù hợp",
                            subtitle = "Thử từ khóa ngắn gọn hoặc không dấu: vi-et, cosin, logarit, dao ham, tich phan, oxyz, bayes...",
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                } else {
                    items(searchHits, key = { "hit_${it.topic.id}_${it.targetPage}" }) { hit ->
                        PdfSearchHitCard(
                            hit = hit,
                            onOpenPage = onOpenPage,
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                }
            } else {
                item(key = "pdf_hero_resume") {
                    PdfHeroResumeCard(
                        lastPage = lastReadPage,
                        bookmarkCount = bookmarkedPages.size,
                        onContinueReading = { onOpenPage(lastReadPage) },
                        onOpenOriginalToc = { onOpenPage(3) },
                        onOpenCover = { onOpenPage(1) },
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }

                if (selectedTab == PdfDocsTab.BOOKMARKS) {
                    if (bookmarkedPages.isEmpty()) {
                        item(key = "pdf_bookmarks_empty") {
                            PdfEmptyStateCard(
                                icon = Icons.Outlined.BookmarkBorder,
                                title = "Chưa có trang nào được đánh dấu",
                                subtitle = "Khi đang đọc PDF, chạm biểu tượng Đánh dấu trên góc phải để lưu trang quan trọng vào đây.",
                                modifier = Modifier.widthIn(max = 720.dp)
                            )
                        }
                    } else {
                        val sortedPages = bookmarkedPages.sorted()
                        items(sortedPages, key = { "bm_$it" }) { pageNum ->
                            PdfBookmarkPageCard(
                                page1Based = pageNum,
                                onOpenPage = onOpenPage,
                                onRemoveBookmark = onRemoveBookmark,
                                modifier = Modifier.widthIn(max = 720.dp)
                            )
                        }
                    }
                } else {
                    val grade = selectedTab.toGradeOrNull() ?: PdfGrade.GRADE_12
                    val topics = PdfCurriculumRepository.topicsFor(grade)

                    item(key = "pdf_grade_banner_${grade.id}") {
                        PdfGradeSectionBanner(
                            grade = grade,
                            topicCount = topics.size,
                            onOpenGradeStart = { onOpenPage(grade.startPage) },
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }

                    if (isWideScreen) {
                        val pairs = topics.chunked(2)
                        items(pairs, key = { pair -> pair.joinToString("_") { it.id } }) { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { topic ->
                                    PdfTopicCard(
                                        topic = topic,
                                        onOpenPage = onOpenPage,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(topics, key = { it.id }) { topic ->
                            PdfTopicCard(
                                topic = topic,
                                onOpenPage = onOpenPage,
                                modifier = Modifier.widthIn(max = 720.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
