package com.example.docs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geo2d.MathWorkspace
import com.example.geo2d.WorkspaceModeSwitch
import com.example.ui.theme.*

/**
 * Integrated 388-page PDF Reference Library ("Tài liệu") powered by the user's attached
 * "Tóm Tắt Kiến Thức Toán 10 – 11 – 12 (DPAD)" PDF:
 * - Interactive Mục lục organized by Lớp 10 (22 topics), Lớp 11 (19 topics), Lớp 12 (18 topics) + Bookmarks
 * - Diacritic-insensitive search across all 59 topics & subtopics jumping straight to exact PDF pages
 * - Native offline Android PdfRenderer viewer with pinch-to-zoom, double-tap zoom, page scrubber,
 *   jump-to-page dialog, night reading filter, and persistent reading progress + bookmarks.
 */
@Composable
fun MathDocumentsScreen(
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    pendingOpenPage: Int? = null,
    onConsumePendingPage: () -> Unit = {},
    onAskAiAboutPage: (Int) -> Unit = {},
    hasUpdateBadge: Boolean = false
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

    // Non-null when viewing the PDF pages directly (value = target 1-based page to open at)
    var activeReaderTargetPage by remember { mutableStateOf<Int?>(null) }

    val searchHits = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList() else PdfCurriculumRepository.searchTopics(searchQuery)
    }

    val openPdfAt: (Int) -> Unit = { page1Based ->
        val safePage = page1Based.coerceIn(1, PDF_TOTAL_PAGES)
        lastReadPage = safePage
        prefs.saveLastPage(safePage)
        activeReaderTargetPage = safePage
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
            activeReaderTargetPage != null -> activeReaderTargetPage = null
            searchQuery.isNotEmpty() -> searchQuery = ""
            else -> onSwitchWorkspace(MathWorkspace.SPACE_3D)
        }
    }

    val bgOnlyBackdrop = rememberLiquidGlassBackdropState()
    val scrollBackdrop = rememberLiquidGlassBackdropState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("math_documents_screen")
    ) {
        val isWideScreen = maxWidth >= 600.dp

        // Layer 1: Live backdrop + Mục lục OR native PDF Reader (captured by floating top capsule bar)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(scrollBackdrop)
        ) {
            LiquidGlassBackdrop(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassBackdropSource(bgOnlyBackdrop)
            )

            CompositionLocalProvider(LocalLiquidGlassBackdrop provides bgOnlyBackdrop) {
                AnimatedContent(
                    targetState = activeReaderTargetPage != null,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "pdf_docs_mode_transition",
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 60.dp)
                ) { inReaderMode ->
                    if (!inReaderMode) {
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
                    } else {
                        PdfReaderView(
                            renderer = pdfRenderer,
                            initialPage1Based = activeReaderTargetPage ?: lastReadPage,
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
                            onBackToToc = { activeReaderTargetPage = null }
                        )
                    }
                }
            }
        }

        // Layer 2: Floating Liquid Glass Top Capsule Bar (live GPU blur & refraction over PDF & TOC)
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides scrollBackdrop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                WorkspaceModeSwitch(
                    current = MathWorkspace.DOCUMENTS,
                    hasUpdateBadge = hasUpdateBadge,
                    onSelect = onSwitchWorkspace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                )
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
    val isGlass = ActiveThemeState.current.liquidGlass.enabled

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
                    text = "Tìm nhanh mục lục PDF: lượng giác, đạo hàm, nguyên hàm, Oxyz, Bayes...",
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
                focusedContainerColor = SlateDark.copy(alpha = if (isGlass) 0.65f else 1f),
                unfocusedContainerColor = SlateDark.copy(alpha = if (isGlass) 0.55f else 1f),
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = SlateBorder,
                cursorColor = CyanAccent
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("docs_search_input")
        )

        if (searchQuery.isBlank()) {
            PdfGradeTabBar(
                selectedTab = selectedTab,
                bookmarkCount = bookmarkedPages.size,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                // Hero card to continue reading / jump to Cover or Original PDF TOC
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
                                subtitle = "Khi đang đọc tài liệu PDF, chạm biểu tượng Đánh dấu trên góc trên bên phải để lưu trang quan trọng vào đây.",
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
