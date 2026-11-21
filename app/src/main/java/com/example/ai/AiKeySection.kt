package com.example.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reusable AI Connection section embedded inside BOTH:
 * - AI Workspace -> "Tài khoản / Key" sub-tab
 * - App Settings -> "Tài khoản Google & AI (Gemini)"
 *
 * Prioritizes 1-tap Google Sign-In (uses Google Gemini via Firebase AI Logic without any API key),
 * while keeping manual Gemini API Key entry available as an optional secondary choice.
 */
@Composable
fun AiKeySetupSection(
    googleUser: GoogleUserProfile?,
    isSigningIn: Boolean,
    authStatusMessage: String?,
    authStatusSuccess: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
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
    var expandManualKey by remember(googleUser != null, keyConfig.hasKey) {
        mutableStateOf(googleUser == null && keyConfig.hasKey)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. PRIMARY OPTION: Google Sign-In -> Keyless Google Gemini
        GoogleGeminiAuthCard(
            googleUser = googleUser,
            isSigningIn = isSigningIn,
            authStatusMessage = authStatusMessage,
            authStatusSuccess = authStatusSuccess,
            onSignInWithGoogle = onSignInWithGoogle,
            onSignOutGoogle = onSignOutGoogle
        )

        // 2. Shared Gemini Model Selector (applies to BOTH Google Gemini & Manual API Key)
        HorizontalDivider(color = SlateBorder.copy(alpha = 0.45f))

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

        // 3. OPTIONAL SECONDARY FALLBACK: Manual Gemini API Key
        HorizontalDivider(color = SlateBorder.copy(alpha = 0.45f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateDeepest.copy(alpha = 0.45f))
                .border(1.dp, SlateBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .clickable { expandManualKey = !expandManualKey }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("ai_toggle_manual_key_section"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = if (keyConfig.hasKey) EmeraldAccent else SlateTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hoặc dùng API Key riêng (tùy chọn)",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        googleUser != null && keyConfig.hasKey -> "Đang ưu tiên Google Gemini · Key riêng đã lưu (${keyConfig.maskedKey})"
                        keyConfig.hasKey -> "Đã lưu Key (${keyConfig.maskedKey})"
                        else -> "Không cần nhập nếu bạn đã đăng nhập Google ở trên"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = if (expandManualKey) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expandManualKey) "Thu gọn mục API Key" else "Mở rộng mục API Key",
                tint = SlateTextSecondary
            )
        }

        if (expandManualKey) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            text = "Lấy API Key riêng tại Google AI Studio",
                            style = MaterialTheme.typography.labelMedium,
                            color = IndigoBright,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Chỉ cần nếu bạn muốn dùng khóa riêng thay vì đăng nhập Google.",
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
    }
}

@Composable
fun GoogleGeminiAuthCard(
    googleUser: GoogleUserProfile?,
    isSigningIn: Boolean,
    authStatusMessage: String?,
    authStatusSuccess: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSignedIn = googleUser != null
    val borderColor = if (isSignedIn) EmeraldAccent.copy(alpha = 0.55f) else CyanAccent.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = if (isSignedIn) {
                        listOf(
                            EmeraldAccent.copy(alpha = 0.16f),
                            CyanAccent.copy(alpha = 0.08f)
                        )
                    } else {
                        listOf(
                            CyanAccent.copy(alpha = 0.16f),
                            IndigoPrimary.copy(alpha = 0.12f)
                        )
                    }
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (googleUser != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(EmeraldAccent.copy(alpha = 0.25f))
                        .border(1.5.dp, EmeraldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = googleUser.initials,
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldLight,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = googleUser.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (googleUser.email.isNotBlank()) {
                        Text(
                            text = googleUser.email,
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Đang dùng Google Gemini · Không cần nhập API Key",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }

            OutlinedButton(
                onClick = onSignOutGoogle,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("google_sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = SlateTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Đăng xuất tài khoản Google", color = SlateTextPrimary, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyanAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dùng Gemini bằng tài khoản Google",
                        style = MaterialTheme.typography.titleSmall,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Đăng nhập Google 1 chạm — sử dụng ngay Google Gemini mà không cần tạo hay dán API Key.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Button(
                onClick = onSignInWithGoogle,
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        color = Color(0xFF1F1F1F),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Đang đăng nhập Google...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng nhập với Google", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }

        if (!authStatusMessage.isNullOrBlank()) {
            Text(
                text = authStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (authStatusSuccess) EmeraldLight else RoseAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("google_auth_status_message")
            )
        }
    }
}
