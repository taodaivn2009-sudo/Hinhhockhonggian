import SwiftUI

final class Space3DStore: ObservableObject {
    @Published var shapeKind: Shape3DKind = .pyramid4Sa
    @Published var dimA: Double = 4.0
    @Published var dimB: Double = 4.0
    @Published var dimH: Double = 4.8
    @Published var showAxes: Bool = false
    @Published var showAltitude: Bool = true
    @Published var showLabels: Bool = true
    @Published var yaw: Double = -0.58
    @Published var pitch: Double = 0.42
    @Published var zoom: Double = 1.0
    @Published var edgePoints: [EdgePoint3D] = []
    @Published var xyzPoints: [XyzPoint3D] = []
    @Published var customLines: [CustomLine3D] = []
    @Published var customPlanes: [CustomPlane3D] = []
    @Published var activePlanTitle: String? = nil

    func apply(plan: AiScene3DPlan, title: String) {
        shapeKind = plan.shapeKind
        dimA = min(max(plan.a, 1.5), 8.0)
        dimB = min(max(plan.b, 1.5), 8.0)
        dimH = min(max(plan.h, 1.5), 8.0)
        showAxes = plan.showAxes
        showAltitude = plan.showAltitude
        edgePoints = plan.edgePoints
        xyzPoints = plan.xyzPoints
        customLines = plan.lines
        customPlanes = plan.planes
        activePlanTitle = title
        resetCamera()
    }

    func resetCamera() {
        yaw = -0.58
        pitch = 0.42
        zoom = 1.0
    }

    func clearCustomDrawings() {
        edgePoints = []
        xyzPoints = []
        customLines = []
        customPlanes = []
        activePlanTitle = nil
    }
}

struct Space3DView: View {
    @ObservedObject var store: Space3DStore
    @Binding var isImmersive: Bool

    @State private var showShapeSheet = false
    @State private var showToolsSheet = false
    @State private var dragStartYaw: Double = -0.58
    @State private var dragStartPitch: Double = 0.42
    @State private var isDragging = false
    @State private var pinchStartZoom: Double = 1.0
    @State private var isPinching = false

    var body: some View {
        ZStack {
            // Interactive 3D Canvas
            Canvas { ctx, size in
                var g = ctx
                renderScene(ctx: &g, size: size)
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
                            dragStartYaw = store.yaw
                            dragStartPitch = store.pitch
                        }
                        store.yaw = dragStartYaw + Double(value.translation.width) * 0.010
                        store.pitch = min(max(dragStartPitch + Double(value.translation.height) * 0.009, -1.35), 1.35)
                    }
                    .onEnded { _ in
                        isDragging = false
                    }
            )
            .simultaneousGesture(
                MagnificationGesture()
                    .onChanged { scale in
                        if !isPinching {
                            isPinching = true
                            pinchStartZoom = store.zoom
                        }
                        store.zoom = min(max(pinchStartZoom * Double(scale), 0.45), 2.8)
                    }
                    .onEnded { _ in
                        isPinching = false
                    }
            )

