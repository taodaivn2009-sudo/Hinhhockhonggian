import SwiftUI
import PDFKit

// MARK: - 388-Page DPAD Curriculum Table of Contents (Synced 1:1 with Android)

enum PdfGradeLevel: String, CaseIterable, Identifiable {
    case grade10 = "Lớp 10"
    case grade11 = "Lớp 11"
    case grade12 = "Lớp 12"

    var id: String { rawValue }

    var accent: Color {
        switch self {
        case .grade10: return AppTheme.cyan
        case .grade11: return AppTheme.purple
        case .grade12: return AppTheme.emerald
        }
    }
}

struct PdfTopicItem: Identifiable {
    let id: String
    let grade: PdfGradeLevel
    let title: String
    let startPage: Int
    let endPage: Int
    let summary: String
}

enum PdfCurriculumData {
    static let totalPages = 388

    static let allTopics: [PdfTopicItem] = [
        // Lớp 10
        PdfTopicItem(id: "g10_1", grade: .grade10, title: "Mệnh đề & Tập hợp", startPage: 18, endPage: 25, summary: "Mệnh đề, ∀/∃, giao – hợp – hiệu – phần bù, tập con của ℝ."),
        PdfTopicItem(id: "g10_2", grade: .grade10, title: "Bất phương trình bậc nhất hai ẩn", startPage: 26, endPage: 28, summary: "Miền nghiệm hệ BPT bậc nhất 2 ẩn & bài toán tối ưu F = ax + by."),
        PdfTopicItem(id: "g10_3", grade: .grade10, title: "Hàm số & Hàm bậc hai (Vi-ét)", startPage: 29, endPage: 35, summary: "Tập xác định, parabol, bảng biến thiên & hệ thức Vi-ét."),
        PdfTopicItem(id: "g10_4", grade: .grade10, title: "Lượng giác 0°–180° & Hệ thức lượng", startPage: 36, endPage: 44, summary: "Định lý Côsin, Sin, trung tuyến, diện tích Heron."),
        PdfTopicItem(id: "g10_5", grade: .grade10, title: "Vectơ & Tích vô hướng", startPage: 45, endPage: 53, summary: "Quy tắc cộng trừ, trung điểm, trọng tâm, góc & tích vô hướng."),
        PdfTopicItem(id: "g10_6", grade: .grade10, title: "Thống kê không ghép nhóm", startPage: 54, endPage: 71, summary: "Trung bình, trung vị, tứ phân vị, phương sai & độ lệch chuẩn."),
        PdfTopicItem(id: "g10_7", grade: .grade10, title: "Tam thức bậc hai & Tổ hợp – Newton", startPage: 72, endPage: 87, summary: "Xét dấu tam thức bậc 2, hoán vị, chỉnh hợp, tổ hợp & Newton."),
        PdfTopicItem(id: "g10_8", grade: .grade10, title: "Tọa độ Oxy & Đường thẳng", startPage: 88, endPage: 98, summary: "Tâm tam giác, PT tổng quát – tham số, góc & khoảng cách."),
        PdfTopicItem(id: "g10_9", grade: .grade10, title: "Đường tròn, Elip, Hyperbol, Parabol", startPage: 99, endPage: 108, summary: "Phương trình đường tròn, tiếp tuyến và ba đường Conic."),
        PdfTopicItem(id: "g10_10", grade: .grade10, title: "Xác suất cổ điển", startPage: 109, endPage: 110, summary: "Không gian mẫu, biến cố, quy tắc cộng – nhân xác suất."),

        // Lớp 11
        PdfTopicItem(id: "g11_1", grade: .grade11, title: "Lượng giác & Phương trình lượng giác", startPage: 111, endPage: 129, summary: "Bảng công thức cộng, nhân đôi, hạ bậc (Tr.121) & PT lượng giác."),
        PdfTopicItem(id: "g11_2", grade: .grade11, title: "Quy nạp, Dãy số, CSC & CSN", startPage: 130, endPage: 140, summary: "Cấp số cộng (Tr.139), cấp số nhân (Tr.140) & dãy tổng."),
        PdfTopicItem(id: "g11_3", grade: .grade11, title: "Giới hạn dãy số & Giới hạn hàm số", startPage: 141, endPage: 168, summary: "CSN lùi vô hạn, 7 dạng vô định (Tr.163) & giới hạn một bên."),
        PdfTopicItem(id: "g11_4", grade: .grade11, title: "Hàm số liên tục & Đạo hàm – Tiếp tuyến", startPage: 169, endPage: 180, summary: "Bảng đạo hàm đầy đủ (Tr.176) & phương trình tiếp tuyến (Tr.178)."),
        PdfTopicItem(id: "g11_5", grade: .grade11, title: "Lũy thừa, Mũ, Logarit & Lãi suất", startPage: 181, endPage: 197, summary: "Công thức lãi suất (Tr.182), Logarit (Tr.192), PT & BPT Mũ–Loga."),
        PdfTopicItem(id: "g11_6", grade: .grade11, title: "Hình không gian: Giao tuyến & Thiết diện", startPage: 198, endPage: 213, summary: "Menelaus, giao tuyến, thiết diện, chứng minh // và ⊥."),
        PdfTopicItem(id: "g11_7", grade: .grade11, title: "Góc, Khoảng cách & Thể tích đa diện", startPage: 214, endPage: 239, summary: "Góc nhị diện (Tr.214), 2 đường chéo nhau (Tr.218), Simson (Tr.235)."),
        PdfTopicItem(id: "g11_8", grade: .grade11, title: "Thống kê ghép nhóm", startPage: 240, endPage: 255, summary: "Trung bình, trung vị, tứ phân vị & phương sai mẫu ghép nhóm."),

        // Lớp 12
        PdfTopicItem(id: "g12_1", grade: .grade12, title: "Đơn điệu & Cực trị hàm số", startPage: 256, endPage: 275, summary: "Tham số m, hàm |f(x)|, cực trị bậc 3 & trùng phương."),
        PdfTopicItem(id: "g12_2", grade: .grade12, title: "Max–Min & Đường tiệm cận", startPage: 276, endPage: 282, summary: "GTLN–GTNN, TCĐ, TCN và tiệm cận xiên."),
        PdfTopicItem(id: "g12_3", grade: .grade12, title: "Khảo sát đồ thị & Tương giao", startPage: 283, endPage: 306, summary: "Bậc 3, phân thức 1/1, 2/1, phép biến đổi đồ thị."),
        PdfTopicItem(id: "g12_4", grade: .grade12, title: "Nguyên hàm & Tích phân – Ứng dụng", startPage: 307, endPage: 331, summary: "Bảng nguyên hàm (Tr.307), từng phần (Tr.314), diện tích & thể tích."),
        PdfTopicItem(id: "g12_5", grade: .grade12, title: "Vectơ & Hệ tọa độ Oxyz", startPage: 332, endPage: 352, summary: "Tọa độ Oxyz, tích có hướng, tích hỗn tạp & thể tích."),
        PdfTopicItem(id: "g12_6", grade: .grade12, title: "Mặt cầu, Mặt phẳng & Đường thẳng Oxyz", startPage: 353, endPage: 373, summary: "11 dạng PTMP (Tr.358), 13 dạng PT đường thẳng (Tr.363), VTTĐ."),
        PdfTopicItem(id: "g12_7", grade: .grade12, title: "Hình chiếu, Góc – Khoảng cách & Cực trị Oxyz", startPage: 374, endPage: 385, summary: "Tổng hợp góc/khoảng cách Oxyz (Tr.375) & tâm tỷ cự."),
        PdfTopicItem(id: "g12_8", grade: .grade12, title: "Xác suất có điều kiện & Công thức Bayes", startPage: 386, endPage: 387, summary: "P(A|B), xác suất toàn phần, định lý Bayes & sơ đồ cây.")
    ]

