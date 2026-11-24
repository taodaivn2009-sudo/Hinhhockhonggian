import Foundation
import UIKit

// MARK: - AI Provider & Config Models

enum AiProviderKind: String, CaseIterable, Identifiable, Codable {
    case geminiApiKey = "google_api_key"
    case openai = "openai"
    case openrouter = "openrouter"
    case groq = "groq"
    case deepseek = "deepseek"
    case anthropic = "anthropic"
    case customOpenAi = "custom_openai"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .geminiApiKey: return "Google Gemini API"
        case .openai: return "OpenAI"
        case .openrouter: return "OpenRouter"
        case .groq: return "Groq"
        case .deepseek: return "DeepSeek"
        case .anthropic: return "Anthropic Claude"
        case .customOpenAi: return "Custom URL"
        }
    }

    var defaultBaseUrl: String {
        switch self {
        case .geminiApiKey: return "https://generativelanguage.googleapis.com/v1beta"
        case .openai: return "https://api.openai.com/v1"
        case .openrouter: return "https://openrouter.ai/api/v1"
        case .groq: return "https://api.groq.com/openai/v1"
        case .deepseek: return "https://api.deepseek.com/v1"
        case .anthropic: return "https://api.anthropic.com/v1"
        case .customOpenAi: return ""
        }
    }

    var defaultModelId: String {
        switch self {
        case .geminiApiKey: return "gemini-2.5-flash"
        case .openai: return "gpt-4o-mini"
        case .openrouter: return "google/gemini-2.5-flash"
        case .groq: return "meta-llama/llama-4-scout-17b-16e-instruct"
        case .deepseek: return "deepseek-chat"
        case .anthropic: return "claude-sonnet-4-5"
        case .customOpenAi: return "gpt-4o-mini"
        }
    }

    var presetModels: [String] {
        switch self {
        case .geminiApiKey: return ["gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"]
        case .openai: return ["gpt-4o-mini", "gpt-4o", "o4-mini"]
        case .openrouter: return ["google/gemini-2.5-flash", "anthropic/claude-sonnet-4", "openai/gpt-4o-mini"]
        case .groq: return ["meta-llama/llama-4-scout-17b-16e-instruct", "llama-3.3-70b-versatile"]
        case .deepseek: return ["deepseek-chat", "deepseek-reasoner"]
        case .anthropic: return ["claude-sonnet-4-5", "claude-3-5-haiku-latest"]
        case .customOpenAi: return ["gpt-4o-mini"]
        }
    }
}

struct AiProviderConfig: Codable {
    var apiKey: String = ""
    var modelId: String = ""
    var baseUrl: String = ""
    var syncedModels: [String] = []
}

struct AiChatItem: Identifiable, Codable {
    let id: UUID
    let isUser: Bool
    let text: String
    let isError: Bool
    let attachedPage: Int?
    let suggestedPages: [CodablePageSuggestion]

    init(
        id: UUID = UUID(),
        isUser: Bool,
        text: String,
        isError: Bool = false,
        attachedPage: Int? = nil,
        suggestedPages: [CodablePageSuggestion] = []
    ) {
        self.id = id
        self.isUser = isUser
        self.text = text
        self.isError = isError
        self.attachedPage = attachedPage
        self.suggestedPages = suggestedPages
    }
}

struct CodablePageSuggestion: Identifiable, Codable, Hashable {
    var id: String { "\(page)_\(title)" }
    let page: Int
    let title: String
    let reason: String
}

// MARK: - Incremental Streaming JSON/Text Parser (Exact Twin of Android)

enum AiStreamParser {
    static func extractJsonStringField(_ raw: String, field: String) -> String {
        let token = "\"\(field)\""
        var searchRange = raw.startIndex..<raw.endIndex
        while let range = raw.range(of: token, range: searchRange) {
            var idx = range.upperBound
            while idx < raw.endIndex && raw[idx].isWhitespace { idx = raw.index(after: idx) }
            guard idx < raw.endIndex else { return "" }
            if raw[idx] != ":" {
                searchRange = range.upperBound..<raw.endIndex
                continue
            }
            idx = raw.index(after: idx)
            while idx < raw.endIndex && raw[idx].isWhitespace { idx = raw.index(after: idx) }
            guard idx < raw.endIndex, raw[idx] == "\"" else { return "" }
            idx = raw.index(after: idx)

            var out = ""
            while idx < raw.endIndex {
                let ch = raw[idx]
                if ch == "\"" { break }
                if ch == "\\" {
                    let nextIdx = raw.index(after: idx)
                    guard nextIdx < raw.endIndex else { break }
                    let esc = raw[nextIdx]
                    switch esc {
                    case "n": out.append("\n"); idx = raw.index(after: nextIdx)
                    case "t": out.append("\t"); idx = raw.index(after: nextIdx)
                    case "r": idx = raw.index(after: nextIdx)
                    case "\"": out.append("\""); idx = raw.index(after: nextIdx)
                    case "\\": out.append("\\"); idx = raw.index(after: nextIdx)
                    case "/": out.append("/"); idx = raw.index(after: nextIdx)
                    case "u":
                        if let endHex = raw.index(nextIdx, offsetBy: 5, limitedBy: raw.endIndex) {
                            let hexStart = raw.index(after: nextIdx)
                            let hex = String(raw[hexStart..<endHex])
                            if let scalarVal = UInt32(hex, radix: 16), let scalar = UnicodeScalar(scalarVal) {
                                out.append(Character(scalar))
                            }
                            idx = endHex
                        } else {
                            return out
                        }
                    default:
                        out.append(esc)
                        idx = raw.index(after: nextIdx)
                    }
                } else {
                    out.append(ch)
                    idx = raw.index(after: idx)
                }
            }
            return out
        }
        return ""
    }

