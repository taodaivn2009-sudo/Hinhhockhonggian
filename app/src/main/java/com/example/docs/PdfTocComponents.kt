package com.example.docs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun PdfHeroResumeCard(
    lastPage: Int,
    bookmarkCount: Int,
    onContinueReading: () -> Unit,
    onOpenOriginalToc: () -> Unit,
    onOpenCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = ActiveThemeState.current.liquidGlass.enabled
    val lastSection = remember(lastPage) { PdfCurriculumRepository.sectionLabelForPage(lastPage) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                liveBackdrop = false,
                shape = RoundedCornerShape(20.dp),
                cornerRadiusDp = 20.dp,
                elevated = true,
                fallbackBg = SlateDark,
                accentBorder = CyanAccent.copy(alpha = 0.55f)
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        CyanAccent.copy(alpha = if (isGlass) 0.16f else 0.14f),
                        PurpleAccent.copy(alpha = if (isGlass) 0.12f else 0.08f),
                        Color.Transparent
                    )
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyanAccent.copy(alpha = 0.20f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.50f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "Tài liệu PDF Toán 10 11 12",
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PDF_BOOK_TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$PDF_BOOK_SUBTITLE · $bookmarkCount trang đã lưu",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Primary CTA: Open / Continue PDF at last read page
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CyanAccent.copy(alpha = 0.20f))
                .border(1.dp, CyanAccent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                .clickable(onClick = onContinueReading)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("pdf_continue_reading_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (lastPage > 1) "Đọc tiếp · Trang $lastPage / $PDF_TOTAL_PAGES" else "Mở tài liệu PDF ($PDF_TOTAL_PAGES trang)",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = lastSection,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        // Quick PDF jump pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PdfQuickChip(
                label = "Bìa & Sơ đồ (Tr. 1)",
                accent = CyanAccent,
                testTag = "pdf_quick_cover",
                onClick = onOpenCover
            )
            PdfQuickChip(
                label = "Mục lục gốc PDF (Tr. 3)",
                accent = PurpleAccent,
                testTag = "pdf_quick_original_toc",
                onClick = onOpenOriginalToc
            )
        }
    }
}

@Composable
fun PdfGradeTabBar(
    selectedTab: PdfDocsTab,
    bookmarkCount: Int,
    onSelectTab: (PdfDocsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                liveBackdrop = false,
                shape = RoundedCornerShape(16.dp),
                pillShape = RoundedCornerShape(50),
                cornerRadiusDp = 16.dp,
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PdfDocsTab.entries.forEach { tab ->
            val active = tab == selectedTab
            val accent = when (tab) {
                PdfDocsTab.GRADE_10 -> Color(PdfGrade.GRADE_10.accentHex)
                PdfDocsTab.GRADE_11 -> Color(PdfGrade.GRADE_11.accentHex)
                PdfDocsTab.GRADE_12 -> Color(PdfGrade.GRADE_12.accentHex)
                PdfDocsTab.BOOKMARKS -> AmberAccent
            }
            val label = if (tab == PdfDocsTab.BOOKMARKS && bookmarkCount > 0) {
                "Lưu ($bookmarkCount)"
            } else {
                tab.label
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .liquidGlassActiveBubble(
                        active = active,
                        shape = RoundedCornerShape(12.dp),
                        fallbackActiveColor = accent.copy(alpha = 0.24f)
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .testTag("pdf_tab_${tab.key}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) accent else SlateTextSecondary,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PdfGradeSectionBanner(
    grade: PdfGrade,
    topicCount: Int,
    onOpenGradeStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(grade.accentHex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                liveBackdrop = false,
                shape = RoundedCornerShape(14.dp),
                cornerRadiusDp = 14.dp,
                elevated = false,
                fallbackBg = SlateDark,
                accentBorder = accent.copy(alpha = 0.45f)
            )
            .clickable(onClick = onOpenGradeStart)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("pdf_grade_banner_${grade.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${grade.title} · $topicCount chủ đề",
                style = MaterialTheme.typography.titleSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Trang ${grade.startPage} – ${grade.endPage} trong tài liệu DPAD",
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary
            )
        }
        Text(
            text = "Mở Tr. ${grade.startPage}",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun PdfTopicCard(
    topic: PdfTopicEntry,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(topic.accentHex)
    var expanded by remember(topic.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                liveBackdrop = false,
                shape = RoundedCornerShape(16.dp),
                cornerRadiusDp = 16.dp,
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .clickable { onOpenPage(topic.startPage) }
            .padding(14.dp)
            .testTag("pdf_topic_${topic.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = topic.indexLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = topic.pageRangeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = "Mở ${topic.title}",
                tint = accent.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (topic.subTopics.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${topic.subTopics.size} mục con — chạm để nhảy đúng trang",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextLight,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Thu gọn mục con" else "Mở rộng mục con",
                    tint = SlateTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topic.subTopics.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.10f))
                                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                                .clickable { onOpenPage(sub.page) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sub.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Tr. ${sub.page}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfSearchHitCard(
    hit: PdfTopicSearchHit,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(hit.topic.accentHex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                liveBackdrop = false,
                shape = RoundedCornerShape(14.dp),
                cornerRadiusDp = 14.dp,
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .clickable { onOpenPage(hit.targetPage) }
            .padding(14.dp)
            .testTag("pdf_search_hit_${hit.topic.id}_${hit.targetPage}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = hit.topic.grade.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "•",
                    color = SlateTextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Trang ${hit.targetPage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = hit.matchedSubTopic?.title ?: hit.topic.title,
                style = MaterialTheme.typography.titleSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (hit.matchedSubTopic != null) hit.topic.title else hit.topic.summary,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Mở trang ${hit.targetPage}",
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun PdfBookmarkPageCard(
    page1Based: Int,
    onOpenPage: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val topic = remember(page1Based) { PdfCurriculumRepository.topicForPage(page1Based) }
    val sectionTitle = remember(page1Based) { PdfCurriculumRepository.sectionLabelForPage(page1Based) }
    val accent = topic?.let { Color(it.accentHex) } ?: AmberAccent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                liveBackdrop = false,
                shape = RoundedCornerShape(14.dp),
                cornerRadiusDp = 14.dp,
                elevated = false,
                fallbackBg = SlateDark,
                accentBorder = AmberAccent.copy(alpha = 0.4f)
            )
            .clickable { onOpenPage(page1Based) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("pdf_bookmark_card_$page1Based"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 54.dp, minHeight = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tr. $page1Based",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (topic != null) {
                Text(
                    text = topic.pageRangeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary
                )
            }
        }
        IconButton(
            onClick = { onRemoveBookmark(page1Based) },
            modifier = Modifier.testTag("pdf_remove_bookmark_$page1Based")
        ) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Bỏ lưu trang $page1Based",
                tint = AmberAccent
            )
        }
    }
}

@Composable
fun PdfEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                liveBackdrop = false,
                shape = RoundedCornerShape(18.dp),
                cornerRadiusDp = 18.dp,
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SlateTextSecondary,
            modifier = Modifier.size(34.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = SlateTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary
        )
    }
}

@Composable
private fun PdfQuickChip(
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val isGlass = ActiveThemeState.current.liquidGlass.enabled
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .liquidGlassPressPhysics(enabled = isGlass, scaleOnPress = 1.05f, bloomColor = accent)
            .liquidGlassSurface(
                shape = RoundedCornerShape(10.dp),
                liveBackdrop = false,
                fallbackBg = accent.copy(alpha = 0.14f),
                fallbackBorder = accent.copy(alpha = 0.45f),
                accentBorder = accent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}
