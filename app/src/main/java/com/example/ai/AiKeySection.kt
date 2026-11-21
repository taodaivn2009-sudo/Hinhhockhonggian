package com.example.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Reusable "Mục Key AI" section embedded inside BOTH the AI Workspace ("Mục Key" tab)
 * and App Settings ("Key AI (Gemini API)").
 */
@Composable
fun AiKeySetupSection(
    keyConfig: AiKeyConfig,
    isVerifying: Boolean,
    statusMessage: String?,
    statusSuccess: Boolean,
    onSaveAndVerify: (apiKey: String, modelId: String) -> Unit,
    onSaveOnly: (apiKey: String, modelId: String) -> Unit,
    onSelectModel: (String) -> Unit,
    onClearKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var keyDraft by remember(keyConfig.apiKey) { mutableStateOf(keyConfig.apiKey) }
    var modelDraft by remember(keyConfig.modelId) { mutableStateOf(keyConfig.effectiveModelId) }
    var showKeyPlain by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (keyConfig.hasKey) EmeraldAccent.copy(alpha = 0.14f)
                    else AmberAccent.copy(alpha = 0.14f)
                )
                .border(
                    1.dp,
                    if (keyConfig.hasKey) EmeraldAccent.copy(alpha = 0.45f)
                    else AmberAccent.copy(alpha = 0.45f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (keyConfig.hasKey) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (keyConfig.hasKey) EmeraldAccent else AmberAccent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (keyConfig.hasKey) "Đã cấu hình API Key (${keyConfig.maskedKey})" else "Chưa nhập API Key AI",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (keyConfig.hasKey) {
                        "Mô hình đang dùng: ${keyConfig.effectiveModelId} · Sẵn sàng hỏi tài liệu & chụp đề vẽ hình"
                    } else {
                        "Dán Gemini API Key của bạn vào ô bên dưới để kích hoạt Trợ lý AI & Quét đề bài vẽ hình."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary
                )
            }
        }

        // API Key input field
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            label = { Text("Gemini API Key") },
            placeholder = { Text("Dán API Key (vd: AIzaSy...)") },
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                IconButton(
                    onClick = { showKeyPlain = !showKeyPlain },
                    modifier = Modifier.testTag("ai_key_toggle_visibility")
                ) {
                    Icon(
                        imageVector = if (showKeyPlain) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showKeyPlain) "Ẩn API Key" else "Hiện API Key",
                        tint = SlateTextSecondary
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (showKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = SlateBorder,
                focusedTextColor = SlateTextPrimary,
                unfocusedTextColor = SlateTextPrimary,
                cursorColor = CyanAccent
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_api_key_input")
        )

        // Action Buttons Row: Save & Verify + Save Only + Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSaveAndVerify(keyDraft, modelDraft) },
                enabled = keyDraft.trim().isNotEmpty() && !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp)
                    .testTag("ai_save_verify_key_btn")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đang kiểm tra...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Lưu & Kiểm tra Key", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = { onSaveOnly(keyDraft, modelDraft) },
                enabled = keyDraft.trim().isNotEmpty() && !isVerifying,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 46.dp)
                    .testTag("ai_save_key_only_btn")
            ) {
                Text("Lưu", color = SlateTextPrimary, fontWeight = FontWeight.SemiBold)
            }

            if (keyConfig.hasKey) {
                IconButton(
                    onClick = {
                        keyDraft = ""
                        onClearKey()
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RoseAccent.copy(alpha = 0.14f))
                        .border(1.dp, RoseAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .testTag("ai_clear_key_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Xóa API Key",
                        tint = RoseAccent
                    )
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusSuccess) EmeraldAccent else RoseAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("ai_key_status_message")
            )
        }

        HorizontalDivider(color = SlateBorder.copy(alpha = 0.45f))

        // Model selector presets
        Text(
            text = "Chọn mô hình Gemini",
            style = MaterialTheme.typography.labelMedium,
            color = SlateTextPrimary,
            fontWeight = FontWeight.Bold
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModelPreset.entries.forEach { preset ->
                val active = modelDraft.equals(preset.modelId, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) CyanAccent.copy(alpha = 0.15f) else SlateDeepest.copy(alpha = 0.55f))
                        .border(
                            1.dp,
                            if (active) CyanAccent else SlateBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            modelDraft = preset.modelId
                            onSelectModel(preset.modelId)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("ai_model_preset_${preset.modelId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (active) CyanAccent else SlateBorder)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) CyanAccent else SlateTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Optional custom model ID input (in case user wants a specific newer Gemini ID)
        OutlinedTextField(
            value = modelDraft,
            onValueChange = {
                modelDraft = it
                if (it.trim().isNotEmpty()) onSelectModel(it.trim())
            },
            label = { Text("Mã mô hình (tùy chỉnh nếu cần)") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = SlateBorder,
                focusedTextColor = SlateTextPrimary,
                unfocusedTextColor = SlateTextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_custom_model_input")
        )

        // Helpful link to Google AI Studio API Keys page
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(IndigoPrimary.copy(alpha = 0.12f))
                .border(1.dp, IndigoPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("ai_get_key_link"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chưa có API Key? Lấy khóa miễn phí tại Google AI Studio",
                    style = MaterialTheme.typography.labelMedium,
                    color = IndigoBright,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Khóa chỉ lưu cục bộ trên máy bạn và dùng trực tiếp khi gửi yêu cầu AI.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Mở trang lấy API Key",
                tint = IndigoBright,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