            // Floating HUD controls
            if !isImmersive {
                VStack(spacing: 10) {
                    HStack(spacing: 8) {
                        Button(action: { showShapeSheet = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "cube.transparent")
                                    .foregroundColor(AppTheme.indigo)
                                Text(store.activePlanTitle ?? store.shapeKind.shortLabel)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(AppTheme.textPrimary)
                                    .lineLimit(1)
                                Image(systemName: "chevron.down")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(AppTheme.textSecondary)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .liquidGlassCard(cornerRadius: 16, borderColor: AppTheme.indigo.opacity(0.45))
                        }

                        Spacer()

                        Button(action: { showToolsSheet = true }) {
                            Image(systemName: "slider.horizontal.3")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(AppTheme.cyan)
                                .frame(width: 40, height: 40)
                                .liquidGlassCard(cornerRadius: 14, borderColor: AppTheme.cyan.opacity(0.4))
                        }

                        Button(action: { store.resetCamera() }) {
                            Image(systemName: "arrow.counterclockwise")
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

                    // Quick shape & toggle strip above floating tab bar
                    HStack(spacing: 8) {
                        TogglePillButton(
                            label: "Oxyz",
                            active: store.showAxes,
                            accent: AppTheme.emerald
                        ) { store.showAxes.toggle() }

                        TogglePillButton(
                            label: "Đường cao",
                            active: store.showAltitude,
                            accent: AppTheme.amber
                        ) { store.showAltitude.toggle() }

                        TogglePillButton(
                            label: "Nhãn đỉnh",
                            active: store.showLabels,
                            accent: AppTheme.cyan
                        ) { store.showLabels.toggle() }

                        if !store.customLines.isEmpty || !store.edgePoints.isEmpty || !store.customPlanes.isEmpty {
                            Button(action: { store.clearCustomDrawings() }) {
                                Text("Xóa nét AI")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(AppTheme.rose)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 7)
                                    .background(AppTheme.rose.opacity(0.15))
                                    .clipShape(Capsule())
                                    .overlay(Capsule().stroke(AppTheme.rose.opacity(0.45), lineWidth: 1))
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 92)
                }
            }
        }
        .sheet(isPresented: $showShapeSheet) {
            Shape3DPickerSheet(store: store)
        }
        .sheet(isPresented: $showToolsSheet) {
            Space3DToolsSheet(store: store)
        }
    }

    // MARK: - Pure 3D Projection & Hidden-Line Engine

    private func rotate(_ p: Vec3) -> Vec3 {
        let cy = cos(store.yaw), sy = sin(store.yaw)
        let cp = cos(store.pitch), sp = sin(store.pitch)
        let x1 = p.x * cy - p.z * sy
        let z1 = p.x * sy + p.z * cy
        let y2 = p.y * cp - z1 * sp
        let z2 = p.y * sp + z1 * cp
        return Vec3(x: x1, y: y2, z: z2)
    }

    private func project(_ p: Vec3, size: CGSize) -> (CGPoint, Double) {
        let r = rotate(p)
        let camDist = 14.0
        let focal = Double(min(size.width, size.height)) * 0.62 * store.zoom
        let denom = max(2.2, camDist - r.z)
        let sx = Double(size.width) * 0.5 + (r.x * focal) / denom
        let sy = Double(size.height) * 0.48 - (r.y * focal) / denom
        return (CGPoint(x: sx, y: sy), r.z)
    }

    private func renderScene(ctx: inout GraphicsContext, size: CGSize) {
        let mesh = Geometry3DBuilder.buildMesh(
            kind: store.shapeKind,
            a: store.dimA,
            b: store.dimB,
            h: store.dimH
        )

        var allVertices = mesh.vertices
        for xp in store.xyzPoints {
            allVertices[xp.name] = xp.position
        }
        for ep in store.edgePoints {
            if let a = allVertices[ep.from], let b = allVertices[ep.to] {
                let t = min(max(ep.ratio, 0.0), 1.0)
                allVertices[ep.name] = Vec3(
                    x: a.x + (b.x - a.x) * t,
                    y: a.y + (b.y - a.y) * t,
                    z: a.z + (b.z - a.z) * t
                )
            }
        }

        // 1. Optional Oxyz axes
        if store.showAxes {
            let axisLen = max(store.dimA, store.dimH) * 0.95
            let o = project(.zero, size: size).0
            let axes: [(Vec3, String, Color)] = [
                (Vec3(x: axisLen, y: 0, z: 0), "x", AppTheme.rose),
                (Vec3(x: 0, y: axisLen, z: 0), "z", AppTheme.cyan),
                (Vec3(x: 0, y: 0, z: axisLen), "y", AppTheme.emerald)
            ]
            for (tip, label, col) in axes {
                let pt = project(tip, size: size).0
                drawArrow(ctx: &ctx, from: o, to: pt, color: col.opacity(0.85), lineWidth: 1.6, dashed: false)
                ctx.draw(
                    Text(label).font(.system(size: 12, weight: .bold)).foregroundColor(col),
                    at: CGPoint(x: pt.x + 8, y: pt.y - 6)
                )
            }
        }

        // 2. Determine front-facing faces for shading & hidden-edge detection
        let camPos = Vec3(x: 0, y: 0, z: 14.0)
        var center = Vec3.zero
        if !mesh.vertices.isEmpty {
            let sum = mesh.vertices.values.reduce(Vec3.zero, +)
            center = sum * (1.0 / Double(mesh.vertices.count))
        }
        let rotCenter = rotate(center)

        var frontEdges = Set<String>()
        var sortedFaces: [(Double, [CGPoint], Bool)] = []

        for face in mesh.faces {
            let pts3D = face.compactMap { allVertices[$0] }
            guard pts3D.count >= 3 else { continue }
            let rPts = pts3D.map { rotate($0) }
            let avg = rPts.reduce(Vec3.zero, +) * (1.0 / Double(rPts.count))
            var normal = (rPts[1] - rPts[0]).cross(rPts[2] - rPts[0])
            if normal.dot(avg - rotCenter) < 0 {
                normal = normal * -1.0
            }
            let viewDir = camPos - avg
            let isFront = normal.dot(viewDir) > 0
            if isFront {
                for i in 0..<face.count {
                    let u = face[i]
                    let w = face[(i + 1) % face.count]
                    frontEdges.insert(edgeKey(u, w))
                }
            }
            let projPts = pts3D.map { project($0, size: size).0 }
            sortedFaces.append((avg.z, projPts, isFront))
        }

        // Paint translucent faces back-to-front
        for (_, poly, isFront) in sortedFaces.sorted(by: { $0.0 < $1.0 }) {
            var path = Path()
            if let first = poly.first {
                path.move(to: first)
                for p in poly.dropFirst() { path.addLine(to: p) }
                path.closeSubpath()
                ctx.fill(
                    path,
                    with: .color(AppTheme.indigo.opacity(isFront ? 0.14 : 0.05))
                )
            }
        }

        // 3. Draw curved solids (Cylinder / Cone / Sphere)
        if mesh.isCurved {
            renderCurvedSolid(ctx: &ctx, mesh: mesh, size: size)
        }

        // 4. Draw polyhedron edges (solid if on any front-facing face, dashed if occluded)
        for (u, w) in mesh.edges {
            guard let p1 = allVertices[u], let p2 = allVertices[w] else { continue }
            let s1 = project(p1, size: size).0
            let s2 = project(p2, size: size).0
            let isVisible = frontEdges.contains(edgeKey(u, w))
            var path = Path()
            path.move(to: s1)
            path.addLine(to: s2)
            if isVisible {
                ctx.stroke(path, with: .color(AppTheme.textPrimary.opacity(0.92)), lineWidth: 2.0)
            } else {
                ctx.stroke(
                    path,
                    with: .color(AppTheme.textSecondary.opacity(0.62)),
                    style: StrokeStyle(lineWidth: 1.5, dash: [6, 5])
                )
            }
        }

        // 5. Optional internal altitude
        if store.showAltitude, let alt = mesh.altitude,
           let p1 = allVertices[alt.0], let p2 = allVertices[alt.1] {
            let s1 = project(p1, size: size).0
            let s2 = project(p2, size: size).0
            var path = Path()
            path.move(to: s1)
            path.addLine(to: s2)
            ctx.stroke(
                path,
                with: .color(AppTheme.amber.opacity(0.78)),
                style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])
            )
        }

