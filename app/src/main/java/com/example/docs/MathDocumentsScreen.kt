package com.example.docs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ShapeType
import com.example.geo2d.MathWorkspace
import com.example.geo2d.WorkspaceModeSwitch
import com.example.ui.theme.*

/**
 * Complete High-School Math Reference Encyclopedia ("Tài liệu") following Vietnam GDPT 2018:
 * - Grade 10 (9 chapters), Grade 11 (9 chapters + interactive Trigonometry tools), Grade 12 (6 chapters)
 * - 2-level browse flow: Grade Tabs + Chapter Grid -> Chapter Detail with all formula groups
 * - Accent-insensitive cross-grade search across all 300+ formulas
 * - Persistent local Favorites bookmarking + One-tap launch to 2D graphs or 3D solids
 */
@Composable
fun MathDocumentsScreen(
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    onPlotTrigIn2D: (String) -> Unit,
    onOpen3DShape: (ShapeType, Boolean) -> Unit = { _, _ -> },
    hasUpdateBadge: Boolean = false
) {
    val context = LocalContext.current
    val docsPrefs = remember(context) { MathDocsPreferences(context.applicationContext) }

    var selectedTab by remember { mutableStateOf(docsPrefs.loadLastTab()) }
    var activeChapterId by remember { mutableStateOf<String?>(null) }
    var selectedGroupFilterId by remember(activeChapterId) { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteIds by remember { mutableStateOf(docsPrefs.loadFavoriteIds()) }

    val activeChapter = remember(activeChapterId) {
        activeChapterId?.let { HighSchoolMathRepository.chapterById(it) }
    }

    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else HighSchoolMathRepository.searchFormulas(searchQuery)
    }

    val favoriteHits = remember(favoriteIds) {
        HighSchoolMathRepository.favoriteHits(favoriteIds)
    }

    val onToggleFavorite: (String) -> Unit = { formulaId ->
        favoriteIds = docsPrefs.toggleFavorite(formulaId)
    }

    val onTriggerQuickAction: (DocQuickAction) -> Unit = { action ->
        when (action) {
            is DocQuickAction.Plot2D -> onPlotTrigIn2D(action.expression)
            is DocQuickAction.Open3D -> onOpen3DShape(action.shape, action.showAxes)
        }
    }

    BackHandler {
        when {
            activeChapter != null -> activeChapterId = null
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

        // Layer 1 (captured by floating top capsule bar): Aurora backdrop + scrolling encyclopedia content
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
                    targetState = activeChapter,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "docs_level_transition",
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 60.dp)
                ) { chapter ->
                    if (chapter == null) {
                        DocsOverviewContent(
                            selectedTab = selectedTab,
                            searchQuery = searchQuery,
                            searchResults = searchResults,
                            favoriteIds = favoriteIds,
                            favoriteHits = favoriteHits,
                            isWideScreen = isWideScreen,
                            onSearchChange = { searchQuery = it },
                            onSelectTab = { nextTab ->
                                selectedTab = nextTab
                                docsPrefs.saveLastTab(nextTab)
                            },
                            onOpenChapter = { chapterId ->
                                activeChapterId = chapterId
                            },
                            onToggleFavorite = onToggleFavorite
                        )
                    } else {
                        ChapterDetailContent(
                            chapter = chapter,
                            selectedGroupFilterId = selectedGroupFilterId,
                            favoriteIds = favoriteIds,
                            onSelectGroupFilter = { selectedGroupFilterId = it },
                            onBack = { activeChapterId = null },
                            onToggleFavorite = onToggleFavorite,
                            onTriggerQuickAction = onTriggerQuickAction
                        )
                    }
                }
            }
        }

        // Layer 2: Floating Liquid Glass Top Capsule Bar (live GPU blur & refraction over scrolling docs!)
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
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                )
            }
        }
    }
}

