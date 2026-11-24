import SwiftUI

enum RootTab: Int, CaseIterable, Identifiable {
    case space3D = 0
    case plane2D = 1
    case pdfDocs = 2
    case ai = 3
    case settings = 4

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .space3D: return "3D"
        case .plane2D: return "2D"
        case .pdfDocs: return "Tài liệu"
        case .ai: return "AI"
        case .settings: return "Cài đặt"
        }
    }

    var icon: String {
        switch self {
        case .space3D: return "cube.transparent"
        case .plane2D: return "chart.xyaxis.line"
        case .pdfDocs: return "book.fill"
        case .ai: return "sparkles"
        case .settings: return "gearshape.fill"
        }
    }

    var accent: Color {
        switch self {
        case .space3D: return AppTheme.indigo
        case .plane2D: return AppTheme.emerald
        case .pdfDocs: return AppTheme.cyan
        case .ai: return AppTheme.purple
        case .settings: return AppTheme.amber
        }
    }
}

@main
struct ToolToanApp: App {
    @StateObject private var space3DStore = Space3DStore()
    @StateObject private var plane2DStore = Plane2DStore()
    @StateObject private var pdfStore = PdfDocsStore()
    @StateObject private var aiStore = AiAssistantStore()

    var body: some Scene {
        WindowGroup {
            RootWorkspaceView(
                space3DStore: space3DStore,
                plane2DStore: plane2DStore,
                pdfStore: pdfStore,
                aiStore: aiStore
            )
            .preferredColorScheme(.dark)
        }
    }
}

struct RootWorkspaceView: View {
    @ObservedObject var space3DStore: Space3DStore
    @ObservedObject var plane2DStore: Plane2DStore
    @ObservedObject var pdfStore: PdfDocsStore
    @ObservedObject var aiStore: AiAssistantStore

    @State private var activeTab: RootTab = .space3D
    @State private var isImmersive = false

    var body: some View {
        ZStack(alignment: .bottom) {
            LiquidGlassBackdropView()

            Group {
                switch activeTab {
                case .space3D:
                    Space3DView(store: space3DStore, isImmersive: $isImmersive)
                case .plane2D:
                    Plane2DView(store: plane2DStore, isImmersive: $isImmersive)
                case .pdfDocs:
                    PdfDocsTab(
                        store: pdfStore,
                        isImmersive: $isImmersive,
                        onAskAiAboutPage: { page in
                            aiStore.attachedPdfPage = page
                            isImmersive = false
                            activeTab = .ai
                        }
                    )
                case .ai:
                    AiWorkspaceView(
                        aiStore: aiStore,
                        onOpenPdfPage: { page in
                            pdfStore.goToPage(page)
                            isImmersive = false
                            activeTab = .pdfDocs
                        },
                        onApplyDrawingPlan: { plan in
                            isImmersive = false
                            if plan.target == .space3D, let s3 = plan.scene3D {
                                space3DStore.apply(plan: s3, title: plan.title)
                                activeTab = .space3D
                            } else if let s2 = plan.scene2D {
                                plane2DStore.apply(plan: s2, title: plan.title)
                                activeTab = .plane2D
                            }
                        },
                        onOpenSettings: {
                            isImmersive = false
                            activeTab = .settings
                        }
                    )
                case .settings:
                    SettingsView(aiStore: aiStore)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if isImmersive && (activeTab == .space3D || activeTab == .plane2D || activeTab == .pdfDocs) {
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.22)) {
                        isImmersive = false
                    }
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.down.right.and.arrow.up.left")
                        Text("Hiện thanh điều hướng")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(AppTheme.textPrimary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .liquidGlassCard(cornerRadius: 20, borderColor: AppTheme.cyan.opacity(0.45))
                }
                .padding(.bottom, 14)
            } else {
                FloatingPillBottomNavBar(activeTab: $activeTab)
                    .padding(.horizontal, 14)
                    .padding(.bottom, 10)
            }
        }
        .onChange(of: activeTab) { newTab in
            if newTab == .ai || newTab == .settings {
                isImmersive = false
            }
        }
    }
}

struct FloatingPillBottomNavBar: View {
    @Binding var activeTab: RootTab
    @Namespace private var tabNamespace

    var body: some View {
        HStack(spacing: 4) {
            ForEach(RootTab.allCases) { tab in
                let selected = (activeTab == tab)
                Button(action: {
                    withAnimation(.spring(response: 0.34, dampingFraction: 0.72)) {
                        activeTab = tab
                    }
                }) {
                    VStack(spacing: 3) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 16, weight: selected ? .bold : .medium))
                            .foregroundColor(selected ? tab.accent : AppTheme.textSecondary)
                        Text(tab.label)
                            .font(.system(size: 10, weight: selected ? .heavy : .medium))
                            .foregroundColor(selected ? Color.white : AppTheme.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(
                        ZStack {
                            if selected {
                                Capsule()
                                    .fill(.ultraThinMaterial)
                                    .saturation(1.26)
                                    .overlay(
                                        Capsule()
                                            .fill(
                                                LinearGradient(
                                                    colors: [
                                                        Color.white.opacity(0.28),
                                                        tab.accent.opacity(0.22),
                                                        Color.white.opacity(0.08)
                                                    ],
                                                    startPoint: .top,
                                                    endPoint: .bottom
                                                )
                                            )
                                    )
                                    .overlay(
                                        Capsule()
                                            .stroke(
                                                LinearGradient(
                                                    stops: [
                                                        .init(color: Color(hex: 0xE0F7FF).opacity(0.92), location: 0.0),
                                                        .init(color: Color.white.opacity(0.22), location: 0.38),
                                                        .init(color: Color(hex: 0xFFD8EB).opacity(0.62), location: 1.0)
                                                    ],
                                                    startPoint: .top,
                                                    endPoint: .bottom
                                                ),
                                                lineWidth: 1.2
                                            )
                                    )
                                    .shadow(color: tab.accent.opacity(0.26), radius: 8, x: 0, y: 2)
                                    .matchedGeometryEffect(id: "active_glass_pill", in: tabNamespace)
                            }
                        }
                    )
                }
                .buttonStyle(LiquidPressButtonStyle(scaleOnPress: 1.07))
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .liquidGlassCard(cornerRadius: 28, borderColor: Color.white.opacity(0.32), crystalClarity: true)
        .shadow(color: Color.black.opacity(0.42), radius: 20, x: 0, y: 10)
    }
}
