import SwiftUI
import UIKit

enum AiWorkspaceMode: String, CaseIterable, Identifiable {
    case docsQa = "Hỏi tài liệu"
    case problemDraw = "Chụp đề vẽ hình"

    var id: String { rawValue }
}

struct AiWorkspaceView: View {
    @ObservedObject var aiStore: AiAssistantStore
    var onOpenPdfPage: (Int) -> Void
    var onApplyDrawingPlan: (AiDrawingPlan) -> Void
    var onOpenSettings: () -> Void

    @State private var mode: AiWorkspaceMode = .docsQa
    @State private var inputText = ""
    @State private var showCamera = false
    @State private var showPhotoLibrary = false
    @State private var showAttachPageSheet = false
    @State private var attachPageInput = ""

    var body: some View {
        VStack(spacing: 10) {
            // Top mode switcher + clear chat button
            HStack(spacing: 8) {
                HStack(spacing: 4) {
                    ForEach(AiWorkspaceMode.allCases) { item in
                        let selected = (mode == item)
                        let accent = item == .docsQa ? AppTheme.cyan : AppTheme.purple
                        Button(action: { mode = item }) {
                            Text(item.rawValue)
                                .font(.system(size: 12, weight: selected ? .heavy : .semibold))
                                .foregroundColor(selected ? accent : AppTheme.textSecondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .background(selected ? accent.opacity(0.2) : Color.clear)
                                .cornerRadius(16)
                        }
                    }
                }
                .padding(4)
                .liquidGlassCard(cornerRadius: 20)

                if mode == .docsQa && !aiStore.messages.isEmpty {
                    Button(action: { aiStore.clearChat() }) {
                        Image(systemName: "trash")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(AppTheme.rose)
                            .frame(width: 40, height: 40)
                            .liquidGlassCard(cornerRadius: 14, borderColor: AppTheme.rose.opacity(0.4))
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 8)

            // Compact active AI provider/model status chip
            Button(action: onOpenSettings) {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(aiStore.isReady ? AppTheme.cyan : AppTheme.amber)
                    Text(aiStore.isReady ? "\(aiStore.activeProvider.title) · \(aiStore.activeModelId)" : "Chưa nhập API Key · Nhấn để cài đặt")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(AppTheme.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(AppTheme.textSecondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .liquidGlassCard(
                    cornerRadius: 14,
                    borderColor: (aiStore.isReady ? AppTheme.cyan : AppTheme.amber).opacity(0.42)
                )
            }
            .padding(.horizontal, 14)

            if mode == .docsQa {
                docsQaBody
            } else {
                problemDrawBody
            }
        }
        .sheet(isPresented: $showCamera) {
            NativeImagePicker(sourceType: .camera) { img in
                aiStore.problemImage = img
                aiStore.problemError = nil
            }
        }
        .sheet(isPresented: $showPhotoLibrary) {
            NativeImagePicker(sourceType: .photoLibrary) { img in
                aiStore.problemImage = img
                aiStore.problemError = nil
            }
        }
        .sheet(isPresented: $showAttachPageSheet) {
            NavigationView {
                ZStack {
                    LiquidGlassBackdropView()
                    VStack(spacing: 16) {
                        Text("Nhập số trang PDF muốn đính kèm (1 – \(PdfCurriculumData.totalPages))")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(AppTheme.textPrimary)
                            .multilineTextAlignment(.center)
                        TextField("Ví dụ: 121", text: $attachPageInput)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .padding(.horizontal, 24)
                        Button(action: {
                            if let p = Int(attachPageInput.trimmingCharacters(in: .whitespaces)),
                               p >= 1 && p <= PdfCurriculumData.totalPages {
                                aiStore.attachedPdfPage = p
                            }
                            showAttachPageSheet = false
                        }) {
                            Text("Đính kèm trang")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(AppTheme.cyan)
                                .cornerRadius(12)
                        }
                        .padding(.horizontal, 24)
                        Spacer()
                    }
                    .padding(.top, 24)
                }
                .navigationTitle("Đính kèm trang PDF")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Đóng") { showAttachPageSheet = false }
                            .foregroundColor(AppTheme.cyan)
                    }
                }
            }
            .navigationViewStyle(StackNavigationViewStyle())
        }
    }

    // MARK: - Mode 1: Docs Q&A with Smooth Streaming + Blinking Cursor

    private var docsQaBody: some View {
        VStack(spacing: 8) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 10) {
                        if aiStore.messages.isEmpty && !aiStore.isAskingDocs {
                            welcomeDocsCard
                        }

                        ForEach(aiStore.messages) { msg in
                            chatBubble(msg)
                        }

                        if aiStore.isAskingDocs {
                            streamingDocsBubble
                                .id("streaming_docs_anchor")
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 12)
                }
                .onChange(of: aiStore.messages.count) { _ in
                    if let lastId = aiStore.messages.last?.id {
                        withAnimation { proxy.scrollTo(lastId, anchor: .bottom) }
                    }
                }
                .onChange(of: aiStore.streamingDocsAnswer.count / 32) { _ in
                    if aiStore.isAskingDocs {
                        proxy.scrollTo("streaming_docs_anchor", anchor: .bottom)
                    }
                }
            }

            if let attached = aiStore.attachedPdfPage {
                HStack(spacing: 6) {
                    Image(systemName: "doc.text.fill").foregroundColor(AppTheme.cyan)
                    Text("Đính kèm Trang \(attached) PDF")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(AppTheme.cyan)
                    Spacer()
                    Button(action: { aiStore.attachedPdfPage = nil }) {
                        Image(systemName: "xmark.circle.fill").foregroundColor(AppTheme.textSecondary)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(AppTheme.cyan.opacity(0.14))
                .cornerRadius(12)
                .padding(.horizontal, 14)
            }

            // Prompt input bar above floating bottom nav
            HStack(spacing: 8) {
                Button(action: {
                    attachPageInput = aiStore.attachedPdfPage.map { "\($0)" } ?? ""
                    showAttachPageSheet = true
                }) {
                    Image(systemName: "plus")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(AppTheme.cyan)
                        .frame(width: 40, height: 40)
                        .liquidGlassCard(cornerRadius: 14, borderColor: AppTheme.cyan.opacity(0.4))
                }

                TextField("Hỏi công thức hoặc trang PDF...", text: $inputText)
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.textPrimary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .liquidGlassCard(cornerRadius: 16)

                Button(action: {
                    let q = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !q.isEmpty else { return }
                    inputText = ""
                    aiStore.askDocs(question: q)
                }) {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                        .frame(width: 42, height: 40)
                        .background(aiStore.isAskingDocs ? AppTheme.slateBorder : AppTheme.cyan)
                        .cornerRadius(14)
                }
                .disabled(aiStore.isAskingDocs)
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 90)
        }
    }

    private var welcomeDocsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "book.closed.fill").foregroundColor(AppTheme.cyan)
                Text("Hỏi AI tra cứu tài liệu 388 trang")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(AppTheme.textPrimary)
            }
            Text("Câu trả lời chảy mượt trực tiếp từng đoạn kèm số trang PDF chính xác để bấm mở ngay.")
                .font(.system(size: 12))
                .foregroundColor(AppTheme.textSecondary)