@Composable
private fun DocsOverviewContent(
    selectedTab: DocsOverviewTab,
    searchQuery: String,
    searchResults: List<FormulaSearchHit>,
    favoriteIds: Set<String>,
    favoriteHits: List<FormulaSearchHit>,
    isWideScreen: Boolean,
    onSearchChange: (String) -> Unit,
    onSelectTab: (DocsOverviewTab) -> Unit,
    onOpenChapter: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val isGlass = ActiveThemeState.current.liquidGlass.enabled

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Global Cross-Grade Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    text = "Tìm công thức Lớp 10–11–12: Vi-ét, sin 2a, đạo hàm, nguyên hàm, Oxyz, Bayes...",
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
                        Icon(Icons.Default.Close, contentDescription = "Xóa tìm kiếm", tint = SlateTextSecondary, modifier = Modifier.size(18.dp))
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

        // Grade Selector Bar (hidden only while actively viewing search results)
        if (searchQuery.isBlank()) {
            GradeTabSelector(
                selectedTab = selectedTab,
                favoriteCount = favoriteIds.size,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Scrollable Body: Search Results OR Favorites OR Grade Chapters
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (searchQuery.isNotBlank()) {
                item(key = "search_summary") {
                    Text(
                        text = "Tìm thấy ${searchResults.size} công thức khớp trên cả 3 lớp",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 4.dp)
                    )
                }

                if (searchResults.isEmpty()) {
                    item(key = "search_empty") {
                        DocsEmptyStateCard(
                            icon = Icons.Default.SearchOff,
                            title = "Không tìm thấy công thức phù hợp",
                            subtitle = "Thử tìm không dấu hoặc ngắn gọn hơn: cosin, viet, cap so cong, logarit, dao ham, tich phan, oxyz, bayes...",
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                } else {
                    items(searchResults, key = { "search_${it.formula.id}" }) { hit ->
                        FormulaSearchHitCard(
                            hit = hit,
                            isFavorite = hit.formula.id in favoriteIds,
                            onToggleFavorite = { onToggleFavorite(hit.formula.id) },
                            onOpenChapter = { onOpenChapter(hit.chapter.id) },
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                }
            } else if (selectedTab == DocsOverviewTab.FAVORITES) {
                if (favoriteHits.isEmpty()) {
                    item(key = "fav_empty") {
                        DocsEmptyStateCard(
                            icon = Icons.Outlined.StarBorder,
                            title = "Chưa có công thức Yêu thích nào",
                            subtitle = "Mở bất kỳ chương nào ở Lớp 10, 11 hoặc 12 rồi chạm biểu tượng ngôi sao ★ bên cạnh công thức bạn hay dùng để lưu vào đây ôn nhanh.",
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                } else {
                    item(key = "fav_count_header") {
                        Text(
                            text = "Đã lưu ${favoriteHits.size} công thức ôn tập nhanh",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .padding(horizontal = 4.dp)
                        )
                    }
                    items(favoriteHits, key = { "fav_${it.formula.id}" }) { hit ->
                        FormulaSearchHitCard(
                            hit = hit,
                            isFavorite = true,
                            onToggleFavorite = { onToggleFavorite(hit.formula.id) },
                            onOpenChapter = { onOpenChapter(hit.chapter.id) },
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                }
            } else {
                val grade = selectedTab.toGradeOrNull() ?: MathGrade.GRADE_10
                val gradeChapters = HighSchoolMathRepository.chaptersFor(grade)

                item(key = "grade_hero_${grade.id}") {
                    GradeHeroBanner(
                        grade = grade,
                        chapterCount = gradeChapters.size,
                        formulaCount = HighSchoolMathRepository.totalFormulaCount(grade),
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }

                if (isWideScreen) {
                    val pairs = gradeChapters.chunked(2)
                    items(pairs, key = { pair -> pair.joinToString("_") { it.id } }) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { chapter ->
                                ChapterOverviewCard(
                                    chapter = chapter,
                                    onClick = { onOpenChapter(chapter.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    items(gradeChapters, key = { it.id }) { chapter ->
                        ChapterOverviewCard(
                            chapter = chapter,
                            onClick = { onOpenChapter(chapter.id) },
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterDetailContent(
    chapter: MathChapter,
    selectedGroupFilterId: String?,
    favoriteIds: Set<String>,
    onSelectGroupFilter: (String?) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTriggerQuickAction: (DocQuickAction) -> Unit
) {
    val accent = Color(chapter.accentHex)
    val visibleGroups = remember(chapter, selectedGroupFilterId) {
        if (selectedGroupFilterId == null) chapter.groups
        else chapter.groups.filter { it.id == selectedGroupFilterId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "detail_header") {
            ChapterDetailHeader(
                chapter = chapter,
                onBack = onBack,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }

        if (chapter.quickActions.isNotEmpty()) {
            item(key = "detail_quick_actions") {
                DocQuickActionsStrip(
                    actions = chapter.quickActions,
                    onTriggerAction = onTriggerQuickAction,
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
        }

        // Sub-group Filter Strip when chapter has 3+ topic groups
        if (chapter.groups.size >= 3) {
            item(key = "detail_group_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChipBox(
                        label = "Tất cả (${chapter.groups.size})",
                        active = selectedGroupFilterId == null,
                        accent = accent,
                        testTag = "docs_subfilter_all",
                        onClick = { onSelectGroupFilter(null) }
                    )
                    chapter.groups.forEach { grp ->
                        FilterChipBox(
                            label = grp.title,
                            active = selectedGroupFilterId == grp.id,
                            accent = Color(grp.accentHex),
                            testTag = "docs_subfilter_${grp.id}",
                            onClick = { onSelectGroupFilter(grp.id) }
                        )
                    }
                }
            }
        }

        // Formula Groups
        items(visibleGroups, key = { it.id }) { group ->
            MathFormulaGroupCard(
                group = group,
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }

        // Preserved Interactive Trigonometry Tools inside Grade 11 Chapter I!
        if (chapter.hasTrigInteractiveTools && selectedGroupFilterId == null) {
            item(key = "trig_deg_rad_converter") {
                DegreeRadianConverterCard(modifier = Modifier.widthIn(max = 720.dp))
            }
            item(key = "trig_unit_circle") {
                InteractiveUnitCircleCard(modifier = Modifier.widthIn(max = 720.dp))
            }
            item(key = "trig_quadrant_table") {
                QuadrantSignTableCard(modifier = Modifier.widthIn(max = 720.dp))
            }
        }
    }
}

@Composable
private fun FilterChipBox(
    label: String,
    active: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val isGlass = ActiveThemeState.current.liquidGlass.enabled
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) accent.copy(alpha = 0.24f)
                else SlateDark.copy(alpha = if (isGlass) 0.55f else 1f)
            )
            .border(1.dp, if (active) accent else SlateBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accent else SlateTextLight,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}
