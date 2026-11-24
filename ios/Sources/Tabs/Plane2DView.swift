import SwiftUI

final class Plane2DStore: ObservableObject {
    @Published var points: [Point2DItem] = [
        Point2DItem(name: "A", x: -1, y: 3, colorHex: "#F43F5E"),
        Point2DItem(name: "B", x: 1, y: -1, colorHex: "#34D399")
    ]
    @Published var objects: [Object2DItem] = []
    @Published var functions: [Function2DItem] = [
        Function2DItem(expression: "x^3 - 3x + 1", colorHex: "#38BDF8")
    ]
    @Published var offsetX: Double = 0
    @Published var offsetY: Double = 0
    @Published var pixelsPerUnit: Double = 44.0
    @Published var activePlanTitle: String? = nil

    func apply(plan: AiScene2DPlan, title: String) {
        points = plan.points
        objects = plan.objects
        functions = plan.functions
        activePlanTitle = title
        fitCamera()
    }

    func resetView() {
        offsetX = 0
        offsetY = 0
        pixelsPerUnit = 44.0
    }

    func fitCamera() {
        guard !points.isEmpty else {
            resetView()
            return
        }
        let xs = points.map { $0.x }
        let ys = points.map { $0.y }
        let minX = xs.min() ?? -4, maxX = xs.max() ?? 4
        let minY = ys.min() ?? -4, maxY = ys.max() ?? 4
        let cx = (minX + maxX) * 0.5
        let cy = (minY + maxY) * 0.5
        let span = max(4.0, max(maxX - minX, maxY - minY) + 3.0)
        pixelsPerUnit = min(max(300.0 / span, 22.0), 80.0)
        offsetX = -cx * pixelsPerUnit
        offsetY = cy * pixelsPerUnit
    }
}

struct Plane2DView: View {
    @ObservedObject var store: Plane2DStore
    @Binding var isImmersive: Bool

    @State private var showEditorSheet = false
    @State private var dragStartX: Double = 0
    @State private var dragStartY: Double = 0
    @State private var isDragging = false
    @State private var pinchStartScale: Double = 44.0
    @State private var isPinching = false

    var body: some View {
        ZStack {
            Canvas { ctx, size in
                var g = ctx
                renderPlane(ctx: &g, size: size)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.22)) {
                    isImmersive.toggle()
                }
            }
            .gesture(
                DragGesture(minimumDistance: 3)
                    .onChanged { value in
                        if !isDragging {
                            isDragging = true
                            dragStartX = store.offsetX
                            dragStartY = store.offsetY
                        }
                        store.offsetX = dragStartX + Double(value.translation.width)
                        store.offsetY = dragStartY + Double(value.translation.height)
                    }
                    .onEnded { _ in isDragging = false }
            )
            .simultaneousGesture(
                MagnificationGesture()
                    .onChanged { scale in
                        if !isPinching {
                            isPinching = true
                            pinchStartScale = store.pixelsPerUnit
                        }
                        store.pixelsPerUnit = min(max(pinchStartScale * Double(scale), 14.0), 160.0)
                    }
                    .onEnded { _ in isPinching = false }
            )

