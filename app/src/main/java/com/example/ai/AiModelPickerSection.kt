package com.example.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Dynamic Gemini model selector with:
 * - 1-tap "Đồng bộ mô hình" button to fetch all live models from the user's account
 * - Instant search filter when more than 5 models are synced
 * - Expandable catalog (top 6 shown by default so parent settings scroll stays silky smooth)
 * - Quick inline "Thêm mã mô hình tùy chỉnh" input so any model ID enabled on the user's account
 *   can be pinned and selected immediately.
 */
@Composable
fun AiModelPickerSection(
    models: List<AiSyncedModel>,
    selectedModelId: String,
    isSyncedFromAccount: Boolean,
    isSyncingModels: Boolean,
    syncStatusMessage: String?,
    syncSuccess: Boolean,
    onSyncModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onAddCustomModel: (String) -> Unit,
    onRemoveCustomModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAllModels by remember { mutableStateOf(false) }
    var showAddCustomRow by remember { mutableStateOf(false) }
    var customModelDraft by remember { mutableStateOf("") }

    val filteredModels = remember(models, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) models
        else models.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                it.modelId.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
        }
    }

    // When not searching and catalog is large (> 6), keep the currently selected model visible + top 6
    val visibleModels = remember(filteredModels, selectedModelId, showAllModels, searchQuery) {
        if (showAllModels || searchQuery.isNotBlank() || filteredModels.size <= 6) {
            filteredModels
        } else {
            val top = filteredModels.take(6).toMutableList()
            val activeModel = filteredModels.firstOrNull { it.modelId.equals(selectedModelId, ignoreCase = true) }
            if (activeModel != null && top.none { it.modelId.equals(activeModel.modelId, ignoreCase = true) }) {
                top.add(0, activeModel)
            }
            top
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header row: Title + Count pill + "Đồng bộ mô hình" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Chọn mô hình Gemini",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSyncedFromAccount) EmeraldAccent.copy(alpha = 0.18f)
                                else SlateDeepest.copy(alpha = 0.7f)
                            )
                            .border(
                                1.dp,
                                if (isSyncedFromAccount) EmeraldAccent.copy(alpha = 0.45f) else SlateBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSyncedFromAccount) "${models.size} mô hình" else "${models.size} mặc định",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSyncedFromAccount) EmeraldLight else SlateTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "Đang chọn: $selectedModelId",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = CyanAccent,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyanAccent.copy(alpha = if (isSyncingModels) 0.12f else 0.18f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !isSyncingModels) { onSyncModels() }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("ai_sync_models_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isSyncingModels) {
                    CircularProgressIndicator(
                        color = CyanAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Đang đồng bộ...",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Đồng bộ mô hình từ tài khoản",
                        tint = CyanAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Đồng bộ mô hình",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Live sync status message banner
        if (!syncStatusMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (syncSuccess) EmeraldAccent.copy(alpha = 0.12f)
                        else RoseAccent.copy(alpha = 0.12f)
                    )
                    .border(
                        1.dp,
                        if (syncSuccess) EmeraldAccent.copy(alpha = 0.4f) else RoseAccent.copy(alpha = 0.45f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("ai_model_sync_status")
            ) {
                Text(
                    text = syncStatusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (syncSuccess) EmeraldLight else RoseAccent,
                    lineHeight = 16.sp,
                    fontSize = 11.sp
                )
            }
        }

        // Instant Search Box when account synced > 5 models
        if (models.size > 5) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Tìm nhanh trong ${models.size} mô hình (vd: flash, pro, lite...)",
                        fontSize = 12.sp,
                        color = SlateTextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Xóa từ khóa tìm kiếm",
                                tint = SlateTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary,
                    cursorColor = CyanAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_model_search_input")
            )
        }

        // Model cards
        if (visibleModels.isEmpty()) {
            Text(
                text = "Không tìm thấy mô hình nào khớp với \"$searchQuery\".",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleModels.forEach { model ->
                    val active = selectedModelId.equals(model.modelId, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) CyanAccent.copy(alpha = 0.16f) else SlateDeepest.copy(alpha = 0.55f))
                            .border(
                                1.dp,
                                if (active) CyanAccent else SlateBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectModel(model.modelId) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("ai_model_preset_${model.modelId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (active) CyanAccent else SlateBorder)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) CyanAccent else SlateTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                val badgeText = when {
                                    model.source == "custom" -> "Tùy chỉnh"
                                    model.formattedContextWindow.isNotEmpty() -> model.formattedContextWindow
                                    model.source == "api" -> "AI Studio"
                                    model.source == "vertex" -> "Vertex"
                                    else -> ""
                                }
                                if (badgeText.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                if (model.source == "custom") IndigoPrimary.copy(alpha = 0.22f)
                                                else SlateDark.copy(alpha = 0.85f)
                                            )
                                            .border(
                                                1.dp,
                                                if (model.source == "custom") IndigoBright.copy(alpha = 0.45f)
                                                else SlateBorder.copy(alpha = 0.7f),
                                                RoundedCornerShape(5.dp)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (model.source == "custom") IndigoBright else SlateTextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = model.modelId,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (active) CyanAccent.copy(alpha = 0.85f) else SlateTextSecondary.copy(alpha = 0.9f),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (model.description.isNotBlank()) {
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        if (model.source == "custom") {
                            IconButton(
                                onClick = { onRemoveCustomModel(model.modelId) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("ai_remove_custom_model_${model.modelId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Xóa mô hình tùy chỉnh ${model.modelId}",
                                    tint = SlateTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expand/Collapse toggle if synced catalog has > 6 models
        if (searchQuery.isBlank() && filteredModels.size > 6) {
            val remaining = filteredModels.size - visibleModels.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateDeepest.copy(alpha = 0.5f))
                    .border(1.dp, SlateBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .clickable { showAllModels = !showAllModels }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("ai_toggle_all_models_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (showAllModels) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (showAllModels) "Thu gọn danh sách mô hình" else "Xem thêm $remaining mô hình khác (${filteredModels.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Quick Add Custom Model ID row (available immediately without needing to expand API Key section)
        if (!showAddCustomRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndigoPrimary.copy(alpha = 0.10f))
                    .border(1.dp, IndigoPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { showAddCustomRow = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("ai_open_add_custom_model_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = IndigoBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Thêm mã mô hình tùy chỉnh từ tài khoản",
                    style = MaterialTheme.typography.labelMedium,
                    color = IndigoBright,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customModelDraft,
                    onValueChange = { customModelDraft = it },
                    label = { Text("Mã mô hình (Model ID)", fontSize = 12.sp) },
                    placeholder = { Text("Nhập mã mô hình trong tài khoản...", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (customModelDraft.trim().isNotEmpty()) {
                                onAddCustomModel(customModelDraft.trim())
                                customModelDraft = ""
                                showAddCustomRow = false
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = SlateTextPrimary,
                        unfocusedTextColor = SlateTextPrimary,
                        cursorColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_custom_model_input")
                )

                Button(
                    onClick = {
                        if (customModelDraft.trim().isNotEmpty()) {
                            onAddCustomModel(customModelDraft.trim())
                            customModelDraft = ""
                            showAddCustomRow = false
                        }
                    },
                    enabled = customModelDraft.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("ai_add_custom_model_btn")
                ) {
                    Text("Thêm", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