            let prompts = [
                "Công thức khoảng cách 2 đường chéo nhau Oxyz ở trang nào?",
                "Tóm tắt công thức nguyên hàm từng phần và chỉ trang PDF",
                "Đọc và tóm tắt giúp tôi trang 121"
            ]
            ForEach(prompts, id: \.self) { p in
                Button(action: { aiStore.askDocs(question: p) }) {
                    HStack(spacing: 8) {
                        Image(systemName: "text.book.closed").foregroundColor(AppTheme.cyan)
                        Text(p)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(AppTheme.textPrimary)
                            .multilineTextAlignment(.leading)
                        Spacer()
                    }
                    .padding(10)
                    .background(AppTheme.cyan.opacity(0.1))
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(AppTheme.cyan.opacity(0.28), lineWidth: 1))
                }
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, borderColor: AppTheme.cyan.opacity(0.4))
    }

    private func chatBubble(_ msg: AiChatItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: msg.isUser ? "person.fill" : (msg.isError ? "exclamationmark.triangle.fill" : "sparkles"))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(msg.isUser ? AppTheme.indigo : (msg.isError ? AppTheme.rose : AppTheme.cyan))
                Text(msg.isUser ? "Bạn" : (msg.isError ? "Thông báo lỗi" : "Trợ lý Tool Toán"))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(msg.isUser ? AppTheme.indigo : (msg.isError ? AppTheme.rose : AppTheme.cyan))
                if let p = msg.attachedPage {
                    Text("· Tr.\(p)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(AppTheme.amber)
                }
                Spacer()
            }

            Text(msg.text)
                .font(.system(size: 14))
                .foregroundColor(AppTheme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)

            if !msg.suggestedPages.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(msg.suggestedPages) { pg in
                            Button(action: { onOpenPdfPage(pg.page) }) {
                                HStack(spacing: 5) {
                                    Image(systemName: "doc.text")
                                    Text("Tr.\(pg.page) · \(pg.title)")
                                        .lineLimit(1)
                                }
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(AppTheme.emerald)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(AppTheme.emerald.opacity(0.15))
                                .clipShape(Capsule())
                                .overlay(Capsule().stroke(AppTheme.emerald.opacity(0.45), lineWidth: 1))
                            }
                        }
                    }
                }
            }
        }
        .padding(13)
        .liquidGlassCard(
            cornerRadius: 16,
            borderColor: (msg.isUser ? AppTheme.indigo : (msg.isError ? AppTheme.rose : AppTheme.cyan)).opacity(0.4)
        )
    }

    private var streamingDocsBubble: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                StreamingPulseDot(color: AppTheme.cyan)
                Text(aiStore.docsStatusText.isEmpty ? "AI đang viết câu trả lời..." : aiStore.docsStatusText)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(AppTheme.cyan)
                Spacer()
                Button(action: { aiStore.cancelDocs() }) {
                    Text("Hủy")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(AppTheme.rose)
                }
            }

            Divider().background(AppTheme.slateBorder)

            if !aiStore.streamingDocsAnswer.isEmpty {
                SmoothStreamingText(
                    targetText: aiStore.streamingDocsAnswer,
                    isStreaming: true,
                    textColor: AppTheme.textPrimary,
                    cursorColor: AppTheme.cyan,
                    font: .system(size: 14)
                )
            } else {
                SmoothStreamingText(
                    targetText: aiStore.docsThinkingChars > 0
                        ? "AI đang suy luận công thức & trang tài liệu phù hợp"
                        : "Đang chờ những chữ đầu tiên từ AI",
                    isStreaming: true,
                    textColor: AppTheme.textSecondary,
                    cursorColor: AppTheme.cyan,
                    font: .system(size: 13)
                )
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 16, borderColor: AppTheme.cyan.opacity(0.55))
    }

    // MARK: - Mode 2: Problem Photo -> Auto 3D / 2D Drawing with Live Streaming Card

    private var problemDrawBody: some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Chụp đề bài → AI tự động dựng hình 3D / 2D")
                        .font(.system(size: 15, weight: .heavy))
                        .foregroundColor(AppTheme.textPrimary)

                    HStack(spacing: 10) {
                        Button(action: {
                            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                                showCamera = true
                            } else {
                                showPhotoLibrary = true
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "camera.fill")
                                Text("Chụp đề bài").font(.system(size: 13, weight: .bold))
                            }
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(AppTheme.cyan)
                            .cornerRadius(12)
                        }

                        Button(action: { showPhotoLibrary = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "photo.on.rectangle")
                                Text("Chọn ảnh").font(.system(size: 13, weight: .bold))
                            }
                            .foregroundColor(AppTheme.textPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .liquidGlassCard(cornerRadius: 12)
                        }
                    }

                    if let img = aiStore.problemImage {
                        ZStack(alignment: .topTrailing) {
                            Image(uiImage: img)
                                .resizable()
                                .scaledToFit()
                                .frame(maxWidth: .infinity, maxHeight: 220)
                                .cornerRadius(12)
                            Button(action: { aiStore.problemImage = nil }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 22))
                                    .foregroundColor(.white)
                                    .padding(6)
                            }
                        }
                    }

                    TextField("Nhập / dán đề bài toán (3D, Oxyz, đồ thị 2D)...", text: $aiStore.problemNote)
                        .font(.system(size: 13))
                        .foregroundColor(AppTheme.textPrimary)
                        .padding(11)
                        .background(AppTheme.slateDeepest.opacity(0.65))
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(AppTheme.slateBorder, lineWidth: 1))

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            sampleChip("Mẫu 3D: Chóp S.ABCD") {
                                aiStore.problemNote = "Cho hình chóp S.ABCD đáy vuông cạnh 4, SA vuông góc đáy, SA = 5. Gọi M, N là trung điểm SC, SD. Vẽ hình và thiết diện (AMN)."
                            }
                            sampleChip("Mẫu 2D: Đồ thị bậc 3") {
                                aiStore.problemNote = "Vẽ đồ thị hàm số y = x^3 - 3x + 1, đánh dấu cực trị và đường thẳng y = 1."
                            }
                        }
                    }

                    HStack(spacing: 8) {
                        Button(action: { aiStore.analyzeProblem() }) {
                            HStack(spacing: 8) {
                                if aiStore.isAnalyzingProblem {
                                    ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                } else {
                                    Image(systemName: "sparkles")
                                }
                                Text(aiStore.isAnalyzingProblem ? "AI đang phân tích..." : "Phân tích & Dựng hình AI")
                                    .font(.system(size: 14, weight: .heavy))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(AppTheme.purple)
                            .cornerRadius(14)
                        }
                        .disabled(aiStore.isAnalyzingProblem)

                        if aiStore.isAnalyzingProblem {
                            Button(action: { aiStore.cancelProblem() }) {
                                Text("Hủy")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(AppTheme.rose)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 13)
                                    .liquidGlassCard(cornerRadius: 14, borderColor: AppTheme.rose.opacity(0.5))
                            }
                        }
                    }
                }
                .padding(14)
                .liquidGlassCard(cornerRadius: 18, borderColor: AppTheme.purple.opacity(0.45))

                if let err = aiStore.problemError {
                    Text(err)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(AppTheme.rose)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(AppTheme.rose.opacity(0.14))
                        .cornerRadius(14)
                }

                // Live Streaming Preview Card
                if aiStore.isAnalyzingProblem {
                    liveProblemStreamingCard
                }

                // Final Drawing Plan Result Card
                if let plan = aiStore.latestDrawingPlan {
                    drawingPlanCard(plan)
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 100)
        }
    }

    private func sampleChip(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(AppTheme.cyan)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(AppTheme.cyan.opacity(0.12))
                .clipShape(Capsule())
                .overlay(Capsule().stroke(AppTheme.cyan.opacity(0.35), lineWidth: 1))
        }
    }

    private var liveProblemStreamingCard: some View {
        let preview = aiStore.streamingProblemPreview
        let accent: Color = preview?.target == .plane2D ? AppTheme.emerald : (preview?.target == .space3D ? AppTheme.indigo : AppTheme.purple)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                StreamingPulseDot(color: accent)
                if let t = preview?.target {
                    Text(t.rawValue)
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundColor(accent)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(accent.opacity(0.2))
                        .cornerRadius(6)
                }
                Text(aiStore.problemStatusText.isEmpty ? "AI đang phân tích đề bài..." : aiStore.problemStatusText)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(accent)
                Spacer()
            }

            Divider().background(AppTheme.slateBorder)

            if let p = preview, p.hasVisibleText {
                if !p.title.isEmpty {
                    SmoothStreamingText(
                        targetText: p.title,
                        isStreaming: true,
                        textColor: AppTheme.textPrimary,
                        cursorColor: accent,
                        font: .system(size: 15),
                        fontWeight: .bold,
                        showCursor: p.problemText.isEmpty && !p.isBuildingGeometry
                    )
                }
                if !p.problemText.isEmpty {
                    Text("Đề bài nhận diện").font(.system(size: 11, weight: .bold)).foregroundColor(AppTheme.cyan)
                    SmoothStreamingText(
                        targetText: p.problemText,
                        isStreaming: true,
                        textColor: AppTheme.textPrimary,
                        cursorColor: AppTheme.cyan,
                        font: .system(size: 13),
                        showCursor: p.drawingSummary.isEmpty && !p.isBuildingGeometry
                    )
                }
                if !p.drawingSummary.isEmpty {
                    Text("Kế hoạch dựng hình").font(.system(size: 11, weight: .bold)).foregroundColor(accent)
                    SmoothStreamingText(
                        targetText: p.drawingSummary,
                        isStreaming: true,
                        textColor: AppTheme.textPrimary,
                        cursorColor: accent,
                        font: .system(size: 13),
                        showCursor: p.solutionHint.isEmpty && !p.isBuildingGeometry
                    )
                }
                if !p.solutionHint.isEmpty {
                    Text("Gợi ý hướng giải").font(.system(size: 11, weight: .bold)).foregroundColor(AppTheme.amber)
                    SmoothStreamingText(
                        targetText: p.solutionHint,
                        isStreaming: true,
                        textColor: AppTheme.textSecondary,
                        cursorColor: AppTheme.amber,
                        font: .system(size: 13),
                        showCursor: !p.isBuildingGeometry
                    )
                }
                if p.isBuildingGeometry {
                    HStack(spacing: 8) {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: accent))
                        Text(p.target == .plane2D ? "Đang hoàn thiện tọa độ 2D..." : "Đang hoàn thiện tọa độ đỉnh & vectơ 3D...")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(accent)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(accent.opacity(0.14))
                    .cornerRadius(10)
                }
            } else {
                SmoothStreamingText(
                    targetText: "Đang nhận nội dung phân tích từng đoạn từ AI",
                    isStreaming: true,
                    textColor: AppTheme.textSecondary,
                    cursorColor: accent,
                    font: .system(size: 13)
                )
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, borderColor: accent.opacity(0.55))
    }

    private func drawingPlanCard(_ plan: AiDrawingPlan) -> some View {
        let is3D = plan.target == .space3D
        let accent = is3D ? AppTheme.indigo : AppTheme.emerald
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text(plan.target.rawValue)
                    .font(.system(size: 11, weight: .heavy))
                    .foregroundColor(.black)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(accent)
                    .cornerRadius(8)
                Text(plan.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(AppTheme.textPrimary)
            }
            if !plan.problemText.isEmpty {
                Text(plan.problemText)
                    .font(.system(size: 13))
                    .foregroundColor(AppTheme.textPrimary)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppTheme.slateDeepest.opacity(0.6))
                    .cornerRadius(10)
            }
            if !plan.drawingSummary.isEmpty {
                Text("Kế hoạch dựng hình: \(plan.drawingSummary)")
                    .font(.system(size: 13))
                    .foregroundColor(AppTheme.textPrimary)
            }
            if !plan.solutionHint.isEmpty {
                Text("Hướng giải: \(plan.solutionHint)")
                    .font(.system(size: 12))
                    .foregroundColor(AppTheme.textSecondary)
            }
            Button(action: { onApplyDrawingPlan(plan) }) {
                HStack(spacing: 8) {
                    Image(systemName: is3D ? "cube.fill" : "chart.xyaxis.line")
                    Text(is3D ? "Vẽ ngay lên màn hình 3D" : "Vẽ ngay lên màn hình 2D")
                        .font(.system(size: 14, weight: .heavy))
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(accent)
                .cornerRadius(14)
            }
            if !plan.relatedPages.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(plan.relatedPages) { pg in
                            Button("Mở Tr.\(pg.page) · \(pg.title)") {
                                onOpenPdfPage(pg.page)
                            }
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(AppTheme.cyan)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(AppTheme.cyan.opacity(0.14))
                            .clipShape(Capsule())
                        }
                    }
                }
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 18, borderColor: accent.opacity(0.65))
    }
}

// MARK: - iOS 15 Compatible UIImagePickerController Wrapper

private struct NativeImagePicker: UIViewControllerRepresentable {
    let sourceType: UIImagePickerController.SourceType
    let onPicked: (UIImage) -> Void
    @Environment(\.presentationMode) private var presentationMode

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = sourceType
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: NativeImagePicker
        init(parent: NativeImagePicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage {
                parent.onPicked(img)
            }
            parent.presentationMode.wrappedValue.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}
