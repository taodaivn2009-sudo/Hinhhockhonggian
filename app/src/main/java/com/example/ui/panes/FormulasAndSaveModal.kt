package com.example.ui.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.GeometryUiState
import com.example.ui.VerifyModeCard3D
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale

@Composable
fun FormulasPane(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    val m = uiState.scene.metrics
    val hide = uiState.verifyHideAnswers
    fun f2(v: Float) = if (hide) "???" else String.format(Locale.US, "%.2f", v)

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = m.title,
                style = MaterialTheme.typography.titleSmall,
                color = IndigoSoft,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Tự động theo đỉnh",
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(SkyBlue)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // 2x2 Stat Grid
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Diện tích đáy (Sđáy)", f2(m.sDay), CyanAccent, Modifier.weight(1f))
            StatCard("DT xung quanh (Sxq)", f2(m.sXq), CyanAccent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("DT toàn phần (Stp)", f2(m.sTp), CyanAccent, Modifier.weight(1f))
            StatCard("Thể tích thực (V)", f2(m.vol), AmberAccent, Modifier.weight(1f))
        }

        // Live Dimensions Breakdown
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateDeepest)
                .border(1.dp, SlateSurface, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            m.dimensionRows.forEach { (label, value) ->
                CalcResultRow(label, value, CyanAccent)
            }
        }

        // Formula notes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurface.copy(alpha = 0.55f))
                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            m.formulaLines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall, color = SlateTextLight)
            }
        }

        // Interactive Verify Mode (Student self-check)
        VerifyModeCard3D(uiState = uiState, viewModel = viewModel)
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateDark)
            .border(1.dp, SlateSurface, RoundedCornerShape(8.dp))
            .padding(9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SaveExportModal(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    onPickExportFolderClick: () -> Unit,
    onExportPngClick: (forcePicker: Boolean) -> Unit,
    onExportSvgClick: (forcePicker: Boolean) -> Unit,
    onExportObjClick: (forcePicker: Boolean) -> Unit,
    onExportJsonClick: (forcePicker: Boolean) -> Unit,
    onImportJsonClick: () -> Unit
) {
    if (!uiState.saveModalOpen) return
    val hasPinnedFolder = !uiState.exportFolderUri.isNullOrBlank()

    Dialog(
        onDismissRequest = { viewModel.closeSaveModal() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateDark)
                .border(1.dp, SlateMuted, RoundedCornerShape(16.dp))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateSurface)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, tint = IndigoSoft, modifier = Modifier.size(19.dp))
                    Text("Lưu & Xuất File Khối Hình", style = MaterialTheme.typography.titleSmall, color = SlateTextPrimary)
                }
                IconButton(onClick = { viewModel.closeSaveModal() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng hộp thoại", tint = SlateTextSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: File Name & External Save Location
                SettingBox(
                    title = "1. Tên File & Vị Trí Lưu Ngoài Máy",
                    titleColor = IndigoSoft,
                    borderColor = IndigoBright,
                    containerColor = IndigoPrimary.copy(alpha = 0.14f)
                ) {
                    OutlinedTextField(
                        value = uiState.customFileName,
                        onValueChange = { viewModel.updateCustomFileName(it) },
                        placeholder = { Text("VD: Hinh-Tru-Cau12", color = SlateTextSecondary) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = SlateTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            focusedBorderColor = IndigoBright,
                            unfocusedBorderColor = SlateMuted
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_filename_input")
                    )

                    // External Save Location Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateDeepest)
                            .border(
                                1.dp,
                                if (hasPinnedFolder) EmeraldAccent.copy(alpha = 0.55f) else SkyBlue.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (hasPinnedFolder) Icons.Default.Folder else Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = if (hasPinnedFolder) EmeraldAccent else CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasPinnedFolder) "Thư mục lưu mặc định" else "Chọn vị trí khi bấm lưu",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hasPinnedFolder) EmeraldAccent else CyanAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.exportFolderLabel
                                        ?: "Mỗi lần lưu sẽ mở trình quản lý tệp để bạn chọn thư mục tùy ý",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextLight,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onPickExportFolderClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasPinnedFolder) SlateMuted else SkyBlue
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .testTag("pick_export_folder_btn")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (hasPinnedFolder) "Đổi vị trí lưu" else "Chọn thư mục lưu",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (hasPinnedFolder) {
                                OutlinedButton(
                                    onClick = { viewModel.clearExportFolder() },
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateMuted),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("clear_export_folder_btn")
                                ) {
                                    Text("Hỏi mỗi lần", style = MaterialTheme.typography.labelSmall, color = SlateTextLight)
                                }
                            }
                        }
                    }
                }

                // Section 2: Export External Files (4 Formats + Transparent Option)
                SettingBox(
                    title = "2. Xuất File Khối Hình Ra Ngoài",
                    titleColor = EmeraldAccent,
                    borderColor = EmeraldDark.copy(alpha = 0.65f),
                    containerColor = SlateSurface.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(SlateDeepest)
                            .border(1.dp, if (uiState.exportTransparentBg) CyanAccent.copy(alpha = 0.6f) else SlateBorder, RoundedCornerShape(9.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Xuất nền trong suốt (không lưới)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (uiState.exportTransparentBg) CyanAccent else SlateTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Áp dụng cho ảnh .PNG & vector .SVG để chèn slide/Word sắc nét",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = uiState.exportTransparentBg,
                            onCheckedChange = { viewModel.toggleExportTransparentBg() },
                            modifier = Modifier.testTag("export_transparent_3d_switch")
                        )
                    }

                    ExportFormatRow(
                        icon = Icons.Default.PhotoCamera,
                        title = if (uiState.exportTransparentBg) "Lưu ảnh trong suốt (.PNG)" else "Lưu ảnh khối 3D (.PNG)",
                        subtitle = if (uiState.exportTransparentBg) "Nền trong suốt không dính lưới, nét đậm rõ trên nền trắng" else "Ảnh sắc nét độ phân giải cao kèm thẻ thông số V, Stp, h",
                        accentColor = EmeraldDark,
                        hasPinnedFolder = hasPinnedFolder,
                        testTag = "export_png_btn",
                        onPrimaryClick = { onExportPngClick(false) },
                        onPickOtherClick = { onExportPngClick(true) }
                    )
                    ExportFormatRow(
                        icon = Icons.Default.Draw,
                        title = "Lưu ảnh Vector (.SVG)",
                        subtitle = "Đồ họa vector phóng to vô hạn không vỡ nét cho tài liệu & slide",
                        accentColor = Color(0xFF7C3AED),
                        hasPinnedFolder = hasPinnedFolder,
                        testTag = "export_svg_btn",
                        onPrimaryClick = { onExportSvgClick(false) },
                        onPickOtherClick = { onExportSvgClick(true) }
                    )
                    ExportFormatRow(
                        icon = Icons.Default.ViewInAr,
                        title = "Lưu mô hình 3D (.OBJ)",
                        subtitle = "Lưới 3D thật mở được trong Blender, 3D Viewer, CAD",
                        accentColor = IndigoBright,
                        hasPinnedFolder = hasPinnedFolder,
                        testTag = "export_obj_btn",
                        onPrimaryClick = { onExportObjClick(false) },
                        onPickOtherClick = { onExportObjClick(true) }
                    )
                    ExportFormatRow(
                        icon = Icons.Default.Description,
                        title = "Lưu file dự án (.JSON)",
                        subtitle = "Giữ nguyên đỉnh, nét vẽ, Oxyz để chia sẻ hoặc mở sửa tiếp",
                        accentColor = SkyBlue,
                        hasPinnedFolder = hasPinnedFolder,
                        testTag = "export_json_btn",
                        onPrimaryClick = { onExportJsonClick(false) },
                        onPickOtherClick = { onExportJsonClick(true) }
                    )
                }

                // Section 3: In-App Quick Slots & Import External File
                SettingBox(title = "3. Mở File Ngoài & Bài Đã Lưu Trong App", titleColor = CyanAccent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.saveNamedSlot() },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("save_named_slot_btn")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Lưu trong app", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onImportJsonClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateMuted),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("import_json_btn")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Mở .JSON ngoài", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (uiState.savedSlots.isEmpty()) {
                        Text(
                            text = "Chưa có bài lưu nhanh nào trong app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.savedSlots.forEach { slot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateSurface)
                                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = slot.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CyanAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${slot.shapeTitle} • ${slot.savedAt}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateTextSecondary,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Button(
                                            onClick = { viewModel.loadNamedSlot(slot) },
                                            colors = ButtonDefaults.buttonColors(containerColor = BlueAxis),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Mở", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteNamedSlot(slot.name) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(RoseAccent.copy(alpha = 0.22f))
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Xóa ${slot.name}",
                                                tint = RoseLight,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
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
private fun ExportFormatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    hasPinnedFolder: Boolean,
    testTag: String,
    onPrimaryClick: () -> Unit,
    onPickOtherClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrimaryClick,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .testTag(testTag)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (hasPinnedFolder) {
            IconButton(
                onClick = onPickOtherClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateDark)
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .testTag("${testTag}_pick_other")
            ) {
                Icon(
                    imageVector = Icons.Default.DriveFileMove,
                    contentDescription = "Chọn vị trí lưu khác cho $title",
                    tint = SlateTextLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
