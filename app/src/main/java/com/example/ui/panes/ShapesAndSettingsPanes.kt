package com.example.ui.panes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GeometryUiState
import com.example.engine.ShapeCategory
import com.example.engine.ShapeType
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale

@Composable
fun SettingBox(
    title: String? = null,
    titleColor: Color = IndigoLight,
    borderColor: Color = SlateBorder,
    containerColor: Color = SlateSurface.copy(alpha = 0.55f),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = titleColor,
                letterSpacing = 0.5.sp
            )
        }
        content()
    }
}

@Composable
fun CompactDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.first == selected }?.second ?: selected

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 38.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(SlateDark)
                    .border(1.dp, SlateMuted, RoundedCornerShape(7.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Chọn $label",
                    tint = SlateTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = SlateSurface
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (key == selected) CyanAccent else SlateTextPrimary
                            )
                        },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ShapesPane(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShapeCategory.entries.forEach { cat ->
            val isOpen = cat in uiState.openCategories
            val shapesInCat = ShapeType.entries.filter { it.category == cat }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface.copy(alpha = 0.42f))
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_${cat.name.lowercase()}")
                        .background(SlateDark.copy(alpha = 0.78f))
                        .clickable { viewModel.toggleShapeCategory(cat) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = "Mở rộng nhóm ${cat.title}",
                            tint = if (isOpen) IndigoSoft else SlateTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isOpen) IndigoSoft else SlateTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = cat.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurface)
                            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                AnimatedVisibility(visible = isOpen) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        shapesInCat.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pair.forEach { shape ->
                                    val active = uiState.snapshot.shape == shape
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("shape_${shape.key}")
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) IndigoPrimary.copy(alpha = 0.32f) else SlateSurface)
                                            .border(
                                                width = if (active) 1.5.dp else 1.dp,
                                                color = if (active) IndigoBright else SlateBorder,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.selectShape(shape) }
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = shape.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) SlateTextPrimary else SlateTextLight,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = shape.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateTextSecondary,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPane(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    onImportJsonClick: () -> Unit
) {
    val snap = uiState.snapshot
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // Save / Export quick actions
        SettingBox(
            title = "Đặt tên File & Lưu / Xuất",
            titleColor = EmeraldLight,
            borderColor = EmeraldDark,
            containerColor = EmeraldDark.copy(alpha = 0.16f)
        ) {
            Button(
                onClick = { viewModel.openSaveModal() },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_open_save_modal_btn")
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Đặt tên File / Tải PNG / Lưu JSON", style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = onImportJsonClick,
                colors = ButtonDefaults.buttonColors(containerColor = SlateMuted),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Mở từ file (.json) trong máy", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = { showResetConfirm = true },
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseAccent),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = RoseAccent.copy(alpha = 0.16f),
                    contentColor = RoseLight
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Đặt lại hình về mặc định ban đầu", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Dimensions Sliders
        SettingBox(title = "Kích thước chuẩn ban đầu") {
            DimensionSliderRow(
                label = if (snap.shape.isRound) "Đường kính (2R):" else "Cạnh đáy (a):",
                value = snap.a,
                range = 1.5f..6.5f,
                onValueChange = { viewModel.updateParamA(it) },
                onValueChangeFinished = { viewModel.commitSliderChange() }
            )
            if (snap.shape.hasB) {
                DimensionSliderRow(
                    label = "Chiều rộng đáy (b):",
                    value = snap.b,
                    range = 1.5f..6.5f,
                    onValueChange = { viewModel.updateParamB(it) },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
            if (snap.shape.hasH) {
                DimensionSliderRow(
                    label = "Chiều cao (h):",
                    value = snap.h,
                    range = 2.0f..7.5f,
                    onValueChange = { viewModel.updateParamH(it) },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
        }

        // Display Switches (SGK standard)
        SettingBox(title = "Hiển thị chuẩn SGK") {
            SettingCheckRow("Hiện lưới mặt phẳng đáy", snap.showGrid) { viewModel.toggleGrid(it) }
            SettingCheckRow("Nét đứt tự động cho cạnh khuất", snap.dashed) { viewModel.toggleDashed(it) }
            SettingCheckRow("Hiện tên các điểm (S, A, B, M...)", snap.labels) { viewModel.toggleLabels(it) }
            SettingCheckRow("Hiện đường cao & góc vuông", snap.altitude) { viewModel.toggleAltitude(it) }
            SettingCheckRow("Tô màu bề mặt khối 3D", snap.faces) { viewModel.toggleFaces(it) }
            SettingCheckRow("Tự động xoay hình", uiState.autoRotate) { viewModel.toggleAutoRotate(it) }
        }

        // Cross-section plane
        SettingBox(title = "Mặt cắt ngang song song đáy", titleColor = AmberAccent) {
            SettingCheckRow("Bật mặt cắt ngang", snap.showSection) { viewModel.toggleSection(it) }
            AnimatedVisibility(visible = snap.showSection) {
                DimensionSliderRow(
                    label = "Vị trí mặt cắt (k = h'/h):",
                    value = snap.secRatio,
                    range = 0.15f..0.85f,
                    decimals = 2,
                    onValueChange = { viewModel.updateSectionRatio(it) },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = SlateDark,
            title = { Text("Đặt lại mặc định?", color = SlateTextPrimary) },
            text = { Text("Toàn bộ đỉnh đã kéo, đường kẻ thêm và cài đặt hiện tại sẽ được đưa về trạng thái mặc định ban đầu.", color = SlateTextLight) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    viewModel.resetAllToDefault()
                }) {
                    Text("Đặt lại", color = RoseAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Hủy", color = SlateTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DimensionSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int = 1,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = SlateTextLight)
            Text(
                text = String.format(Locale.US, "%.${decimals}f", value),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = IndigoSoft,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = IndigoBright,
                activeTrackColor = IndigoBright,
                inactiveTrackColor = SlateBorder
            ),
            modifier = Modifier.height(30.dp)
        )
    }
}

@Composable
fun SettingCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = IndigoBright,
                uncheckedColor = SlateMuted,
                checkmarkColor = SlateTextPrimary
            ),
            modifier = Modifier.size(28.dp)
        )
    }
}
