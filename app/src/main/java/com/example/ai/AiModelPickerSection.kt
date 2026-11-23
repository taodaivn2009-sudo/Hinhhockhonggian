package com.example.ai

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
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
 * - 1-tap "Test tất cả" live request checker that sends a minimal generateContent ping to every model,
 *   measures latency, highlights working vs quota-limited vs failed models, and floats working ones to the top
 * - Instant search & "Chỉ hiện hoạt động" filter + expandable catalog + inline custom model ID adder.
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
    isProbingAllModels: Boolean = false,
    probeProgressDone: Int = 0,
    probeProgressTotal: Int = 0,
    modelProbeResults: Map<String, AiModelProbeResult> = emptyMap(),
    modelProbeSummary: String? = null,
    onProbeAllModels: () -> Unit = {},
    tokenState: AiContextTokenState = AiContextTokenState(),
    tokenLedgers: Map<String, AiModelTokenLedger> = emptyMap(),
    onRefreshTokens: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAllModels by remember { mutableStateOf(false) }
    var onlyWorkingFilter by remember { mutableStateOf(false) }
    var showAddCustomRow by remember { mutableStateOf(false) }
    var customModelDraft by remember { mutableStateOf("") }

    val workingCount = remember(modelProbeResults) {
        modelProbeResults.values.count { it.status == AiModelProbeStatus.WORKING }
    }
    val quotaCount = remember(modelProbeResults) {
        modelProbeResults.values.count { it.status == AiModelProbeStatus.QUOTA_LIMIT }
    }
    val failedCount = remember(modelProbeResults) {
        modelProbeResults.values.count { it.status == AiModelProbeStatus.FAILED }
    }

    // Reset working-only filter if probe results were cleared or none worked
    LaunchedEffect(workingCount) {
        if (workingCount == 0 && onlyWorkingFilter) onlyWorkingFilter = false
    }

    val orderedModels = remember(models, modelProbeResults) {
        AiResponseParser.sortModelsWithProbeResults(models, modelProbeResults)
    }

    val filteredModels = remember(orderedModels, searchQuery, onlyWorkingFilter, modelProbeResults) {
        val q = searchQuery.trim()
        orderedModels.filter { model ->
            val matchesQuery = q.isEmpty() ||
                model.displayName.contains(q, ignoreCase = true) ||
                model.modelId.contains(q, ignoreCase = true) ||
                model.description.contains(q, ignoreCase = true)
            val matchesWorking = !onlyWorkingFilter ||
                modelProbeResults[model.modelId.lowercase()]?.status == AiModelProbeStatus.WORKING
            matchesQuery && matchesWorking
        }
    }

    // Keep parent settings scroll smooth by showing top 6 unless expanded, searching, or filtering working models
    val visibleModels = remember(filteredModels, selectedModelId, showAllModels, searchQuery, onlyWorkingFilter) {
        if (showAllModels || searchQuery.isNotBlank() || onlyWorkingFilter || filteredModels.size <= 6) {
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
        // Header: Title + count pill + active model
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

        // Exact Server-Synced Token Telemetry Card
        AiTokenStatusCard(
            tokenState = tokenState,
            onRefreshTokens = onRefreshTokens
        )

        // Dual Action Bar: "Đồng bộ mô hình" + "Test tất cả mô hình"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(CyanAccent.copy(alpha = if (isSyncingModels) 0.10f else 0.16f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                    .clickable(enabled = !isSyncingModels && !isProbingAllModels) { onSyncModels() }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .testTag("ai_sync_models_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSyncingModels) {
                    CircularProgressIndicator(
                        color = CyanAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Đang đồng bộ...",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Đồng bộ mô hình từ tài khoản",
                        tint = CyanAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Đồng bộ mô hình",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (isProbingAllModels) RoseAccent.copy(alpha = 0.18f)
                        else EmeraldAccent.copy(alpha = 0.16f)
                    )
                    .border(
                        1.dp,
                        if (isProbingAllModels) RoseAccent.copy(alpha = 0.65f)
                        else EmeraldAccent.copy(alpha = 0.55f),
                        RoundedCornerShape(11.dp)
                    )
                    .clickable(enabled = !isSyncingModels) { onProbeAllModels() }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .testTag("ai_probe_all_models_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isProbingAllModels) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Dừng kiểm tra mô hình",
                        tint = RoseAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (probeProgressTotal > 0) "Dừng ($probeProgressDone/$probeProgressTotal)" else "Dừng test",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Gửi request kiểm tra tất cả mô hình",
                        tint = EmeraldLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Test tất cả (${models.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        maxLines = 1
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

        // Live Batch Probe Progress + Stats + Filter Card
        AnimatedVisibility(
            visible = isProbingAllModels || !modelProbeSummary.isNullOrBlank() || modelProbeResults.isNotEmpty()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateDeepest.copy(alpha = 0.72f))
                    .border(
                        1.dp,
                        when {
                            isProbingAllModels -> CyanAccent.copy(alpha = 0.45f)
                            workingCount > 0 -> EmeraldAccent.copy(alpha = 0.45f)
                            else -> AmberAccent.copy(alpha = 0.45f)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 11.dp, vertical = 9.dp)
                    .testTag("ai_model_probe_summary_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!modelProbeSummary.isNullOrBlank()) {
                    Text(
                        text = modelProbeSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isProbingAllModels -> CyanAccent
                            workingCount > 0 -> EmeraldLight
                            else -> AmberAccent
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }

                if (isProbingAllModels) {
                    if (probeProgressTotal > 0) {
                        val fraction = (probeProgressDone.toFloat() / probeProgressTotal.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            color = EmeraldAccent,
                            trackColor = SlateBorder.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = CyanAccent,
                            trackColor = SlateBorder.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                if (modelProbeResults.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProbeStatChip(label = "Hoạt động: $workingCount", color = EmeraldLight)
                        if (quotaCount > 0) {
                            ProbeStatChip(label = "429: $quotaCount", color = AmberAccent)
                        }
                        if (failedCount > 0) {
                            ProbeStatChip(label = "Lỗi: $failedCount", color = RoseAccent)
                        }
                        Spacer(Modifier.weight(1f))
                        if (workingCount > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (onlyWorkingFilter) EmeraldAccent.copy(alpha = 0.25f)
                                        else SlateDark.copy(alpha = 0.8f)
                                    )
                                    .border(
                                        1.dp,
                                        if (onlyWorkingFilter) EmeraldAccent else SlateBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onlyWorkingFilter = !onlyWorkingFilter }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("ai_filter_working_models_btn"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Chỉ hiện mô hình hoạt động",
                                    tint = if (onlyWorkingFilter) EmeraldLight else SlateTextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (onlyWorkingFilter) "Chỉ chạy OK" else "Lọc chạy OK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (onlyWorkingFilter) EmeraldLight else SlateTextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
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
                text = if (onlyWorkingFilter) {
                    "Chưa có mô hình hoạt động nào khớp với bộ lọc hiện tại."
                } else {
                    "Không tìm thấy mô hình nào khớp với \"$searchQuery\"."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleModels.forEach { model ->
                    val active = selectedModelId.equals(model.modelId, ignoreCase = true)
                    val probe = modelProbeResults[model.modelId.lowercase()]
                    val ledger = tokenLedgers[model.modelId.lowercase()]
                    ModelOptionCard(
                        model = model,
                        active = active,
                        probe = probe,
                        ledger = ledger,
                        onSelect = { onSelectModel(model.modelId) },
                        onRemoveCustom = { onRemoveCustomModel(model.modelId) }
                    )
                }
            }
        }

        // Expand/Collapse toggle if synced catalog has > 6 models
        if (searchQuery.isBlank() && !onlyWorkingFilter && filteredModels.size > 6) {
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

        // Quick Add Custom Model ID row
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

@Composable
private fun ProbeStatChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ModelOptionCard(
    model: AiSyncedModel,
    active: Boolean,
    probe: AiModelProbeResult?,
    ledger: AiModelTokenLedger? = null,
    onSelect: () -> Unit,
    onRemoveCustom: () -> Unit
) {
    val dotColor = when {
        probe?.status == AiModelProbeStatus.WORKING -> EmeraldAccent
        probe?.status == AiModelProbeStatus.QUOTA_LIMIT -> AmberAccent
        probe?.status == AiModelProbeStatus.FAILED -> RoseAccent
        active -> CyanAccent
        else -> SlateBorder
    }
    val cardBorder = when {
        active -> CyanAccent
        probe?.status == AiModelProbeStatus.WORKING -> EmeraldAccent.copy(alpha = 0.5f)
        probe?.status == AiModelProbeStatus.QUOTA_LIMIT -> AmberAccent.copy(alpha = 0.4f)
        else -> SlateBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) CyanAccent.copy(alpha = 0.16f) else SlateDeepest.copy(alpha = 0.55f))
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("ai_model_preset_${model.modelId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
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
                    model.formattedContextWindow.isNotEmpty() -> "Ctx ${model.formattedContextWindow.removeSuffix(" token")}"
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
                if (ledger != null && ledger.usedTokensToday > 0L) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(CyanAccent.copy(alpha = 0.12f))
                            .border(1.dp, CyanAccent.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Dùng ${AiResponseParser.formatCompactTokens(ledger.usedTokensToday)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
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
            if (probe != null && probe.status != AiModelProbeStatus.TESTING && probe.detail.isNotBlank()) {
                val detailColor = when (probe.status) {
                    AiModelProbeStatus.WORKING -> EmeraldLight
                    AiModelProbeStatus.QUOTA_LIMIT -> AmberAccent
                    AiModelProbeStatus.FAILED -> RoseAccent
                    AiModelProbeStatus.TESTING -> CyanAccent
                }
                Text(
                    text = probe.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = detailColor,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            } else if (model.description.isNotBlank()) {
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

        // Right-side live probe badge
        if (probe != null) {
            val pillColor = when (probe.status) {
                AiModelProbeStatus.TESTING -> CyanAccent
                AiModelProbeStatus.WORKING -> EmeraldLight
                AiModelProbeStatus.QUOTA_LIMIT -> AmberAccent
                AiModelProbeStatus.FAILED -> RoseAccent
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(pillColor.copy(alpha = 0.15f))
                    .border(1.dp, pillColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("ai_model_probe_badge_${model.modelId}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (probe.status == AiModelProbeStatus.TESTING) {
                    CircularProgressIndicator(
                        color = CyanAccent,
                        strokeWidth = 1.8.dp,
                        modifier = Modifier.size(11.dp)
                    )
                } else if (probe.status == AiModelProbeStatus.WORKING) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Hoạt động",
                        tint = EmeraldLight,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = probe.shortBadgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = pillColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        if (model.source == "custom") {
            IconButton(
                onClick = onRemoveCustom,
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