    static func loadBundledDocument() -> PDFDocument? {
        if let url = Bundle.main.url(forResource: "toan_10_11_12_dpad", withExtension: "pdf") {
            return PDFDocument(url: url)
        }
        return nil
    }

    static func renderPageImage(page1Based: Int, maxWidth: CGFloat = 1024) -> UIImage? {
        guard let doc = loadBundledDocument() else { return nil }
        let idx = min(max(page1Based - 1, 0), max(doc.pageCount - 1, 0))
        guard let page = doc.page(at: idx) else { return nil }
        let bounds = page.bounds(for: .mediaBox)
        let scale = maxWidth / max(bounds.width, 1)
        let targetSize = CGSize(width: bounds.width * scale, height: bounds.height * scale)
        return page.thumbnail(of: targetSize, for: .mediaBox)
    }
}

final class PdfDocsStore: ObservableObject {
    @Published var currentPage: Int = 1 {
        didSet {
            UserDefaults.standard.set(currentPage, forKey: "tooltoan_pdf_last_page")
        }
    }
    @Published var nightFilter: Bool = false {
        didSet {
            UserDefaults.standard.set(nightFilter, forKey: "tooltoan_pdf_night_filter")
        }
    }
    @Published var bookmarks: [Int] = [] {
        didSet {
            UserDefaults.standard.set(bookmarks, forKey: "tooltoan_pdf_bookmarks")
        }
    }
    @Published var jumpRequestToken: Int = 0

    let document: PDFDocument?