        // 6. Custom planes / sections from AI
        for plane in store.customPlanes {
            let pts = plane.pointNames.compactMap { allVertices[$0] }.map { project($0, size: size).0 }
            guard pts.count >= 3 else { continue }
            let col = Color(hexString: plane.colorHex) ?? AppTheme.purple
            var path = Path()
            path.move(to: pts[0])
            for pt in pts.dropFirst() { path.addLine(to: pt) }
            path.closeSubpath()
            ctx.fill(path, with: .color(col.opacity(0.24)))
            ctx.stroke(path, with: .color(col.opacity(0.9)), lineWidth: 1.8)
        }

        // 7. Custom lines & vectors (depth-aware dashed vs solid)
        for line in store.customLines {
            guard let v1 = allVertices[line.p1], let v2 = allVertices[line.p2] else { continue }
            let (s1, z1) = project(v1, size: size)
            let (s2, z2) = project(v2, size: size)
            let midZ = (z1 + z2) * 0.5
            let onFrontEdge = frontEdges.contains(edgeKey(line.p1, line.p2))
            let autoDashed = line.forceDashed || (!onFrontEdge && midZ < rotCenter.z + 0.15)
            let strokeColor: Color = line.highlighted
                ? (Color(hexString: line.colorHex) ?? AppTheme.rose)
                : AppTheme.textPrimary.opacity(0.88)
            let width: CGFloat = line.highlighted ? 2.8 : 1.9

            if line.isVector {
                drawArrow(ctx: &ctx, from: s1, to: s2, color: strokeColor, lineWidth: width, dashed: autoDashed)
            } else {
                var path = Path()
                path.move(to: s1)
                path.addLine(to: s2)
                if autoDashed {
                    ctx.stroke(path, with: .color(strokeColor.opacity(0.85)), style: StrokeStyle(lineWidth: width, dash: [7, 5]))
                } else {
                    ctx.stroke(path, with: .color(strokeColor), lineWidth: width)
                }
            }
        }

