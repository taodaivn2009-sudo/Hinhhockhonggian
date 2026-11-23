package com.example.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import java.io.File

/**
 * Tab "Chụp đề vẽ hình":
 * - Capture a math problem via Camera (runtime CAMERA permission + FileProvider)
 * - Or pick a math problem image via zero-permission Android Photo Picker (PickVisualMedia)
 * - Or enter/paste problem text
 * - Gemini Vision parses the geometry/graph problem and outputs a structured 3D or 2D drawing plan
 *   that renders immediately onto the real 3D Space or 2D Oxy Studio canvas with 1 tap!
 */
@Composable
fun AiProblemDrawTab(
    uiState: AiAssistantUiState,
    onImageSelected: (Uri) -> Unit,
    onNoteChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onCancelAnalyze: () -> Unit = {},
    onApplyDrawingPlan: (AiDrawingPlan) -> Unit,
    onOpenPdfPage: (Int) -> Unit,
    onGoToKeyTab: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var pendingCameraUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraPermissionMessage by remember { mutableStateOf<String?>(null) }

    // Auto-scroll down to reveal AI drawing result or error banner immediately when it arrives
    LaunchedEffect(uiState.latestDrawingPlan, uiState.problemError) {
        if (uiState.latestDrawingPlan != null || !uiState.problemError.isNullOrBlank()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingCameraUriStr
        if (success && !uriStr.isNullOrBlank()) {
            cameraPermissionMessage = null
            onImageSelected(Uri.parse(uriStr))
        }
    }

    fun launchCameraCapture() {
        val uri = createTempProblemPhotoUri(context)
        if (uri != null) {
            pendingCameraUriStr = uri.toString()
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionMessage = "Không thể khởi tạo tệp ảnh tạm trên thiết bị."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionMessage = null
            launchCameraCapture()
        } else {
            cameraPermissionMessage = "Chưa có quyền Camera. Bạn vẫn có thể dùng nút \"Chọn ảnh thư viện\" ở bên cạnh."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            cameraPermissionMessage = null
            onImageSelected(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!uiState.isAiReady) {
                AiMissingKeyBanner(
                    isSigningIn = uiState.isSigningIn,
                    onSignInWithGoogle = onSignInWithGoogle,
                    onGoToKeyTab = onGoToKeyTab
                )
            }

            // Hero capture card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        cornerRadiusDp = 18.dp,
                        elevated = true,
                        fallbackBg = SlateDark,
                        accentBorder = PurpleAccent.copy(alpha = 0.5f)
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(
                                PurpleAccent.copy(alpha = 0.14f),
                                CyanAccent.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chụp đề bài → AI tự động vẽ hình 3D / 2D",
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateTextPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Hỗ trợ hình chóp, lăng trụ, Oxyz, thiết diện 3 điểm, tam giác/đường tròn Oxy và khảo sát đồ thị hàm số.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                launchCameraCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("ai_capture_camera_btn")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Chụp đề bài", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("ai_pick_gallery_btn")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = SlateTextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Chọn ảnh", color = SlateTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (!cameraPermissionMessage.isNullOrBlank()) {
                    Text(
                        text = cameraPermissionMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberAccent
                    )
                }

                // Selected photo preview
                val bmp = uiState.problemBitmap
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 260.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(1.dp, CyanAccent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Ảnh đề bài đã chụp",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 260.dp)
                        )
                        IconButton(
                            onClick = onClearInput,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .testTag("ai_remove_photo_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa ảnh đề bài", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Optional problem text or extra instruction
                OutlinedTextField(
                    value = uiState.problemNote,
                    onValueChange = onNoteChange,
                    label = { Text(if (bmp != null) "Ghi chú thêm (không bắt buộc)" else "Hoặc nhập / dán đề bài toán") },
                    placeholder = {
                        Text(
                            "Vd: Cho chóp S.ABCD đáy vuông cạnh 4, SA ⊥ đáy, SA = 5. M là trung điểm SC, vẽ thiết diện (AMB)...",
                            fontSize = 12.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = SlateTextPrimary,
                        unfocusedTextColor = SlateTextPrimary,
                        cursorColor = PurpleAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_problem_note_input")
                )

                // Quick sample problem chips so users can test immediately
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SampleProblemChip(
                        label = "Mẫu 3D: Chóp S.ABCD & thiết diện",
                        onClick = {
                            onNoteChange("Cho hình chóp S.ABCD có đáy ABCD là hình vuông cạnh 4, SA vuông góc với đáy, SA = 5. Gọi M, N lần lượt là trung điểm SC, SD. Vẽ hình và dựng thiết diện (AMN).")
                        }
                    )
                    SampleProblemChip(
                        label = "Mẫu 2D: Đồ thị bậc 3 & tiếp tuyến",
                        onClick = {
                            onNoteChange("Vẽ đồ thị hàm số y = x^3 - 3x + 1, đánh dấu các điểm cực trị và vẽ thêm đường thẳng y = 1.")
                        }
                    )
                    SampleProblemChip(
                        label = "Mẫu 2D: Tam giác & đường tròn Oxy",
                        onClick = {
                            onNoteChange("Trong mặt phẳng Oxy cho tam giác ABC với A(0;0), B(6;0), C(2;4) và đường tròn tâm I(2;1) bán kính R = 2.")
                        }
                    )
                }

                // Analyze CTA + Cancel button while running
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAnalyzeClick,
                        enabled = !uiState.isAnalyzingProblem && (uiState.problemBitmap != null || uiState.problemNote.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("ai_analyze_problem_btn")
                    ) {
                        if (uiState.isAnalyzingProblem) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            val secSuffix = if (uiState.problemElapsedSeconds > 0) " (${uiState.problemElapsedSeconds}s)" else ""
                            Text("AI đang phân tích...$secSuffix", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Phân tích đề bài & Dựng hình AI", fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    if (uiState.isAnalyzingProblem) {
                        OutlinedButton(
                            onClick = onCancelAnalyze,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 50.dp)
                                .testTag("ai_cancel_problem_btn")
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = "Hủy phân tích", tint = RoseAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hủy", color = RoseAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (uiState.isAnalyzingProblem && uiState.problemStatusText.isNotBlank()) {
                    Text(
                        text = uiState.problemStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error display with Retry & Connection check
            if (!uiState.problemError.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RoseAccent.copy(alpha = 0.14f))
                        .border(1.dp, RoseAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = RoseAccent, modifier = Modifier.size(20.dp))
                        Text(
                            text = uiState.problemError,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAnalyzeClick,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("ai_problem_retry_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Thử lại", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onGoToKeyTab,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                        ) {
                            Text("Kiểm tra kết nối / Key", color = SlateTextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // AI Drawing Result Card
            val plan = uiState.latestDrawingPlan
            if (plan != null) {
                AiDrawingPlanResultCard(
                    plan = plan,
                    onApplyDrawingPlan = { onApplyDrawingPlan(plan) },
                    onOpenPdfPage = onOpenPdfPage
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun AiDrawingPlanResultCard(
    plan: AiDrawingPlan,
    onApplyDrawingPlan: () -> Unit,
    onOpenPdfPage: (Int) -> Unit
) {
    val is3D = plan.target == AiDrawTarget.SPACE_3D
    val accent = if (is3D) IndigoBright else EmeraldAccent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                cornerRadiusDp = 18.dp,
                elevated = true,
                fallbackBg = SlateDark,
                accentBorder = accent.copy(alpha = 0.65f)
            )
            .padding(16.dp)
            .testTag("ai_drawing_plan_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (is3D) "MÔ HÌNH 3D" else "MẶT PHẲNG & ĐỒ THỊ 2D",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = plan.title,
                style = MaterialTheme.typography.titleSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        if (plan.problemText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateDeepest.copy(alpha = 0.55f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("Đề bài nhận diện:", style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                Text(plan.problemText, style = MaterialTheme.typography.bodySmall, color = SlateTextPrimary)
            }
        }

        if (plan.drawingSummary.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Các yếu tố AI sẽ dựng:", style = MaterialTheme.typography.labelSmall, color = CyanAccent, fontWeight = FontWeight.Bold)
                Text(plan.drawingSummary, style = MaterialTheme.typography.bodySmall, color = SlateTextPrimary)
            }
        }

        if (plan.solutionHint.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Gợi ý hướng giải:", style = MaterialTheme.typography.labelSmall, color = AmberAccent, fontWeight = FontWeight.Bold)
                Text(plan.solutionHint, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
            }
        }

        // Primary CTA: Draw directly on 3D or 2D Canvas!
        Button(
            onClick = onApplyDrawingPlan,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .testTag("ai_apply_drawing_btn")
        ) {
            Icon(
                imageVector = if (is3D) Icons.Default.Category else Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (is3D) "Vẽ ngay lên không gian 3D" else "Vẽ ngay lên mặt phẳng 2D",
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (plan.relatedPages.isNotEmpty()) {
            Text(
                text = "Trang công thức liên quan trong PDF 388 trang:",
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary,
                fontWeight = FontWeight.Bold
            )
            plan.relatedPages.forEach { suggestion ->
                AiSuggestedPdfPageRow(suggestion = suggestion, onOpenPdfPage = onOpenPdfPage)
            }
        }
    }
}

@Composable
fun AiSuggestedPdfPageRow(
    suggestion: AiPdfPageSuggestion,
    onOpenPdfPage: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CyanAccent.copy(alpha = 0.12f))
            .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onOpenPdfPage(suggestion.page) }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("ai_jump_pdf_page_${suggestion.page}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.MenuBook, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Trang ${suggestion.page} · ${suggestion.title}",
                style = MaterialTheme.typography.labelMedium,
                color = CyanAccent,
                fontWeight = FontWeight.Bold
            )
            if (suggestion.reason.isNotBlank()) {
                Text(
                    text = suggestion.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Mở trang ${suggestion.page}",
            tint = CyanAccent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AiMissingKeyBanner(
    isSigningIn: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onGoToKeyTab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyanAccent.copy(alpha = 0.13f))
            .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("ai_missing_key_banner"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chưa kết nối nguồn AI đang chọn",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Đăng nhập Google 1 chạm hoặc mở \"Kết nối AI\" để nhập API Key (Google, OpenAI, OpenRouter, Groq, DeepSeek, Claude, Custom URL).",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSignInWithGoogle,
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp)
                    .testTag("ai_banner_google_sign_in_btn")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(color = Color(0xFF1F1F1F), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Đang đăng nhập...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Đăng nhập Google", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            OutlinedButton(
                onClick = onGoToKeyTab,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 42.dp)
            ) {
                Text("Mục Key", color = SlateTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SampleProblemChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SlateDeepest.copy(alpha = 0.65f))
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SlateTextLight,
            fontSize = 11.sp
        )
    }
}

private fun createTempProblemPhotoUri(context: Context): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "ai_photos").apply { mkdirs() }
        val file = File(dir, "problem_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
