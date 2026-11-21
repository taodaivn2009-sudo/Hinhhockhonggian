package com.example.docs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
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
import com.example.geo2d.MathWorkspace
import com.example.geo2d.WorkspaceModeSwitch
import com.example.ui.theme.*
import java.util.Locale

/**
 * Dedicated "Tài liệu" (Math Reference & Study Sheet) workspace featuring Chapter I:
 * Hàm Số Lượng Giác (complete formulas, interactive unit circle, quadrant sign table,
 * special-angle diagrams, degree/radian converter, and instant search).
 */
@Composable
fun MathDocumentsScreen(
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    onPlotTrigIn2D: (String) -> Unit,
    hasUpdateBadge: Boolean = false
) {
    var selectedCategory by remember { mutableStateOf(TrigDocCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(selectedCategory, searchQuery) {
        TrigReferenceRepository.filterGroups(selectedCategory, searchQuery)
    }

    val showUnitCircleAndTable = remember(selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        when {
            q.isNotEmpty() -> listOf("đường tròn", "dấu", "phần tư", "rad", "độ", "sin", "cos", "tan", "cot").any { q.contains(it) || it.contains(q) }
            selectedCategory == TrigDocCategory.ALL || selectedCategory == TrigDocCategory.UNIT_CIRCLE -> true
            else -> false
        }
    }

    val showConverter = remember(selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        when {
            q.isNotEmpty() -> q.contains("rad") || q.contains("độ") || q.contains("đổi")
            selectedCategory == TrigDocCategory.ALL || selectedCategory == TrigDocCategory.BASIC || selectedCategory == TrigDocCategory.UNIT_CIRCLE -> true
            else -> false
        }
    }

    BackHandler {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else if (selectedCategory != TrigDocCategory.ALL) {
            selectedCategory = TrigDocCategory.ALL
        } else {
            onSwitchWorkspace(MathWorkspace.SPACE_3D)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("math_documents_screen")
    ) {
        LiquidGlassBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Unified Top Navigation Bar (3D | 2D | Tài liệu | Cài đặt)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Tìm nhanh: sin 2a, hạ bậc, góc bù, tổng thành tích...",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa tìm kiếm", tint = SlateTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = SlateTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SlateDark,
                    unfocusedContainerColor = SlateDark,
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = SlateBorder,
                    cursorColor = CyanAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .testTag("docs_search_input")
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrigDocCategory.entries.forEach { cat ->
                    val active = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) AmberAccent.copy(alpha = 0.22f) else SlateDark)
                            .border(1.dp, if (active) AmberAccent else SlateBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                            .testTag("docs_cat_${cat.name.lowercase()}")
                    ) {
                        Text(
                            text = cat.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) AmberAccent else SlateTextLight,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Main Scrollable Reference Content (Adaptive max width for tablets/foldables)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero Mindmap Card
                if (searchQuery.isBlank()) {
                    item(key = "mindmap_header") {
                        TrigMindmapOverviewCard(
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            modifier = Modifier.widthIn(max = 680.dp)
                        )
                    }
                }

                // Quick Plot in 2D strip
                if (searchQuery.isBlank() && selectedCategory == TrigDocCategory.ALL) {
                    item(key = "quick_plot_trig") {
                        QuickPlotTrigStrip(
                            onPlotTrigIn2D = onPlotTrigIn2D,
                            modifier = Modifier.widthIn(max = 680.dp)
                        )
                    }
                }

                // Formula Groups
                items(filteredGroups, key = { it.id }) { group ->
                    TrigFormulaGroupCard(
                        group = group,
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }

                // Interactive Degree <-> Radian Converter
                if (showConverter) {
                    item(key = "deg_rad_converter") {
                        DegreeRadianConverterCard(modifier = Modifier.widthIn(max = 680.dp))
                    }
                }

                // Interactive Unit Circle & Quadrant Sign Table
                if (showUnitCircleAndTable) {
                    item(key = "unit_circle") {
                        InteractiveUnitCircleCard(modifier = Modifier.widthIn(max = 680.dp))
                    }
                    item(key = "quadrant_sign_table") {
                        QuadrantSignTableCard(modifier = Modifier.widthIn(max = 680.dp))
                    }
                }

                // Empty Search State
                if (filteredGroups.isEmpty() && !showUnitCircleAndTable && !showConverter) {
                    item(key = "empty_search") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SlateDark)
                                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(32.dp))
                            Text("Không tìm thấy công thức phù hợp", style = MaterialTheme.typography.titleSmall, color = SlateTextPrimary)
                            Text("Thử tìm: sin 2a, cos, tan, hạ bậc, góc đối, góc bù...", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPlotTrigStrip(
    onPlotTrigIn2D: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val trigDemos = listOf("sin(x)", "cos(x)", "tan(x)", "sin(2x)")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EmeraldDark.copy(alpha = 0.16f))
            .border(1.dp, EmeraldAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(18.dp))
        Text("Vẽ nhanh 2D:", style = MaterialTheme.typography.labelSmall, color = EmeraldLight, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            trigDemos.forEach { expr ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateDeepest)
                        .border(1.dp, EmeraldAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onPlotTrigIn2D(expr) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("docs_plot_$expr")
                ) {
                    Text(
                        text = "y = $expr",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TrigFormulaGroupCard(
    group: TrigFormulaGroup,
    modifier: Modifier = Modifier
) {
    val accent = Color(group.accentHex)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackBg = SlateDark,
                accentBorder = accent.copy(alpha = 0.52f)
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            group.formulas.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateDeepest)
                        .border(1.dp, SlateSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.lhs}  =  ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            color = SlateTextLight,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = item.rhs,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val subNote = listOfNotNull(
                        item.condition?.let { "ĐK: $it" },
                        item.note
                    ).joinToString("  •  ")
                    if (subNote.isNotEmpty()) {
                        Text(
                            text = subNote,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = SlateTextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }

        group.mnemonic?.let { tip ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(15.dp))
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextPrimary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DegreeRadianConverterCard(modifier: Modifier = Modifier) {
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
