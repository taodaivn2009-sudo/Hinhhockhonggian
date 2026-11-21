package com.example.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun GradeTabSelector(
    selectedTab: DocsOverviewTab,
    favoriteCount: Int,
    onSelectTab: (DocsOverviewTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ActiveThemeState.current
    val isGlass = palette.liquidGlass.enabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(14.dp),
                pillShape = CircleShape,
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocsOverviewTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val tabAccent = when (tab) {
                DocsOverviewTab.GRADE_10 -> Color(MathGrade.GRADE_10.accentHex)
                DocsOverviewTab.GRADE_11 -> Color(MathGrade.GRADE_11.accentHex)
                DocsOverviewTab.GRADE_12 -> Color(MathGrade.GRADE_12.accentHex)
                DocsOverviewTab.FAVORITES -> RoseAccent
            }
            val chipShape = if (isGlass) CircleShape else RoundedCornerShape(10.dp)
            val contentTint = when {
                selected && isGlass && !palette.isDark -> SlateTextPrimary
                selected -> Color.White
                isGlass && palette.isDark -> Color.White.copy(alpha = 0.82f)
                else -> SlateTextSecondary
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .liquidGlassActiveBubble(
                        active = selected,
                        shape = chipShape,
                        fallbackActiveColor = tabAccent
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 4.dp, vertical = 7.dp)
                    .testTag("docs_tab_${tab.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (tab == DocsOverviewTab.FAVORITES) {
                        Icon(
                            imageVector = if (selected) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Yêu thích",
                            tint = if (selected) contentTint else AmberAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    val displayLabel = if (tab == DocsOverviewTab.FAVORITES && favoriteCount > 0) {
                        "$favoriteCount"
                    } else {
                        tab.label
                    }
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentTint,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun GradeHeroBanner(
    grade: MathGrade,
    chapterCount: Int,
    formulaCount: Int,
    modifier: Modifier = Modifier
) {
    val accent = Color(grade.accentHex)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                elevated = false,
                fallbackBg = SlateDark,
                accentBorder = accent.copy(alpha = 0.60f)
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                )
            )
            .padding(16.dp)
            .testTag("docs_hero_${grade.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.22f))
                        .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "GDPT 2018",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$chapterCount chương • $formulaCount công thức",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = SlateTextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = grade.title,
            style = MaterialTheme.typography.titleMedium,
            color = SlateTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = grade.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun ChapterOverviewCard(
    chapter: MathChapter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(chapter.accentHex)
    val has2D = chapter.quickActions.any { it is DocQuickAction.Plot2D }
    val has3D = chapter.quickActions.any { it is DocQuickAction.Open3D }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                elevated = false,
                fallbackBg = SlateDark,
                accentBorder = accent.copy(alpha = 0.48f)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("docs_chapter_card_${chapter.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.20f))
                        .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = chapter.chapterNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (chapter.hasTrigInteractiveTools) {
                    BadgeTag("Đường tròn LG", CyanAccent)
                }
                if (has2D) {
                    BadgeTag("Đồ thị 2D", EmeraldAccent)
                }
                if (has3D) {
                    BadgeTag("Mô hình 3D", IndigoBright)
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Mở ${chapter.title}",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = chapter.title,
            style = MaterialTheme.typography.titleSmall,
            color = SlateTextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = chapter.summary,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${chapter.groups.size} chuyên đề • ${chapter.formulaCount} công thức",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Xem chi tiết →",
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary
            )
        }
    }
}

@Composable
private fun BadgeTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ChapterDetailHeader(
    chapter: MathChapter,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(chapter.accentHex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                elevated = false,
                fallbackBg = SlateDark,
                accentBorder = accent.copy(alpha = 0.55f)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("docs_chapter_detail_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("docs_chapter_back_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại danh sách chương",
                tint = SlateTextPrimary
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${chapter.grade.shortLabel} • ${chapter.chapterNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• ${chapter.formulaCount} công thức",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = SlateTextSecondary
                )
            }
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DocQuickActionsStrip(
    actions: List<DocQuickAction>,
    onTriggerAction: (DocQuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return
    val has3D = actions.any { it is DocQuickAction.Open3D }
    val stripAccent = if (has3D) IndigoBright else EmeraldAccent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(stripAccent.copy(alpha = 0.12f))
            .border(1.dp, stripAccent.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (has3D) Icons.Default.ViewInAr else Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                tint = stripAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Mở nhanh mô phỏng trực quan:",
                style = MaterialTheme.typography.labelSmall,
                color = stripAccent,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEachIndexed { idx, action ->
                val is3D = action is DocQuickAction.Open3D
                val chipColor = if (is3D) IndigoBright else EmeraldAccent
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateDeepest.copy(alpha = 0.85f))
                        .border(1.dp, chipColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .clickable { onTriggerAction(action) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("docs_quick_action_$idx"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (is3D) Icons.Default.Category else Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = chipColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun MathFormulaGroupCard(
    group: MathFormulaGroup,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(group.accentHex)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackBg = SlateDark,
                accentBorder = accent.copy(alpha = 0.50f)
            )
            .padding(14.dp)
            .testTag("docs_group_${group.id}"),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = group.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            if (group.specialKind != null) {
                SpecialAngleMiniDiagram(kind = group.specialKind, accent = accent)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            group.formulas.forEach { item ->
                FormulaRowBox(
                    item = item,
                    accent = accent,
                    isFavorite = item.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(item.id) }
                )
            }
        }

        group.mnemonic?.let { tip ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextPrimary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun FormulaRowBox(
    item: MathFormulaItem,
    accent: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val isGlass = ActiveThemeState.current.liquidGlass.enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(SlateDeepest.copy(alpha = if (isGlass) 0.58f else 1f))
            .border(1.dp, SlateSurface, RoundedCornerShape(11.dp))
            .padding(start = 11.dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.lhs,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = SlateTextLight,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
            Text(
                text = "= ${item.rhs}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                color = accent,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp
            )
            val subNote = listOfNotNull(
                item.condition?.let { "ĐK: $it" },
                item.note
            ).joinToString("  •  ")
            if (subNote.isNotEmpty()) {
                Text(
                    text = subNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
            }
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(48.dp)
                .testTag("docs_fav_btn_${item.id}")
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Bỏ lưu công thức" else "Lưu công thức yêu thích",
                tint = if (isFavorite) AmberAccent else SlateTextSecondary.copy(alpha = 0.72f),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
fun FormulaSearchHitCard(
    hit: FormulaSearchHit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradeAccent = Color(hit.chapter.grade.accentHex)
    val groupAccent = Color(hit.group.accentHex)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(15.dp),
                fallbackBg = SlateDark,
                accentBorder = groupAccent.copy(alpha = 0.45f)
            )
            .clickable(onClick = onOpenChapter)
            .padding(12.dp)
            .testTag("docs_hit_${hit.formula.id}"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BadgeTag(hit.chapter.grade.shortLabel, gradeAccent)
                Text(
                    text = "${hit.chapter.chapterNumber}: ${hit.chapter.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Mở chương →",
                style = MaterialTheme.typography.labelSmall,
                color = gradeAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = hit.group.title,
            style = MaterialTheme.typography.labelMedium,
            color = groupAccent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        FormulaRowBox(
            item = hit.formula,
            accent = groupAccent,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite
        )
    }
}

@Composable
fun DocsEmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(34.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = SlateTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun DegreeRadianConverterCard(modifier: Modifier = Modifier) {
    var degInput by remember { mutableStateOf("60") }
    var radInput by remember { mutableStateOf("1.0472") }

    val parsedDeg = degInput.replace(',', '.').toDoubleOrNull()
    val piFrac = parsedDeg?.let { TrigReferenceRepository.formatDegreesAsPiFraction(it) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackBg = SlateDark,
                accentBorder = CyanAccent.copy(alpha = 0.50f)
            )
            .padding(14.dp)
            .testTag("trig_deg_rad_converter"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ĐỔI NHANH ĐỘ (°) ↔ RADIAN (rad)",
            style = MaterialTheme.typography.labelLarge,
            color = CyanAccent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "1 rad = 180°/π   •   1° = π/180 rad",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = SlateTextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = degInput,
                onValueChange = { next ->
                    degInput = next
                    val d = next.replace(',', '.').toDoubleOrNull()
                    if (d != null && d.isFinite()) {
                        radInput = String.format(Locale.US, "%.4f", TrigReferenceRepository.degreesToRadians(d))
                    }
                },
                label = { Text("Độ (°)", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily, color = SlateTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = SlateBorder,
                    focusedContainerColor = SlateDeepest,
                    unfocusedContainerColor = SlateDeepest
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(22.dp))
            OutlinedTextField(
                value = radInput,
                onValueChange = { next ->
                    radInput = next
                    val r = next.replace(',', '.').toDoubleOrNull()
                    if (r != null && r.isFinite()) {
                        degInput = String.format(Locale.US, "%.2f", TrigReferenceRepository.radiansToDegrees(r))
                    }
                },
                label = { Text("Radian (rad)", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily, color = SlateTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldAccent,
                    unfocusedBorderColor = SlateBorder,
                    focusedContainerColor = SlateDeepest,
                    unfocusedContainerColor = SlateDeepest
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
        }
        if (piFrac != null) {
            Text(
                text = "Dạng chính xác theo π:  ${degInput.trim()}° = $piFrac rad",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = AmberAccent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