        // 8. Vertices & crisp labels
        for (name, pos) in allVertices {
            let (pt, _) = project(pos, size: size)
            let isSpecial = store.edgePoints.contains { $0.name == name } || store.xyzPoints.contains { $0.name == name }
            let dotRadius: CGFloat = isSpecial ? 4.5 : 3.4
            let dotRect = CGRect(x: pt.x - dotRadius, y: pt.y - dotRadius, width: dotRadius * 2, height: dotRadius * 2)
            ctx.fill(Path(ellipseIn: dotRect), with: .color(isSpecial ? AppTheme.amber : AppTheme.cyan))
            if store.showLabels {
                ctx.draw(
                    Text(name)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(isSpecial ? AppTheme.amber : AppTheme.textPrimary),
                    at: CGPoint(x: pt.x + 11, y: pt.y - 10)
                )
            }
        }
    }

    private func renderCurvedSolid(ctx: inout GraphicsContext, mesh: Mesh3DData, size: CGSize) {
        let r = mesh.curvedRadius
        let h = mesh.curvedHeight
        let steps = 48

        func drawCircleRing(y: Double, color: Color) {
            var frontPath = Path()
            var backPath = Path()
            var prevPt: CGPoint?
            var prevZ: Double = 0
            for i in 0...steps {
                let ang = Double(i) * 2.0 * .pi / Double(steps)
                let p = Vec3(x: r * cos(ang), y: y, z: r * sin(ang))
                let (pt, z) = project(p, size: size)
                if let prev = prevPt {
                    if (z + prevZ) * 0.5 >= 0 {
                        frontPath.move(to: prev)
                        frontPath.addLine(to: pt)
                    } else {
                        backPath.move(to: prev)
                        backPath.addLine(to: pt)
                    }
                }
                prevPt = pt
                prevZ = z
            }
            ctx.stroke(frontPath, with: .color(color.opacity(0.92)), lineWidth: 2.0)
            ctx.stroke(backPath, with: .color(color.opacity(0.5)), style: StrokeStyle(lineWidth: 1.4, dash: [6, 5]))
        }

        switch store.shapeKind {
        case .cylinder:
            drawCircleRing(y: -h * 0.5, color: AppTheme.textPrimary)
            drawCircleRing(y: h * 0.5, color: AppTheme.textPrimary)
            let leftBot = project(Vec3(x: -r * cos(store.yaw), y: -h * 0.5, z: r * sin(store.yaw)), size: size).0
            let leftTop = project(Vec3(x: -r * cos(store.yaw), y: h * 0.5, z: r * sin(store.yaw)), size: size).0
            let rightBot = project(Vec3(x: r * cos(store.yaw), y: -h * 0.5, z: -r * sin(store.yaw)), size: size).0
            let rightTop = project(Vec3(x: r * cos(store.yaw), y: h * 0.5, z: -r * sin(store.yaw)), size: size).0
            var sides = Path()
            sides.move(to: leftBot); sides.addLine(to: leftTop)
            sides.move(to: rightBot); sides.addLine(to: rightTop)
            ctx.stroke(sides, with: .color(AppTheme.textPrimary.opacity(0.9)), lineWidth: 2.0)

        case .cone:
            drawCircleRing(y: -h * 0.4, color: AppTheme.textPrimary)
            let apex = project(Vec3(x: 0, y: h * 0.6, z: 0), size: size).0
            let leftBot = project(Vec3(x: -r * cos(store.yaw), y: -h * 0.4, z: r * sin(store.yaw)), size: size).0
            let rightBot = project(Vec3(x: r * cos(store.yaw), y: -h * 0.4, z: -r * sin(store.yaw)), size: size).0
            var sides = Path()
            sides.move(to: apex); sides.addLine(to: leftBot)
            sides.move(to: apex); sides.addLine(to: rightBot)
            ctx.stroke(sides, with: .color(AppTheme.textPrimary.opacity(0.9)), lineWidth: 2.0)

        case .sphere:
            let centerPt = project(.zero, size: size).0
            let edgePt = project(Vec3(x: r * cos(store.yaw), y: 0, z: -r * sin(store.yaw)), size: size).0
            let rad = hypot(edgePt.x - centerPt.x, edgePt.y - centerPt.y)
            let rect = CGRect(x: centerPt.x - rad, y: centerPt.y - rad, width: rad * 2, height: rad * 2)
            ctx.fill(Path(ellipseIn: rect), with: .color(AppTheme.indigo.opacity(0.14)))
            ctx.stroke(Path(ellipseIn: rect), with: .color(AppTheme.textPrimary.opacity(0.92)), lineWidth: 2.0)
            drawCircleRing(y: 0, color: AppTheme.cyan)

        default:
            break
        }
    }

    private func drawArrow(
        ctx: inout GraphicsContext,
        from: CGPoint,
        to: CGPoint,
        color: Color,
        lineWidth: CGFloat,
        dashed: Bool
    ) {
        var line = Path()
        line.move(to: from)
        line.addLine(to: to)
        if dashed {
            ctx.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: lineWidth, dash: [7, 5]))
        } else {
            ctx.stroke(line, with: .color(color), lineWidth: lineWidth)
        }
        let dx = to.x - from.x
        let dy = to.y - from.y
        let len = hypot(dx, dy)
        guard len > 8 else { return }
        let ux = dx / len, uy = dy / len
        let headLen: CGFloat = 11
        let headW: CGFloat = 5.5
        let baseX = to.x - ux * headLen
        let baseY = to.y - uy * headLen
        var head = Path()
        head.move(to: to)
        head.addLine(to: CGPoint(x: baseX - uy * headW, y: baseY + ux * headW))
        head.addLine(to: CGPoint(x: baseX + uy * headW, y: baseY - ux * headW))
        head.closeSubpath()
        ctx.fill(head, with: .color(color))
    }

    private func edgeKey(_ u: String, _ v: String) -> String {
        u < v ? "\(u)|\(v)" : "\(v)|\(u)"
    }
}