    private static func stripLeadingFence(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("```") else { return trimmed }
        if let nl = trimmed.firstIndex(of: "\n") {
            return String(trimmed[trimmed.index(after: nl)...]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return ""
    }

    static func extractStreamingAnswerPreview(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let cleaned = stripLeadingFence(trimmed)
        if cleaned.isEmpty { return "" }
        let extracted = extractJsonStringField(cleaned, field: "answer")
        if !extracted.isEmpty { return extracted }
        let looksLikeOpeningFence = trimmed == "`" || trimmed == "``" || (trimmed.hasPrefix("```") && !trimmed.contains("\n"))
        if looksLikeOpeningFence { return "" }
        if !cleaned.hasPrefix("{") {
            return cleaned.replacingOccurrences(of: "```", with: "")
        }
        return ""
    }

    static func extractStreamingProblemPreview(_ raw: String, thinkingChars: Int = 0) -> AiProblemStreamingPreview {
        let cleaned = stripLeadingFence(raw)
        let targetRaw = extractJsonStringField(cleaned, field: "target").uppercased()
        let detectedTarget: AiDrawTarget?
        if targetRaw.contains("2D") || cleaned.contains("\"scene2D\"") {
            detectedTarget = .plane2D
        } else if targetRaw.contains("3D") || cleaned.contains("\"scene3D\"") {
            detectedTarget = .space3D
        } else {
            detectedTarget = nil
        }

        let title = extractJsonStringField(cleaned, field: "title")
        let problemText = extractJsonStringField(cleaned, field: "problemText")
        let drawingSummary = extractJsonStringField(cleaned, field: "drawingSummary")
        let solutionHint = extractJsonStringField(cleaned, field: "solutionHint")
        let isBuildingGeometry = cleaned.contains("\"scene3D\"") || cleaned.contains("\"scene2D\"")

        return AiProblemStreamingPreview(
            target: detectedTarget,
            title: title,
            problemText: problemText,
            drawingSummary: drawingSummary,
            solutionHint: solutionHint,
            isBuildingGeometry: isBuildingGeometry,
            thinkingChars: thinkingChars,
            rawChars: raw.count
        )
    }

    static func extractJsonObject(_ raw: String) -> [String: Any]? {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("```") {
            if let nl = text.firstIndex(of: "\n") {
                text = String(text[text.index(after: nl)...])
            }
            if text.hasSuffix("```") {
                text = String(text.dropLast(3))
            }
        }
        guard let firstBrace = text.firstIndex(of: "{"),
              let lastBrace = text.lastIndex(of: "}"),
              firstBrace <= lastBrace else {
            return nil
        }
        let slice = String(text[firstBrace...lastBrace])
        guard let data = slice.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return obj
    }

    static func parseDocsReply(_ raw: String) -> (String, [CodablePageSuggestion]) {
        guard let obj = extractJsonObject(raw) else {
            return (raw.trimmingCharacters(in: .whitespacesAndNewlines), [])
        }
        let answer = (obj["answer"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalAnswer = (answer?.isEmpty == false) ? answer! : raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let pages = parsePageSuggestions(obj["suggestedPages"] as? [[String: Any]])
        return (finalAnswer, pages)
    }

    static func parsePageSuggestions(_ arr: [[String: Any]]?) -> [CodablePageSuggestion] {
        guard let arr = arr else { return [] }
        return arr.compactMap { item -> CodablePageSuggestion? in
            let pageNum: Int?
            if let n = item["page"] as? Int { pageNum = n }
            else if let s = item["page"] as? String { pageNum = Int(s) }
            else { pageNum = nil }
            guard let p = pageNum, p >= 1 && p <= PdfCurriculumData.totalPages else { return nil }
            let title = (item["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Trang \(p)"
            let reason = (item["reason"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return CodablePageSuggestion(page: p, title: title, reason: reason)
        }
    }

    static func parseDrawingPlan(_ raw: String) -> AiDrawingPlan? {
        guard let obj = extractJsonObject(raw) else { return nil }
        let raw3D = obj["scene3D"] as? [String: Any]
        let raw2D = obj["scene2D"] as? [String: Any]
        let targetStr = (obj["target"] as? String ?? "").uppercased()

        let target: AiDrawTarget
        if targetStr.contains("2D") && raw2D != nil {
            target = .plane2D
        } else if targetStr.contains("3D") && raw3D != nil {
            target = .space3D
        } else if raw3D != nil {
            target = .space3D
        } else if raw2D != nil {
            target = .plane2D
        } else {
            return nil
        }

        let title = (obj["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? (target == .space3D ? "Hình không gian 3D từ AI" : "Hình phẳng & Đồ thị 2D từ AI")
        let problemText = (obj["problemText"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let drawingSummary = (obj["drawingSummary"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let solutionHint = (obj["solutionHint"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let relatedPages = parsePageSuggestions(obj["relatedPages"] as? [[String: Any]])
            .map { AiPageSuggestion(page: $0.page, title: $0.title, reason: $0.reason) }

        var scene3D: AiScene3DPlan? = nil
        if let s3 = raw3D {
            let kind = Shape3DKind.fromKey(s3["shapeKey"] as? String ?? "pyramid4_sa")
            let a = (s3["a"] as? NSNumber)?.doubleValue ?? 4.0
            let b = (s3["b"] as? NSNumber)?.doubleValue ?? a
            let h = (s3["h"] as? NSNumber)?.doubleValue ?? 4.8
            let showAxes = (s3["showAxes"] as? Bool) ?? false
            let showAltitude = (s3["showAltitude"] as? Bool) ?? false

            let edgePoints = (s3["edgePoints"] as? [[String: Any]] ?? []).compactMap { ep -> EdgePoint3D? in
                guard let name = ep["name"] as? String,
                      let from = ep["from"] as? String,
                      let to = ep["to"] as? String else { return nil }
                let ratio = (ep["ratio"] as? NSNumber)?.doubleValue ?? 0.5
                return EdgePoint3D(name: name, from: from, to: to, ratio: ratio)
            }
            let xyzPoints = (s3["xyzPoints"] as? [[String: Any]] ?? []).compactMap { xp -> XyzPoint3D? in
                guard let name = xp["name"] as? String else { return nil }
                let x = (xp["x"] as? NSNumber)?.doubleValue ?? 0
                let y = (xp["y"] as? NSNumber)?.doubleValue ?? 0
                let z = (xp["z"] as? NSNumber)?.doubleValue ?? 0
                return XyzPoint3D(name: name, position: Vec3(x: x, y: z, z: y))
            }
            let lines = (s3["lines"] as? [[String: Any]] ?? []).compactMap { ln -> CustomLine3D? in
                guard let p1 = ln["p1"] as? String, let p2 = ln["p2"] as? String else { return nil }
                let style = (ln["style"] as? String ?? "solid").lowercased()
                let highlighted = (ln["highlighted"] as? Bool) ?? false
                let colorHex = (ln["colorHex"] as? String) ?? ""
                return CustomLine3D(
                    p1: p1, p2: p2,
                    isVector: style.contains("vector"),
                    forceDashed: style.contains("dashed"),
                    highlighted: highlighted,
                    colorHex: colorHex
                )
            }
            var planes = (s3["planes"] as? [[String: Any]] ?? []).compactMap { pl -> CustomPlane3D? in
                let pts = (pl["pointNames"] as? [String]) ?? []
                guard pts.count >= 3 else { return nil }
                return CustomPlane3D(pointNames: pts, colorHex: (pl["colorHex"] as? String) ?? "#A78BFA")
            }
            if let sec = s3["dynamicSection"] as? [String: Any],
               let p1 = sec["p1"] as? String,
               let p2 = sec["p2"] as? String,
               let p3 = sec["p3"] as? String {
                planes.append(CustomPlane3D(pointNames: [p1, p2, p3], colorHex: "#A78BFA"))
            }

            scene3D = AiScene3DPlan(
                shapeKind: kind,
                a: a, b: b, h: h,
                showAxes: showAxes,
                showAltitude: showAltitude,
                edgePoints: edgePoints,
                xyzPoints: xyzPoints,
                lines: lines,
                planes: planes
            )
        }

        var scene2D: AiScene2DPlan? = nil
        if let s2 = raw2D {
            let pts = (s2["points"] as? [[String: Any]] ?? []).compactMap { p -> Point2DItem? in
                guard let name = p["name"] as? String else { return nil }
                let x = (p["x"] as? NSNumber)?.doubleValue ?? 0
                let y = (p["y"] as? NSNumber)?.doubleValue ?? 0
                return Point2DItem(name: name, x: x, y: y, colorHex: (p["colorHex"] as? String) ?? "")
            }
            let objs = (s2["objects"] as? [[String: Any]] ?? []).compactMap { o -> Object2DItem? in
                let kindStr = (o["kind"] as? String ?? "segment").lowercased()
                let kind = Object2DKind(rawValue: kindStr) ?? .segment
                return Object2DItem(
                    kind: kind,
                    p1: (o["p1"] as? String) ?? "",
                    p2: (o["p2"] as? String) ?? "",
                    radius: (o["radius"] as? NSNumber)?.doubleValue ?? 0,
                    pointNames: (o["pointNames"] as? [String]) ?? [],
                    dashed: (o["dashed"] as? Bool) ?? false,
                    highlighted: (o["highlighted"] as? Bool) ?? false,
                    colorHex: (o["colorHex"] as? String) ?? ""
                )
            }
            let palette = ["#38BDF8", "#34D399", "#F43F5E", "#FBBF24", "#A78BFA"]
            let rawFns = s2["functions"] as? [Any] ?? []
            var fns: [Function2DItem] = []
            for (idx, item) in rawFns.enumerated() {
                if let expr = item as? String, !expr.isEmpty {
                    fns.append(Function2DItem(expression: expr, colorHex: palette[idx % palette.count]))
                } else if let dict = item as? [String: Any], let expr = dict["expression"] as? String, !expr.isEmpty {
                    let col = (dict["colorHex"] as? String)?.isEmpty == false ? (dict["colorHex"] as! String) : palette[idx % palette.count]
                    fns.append(Function2DItem(expression: expr, colorHex: col))
                }
            }
            scene2D = AiScene2DPlan(points: pts, objects: objs, functions: fns)
        }

        return AiDrawingPlan(
            target: target,
            title: title,
            problemText: problemText,
            drawingSummary: drawingSummary,
            solutionHint: solutionHint,
            scene3D: scene3D,
            scene2D: scene2D,
            relatedPages: relatedPages
        )
    }
}

// MARK: - AI Assistant ViewModel (7 Providers + Real-Time SSE Streaming)

@MainActor
final class AiAssistantStore: ObservableObject {
    @Published var activeProvider: AiProviderKind = .geminiApiKey {
        didSet { saveConfigs() }
    }
    @Published var configs: [AiProviderKind: AiProviderConfig] = [:] {
        didSet { saveConfigs() }
    }

    // Docs Q&A state
    @Published var messages: [AiChatItem] = []
    @Published var isAskingDocs = false
    @Published var streamingDocsAnswer = ""
    @Published var docsThinkingChars = 0
    @Published var docsStatusText = ""
    @Published var attachedPdfPage: Int? = nil

    // Problem Draw state
    @Published var problemImage: UIImage? = nil
    @Published var problemNote = ""
    @Published var isAnalyzingProblem = false
    @Published var streamingProblemPreview: AiProblemStreamingPreview? = nil
    @Published var problemStatusText = ""
    @Published var problemError: String? = nil
    @Published var latestDrawingPlan: AiDrawingPlan? = nil

    // Settings status
    @Published var connectionStatusMessage: String? = nil
    @Published var isSyncingModels = false

    private var docsTask: Task<Void, Never>? = nil
    private var problemTask: Task<Void, Never>? = nil

    init() {
        loadConfigs()
    }

    var activeConfig: AiProviderConfig {
        configs[activeProvider] ?? AiProviderConfig(
            apiKey: "",
            modelId: activeProvider.defaultModelId,
            baseUrl: activeProvider.defaultBaseUrl,
            syncedModels: []
        )
    }

    var activeModelId: String {
        let m = activeConfig.modelId.trimmingCharacters(in: .whitespacesAndNewlines)
        return m.isEmpty ? activeProvider.defaultModelId : m
    }

    var isReady: Bool {
        let cfg = activeConfig
        if activeProvider == .customOpenAi {
            return !cfg.baseUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        return !cfg.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func updateActiveConfig(_ transform: (inout AiProviderConfig) -> Void) {
        var cfg = activeConfig
        transform(&cfg)
        configs[activeProvider] = cfg
    }

    func clearChat() {
        docsTask?.cancel()
        messages = []
        isAskingDocs = false
        streamingDocsAnswer = ""
        docsThinkingChars = 0
        docsStatusText = ""
    }

    func cancelDocs() {
        docsTask?.cancel()
        isAskingDocs = false
        streamingDocsAnswer = ""
        docsThinkingChars = 0
        docsStatusText = ""
    }

    func cancelProblem() {
        problemTask?.cancel()
        isAnalyzingProblem = false
        streamingProblemPreview = nil
        problemStatusText = ""
        problemError = "Đã hủy yêu cầu phân tích đề bài."
    }

    func askDocs(question rawQuestion: String) {
        let question = rawQuestion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !question.isEmpty, !isAskingDocs else { return }
        guard isReady else {
            messages.append(AiChatItem(isUser: false, text: "Chưa cấu hình API Key / Base URL cho \(activeProvider.title). Hãy vào tab Cài đặt để nhập key.", isError: true))
            return
        }

        let explicitPage = attachedPdfPage ?? extractPageNumber(from: question)
        let history = Array(messages.filter { !$0.isError }.suffix(6))
        messages.append(AiChatItem(isUser: true, text: question, attachedPage: explicitPage))
        attachedPdfPage = nil

        let provider = activeProvider
        let cfg = activeConfig
        let model = activeModelId

        isAskingDocs = true
        streamingDocsAnswer = ""
        docsThinkingChars = 0
        docsStatusText = "Đang kết nối \(provider.title) (\(model))..."

        docsTask?.cancel()
        docsTask = Task {
            do {
                let pageImg = explicitPage.flatMap { PdfCurriculumData.renderPageImage(page1Based: $0, maxWidth: 960) }
                let raw = try await runStreamingCompletion(
                    provider: provider,
                    config: cfg,
                    modelId: model,
                    systemPrompt: buildDocsSystemPrompt(),
                    userPrompt: explicitPage != nil ? "\(question) (Đính kèm ảnh Trang \(explicitPage!) trong tài liệu PDF 388 trang)" : question,
                    history: history,
                    image: pageImg,
                    temperature: 0.25,
                    onRawAccumulated: { accum in
                        let preview = AiStreamParser.extractStreamingAnswerPreview(accum)
                        if !preview.isEmpty {
                            await MainActor.run {
                                self.streamingDocsAnswer = preview
                                self.docsStatusText = "\(provider.title) đang viết câu trả lời..."
                            }
                        }
                    },
                    onThinkingChars: { chars in
                        await MainActor.run {
                            if self.streamingDocsAnswer.isEmpty {
                                self.docsThinkingChars = chars
                                self.docsStatusText = "\(provider.title) đang suy luận... (\(chars) ký tự)"
                            }
                        }
                    }
                )
                let (answer, pages) = AiStreamParser.parseDocsReply(raw)
                messages.append(AiChatItem(isUser: false, text: answer, suggestedPages: pages))
            } catch is CancellationError {
                // Cancelled cleanly
            } catch {
                messages.append(AiChatItem(isUser: false, text: error.localizedDescription, isError: true))
            }
            isAskingDocs = false
            streamingDocsAnswer = ""
            docsThinkingChars = 0
            docsStatusText = ""
        }
    }

    func analyzeProblem() {
        guard !isAnalyzingProblem else { return }
        guard isReady else {
            problemError = "Chưa cấu hình API Key / Base URL cho \(activeProvider.title). Hãy vào tab Cài đặt."
            return
        }
        guard problemImage != nil || !problemNote.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            problemError = "Hãy chụp/chọn ảnh đề bài hoặc nhập nội dung đề bài."
            return
        }

        let provider = activeProvider
        let cfg = activeConfig
        let model = activeModelId
        let img = problemImage
        let note = problemNote.trimmingCharacters(in: .whitespacesAndNewlines)

        isAnalyzingProblem = true
        streamingProblemPreview = nil
        problemError = nil
        latestDrawingPlan = nil
        problemStatusText = "Đang gửi đề bài tới \(provider.title) (\(model))..."

        problemTask?.cancel()
        problemTask = Task {
            do {
                let prompt = (img != nil
                    ? "Hãy đọc thật chính xác đề bài toán trong ảnh đính kèm và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON."
                    : "Hãy phân tích đề bài toán sau và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON.")
                    + (note.isEmpty ? "" : "\nNội dung đề bài / ghi chú:\n\(note)")

                let raw = try await runStreamingCompletion(
                    provider: provider,
                    config: cfg,
                    modelId: model,
                    systemPrompt: buildProblemSystemPrompt(),
                    userPrompt: prompt,
                    history: [],
                    image: img,
                    temperature: 0.2,
                    onRawAccumulated: { accum in
                        await MainActor.run {
                            let thinkCount = self.streamingProblemPreview?.thinkingChars ?? 0
                            let prev = AiStreamParser.extractStreamingProblemPreview(accum, thinkingChars: thinkCount)
                            self.streamingProblemPreview = prev
                            if prev.isBuildingGeometry {
                                let dim = prev.target == .plane2D ? "2D" : "3D"
                                self.problemStatusText = "\(provider.title) đang hoàn thiện tọa độ hình \(dim)..."
                            } else if prev.hasVisibleText {
                                self.problemStatusText = "\(provider.title) đang viết phân tích & hướng giải..."
                            }
                        }
                    },
                    onThinkingChars: { chars in
                        await MainActor.run {
                            self.streamingProblemPreview = AiStreamParser.extractStreamingProblemPreview("", thinkingChars: chars)
                            self.problemStatusText = "\(provider.title) đang suy luận đề bài... (\(chars) ký tự)"
                        }
                    }
                )
                if let plan = AiStreamParser.parseDrawingPlan(raw) {
                    latestDrawingPlan = plan
                } else {
                    problemError = "AI không trả về dữ liệu dựng hình hợp lệ. Hãy thử chụp ảnh rõ hơn hoặc mô tả thêm đề bài."
                }
            } catch is CancellationError {
                // Cancelled cleanly
            } catch {
                problemError = error.localizedDescription
            }
            isAnalyzingProblem = false
            streamingProblemPreview = nil
            problemStatusText = ""
        }
    }

    func syncModels() {
        guard isReady else {
            connectionStatusMessage = "Hãy nhập API Key hoặc Base URL trước."
            return
        }
        isSyncingModels = true
        connectionStatusMessage = "Đang đồng bộ danh sách mô hình từ \(activeProvider.title)..."
        let provider = activeProvider
        let cfg = activeConfig

        Task {
            defer { isSyncingModels = false }
            do {
                let models = try await fetchLiveModels(provider: provider, config: cfg)
                if !models.isEmpty {
                    updateActiveConfig { c in
                        c.syncedModels = models
                        if !models.contains(c.modelId) {
                            c.modelId = models[0]
                        }
                    }
                    connectionStatusMessage = "Đồng bộ thành công \(models.count) mô hình từ \(provider.title)!"
                } else {
                    connectionStatusMessage = "Kết nối thành công nhưng không có danh sách mô hình trả về."
                }
            } catch {
                connectionStatusMessage = "Lỗi đồng bộ: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Unified SSE Streaming Transport for All Providers

    private func runStreamingCompletion(
        provider: AiProviderKind,
        config: AiProviderConfig,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        history: [AiChatItem],
        image: UIImage?,
        temperature: Double,
        onRawAccumulated: @escaping (String) async -> Void,
        onThinkingChars: @escaping (Int) async -> Void
    ) async throws -> String {
        switch provider {
        case .geminiApiKey:
            return try await streamGemini(
                apiKey: config.apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
                modelId: modelId,
                systemPrompt: systemPrompt,
                userPrompt: userPrompt,
                history: history,
                image: image,
                temperature: temperature,
                onRawAccumulated: onRawAccumulated,
                onThinkingChars: onThinkingChars
            )
        case .anthropic:
            return try await streamAnthropic(
                apiKey: config.apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
                modelId: modelId,
                systemPrompt: systemPrompt,
                userPrompt: userPrompt,
                history: history,
                image: image,
                temperature: temperature,
                onRawAccumulated: onRawAccumulated,
                onThinkingChars: onThinkingChars
            )
        case .openai, .openrouter, .groq, .deepseek, .customOpenAi:
            let base = resolveOpenAiBase(provider: provider, custom: config.baseUrl)
            return try await streamOpenAiCompat(
                provider: provider,
                baseUrl: base,
                apiKey: config.apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
                modelId: modelId,
                systemPrompt: systemPrompt,
                userPrompt: userPrompt,
                history: history,
                image: image,
                temperature: temperature,
                onRawAccumulated: onRawAccumulated,
                onThinkingChars: onThinkingChars
            )
        }
    }

    private func streamGemini(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        history: [AiChatItem],
        image: UIImage?,
        temperature: Double,
        onRawAccumulated: @escaping (String) async -> Void,
        onThinkingChars: @escaping (Int) async -> Void
    ) async throws -> String {
        let cleanModel = modelId.replacingOccurrences(of: "models/", with: "")
        guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(cleanModel):streamGenerateContent?alt=sse") else {
            throw NSError(domain: "ToolToan", code: -1, userInfo: [NSLocalizedDescriptionKey: "URL Gemini không hợp lệ."])
        }

        var contents: [[String: Any]] = history.map { msg in
            ["role": msg.isUser ? "user" : "model", "parts": [["text": msg.text]]]
        }
        var userParts: [[String: Any]] = []
        if let img = image, let b64 = encodeJpegBase64(img) {
            userParts.append(["inlineData": ["mimeType": "image/jpeg", "data": b64]])
        }
        userParts.append(["text": userPrompt])
        contents.append(["role": "user", "parts": userParts])

        let body: [String: Any] = [
            "systemInstruction": ["parts": [["text": systemPrompt]]],
            "contents": contents,
            "generationConfig": [
                "responseMimeType": "application/json",
                "temperature": temperature
            ]
        ]

        var req = URLRequest(url: url, timeoutInterval: 85)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        req.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (bytes, resp) = try await URLSession.shared.bytes(for: req)
        try await validateHttpResponse(resp: resp, bytes: bytes, providerName: "Google Gemini")

        var accumulated = ""
        var thinking = 0
        for try await line in bytes.lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard trimmed.hasPrefix("data:") else { continue }
            let dataStr = String(trimmed.dropFirst(5)).trimmingCharacters(in: .whitespaces)
            if dataStr.isEmpty || dataStr == "[DONE]" { continue }
            guard let data = dataStr.data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let candidates = obj["candidates"] as? [[String: Any]],
                  let content = candidates.first?["content"] as? [String: Any],
                  let parts = content["parts"] as? [[String: Any]] else { continue }
            var appended = false
            for p in parts {
                guard let t = p["text"] as? String, !t.isEmpty else { continue }
                if (p["thought"] as? Bool) == true {
                    thinking += t.count
                    await onThinkingChars(thinking)
                } else {
                    accumulated += t
                    appended = true
                }
            }
            if appended {
                await onRawAccumulated(accumulated)
            }
        }
        if accumulated.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            throw NSError(domain: "ToolToan", code: -2, userInfo: [NSLocalizedDescriptionKey: "Google Gemini trả về nội dung trống."])
        }
        return accumulated
    }

    private func streamOpenAiCompat(
        provider: AiProviderKind,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        history: [AiChatItem],
        image: UIImage?,
        temperature: Double,
        onRawAccumulated: @escaping (String) async -> Void,
        onThinkingChars: @escaping (Int) async -> Void
    ) async throws -> String {
        guard let url = URL(string: "\(baseUrl)/chat/completions") else {
            throw NSError(domain: "ToolToan", code: -1, userInfo: [NSLocalizedDescriptionKey: "Base URL không hợp lệ."])
        }

        var msgs: [[String: Any]] = [["role": "system", "content": systemPrompt]]
        for msg in history {
            msgs.append(["role": msg.isUser ? "user" : "assistant", "content": msg.text])
        }
        if let img = image, let b64 = encodeJpegBase64(img) {
            msgs.append([
                "role": "user",
                "content": [
                    ["type": "image_url", "image_url": ["url": "data:image/jpeg;base64,\(b64)"]],
                    ["type": "text", "text": userPrompt]
                ]
            ])
        } else {
            msgs.append(["role": "user", "content": userPrompt])
        }

        func makePayload(strict: Bool, stream: Bool) -> [String: Any] {
            var p: [String: Any] = ["model": modelId, "messages": msgs, "stream": stream]
            if strict {
                p["temperature"] = temperature
                p["response_format"] = ["type": "json_object"]
            }
            return p
        }

        func attempt(strict: Bool) async throws -> String {
            var req = URLRequest(url: url, timeoutInterval: 85)
            req.httpMethod = "POST"
            req.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
            req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
            if !apiKey.isEmpty {
                req.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
            }
            req.httpBody = try JSONSerialization.data(withJSONObject: makePayload(strict: strict, stream: true))

            let (bytes, resp) = try await URLSession.shared.bytes(for: req)
            if let http = resp as? HTTPURLResponse, (http.statusCode == 400 || http.statusCode == 422), strict {
                throw NSError(domain: "ToolToanRetry", code: http.statusCode, userInfo: nil)
            }
            try await validateHttpResponse(resp: resp, bytes: bytes, providerName: provider.title)

            var accumulated = ""
            var rawJsonFallback = ""
            var isNonSse = false
            var sawLine = false
            var thinking = 0

            for try await line in bytes.lines {
                let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty { continue }
                if !sawLine {
                    sawLine = true
                    if trimmed.hasPrefix("{") && !trimmed.hasPrefix("data:") {
                        isNonSse = true
                        rawJsonFallback += line + "\n"
                        continue
                    }
                }
                if isNonSse {
                    rawJsonFallback += line + "\n"
                    continue
                }
                guard trimmed.hasPrefix("data:") else { continue }
                let dataStr = String(trimmed.dropFirst(5)).trimmingCharacters(in: .whitespaces)
                if dataStr == "[DONE]" { break }
                guard let data = dataStr.data(using: .utf8),
                      let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let choices = obj["choices"] as? [[String: Any]],
                      let delta = choices.first?["delta"] as? [String: Any] else { continue }

                if let reason = (delta["reasoning_content"] as? String) ?? (delta["reasoning"] as? String), !reason.isEmpty {
                    thinking += reason.count
                    await onThinkingChars(thinking)
                }
                if let piece = delta["content"] as? String, !piece.isEmpty {
                    accumulated += piece
                    await onRawAccumulated(accumulated)
                }
            }

            if isNonSse, let d = rawJsonFallback.data(using: .utf8),
               let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
               let choices = obj["choices"] as? [[String: Any]],
               let msg = choices.first?["message"] as? [String: Any],
               let text = msg["content"] as? String {
                await onRawAccumulated(text)
                return text
            }
            if accumulated.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                throw NSError(domain: "ToolToan", code: -2, userInfo: [NSLocalizedDescriptionKey: "\(provider.title) trả về nội dung trống."])
            }
            return accumulated
        }

        do {
            return try await attempt(strict: true)
        } catch let err as NSError where err.domain == "ToolToanRetry" {
            // Tier 2: Keep stream=true while relaxing response_format / temperature!
            return try await attempt(strict: false)
        }
    }

    private func streamAnthropic(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        history: [AiChatItem],
        image: UIImage?,
        temperature: Double,
        onRawAccumulated: @escaping (String) async -> Void,
        onThinkingChars: @escaping (Int) async -> Void
    ) async throws -> String {
        guard let url = URL(string: "https://api.anthropic.com/v1/messages") else {
            throw NSError(domain: "ToolToan", code: -1, userInfo: [NSLocalizedDescriptionKey: "URL Anthropic không hợp lệ."])
        }

        var msgs: [[String: Any]] = history.map {
            ["role": $0.isUser ? "user" : "assistant", "content": $0.text]
        }
        if let img = image, let b64 = encodeJpegBase64(img) {
            msgs.append([
                "role": "user",
                "content": [
                    ["type": "image", "source": ["type": "base64", "media_type": "image/jpeg", "data": b64]],
                    ["type": "text", "text": userPrompt]
                ]
            ])
        } else {
            msgs.append(["role": "user", "content": userPrompt])
        }

        let payload: [String: Any] = [
            "model": modelId,
            "max_tokens": 4096,
            "temperature": temperature,
            "system": systemPrompt,
            "messages": msgs,
            "stream": true
        ]

        var req = URLRequest(url: url, timeoutInterval: 85)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        req.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        req.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (bytes, resp) = try await URLSession.shared.bytes(for: req)
        try await validateHttpResponse(resp: resp, bytes: bytes, providerName: "Anthropic Claude")

        var accumulated = ""
        var thinking = 0
        for try await line in bytes.lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard trimmed.hasPrefix("data:") else { continue }
            let dataStr = String(trimmed.dropFirst(5)).trimmingCharacters(in: .whitespaces)
            guard let data = dataStr.data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  (obj["type"] as? String) == "content_block_delta",
                  let delta = obj["delta"] as? [String: Any] else { continue }
            let dType = delta["type"] as? String ?? ""
            if dType == "text_delta", let piece = delta["text"] as? String, !piece.isEmpty {
                accumulated += piece
                await onRawAccumulated(accumulated)
            } else if dType == "thinking_delta", let think = delta["thinking"] as? String, !think.isEmpty {
                thinking += think.count
                await onThinkingChars(thinking)
            }
        }
        if accumulated.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            throw NSError(domain: "ToolToan", code: -2, userInfo: [NSLocalizedDescriptionKey: "Claude trả về nội dung trống."])
        }
        return accumulated
    }

    private func fetchLiveModels(provider: AiProviderKind, config: AiProviderConfig) async throws -> [String] {
        let key = config.apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        switch provider {
        case .geminiApiKey:
            guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100") else { return [] }
            var req = URLRequest(url: url, timeoutInterval: 20)
            req.setValue(key, forHTTPHeaderField: "x-goog-api-key")
            let (data, resp) = try await URLSession.shared.data(for: req)
            if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                throw NSError(domain: "ToolToan", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(http.statusCode)"])
            }
            let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            let list = obj?["models"] as? [[String: Any]] ?? []
            return list.compactMap { m -> String? in
                guard let name = m["name"] as? String else { return nil }
                let methods = m["supportedGenerationMethods"] as? [String] ?? []
                guard methods.contains("generateContent") else { return nil }
                return name.replacingOccurrences(of: "models/", with: "")
            }

        case .anthropic:
            guard let url = URL(string: "https://api.anthropic.com/v1/models") else { return [] }
            var req = URLRequest(url: url, timeoutInterval: 20)
            req.setValue(key, forHTTPHeaderField: "x-api-key")
            req.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
            let (data, resp) = try await URLSession.shared.data(for: req)
            if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                throw NSError(domain: "ToolToan", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(http.statusCode)"])
            }
            let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            return (obj?["data"] as? [[String: Any]] ?? []).compactMap { $0["id"] as? String }

        default:
            let base = resolveOpenAiBase(provider: provider, custom: config.baseUrl)
            guard let url = URL(string: "\(base)/models") else { return [] }
            var req = URLRequest(url: url, timeoutInterval: 20)
            if !key.isEmpty { req.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization") }
            let (data, resp) = try await URLSession.shared.data(for: req)
            if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                throw NSError(domain: "ToolToan", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(http.statusCode)"])
            }
            let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            return (obj?["data"] as? [[String: Any]] ?? []).compactMap { $0["id"] as? String }
        }
    }

    private func validateHttpResponse(resp: URLResponse, bytes: URLSession.AsyncBytes, providerName: String) async throws {
        guard let http = resp as? HTTPURLResponse else { return }
        guard !(200...299).contains(http.statusCode) else { return }
        var snippet = ""
        for try await line in bytes.lines {
            snippet += line
            if snippet.count > 260 { break }
        }
        throw NSError(
            domain: "ToolToan",
            code: http.statusCode,
            userInfo: [NSLocalizedDescriptionKey: "\(providerName) lỗi HTTP \(http.statusCode): \(snippet.prefix(200))"]
        )
    }

    private func resolveOpenAiBase(provider: AiProviderKind, custom: String) -> String {
        let raw = provider == .customOpenAi ? custom : provider.defaultBaseUrl
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        if s.hasSuffix("/chat/completions") {
            s = String(s.dropLast("/chat/completions".count))
        }
        return s
    }

    private func encodeJpegBase64(_ image: UIImage) -> String? {
        let maxDim: CGFloat = 1024
        let size = image.size
        let scale = min(1.0, maxDim / max(size.width, size.height))
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        UIGraphicsBeginImageContextWithOptions(target, true, 1.0)
        image.draw(in: CGRect(origin: .zero, size: target))
        let resized = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return (resized ?? image).jpegData(compressionQuality: 0.82)?.base64EncodedString()
    }

    private func extractPageNumber(from text: String) -> Int? {
        let pattern = "(?i)\\b(?:trang|tr\\.?)\\s*(\\d{1,3})\\b"
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let r = Range(match.range(at: 1), in: text),
              let n = Int(text[r]), n >= 1 && n <= PdfCurriculumData.totalPages else {
            return nil
        }
        return n
    }

    // MARK: - Prompts (Streaming-First Key Ordering)

    private func buildDocsSystemPrompt() -> String {
        let toc = PdfCurriculumData.allTopics.map {
            "- [\($0.grade.rawValue)] \($0.title) (Tr.\($0.startPage)-\($0.endPage)): \($0.summary)"
        }.joined(separator: "\n")
        return """
        Bạn là Trợ lý Toán học THPT (Lớp 10, 11, 12) tích hợp trong ứng dụng Tool Toán.
        Tài liệu PDF đi kèm gồm 388 trang (Tóm Tắt Kiến Thức Toán 10-11-12 DPAD):
        \(toc)

        NHIỆM VỤ:
        - Trả lời ngắn gọn, dễ hiểu bằng tiếng Việt, dùng ký hiệu toán Unicode (√, π, α, ², ³, ∫, ∞, ≤, ≥, ⊥, //, ∈, ℝ).
        - Chỉ rõ các trang PDF 388 trang liên quan nhất để người dùng bấm mở thẳng.
        - BẮT BUỘC TRẢ VỀ JSON HỢP LỆ (không bọc markdown) VÀ ĐẶT TRƯỜNG "answer" LÊN ĐẦU TIÊN TRƯỚC "suggestedPages":
        {"answer": "Nội dung trả lời chi tiết bằng tiếng Việt", "suggestedPages": [{"page": 121, "title": "Công thức lượng giác", "reason": "Công thức cộng, nhân đôi"}]}
        """
    }

    private func buildProblemSystemPrompt() -> String {
        return """
        Bạn là chuyên gia Toán THPT (Hình không gian 3D, Oxyz, Hình phẳng Oxy và Khảo sát hàm số 2D) trong ứng dụng Tool Toán.
        Danh sách 15 khối 3D chuẩn (`shapeKey`): pyramid3, pyramid3_sa, tetra_ortho, pyramid4, pyramid4_sa, pyramid_trap, prism3, prism3_oblique, prism6, cube, box, octahedron, cylinder, cone, sphere.
        BẮT BUỘC TRẢ VỀ JSON HỢP LỆ DUY NHẤT (không bọc markdown) VÀ XUẤT ĐÚNG THỨ TỰ CÁC TRƯỜNG "target" → "title" → "problemText" → "drawingSummary" → "solutionHint" TRƯỚC, sau đó mới đến "scene3D" / "scene2D" và "relatedPages":
        {
          "target": "3D",
          "title": "Tên ngắn gọn hình vẽ",
          "problemText": "Nội dung đề bài đọc được",
          "drawingSummary": "Mô tả hình khối, điểm phụ và đoạn/vectơ tô màu",
          "solutionHint": "Gợi ý hướng giải ngắn gọn",
          "scene3D": {
            "shapeKey": "pyramid4_sa", "a": 4.0, "b": 4.0, "h": 5.0,
            "showAxes": false, "showAltitude": false,
            "edgePoints": [{"name":"M","from":"S","to":"C","ratio":0.5}],
            "xyzPoints": [],
            "lines": [{"p1":"A","p2":"M","style":"vector_solid","highlighted":true,"colorHex":"#F43F5E"}],
            "planes": [],
            "dynamicSection": null
          },
          "scene2D": null,
          "relatedPages": [{"page": 235, "title": "Thể tích đa diện", "reason": "Công thức thể tích khối chóp"}]
        }
        Nếu bài toán là 2D/Oxy/đồ thị hàm số, đặt target="2D", scene3D=null và điền scene2D với `points`, `objects` (segment|line|ray|vector|circle|polygon) và `functions` (vd: ["x^3 - 3x + 1"]).
        """
    }

    // MARK: - Persistence

    private func saveConfigs() {
        UserDefaults.standard.set(activeProvider.rawValue, forKey: "tooltoan_ai_active_provider")
        var rawDict: [String: AiProviderConfig] = [:]
        for (k, v) in configs { rawDict[k.rawValue] = v }
        if let data = try? JSONEncoder().encode(rawDict) {
            UserDefaults.standard.set(data, forKey: "tooltoan_ai_configs_v1")
        }
    }

    private func loadConfigs() {
        if let rawProv = UserDefaults.standard.string(forKey: "tooltoan_ai_active_provider"),
           let p = AiProviderKind(rawValue: rawProv) {
            activeProvider = p
        }
        if let data = UserDefaults.standard.data(forKey: "tooltoan_ai_configs_v1"),
           let rawDict = try? JSONDecoder().decode([String: AiProviderConfig].self, from: data) {
            var map: [AiProviderKind: AiProviderConfig] = [:]
            for (k, v) in rawDict {
                if let p = AiProviderKind(rawValue: k) { map[p] = v }
            }
            configs = map
        }
    }
}
