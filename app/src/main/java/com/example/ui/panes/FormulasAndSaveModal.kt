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
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale

@Composable
fun FormulasPane(uiState: GeometryUiState) {
    val m = uiState.scene.metrics
    fun f2(v: Float) = String.format(Locale.US, "%.2f", v)

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
    onExportPngClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportJsonClick: () -> Unit
) {
    if (!uiState.saveModalOpen) return

    Dialog(
        onDismissRequest = { viewModel.closeSaveModal() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SlateDark)
                .border(1.dp, SlateMuted, RoundedCornerShape(14.dp))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Đặt tên File & Lưu / Xuất", style = MaterialTheme.typography.titleSmall, color = SlateTextPrimary)
                IconButton(onClick = { viewModel.closeSaveModal() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng hộp thoại", tint = SlateTextSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: File Name & Direct Actions
                SettingBox(
                    title = "1. Đặt tên File (Gắn vào ảnh PNG & file JSON)",
                    titleColor = IndigoSoft,
                    borderColor = IndigoBright,
                    containerColor = IndigoPrimary.copy(alpha = 0.14f)
                ) {
                    OutlinedTextField(
                        value = uiState.customFileName,
                        onValueChange = { viewModel.updateCustomFileName(it) },
                        placeholder = { Text("VD: Chop-SABCD-Cau45", color = SlateTextSecondary) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = SlateTextPrimary, fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            focusedBorderColor = IndigoBright,
                            unfocusedBorderColor = SlateMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("custom_filename_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onExportPngClick,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 9.dp, horizontal = 8.dp),
                            modifier = Modifier.weight(1f).testTag("export_png_btn")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tải ảnh .PNG", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onExportJsonClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 9.dp, horizontal = 8.dp),
                            modifier = Modifier.weight(1f).testTag("export_json_btn")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tải .JSON", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.saveNamedSlot() },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                            modifier = Modifier.weight(1f).testTag("save_named_slot_btn")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Lưu trong app", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onImportJsonClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateMuted),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                            modifier = Modifier.weight(1f).testTag("import_json_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mở .JSON", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Section 2: Saved Named Slots
                SettingBox(title = "2. Các bài đã lưu trong máy (1 chạm mở lại)", titleColor = CyanAccent) {
                    if (uiState.savedSlots.isEmpty()) {
                        Text(
                            text = "Chưa có bài nào được lưu. Đặt tên ở mục 1 rồi bấm \"Lưu trong app\" để lưu nhanh!",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
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
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
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
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Mở", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteNamedSlot(slot.name) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(RoseAccent.copy(alpha = 0.22f))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Xóa ${slot.name}", tint = RoseLight, modifier = Modifier.size(16.dp))
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