private struct TogglePillButton: View {
    let label: String
    let active: Bool
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(active ? accent : AppTheme.textSecondary)
                .padding(.horizontal, 11)
                .padding(.vertical, 7)
                .background(active ? accent.opacity(0.18) : AppTheme.slateDark.opacity(0.75))
                .clipShape(Capsule())
                .overlay(
                    Capsule().stroke(active ? accent.opacity(0.55) : AppTheme.slateBorder, lineWidth: 1)
                )
        }
    }
}

private struct Shape3DPickerSheet: View {
    @ObservedObject var store: Space3DStore
    @Environment(\.presentationMode) private var presentationMode

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackdropView()
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(Shape3DKind.allCases) { kind in
                            let selected = store.shapeKind == kind
                            Button(action: {
                                store.shapeKind = kind
                                store.activePlanTitle = nil
                                presentationMode.wrappedValue.dismiss()
                            }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    Image(systemName: "cube.transparent")
                                        .font(.system(size: 20))
                                        .foregroundColor(selected ? AppTheme.cyan : AppTheme.indigo)
                                    Text(kind.displayName)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(AppTheme.textPrimary)
                                        .multilineTextAlignment(.leading)
                                }
                                .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
                                .padding(12)
                                .liquidGlassCard(
                                    cornerRadius: 14,
                                    borderColor: selected ? AppTheme.cyan : AppTheme.slateBorder
                                )
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Chọn 15 khối hình 3D")
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

private struct Space3DToolsSheet: View {
    @ObservedObject var store: Space3DStore
    @Environment(\.presentationMode) private var presentationMode

    @State private var newPointName = "M"
    @State private var fromVertex = "S"
    @State private var toVertex = "C"
    @State private var ratio = 0.5

    @State private var lineP1 = "A"
    @State private var lineP2 = "M"
    @State private var isVector = false

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackdropView()
                ScrollView {
                    VStack(spacing: 14) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Kích thước khối hình")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(AppTheme.cyan)
                            sliderRow(label: "Cạnh a", value: $store.dimA, range: 1.5...7.5)
                            sliderRow(label: "Cạnh b", value: $store.dimB, range: 1.5...7.5)
                            sliderRow(label: "Chiều cao h", value: $store.dimH, range: 1.5...7.5)
                        }
                        .padding(14)
                        .liquidGlassCard()

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Thêm điểm chia cạnh (trung điểm / tỉ lệ)")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(AppTheme.amber)
                            HStack(spacing: 8) {
                                TextField("Tên (M)", text: $newPointName)
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                                TextField("Từ (S)", text: $fromVertex)
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                                TextField("Đến (C)", text: $toVertex)
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                            }
                            sliderRow(label: "Tỉ lệ k", value: $ratio, range: 0.1...0.9)
                            Button(action: {
                                let n = newPointName.trimmingCharacters(in: .whitespacesAndNewlines)
                                let f = fromVertex.trimmingCharacters(in: .whitespacesAndNewlines)
                                let t = toVertex.trimmingCharacters(in: .whitespacesAndNewlines)
                                guard !n.isEmpty, !f.isEmpty, !t.isEmpty else { return }
                                store.edgePoints.append(EdgePoint3D(name: n, from: f, to: t, ratio: ratio))
                            }) {
                                Text("Thêm điểm \(newPointName)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(AppTheme.amber)
                                    .cornerRadius(12)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard()

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Nối đoạn thẳng / Vectơ")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(AppTheme.rose)
                            HStack(spacing: 8) {
                                TextField("Đỉnh 1 (A)", text: $lineP1)
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                                TextField("Đỉnh 2 (M)", text: $lineP2)
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                            }
                            Toggle("Vẽ mũi tên Vectơ", isOn: $isVector)
                                .foregroundColor(AppTheme.textPrimary)
                            Button(action: {
                                let a = lineP1.trimmingCharacters(in: .whitespacesAndNewlines)
                                let b = lineP2.trimmingCharacters(in: .whitespacesAndNewlines)
                                guard !a.isEmpty, !b.isEmpty else { return }
                                store.customLines.append(
                                    CustomLine3D(
                                        p1: a, p2: b,
                                        isVector: isVector,
                                        forceDashed: false,
                                        highlighted: true,
                                        colorHex: "#F43F5E"
                                    )
                                )
                            }) {
                                Text("Thêm \(isVector ? "vectơ" : "đoạn") \(lineP1)\(lineP2)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(AppTheme.rose)
                                    .cornerRadius(12)
                            }
                        }
                        .padding(14)
                        .liquidGlassCard()
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Công cụ dựng hình 3D")
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

    private func sliderRow(label: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View {
        VStack(spacing: 2) {
            HStack {
                Text(label).font(.system(size: 12)).foregroundColor(AppTheme.textSecondary)
                Spacer()
                Text(String(format: "%.1f", value.wrappedValue))
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(AppTheme.textPrimary)
            }
            Slider(value: value, in: range, step: 0.1)
                .accentColor(AppTheme.cyan)
        }
    }
}