            if !isImmersive {
                VStack(spacing: 10) {
                    HStack(spacing: 8) {
                        Button(action: { showEditorSheet = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "function")
                                    .foregroundColor(AppTheme.emerald)
                                Text(store.activePlanTitle ?? (store.functions.first?.expression.isEmpty == false ? "y = \(store.functions.first!.expression)" : "Hình phẳng & Đồ thị 2D"))
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(AppTheme.textPrimary)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .liquidGlassCard(cornerRadius: 16, borderColor: AppTheme.emerald.opacity(0.45))
                        }

                        Spacer()

                        Button(action: { showEditorSheet = true }) {
                            Image(systemName: "plus")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(AppTheme.emerald)
                                .frame(width: 40, height: 40)
                                .liquidGlassCard(cornerRadius: 14, borderColor: AppTheme.emerald.opacity(0.45))
                        }

                        Button(action: { store.resetView() }) {
                            Image(systemName: "scope")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(AppTheme.textPrimary)
                                .frame(width: 40, height: 40)
                                .liquidGlassCard(cornerRadius: 14)
                        }

                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.22)) {
                                isImmersive = true
                            }
                        }) {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(AppTheme.textPrimary)
                                .frame(width: 40, height: 40)
                                .liquidGlassCard(cornerRadius: 14)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 8)

                    Spacer()
                }
            }
        }
        .sheet(isPresented: $showEditorSheet) {
            Plane2DEditorSheet(store: store)
        }
    }

    private func toScreen(_ x: Double, _ y: Double, size: CGSize) -> CGPoint {
        let cx = Double(size.width) * 0.5 + store.offsetX
        let cy = Double(size.height) * 0.48 + store.offsetY
        return CGPoint(x: cx + x * store.pixelsPerUnit, y: cy - y * store.pixelsPerUnit)
    }

    private func renderPlane(ctx: inout GraphicsContext, size: CGSize) {
        let w = Double(size.width)
        let h = Double(size.height)
        let cx = w * 0.5 + store.offsetX
        let cy = h * 0.48 + store.offsetY
        let ppu = store.pixelsPerUnit

        // Grid step adapts cleanly to zoom
        let rawStep = 55.0 / ppu
        let step: Double = rawStep <= 0.5 ? 0.5 : (rawStep <= 1.2 ? 1.0 : (rawStep <= 2.5 ? 2.0 : 5.0))

        let minX = Int(floor((-cx) / (ppu * step))) - 1
        let maxX = Int(ceil((w - cx) / (ppu * step))) + 1
        let minY = Int(floor((cy - h) / (ppu * step))) - 1
        let maxY = Int(ceil(cy / (ppu * step))) + 1

        var gridPath = Path()
        for i in minX...maxX {
            let x = Double(i) * step
            let sx = cx + x * ppu
            gridPath.move(to: CGPoint(x: sx, y: 0))
            gridPath.addLine(to: CGPoint(x: sx, y: h))
        }
        for j in minY...maxY {
            let y = Double(j) * step
            let sy = cy - y * ppu
            gridPath.move(to: CGPoint(x: 0, y: sy))
            gridPath.addLine(to: CGPoint(x: w, y: sy))
        }
        ctx.stroke(gridPath, with: .color(AppTheme.slateBorder.opacity(0.32)), lineWidth: 1)

        // Axes Ox / Oy
        var axes = Path()
        axes.move(to: CGPoint(x: 0, y: cy)); axes.addLine(to: CGPoint(x: w, y: cy))
        axes.move(to: CGPoint(x: cx, y: 0)); axes.addLine(to: CGPoint(x: cx, y: h))
        ctx.stroke(axes, with: .color(AppTheme.textSecondary.opacity(0.75)), lineWidth: 1.5)

        // Axis ticks
        for i in minX...maxX where i != 0 {
            let val = Double(i) * step
            let sx = cx + val * ppu
            if sx > 16 && sx < w - 16 {
                let label = val == floor(val) ? "\(Int(val))" : String(format: "%.1f", val)
                ctx.draw(
                    Text(label).font(.system(size: 10, design: .monospaced)).foregroundColor(AppTheme.textSecondary),
                    at: CGPoint(x: sx, y: min(max(cy + 12, 14), h - 14))
                )
            }
        }
        for j in minY...maxY where j != 0 {
            let val = Double(j) * step
            let sy = cy - val * ppu
            if sy > 16 && sy < h - 16 {
                let label = val == floor(val) ? "\(Int(val))" : String(format: "%.1f", val)
                ctx.draw(
                    Text(label).font(.system(size: 10, design: .monospaced)).foregroundColor(AppTheme.textSecondary),
                    at: CGPoint(x: min(max(cx - 14, 16), w - 16), y: sy)
                )
            }
        }

        // Plot Functions y = f(x)
        let samples = max(240, Int(w / 1.5))
        for fn in store.functions where fn.isVisible && !fn.expression.trimmingCharacters(in: .whitespaces).isEmpty {
            let color = Color(hexString: fn.colorHex) ?? AppTheme.cyan
            var path = Path()
            var penDown = false
            var prevSy: Double = 0
            for s in 0...samples {
                let sx = Double(s) * w / Double(samples)
                let x = (sx - cx) / ppu
                if let y = MathExprEvaluator.evaluate(fn.expression, x: x) {
                    let sy = cy - y * ppu
                    if sy > -h * 2 && sy < h * 3 {
                        if penDown && abs(sy - prevSy) < h * 0.8 {
                            path.addLine(to: CGPoint(x: sx, y: sy))
                        } else {
                            path.move(to: CGPoint(x: sx, y: sy))
                            penDown = true
                        }
                        prevSy = sy
                    } else {
                        penDown = false
                    }
                } else {
                    penDown = false
                }
            }
            ctx.stroke(path, with: .color(color), lineWidth: 2.5)
        }

        // Map point lookup
        var ptMap: [String: Point2DItem] = [:]
        for p in store.points { ptMap[p.name] = p }

        // Render 2D Objects
        for obj in store.objects {
            let color = obj.highlighted
                ? (Color(hexString: obj.colorHex) ?? AppTheme.rose)
                : (Color(hexString: obj.colorHex) ?? AppTheme.textPrimary.opacity(0.88))
            let width: CGFloat = obj.highlighted ? 2.6 : 1.8

            switch obj.kind {
            case .polygon:
                let pts = obj.pointNames.compactMap { ptMap[$0] }.map { toScreen($0.x, $0.y, size: size) }
                guard pts.count >= 3 else { continue }
                var path = Path()
                path.move(to: pts[0])
                for pt in pts.dropFirst() { path.addLine(to: pt) }
                path.closeSubpath()
                ctx.fill(path, with: .color(AppTheme.emerald.opacity(0.12)))
                ctx.stroke(path, with: .color(color), lineWidth: width)

            case .circle:
                guard let center = ptMap[obj.p1] else { continue }
                let radiusUnits: Double
                if obj.radius > 0 {
                    radiusUnits = obj.radius
                } else if let p2 = ptMap[obj.p2] {
                    radiusUnits = hypot(p2.x - center.x, p2.y - center.y)
                } else { continue }
                let cPt = toScreen(center.x, center.y, size: size)
                let rPx = CGFloat(radiusUnits * ppu)
                let rect = CGRect(x: cPt.x - rPx, y: cPt.y - rPx, width: rPx * 2, height: rPx * 2)
                ctx.stroke(Path(ellipseIn: rect), with: .color(color), lineWidth: width)

            case .segment, .vector, .line, .ray:
                guard let a = ptMap[obj.p1], let b = ptMap[obj.p2] else { continue }
                var s1 = toScreen(a.x, a.y, size: size)
                var s2 = toScreen(b.x, b.y, size: size)
                let dx = s2.x - s1.x, dy = s2.y - s1.y
                let len = hypot(dx, dy)
                if obj.kind == .line && len > 1 {
                    let ext = CGFloat(max(w, h) * 1.5) / len
                    s1 = CGPoint(x: s1.x - dx * ext, y: s1.y - dy * ext)
                    s2 = CGPoint(x: s2.x + dx * ext, y: s2.y + dy * ext)
                } else if obj.kind == .ray && len > 1 {
                    let ext = CGFloat(max(w, h) * 1.5) / len
                    s2 = CGPoint(x: s2.x + dx * ext, y: s2.y + dy * ext)
                }
                var path = Path()
                path.move(to: s1); path.addLine(to: s2)
                if obj.dashed {
                    ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, dash: [7, 5]))
                } else {
                    ctx.stroke(path, with: .color(color), lineWidth: width)
                }
                if obj.kind == .vector && len > 8 {
                    let ux = dx / len, uy = dy / len
                    let baseX = s2.x - ux * 11, baseY = s2.y - uy * 11
                    var head = Path()
                    head.move(to: s2)
                    head.addLine(to: CGPoint(x: baseX - uy * 5.5, y: baseY + ux * 5.5))
                    head.addLine(to: CGPoint(x: baseX + uy * 5.5, y: baseY - ux * 5.5))
                    head.closeSubpath()
                    ctx.fill(head, with: .color(color))
                }
            }
        }

        // Render Points
        for pt in store.points {
            let sp = toScreen(pt.x, pt.y, size: size)
            let col = Color(hexString: pt.colorHex) ?? AppTheme.amber
            let r: CGFloat = 4.5
            ctx.fill(Path(ellipseIn: CGRect(x: sp.x - r, y: sp.y - r, width: r * 2, height: r * 2)), with: .color(col))
            let coordStr = "\(pt.name)(\(fmt(pt.x)); \(fmt(pt.y)))"
            ctx.draw(
                Text(coordStr).font(.system(size: 12, weight: .bold)).foregroundColor(AppTheme.textPrimary),
                at: CGPoint(x: sp.x + 26, y: sp.y - 12)
            )
        }
    }

    private func fmt(_ v: Double) -> String {
        abs(v - round(v)) < 1e-4 ? "\(Int(round(v)))" : String(format: "%.1f", v)
    }
}

