import SwiftUI

// MARK: - Tool Toán Liquid Glass Palette

enum AppTheme {
    static let slateDeepest = Color(hex: 0x090E1A)
    static let slateDark = Color(hex: 0x111A2E)
    static let slateCard = Color(hex: 0x15223B)
    static let slateBorder = Color(hex: 0x2A3B5E)
    static let textPrimary = Color(hex: 0xF1F5F9)
    static let textSecondary = Color(hex: 0x94A3B8)

    static let cyan = Color(hex: 0x38BDF8)
    static let indigo = Color(hex: 0x818CF8)
    static let purple = Color(hex: 0xA78BFA)
    static let emerald = Color(hex: 0x34D399)
    static let amber = Color(hex: 0xFBBF24)
    static let rose = Color(hex: 0xF43F5E)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }

    init?(hexString: String) {
        let cleaned = hexString
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6 || cleaned.count == 8,
              let value = UInt64(cleaned, radix: 16) else {
            return nil
        }
        if cleaned.count == 6 {
            self.init(hex: UInt32(value))
        } else {
            let a = Double((value >> 24) & 0xFF) / 255.0
            let rgb = UInt32(value & 0xFFFFFF)
            self.init(hex: rgb, alpha: a)
        }
    }
}

// MARK: - Aurora Liquid Glass Backdrop

struct LiquidGlassBackdropView: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(hex: 0x070B14),
                    Color(hex: 0x0D1528),
                    Color(hex: 0x090F1E)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            RadialGradient(
                colors: [AppTheme.indigo.opacity(0.16), .clear],
                center: .topLeading,
                startRadius: 20,
                endRadius: 340
            )
            RadialGradient(
                colors: [AppTheme.cyan.opacity(0.12), .clear],
                center: .bottomTrailing,
                startRadius: 20,
                endRadius: 360
            )
        }
        .ignoresSafeArea()
    }
}

// MARK: - Liquid Glass Card Surface

struct LiquidGlassCardModifier: ViewModifier {
    var cornerRadius: CGFloat = 18
    var borderColor: Color = AppTheme.slateBorder.opacity(0.75)

    func body(content: Content) -> some View {
        content
            .background(
                ZStack {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(.ultraThinMaterial)
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(AppTheme.slateDark.opacity(0.68))
                }
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        LinearGradient(
                            colors: [Color.white.opacity(0.18), borderColor.opacity(0.6)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
    }
}

extension View {
    func liquidGlassCard(
        cornerRadius: CGFloat = 18,
        borderColor: Color = AppTheme.slateBorder.opacity(0.75)
    ) -> some View {
        modifier(LiquidGlassCardModifier(cornerRadius: cornerRadius, borderColor: borderColor))
    }
}

// MARK: - 60fps Smooth Streaming Text with Inline Blinking Cursor

struct SmoothStreamingText: View {
    let targetText: String
    let isStreaming: Bool
    var textColor: Color = AppTheme.textPrimary
    var cursorColor: Color = AppTheme.cyan
    var font: Font = .system(size: 14)
    var fontWeight: Font.Weight = .regular
    var showCursor: Bool = true

    @State private var displayedCount: Int = 0
    @State private var cursorVisible: Bool = true

    var body: some View {
        let clamped = min(displayedCount, targetText.count)
        let visibleSlice = String(targetText.prefix(clamped))
        let separator = (!visibleSlice.isEmpty && !visibleSlice.hasSuffix(" ") && !visibleSlice.hasSuffix("\n")) ? "\u{2009}" : ""

        Group {
            if showCursor && isStreaming {
                (Text(visibleSlice)
                    .font(font)
                    .fontWeight(fontWeight)
                    .foregroundColor(textColor)
                 + Text(separator + "▍")
                    .font(font)
                    .fontWeight(.heavy)
                    .foregroundColor(cursorColor.opacity(cursorVisible ? 1.0 : 0.15)))
            } else {
                Text(visibleSlice)
                    .font(font)
                    .fontWeight(fontWeight)
                    .foregroundColor(textColor)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear {
            if !isStreaming {
                displayedCount = targetText.count
            }
        }
        .task(id: targetText) {
            if targetText.count < displayedCount {
                displayedCount = targetText.count
            }
            while displayedCount < targetText.count && !Task.isCancelled {
                let remaining = targetText.count - displayedCount
                let step: Int
                if !isStreaming {
                    step = max(10, remaining / 3)
                } else if remaining <= 10 {
                    step = 1
                } else if remaining <= 30 {
                    step = 2
                } else if remaining <= 80 {
                    step = 4
                } else {
                    step = max(8, remaining / 10)
                }
                displayedCount = min(targetText.count, displayedCount + step)
                if displayedCount < targetText.count {
                    try? await Task.sleep(nanoseconds: 18_000_000)
                }
            }
        }
        .task(id: isStreaming) {
            guard isStreaming && showCursor else {
                cursorVisible = false
                return
            }
            cursorVisible = true
            while isStreaming && showCursor && !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 480_000_000)
                withAnimation(.easeInOut(duration: 0.22)) {
                    cursorVisible.toggle()
                }
            }
        }
    }
}

struct StreamingPulseDot: View {
    var color: Color
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(color.opacity(pulse ? 0.28 : 1.0))
            .frame(width: 8, height: 8)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.65).repeatForever(autoreverses: true)) {
                    pulse = true
                }
            }
    }
}