    init() {
        document = PdfCurriculumData.loadBundledDocument()
        let saved = UserDefaults.standard.integer(forKey: "tooltoan_pdf_last_page")
        currentPage = (saved >= 1 && saved <= PdfCurriculumData.totalPages) ? saved : 1
        nightFilter = UserDefaults.standard.bool(forKey: "tooltoan_pdf_night_filter")
        bookmarks = (UserDefaults.standard.array(forKey: "tooltoan_pdf_bookmarks") as? [Int]) ?? []
    }

    func goToPage(_ page1Based: Int) {
        let clamped = min(max(page1Based, 1), max(document?.pageCount ?? PdfCurriculumData.totalPages, 1))
        currentPage = clamped
        jumpRequestToken += 1
    }

    func toggleBookmark(_ page: Int) {
        if let idx = bookmarks.firstIndex(of: page) {
            bookmarks.remove(at: idx)
        } else {
            bookmarks.append(page)
            bookmarks.sort()
        }
    }
}

struct PdfDocsTab: View {
    @ObservedObject var store: PdfDocsStore
    @Binding var isImmersive: Bool
    var onAskAiAboutPage: (Int) -> Void

    @State private var showTocSheet = false
    @State private var showJumpAlert = false
    @State private var jumpText = ""

    var body: some View {
        ZStack {
            if let doc = store.document {
                NativePdfKitView(
                    document: doc,
                    currentPage: $store.currentPage,
                    jumpToken: store.jumpRequestToken
                )
                .colorInvertIf(store.nightFilter)
                .ignoresSafeArea(edges: .bottom)
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.22)) {
                        isImmersive.toggle()
                    }
                }
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "doc.text.magnifyingglass")
                        .font(.system(size: 40))
                        .foregroundColor(AppTheme.amber)
                    Text("Không tìm thấy tệp PDF 388 trang trong bản cài đặt.")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(AppTheme.textPrimary)
                        .multilineTextAlignment(.center)
                }
                .padding(24)
            }

            if !isImmersive {
                VStack {
                    HStack(spacing: 8) {
                        Button(action: { showTocSheet = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "list.bullet.rectangle")
                                    .foregroundColor(AppTheme.cyan)
                                Text("Mục lục")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(AppTheme.textPrimary)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .liquidGlassCard(cornerRadius: 15, borderColor: AppTheme.cyan.opacity(0.45))
                        }

                        Button(action: {
                            jumpText = "\(store.currentPage)"
                            showJumpAlert = true
                        }) {
                            Text("Tr. \(store.currentPage) / \(store.document?.pageCount ?? PdfCurriculumData.totalPages)")
                                .font(.system(size: 12, weight: .bold, design: .monospaced))
                                .foregroundColor(AppTheme.textPrimary)
                                .padding(.horizontal, 11)
                                .padding(.vertical, 9)
                                .liquidGlassCard(cornerRadius: 15)
                        }

                        Spacer()

                        Button(action: { onAskAiAboutPage(store.currentPage) }) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(AppTheme.purple)
                                .frame(width: 38, height: 38)
                                .liquidGlassCard(cornerRadius: 13, borderColor: AppTheme.purple.opacity(0.5))
                        }

                        Button(action: { store.toggleBookmark(store.currentPage) }) {
                            let saved = store.bookmarks.contains(store.currentPage)
                            Image(systemName: saved ? "bookmark.fill" : "bookmark")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(saved ? AppTheme.amber : AppTheme.textPrimary)
                                .frame(width: 38, height: 38)
                                .liquidGlassCard(cornerRadius: 13)
                        }

                        Button(action: { store.nightFilter.toggle() }) {
                            Image(systemName: store.nightFilter ? "sun.max.fill" : "moon.fill")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(store.nightFilter ? AppTheme.amber : AppTheme.cyan)
                                .frame(width: 38, height: 38)
                                .liquidGlassCard(cornerRadius: 13)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 8)

                    Spacer()
                }
            }
        }
        .sheet(isPresented: $showTocSheet) {
            PdfTocSheet(store: store)
        }
        .sheet(isPresented: $showJumpAlert) {
            NavigationView {
                ZStack {
                    LiquidGlassBackdropView()
                    VStack(spacing: 16) {
                        Text("Nhập số trang (1 – \(PdfCurriculumData.totalPages))")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(AppTheme.textPrimary)
                        TextField("Số trang", text: $jumpText)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .padding(.horizontal, 24)
                        Button(action: {
                            if let p = Int(jumpText.trimmingCharacters(in: .whitespaces)) {
                                store.goToPage(p)
                            }
                            showJumpAlert = false
                        }) {
                            Text("Mở trang")
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
                    .padding(.top, 28)
                }
                .navigationTitle("Chuyển nhanh trang PDF")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Đóng") { showJumpAlert = false }
                            .foregroundColor(AppTheme.cyan)
                    }
                }
            }
            .navigationViewStyle(StackNavigationViewStyle())
        }
    }
}