private struct Plane2DEditorSheet: View {
    @ObservedObject var store: Plane2DStore
    @Environment(\.presentationMode) private var presentationMode

    @State private var newExpr = ""
    @State private var ptName = "C"
    @State private var ptX = "2"
    @State private var ptY = "3"

    private let palette = ["#38BDF8", "#34D399", "#F43F5E", "#FBBF24", "#A78BFA"]

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackdropView()
                ScrollView {
                    VStack(spacing: 14) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Đồ thị hàm số y = f(x)")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(AppTheme.cyan)
                            HStack(spacing: 8) {
                                TextField("Vd: x^3 - 3x + 1, sin(x)...", text: $newExpr)
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                                Button("Vẽ") {
                                    let trimmed = newExpr.trimmingCharacters(in: .whitespacesAndNewlines)
                                    guard !trimmed.isEmpty else { return }
                                    let col = palette[store.functions.count % palette.count]
                                    store.functions.append(Function2DItem(expression: trimmed, colorHex: col))
                                    newExpr = ""
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(AppTheme.cyan)
                                .foregroundColor(.black)
                                .cornerRadius(10)
                            }

                            ForEach(store.functions) { fn in
                                HStack {
                                    Circle()
                                        .fill(Color(hexString: fn.colorHex) ?? AppTheme.cyan)
                                        .frame(width: 10, height: 10)
                                    Text("y = \(fn.expression)")
                                        .font(.system(size: 13, weight: .semibold, design: .monospaced))
                                        .foregroundColor(AppTheme.textPrimary)
                                    Spacer()
                                    Button(action: {
                                        store.functions.removeAll { $0.id == fn.id }
                                    }) {
                                        Image(systemName: "trash")
                                            .foregroundColor(AppTheme.rose)
                                    }
                                }
                                .padding(8)
                                .background(AppTheme.slateDeepest.opacity(0.6))
                                .cornerRadius(10)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard()

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Điểm trên mặt phẳng Oxy")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(AppTheme.emerald)
                            HStack(spacing: 8) {
                                TextField("Tên", text: $ptName).textFieldStyle(RoundedBorderTextFieldStyle())
                                TextField("x", text: $ptX).textFieldStyle(RoundedBorderTextFieldStyle()).keyboardType(.numbersAndPunctuation)
                                TextField("y", text: $ptY).textFieldStyle(RoundedBorderTextFieldStyle()).keyboardType(.numbersAndPunctuation)
                                Button("Thêm") {
                                    guard let x = Double(ptX.replacingOccurrences(of: ",", with: ".")),
                                          let y = Double(ptY.replacingOccurrences(of: ",", with: ".")),
                                          !ptName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                                    store.points.append(Point2DItem(name: ptName.trimmingCharacters(in: .whitespaces), x: x, y: y, colorHex: "#FBBF24"))
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(AppTheme.emerald)
                                .foregroundColor(.black)
                                .cornerRadius(10)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard()

                        Button(action: {
                            store.points = []
                            store.objects = []
                            store.functions = []
                            store.activePlanTitle = nil
                        }) {
                            Text("Xóa toàn bộ hình 2D")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(AppTheme.rose)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(AppTheme.rose.opacity(0.14))
                                .cornerRadius(12)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Đồ thị & Hình phẳng 2D")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Xong") { presentationMode.wrappedValue.dismiss() }
                        .foregroundColor(AppTheme.cyan)
                }
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
}