private extension View {
    @ViewBuilder
    func colorInvertIf(_ active: Bool) -> some View {
        if active {
            self.colorInvert()
        } else {
            self
        }
    }
}

private struct NativePdfKitView: UIViewRepresentable {
    let document: PDFDocument
    @Binding var currentPage: Int
    let jumpToken: Int

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.document = document
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        pdfView.backgroundColor = UIColor(red: 0.04, green: 0.06, blue: 0.11, alpha: 1.0)
        NotificationCenter.default.addObserver(
            context.coordinator,
            selector: #selector(Coordinator.pageChanged(_:)),
            name: .PDFViewPageChanged,
            object: pdfView
        )
        if let page = document.page(at: max(0, min(currentPage - 1, document.pageCount - 1))) {
            pdfView.go(to: page)
        }
        context.coordinator.lastHandledJumpToken = jumpToken
        return pdfView
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        if context.coordinator.lastHandledJumpToken != jumpToken {
            context.coordinator.lastHandledJumpToken = jumpToken
            let idx = max(0, min(currentPage - 1, document.pageCount - 1))
            if let page = document.page(at: idx) {
                uiView.go(to: page)
            }
        }
    }

    final class Coordinator: NSObject {
        var parent: NativePdfKitView
        var lastHandledJumpToken: Int = -1

        init(parent: NativePdfKitView) {
            self.parent = parent
        }

        @objc func pageChanged(_ note: Notification) {
            guard let pdfView = note.object as? PDFView,
                  let page = pdfView.currentPage,
                  let idx = pdfView.document?.index(for: page) else { return }
            let oneBased = idx + 1
            DispatchQueue.main.async {
                if self.parent.currentPage != oneBased {
                    self.parent.currentPage = oneBased
                }
            }
        }
    }
}

private struct PdfTocSheet: View {
    @ObservedObject var store: PdfDocsStore
    @Environment(\.presentationMode) private var presentationMode
    @State private var selectedGrade: PdfGradeLevel = .grade12
    @State private var query = ""

    private var filteredTopics: [PdfTopicItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            let fold = trimmed.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            return PdfCurriculumData.allTopics.filter { item in
                let hay = "\(item.title) \(item.summary) \(item.grade.rawValue) trang \(item.startPage)"
                    .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
                return hay.contains(fold)
            }
        }
        return PdfCurriculumData.allTopics.filter { $0.grade == selectedGrade }
    }

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackdropView()
                VStack(spacing: 10) {
                    TextField("Tìm công thức, chuyên đề, số trang...", text: $query)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .padding(.horizontal, 14)
                        .padding(.top, 8)

                    if query.trimmingCharacters(in: .whitespaces).isEmpty {
                        Picker("Khối lớp", selection: $selectedGrade) {
                            ForEach(PdfGradeLevel.allCases) { g in
                                Text(g.rawValue).tag(g)
                            }
                        }
                        .pickerStyle(SegmentedPickerStyle())
                        .padding(.horizontal, 14)
                    }

                    if !store.bookmarks.isEmpty && query.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(store.bookmarks, id: \.self) { p in
                                    Button("★ Tr.\(p)") {
                                        store.goToPage(p)
                                        presentationMode.wrappedValue.dismiss()
                                    }
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(AppTheme.amber)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(AppTheme.amber.opacity(0.16))
                                    .clipShape(Capsule())
                                }
                            }
                            .padding(.horizontal, 14)
                        }
                    }

                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(filteredTopics) { topic in
                                Button(action: {
                                    store.goToPage(topic.startPage)
                                    presentationMode.wrappedValue.dismiss()
                                }) {
                                    VStack(alignment: .leading, spacing: 5) {
                                        HStack {
                                            Text(topic.title)
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(AppTheme.textPrimary)
                                                .multilineTextAlignment(.leading)
                                            Spacer()
                                            Text("Tr. \(topic.startPage)–\(topic.endPage)")
                                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                                .foregroundColor(topic.grade.accent)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(topic.grade.accent.opacity(0.16))
                                                .clipShape(Capsule())
                                        }
                                        Text(topic.summary)
                                            .font(.system(size: 12))
                                            .foregroundColor(AppTheme.textSecondary)
                                            .multilineTextAlignment(.leading)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(12)
                                    .liquidGlassCard(cornerRadius: 14, borderColor: topic.grade.accent.opacity(0.35))
                                }
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.bottom, 20)
                    }
                }
            }
            .navigationTitle("Mục lục 388 trang (DPAD)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Đóng") { presentationMode.wrappedValue.dismiss() }
                        .foregroundColor(AppTheme.cyan)
                }
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
}
