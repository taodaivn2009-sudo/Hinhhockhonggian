import Foundation
import SwiftUI

// MARK: - 3D Geometry Models & 15 Standard Shapes

struct Vec3: Hashable {
    var x: Double
    var y: Double
    var z: Double

    static let zero = Vec3(x: 0, y: 0, z: 0)

    static func + (lhs: Vec3, rhs: Vec3) -> Vec3 {
        Vec3(x: lhs.x + rhs.x, y: lhs.y + rhs.y, z: lhs.z + rhs.z)
    }

    static func - (lhs: Vec3, rhs: Vec3) -> Vec3 {
        Vec3(x: lhs.x - rhs.x, y: lhs.y - rhs.y, z: lhs.z - rhs.z)
    }

    static func * (lhs: Vec3, rhs: Double) -> Vec3 {
        Vec3(x: lhs.x * rhs, y: lhs.y * rhs, z: lhs.z * rhs)
    }

    func dot(_ other: Vec3) -> Double {
        x * other.x + y * other.y + z * other.z
    }

    func cross(_ other: Vec3) -> Vec3 {
        Vec3(
            x: y * other.z - z * other.y,
            y: z * other.x - x * other.z,
            z: x * other.y - y * other.x
        )
    }

    var length: Double {
        (x * x + y * y + z * z).squareRoot()
    }

    var normalized: Vec3 {
        let l = length
        return l > 1e-9 ? Vec3(x: x / l, y: y / l, z: z / l) : .zero
    }
}

enum Shape3DKind: String, CaseIterable, Identifiable {
    case pyramid3 = "pyramid3"
    case pyramid3Sa = "pyramid3_sa"
    case tetraOrtho = "tetra_ortho"
    case pyramid4 = "pyramid4"
    case pyramid4Sa = "pyramid4_sa"
    case pyramidTrap = "pyramid_trap"
    case prism3 = "prism3"
    case prism3Oblique = "prism3_oblique"
    case prism6 = "prism6"
    case cube = "cube"
    case box = "box"
    case octahedron = "octahedron"
    case cylinder = "cylinder"
    case cone = "cone"
    case sphere = "sphere"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .pyramid3: return "Chóp tam giác đều S.ABC"
        case .pyramid3Sa: return "Chóp S.ABC (SA ⊥ đáy)"
        case .tetraOrtho: return "Tứ diện vuông O.ABC"
        case .pyramid4: return "Chóp tứ giác đều S.ABCD"
        case .pyramid4Sa: return "Chóp S.ABCD (SA ⊥ đáy)"
        case .pyramidTrap: return "Chóp đáy hình thang vuông"
        case .prism3: return "Lăng trụ đứng tam giác"
        case .prism3Oblique: return "Lăng trụ xiên tam giác"
        case .prism6: return "Lăng trụ lục giác đều"
        case .cube: return "Hình lập phương"
        case .box: return "Hình hộp chữ nhật"
        case .octahedron: return "Bát diện đều"
        case .cylinder: return "Hình trụ tròn"
        case .cone: return "Hình nón tròn"
        case .sphere: return "Hình cầu"
        }
    }

    var shortLabel: String {
        switch self {
        case .pyramid3: return "Chóp S.ABC"
        case .pyramid3Sa: return "Chóp SA⊥(ABC)"
        case .tetraOrtho: return "Tứ diện vuông"
        case .pyramid4: return "Chóp S.ABCD"
        case .pyramid4Sa: return "Chóp SA⊥(ABCD)"
        case .pyramidTrap: return "Chóp thang vuông"
        case .prism3: return "Lăng trụ △ đứng"
        case .prism3Oblique: return "Lăng trụ △ xiên"
        case .prism6: return "Lăng trụ lục giác"
        case .cube: return "Lập phương"
        case .box: return "Hộp chữ nhật"
        case .octahedron: return "Bát diện đều"
        case .cylinder: return "Hình trụ"
        case .cone: return "Hình nón"
        case .sphere: return "Hình cầu"
        }
    }

    static func fromKey(_ key: String) -> Shape3DKind {
        let norm = key.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return Shape3DKind(rawValue: norm) ?? .pyramid4Sa
    }
}

struct EdgePoint3D: Identifiable, Hashable {
    let id = UUID()
    var name: String
    var from: String
    var to: String
    var ratio: Double
}

struct XyzPoint3D: Identifiable, Hashable {
    let id = UUID()
    var name: String
    var position: Vec3
}

struct CustomLine3D: Identifiable, Hashable {
    let id = UUID()
    var p1: String
    var p2: String
    var isVector: Bool
    var forceDashed: Bool
    var highlighted: Bool
    var colorHex: String
}

struct CustomPlane3D: Identifiable, Hashable {
    let id = UUID()
    var pointNames: [String]
    var colorHex: String
}

struct Mesh3DData {
    var vertices: [String: Vec3]
    var edges: [(String, String)]
    var faces: [[String]]
    var altitude: (String, String)?
    var isCurved: Bool
    var curvedRadius: Double
    var curvedHeight: Double
}

enum Geometry3DBuilder {
    static func buildMesh(kind: Shape3DKind, a: Double, b: Double, h: Double) -> Mesh3DData {
        var v: [String: Vec3] = [:]
        var edges: [(String, String)] = []
        var faces: [[String]] = []
        var alt: (String, String)? = nil
        var isCurved = false

        let sa = max(1.0, a)
        let sb = max(1.0, b)
        let sh = max(1.0, h)

        switch kind {
        case .pyramid3:
            let r = sa / sqrt(3.0)
            let pA = Vec3(x: r * cos(.pi / 6), y: -sh * 0.35, z: r * sin(.pi / 6))
            let pB = Vec3(x: -r * cos(.pi / 6), y: -sh * 0.35, z: r * sin(.pi / 6))
            let pC = Vec3(x: 0, y: -sh * 0.35, z: -r)
            let pO = Vec3(x: 0, y: -sh * 0.35, z: 0)
            let pS = Vec3(x: 0, y: sh * 0.65, z: 0)
            v = ["S": pS, "A": pA, "B": pB, "C": pC, "O": pO]
            edges = [("A","B"), ("B","C"), ("C","A"), ("S","A"), ("S","B"), ("S","C")]
            faces = [["A","B","C"], ["S","A","B"], ["S","B","C"], ["S","C","A"]]
            alt = ("S","O")

        case .pyramid3Sa:
            let pA = Vec3(x: -sa * 0.45, y: -sh * 0.35, z: -sa * 0.3)
            let pB = Vec3(x: sa * 0.55, y: -sh * 0.35, z: -sa * 0.3)
            let pC = Vec3(x: 0, y: -sh * 0.35, z: sa * 0.55)
            let pS = Vec3(x: pA.x, y: sh * 0.65, z: pA.z)
            v = ["S": pS, "A": pA, "B": pB, "C": pC, "O": pA]
            edges = [("A","B"), ("B","C"), ("C","A"), ("S","A"), ("S","B"), ("S","C")]
            faces = [["A","B","C"], ["S","A","B"], ["S","B","C"], ["S","C","A"]]
            alt = ("S","A")

        case .tetraOrtho:
            let pO = Vec3(x: -sa * 0.3, y: -sh * 0.35, z: -sb * 0.3)
            let pA = Vec3(x: pO.x + sa, y: pO.y, z: pO.z)
            let pB = Vec3(x: pO.x, y: pO.y, z: pO.z + sb)
            let pC = Vec3(x: pO.x, y: pO.y + sh, z: pO.z)
            v = ["O": pO, "A": pA, "B": pB, "C": pC]
            edges = [("O","A"), ("O","B"), ("O","C"), ("A","B"), ("B","C"), ("C","A")]
            faces = [["O","A","B"], ["O","B","C"], ["O","C","A"], ["A","B","C"]]
            alt = ("C","O")

        case .pyramid4:
            let hx = sa * 0.5, hz = sb * 0.5, y0 = -sh * 0.35
            v = [
                "A": Vec3(x: -hx, y: y0, z: -hz),
                "B": Vec3(x: hx, y: y0, z: -hz),
                "C": Vec3(x: hx, y: y0, z: hz),
                "D": Vec3(x: -hx, y: y0, z: hz),
                "O": Vec3(x: 0, y: y0, z: 0),
                "S": Vec3(x: 0, y: sh * 0.65, z: 0)
            ]
            edges = [("A","B"), ("B","C"), ("C","D"), ("D","A"), ("S","A"), ("S","B"), ("S","C"), ("S","D")]
            faces = [["A","B","C","D"], ["S","A","B"], ["S","B","C"], ["S","C","D"], ["S","D","A"]]
            alt = ("S","O")

        case .pyramid4Sa:
            let hx = sa * 0.5, hz = sb * 0.5, y0 = -sh * 0.35
            let pA = Vec3(x: -hx, y: y0, z: -hz)
            v = [
                "A": pA,
                "B": Vec3(x: hx, y: y0, z: -hz),
                "C": Vec3(x: hx, y: y0, z: hz),
                "D": Vec3(x: -hx, y: y0, z: hz),
                "O": Vec3(x: 0, y: y0, z: 0),
                "S": Vec3(x: pA.x, y: sh * 0.65, z: pA.z)
            ]
            edges = [("A","B"), ("B","C"), ("C","D"), ("D","A"), ("S","A"), ("S","B"), ("S","C"), ("S","D")]
            faces = [["A","B","C","D"], ["S","A","B"], ["S","B","C"], ["S","C","D"], ["S","D","A"]]
            alt = ("S","A")

        case .pyramidTrap:
            let hx = sa * 0.5, hz = sb * 0.5, y0 = -sh * 0.35
            let pA = Vec3(x: -hx, y: y0, z: -hz)
            v = [
                "A": pA,
                "B": Vec3(x: -hx, y: y0, z: hz),
                "C": Vec3(x: hx * 0.35, y: y0, z: hz),
                "D": Vec3(x: hx, y: y0, z: -hz),
                "O": pA,
                "S": Vec3(x: pA.x, y: sh * 0.65, z: pA.z)
            ]
            edges = [("A","B"), ("B","C"), ("C","D"), ("D","A"), ("S","A"), ("S","B"), ("S","C"), ("S","D")]
            faces = [["A","B","C","D"], ["S","A","B"], ["S","B","C"], ["S","C","D"], ["S","D","A"]]
            alt = ("S","A")

        case .prism3, .prism3Oblique:
            let r = sa / sqrt(3.0)
            let y0 = -sh * 0.5, y1 = sh * 0.5
            let shiftX = kind == .prism3Oblique ? sa * 0.35 : 0
            let shiftZ = kind == .prism3Oblique ? sa * 0.18 : 0
            let pA = Vec3(x: r * cos(.pi / 6), y: y0, z: r * sin(.pi / 6))
            let pB = Vec3(x: -r * cos(.pi / 6), y: y0, z: r * sin(.pi / 6))
            let pC = Vec3(x: 0, y: y0, z: -r)
            let pAp = Vec3(x: pA.x + shiftX, y: y1, z: pA.z + shiftZ)
            let pBp = Vec3(x: pB.x + shiftX, y: y1, z: pB.z + shiftZ)
            let pCp = Vec3(x: pC.x + shiftX, y: y1, z: pC.z + shiftZ)
            v = [
                "A": pA, "B": pB, "C": pC,
                "A'": pAp, "B'": pBp, "C'": pCp,
                "O": Vec3(x: 0, y: y0, z: 0)
            ]
            edges = [
                ("A","B"), ("B","C"), ("C","A"),
                ("A'","B'"), ("B'","C'"), ("C'","A'"),
                ("A","A'"), ("B","B'"), ("C","C'")
            ]
            faces = [
                ["A","B","C"], ["A'","B'","C'"],
                ["A","B","B'","A'"], ["B","C","C'","B'"], ["C","A","A'","C'"]
            ]
            alt = ("A'","O")

        case .prism6:
            let r = sa * 0.55
            let y0 = -sh * 0.5, y1 = sh * 0.5
            let names = ["A","B","C","D","E","F"]
            for (idx, n) in names.enumerated() {
                let ang = Double(idx) * .pi / 3.0
                v[n] = Vec3(x: r * cos(ang), y: y0, z: r * sin(ang))
                v["\(n)'"] = Vec3(x: r * cos(ang), y: y1, z: r * sin(ang))
            }
            v["O"] = Vec3(x: 0, y: y0, z: 0)
            for idx in 0..<6 {
                let cur = names[idx]
                let nxt = names[(idx + 1) % 6]
                edges.append((cur, nxt))
                edges.append(("\(cur)'", "\(nxt)'"))
                edges.append((cur, "\(cur)'"))
                faces.append([cur, nxt, "\(nxt)'", "\(cur)'"])
            }
            faces.append(names)
            faces.append(names.map { "\($0)'" })

        case .cube, .box:
            let hx = sa * 0.5
            let hz = (kind == .cube ? sa : sb) * 0.5
            let hy = (kind == .cube ? sa : sh) * 0.5
            v = [
                "A": Vec3(x: -hx, y: -hy, z: -hz),
                "B": Vec3(x: hx, y: -hy, z: -hz),
                "C": Vec3(x: hx, y: -hy, z: hz),
                "D": Vec3(x: -hx, y: -hy, z: hz),
                "A'": Vec3(x: -hx, y: hy, z: -hz),
                "B'": Vec3(x: hx, y: hy, z: -hz),
                "C'": Vec3(x: hx, y: hy, z: hz),
                "D'": Vec3(x: -hx, y: hy, z: hz),
                "O": Vec3(x: 0, y: -hy, z: 0)
            ]
            edges = [
                ("A","B"), ("B","C"), ("C","D"), ("D","A"),
                ("A'","B'"), ("B'","C'"), ("C'","D'"), ("D'","A'"),
                ("A","A'"), ("B","B'"), ("C","C'"), ("D","D'")
            ]
            faces = [
                ["A","B","C","D"], ["A'","B'","C'","D'"],
                ["A","B","B'","A'"], ["B","C","C'","B'"], ["C","D","D'","C'"], ["D","A","A'","D'"]
            ]

        case .octahedron:
            let r = sa * 0.65
            v = [
                "S": Vec3(x: 0, y: r, z: 0),
                "S'": Vec3(x: 0, y: -r, z: 0),
                "A": Vec3(x: r, y: 0, z: 0),
                "B": Vec3(x: 0, y: 0, z: r),
                "C": Vec3(x: -r, y: 0, z: 0),
                "D": Vec3(x: 0, y: 0, z: -r),
                "O": Vec3.zero
            ]
            edges = [
                ("A","B"), ("B","C"), ("C","D"), ("D","A"),
                ("S","A"), ("S","B"), ("S","C"), ("S","D"),
                ("S'","A"), ("S'","B"), ("S'","C"), ("S'","D")
            ]
            faces = [
                ["S","A","B"], ["S","B","C"], ["S","C","D"], ["S","D","A"],
                ["S'","A","B"], ["S'","B","C"], ["S'","C","D"], ["S'","D","A"]
            ]
            alt = ("S","S'")

        case .cylinder:
            isCurved = true
            let r = sa * 0.5
            v = [
                "O": Vec3(x: 0, y: -sh * 0.5, z: 0),
                "O'": Vec3(x: 0, y: sh * 0.5, z: 0),
                "A": Vec3(x: r, y: -sh * 0.5, z: 0),
                "B": Vec3(x: -r, y: -sh * 0.5, z: 0)
            ]
            alt = ("O'","O")

        case .cone:
            isCurved = true
            let r = sa * 0.5
            v = [
                "S": Vec3(x: 0, y: sh * 0.6, z: 0),
                "O": Vec3(x: 0, y: -sh * 0.4, z: 0),
                "A": Vec3(x: r, y: -sh * 0.4, z: 0),
                "B": Vec3(x: -r, y: -sh * 0.4, z: 0)
            ]
            alt = ("S","O")

        case .sphere:
            isCurved = true
            let r = sa * 0.5
            v = [
                "O": Vec3.zero,
                "A": Vec3(x: r, y: 0, z: 0),
                "B": Vec3(x: 0, y: r, z: 0)
            ]
        }

        return Mesh3DData(
            vertices: v,
            edges: edges,
            faces: faces,
            altitude: alt,
            isCurved: isCurved,
            curvedRadius: sa * 0.5,
            curvedHeight: sh
        )
    }
}

// MARK: - 2D Oxy & Math Function Models

struct Point2DItem: Identifiable, Hashable {
    let id = UUID()
    var name: String
    var x: Double
    var y: Double
    var colorHex: String = ""
}

enum Object2DKind: String, Hashable {
    case segment
    case line
    case ray
    case vector
    case circle
    case polygon
}

struct Object2DItem: Identifiable, Hashable {
    let id = UUID()
    var kind: Object2DKind
    var p1: String = ""
    var p2: String = ""
    var radius: Double = 0
    var pointNames: [String] = []
    var dashed: Bool = false
    var highlighted: Bool = false
    var colorHex: String = ""
}

struct Function2DItem: Identifiable, Hashable {
    let id = UUID()
    var expression: String
    var colorHex: String
    var isVisible: Bool = true
}

// MARK: - Safe Recursive-Descent Math Expression Evaluator (f(x))

struct MathExprEvaluator {
    private let chars: [Character]
    private var idx: Int = 0
    private let xValue: Double

    static func evaluate(_ raw: String, x: Double) -> Double? {
        let normalized = normalizeExpression(raw)
        if normalized.isEmpty { return nil }
        var parser = MathExprEvaluator(chars: Array(normalized), xValue: x)
        guard let result = parser.parseExpression(), result.isFinite else { return nil }
        return result
    }

    private static func normalizeExpression(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "−", with: "-")
            .replacingOccurrences(of: "×", with: "*")
            .replacingOccurrences(of: "÷", with: "/")
            .replacingOccurrences(of: "²", with: "^2")
            .replacingOccurrences(of: "³", with: "^3")
            .replacingOccurrences(of: "√", with: "sqrt")
            .replacingOccurrences(of: "π", with: "pi")
        if let eqIdx = s.firstIndex(of: "=") {
            s = String(s[s.index(after: eqIdx)...])
        }
        // Convert |expr| to abs(expr)
        var out = ""
        var inAbs = false
        for ch in s {
            if ch == "|" {
                out += inAbs ? ")" : "abs("
                inAbs.toggle()
            } else {
                out.append(ch)
            }
        }
        // Insert implicit multiplication: 2x -> 2*x, 2( -> 2*(, x( -> x*(, )x -> )*x, )( -> )*(
        let arr = Array(out)
        var finalStr = ""
        for i in 0..<arr.count {
            let cur = arr[i]
            finalStr.append(cur)
            if i + 1 < arr.count {
                let nxt = arr[i + 1]
                let curIsNum = cur.isNumber || cur == "."
                let curIsClose = cur == ")" || cur == "x"
                let nxtIsOpenOrVar = nxt == "(" || nxt == "x" || nxt.isLetter
                if (curIsNum && nxtIsOpenOrVar) || (curIsClose && (nxt == "(" || nxt.isNumber || nxt == "x")) {
                    // Don't split function names or scientific notation
                    if !(cur.isLetter && nxt.isLetter && cur != "x") {
                        finalStr.append("*")
                    }
                }
            }
        }
        return finalStr
    }

    private mutating func parseExpression() -> Double? {
        guard var left = parseTerm() else { return nil }
        while idx < chars.count {
            let c = chars[idx]
            if c == "+" {
                idx += 1
                guard let right = parseTerm() else { return nil }
                left += right
            } else if c == "-" {
                idx += 1
                guard let right = parseTerm() else { return nil }
                left -= right
            } else {
                break
            }
        }
        return left
    }

    private mutating func parseTerm() -> Double? {
        guard var left = parsePower() else { return nil }
        while idx < chars.count {
            let c = chars[idx]
            if c == "*" {
                idx += 1
                guard let right = parsePower() else { return nil }
                left *= right
            } else if c == "/" {
                idx += 1
                guard let right = parsePower() else { return nil }
                if abs(right) < 1e-12 { return nil }
                left /= right
            } else {
                break
            }
        }
        return left
    }

    private mutating func parsePower() -> Double? {
        guard let base = parseUnary() else { return nil }
        if idx < chars.count && chars[idx] == "^" {
            idx += 1
            guard let exp = parsePower() else { return nil }
            return pow(base, exp)
        }
        return base
    }

    private mutating func parseUnary() -> Double? {
        if idx < chars.count && chars[idx] == "+" {
            idx += 1
            return parseUnary()
        }
        if idx < chars.count && chars[idx] == "-" {
            idx += 1
            guard let v = parseUnary() else { return nil }
            return -v
        }
        return parsePrimary()
    }

    private mutating func parsePrimary() -> Double? {
        guard idx < chars.count else { return nil }
        let c = chars[idx]
        if c == "(" {
            idx += 1
            guard let v = parseExpression() else { return nil }
            if idx < chars.count && chars[idx] == ")" { idx += 1 }
            return v
        }
        if c.isNumber || c == "." {
            let start = idx
            while idx < chars.count && (chars[idx].isNumber || chars[idx] == ".") {
                idx += 1
            }
            return Double(String(chars[start..<idx]))
        }
        if c.isLetter {
            let start = idx
            while idx < chars.count && (chars[idx].isLetter || chars[idx].isNumber) {
                idx += 1
            }
            let ident = String(chars[start..<idx])
            if ident == "x" { return xValue }
            if ident == "pi" { return Double.pi }
            if ident == "e" { return M_E }
            if idx < chars.count && chars[idx] == "(" {
                idx += 1
                guard let arg = parseExpression() else { return nil }
                if idx < chars.count && chars[idx] == ")" { idx += 1 }
                switch ident {
                case "sin": return sin(arg)
                case "cos": return cos(arg)
                case "tan": return tan(arg)
                case "cot":
                    let t = tan(arg)
                    return abs(t) < 1e-12 ? nil : 1.0 / t
                case "sqrt": return arg < 0 ? nil : sqrt(arg)
                case "cbrt": return cbrt(arg)
                case "abs": return abs(arg)
                case "ln": return arg <= 0 ? nil : log(arg)
                case "log", "log10": return arg <= 0 ? nil : log10(arg)
                case "log2": return arg <= 0 ? nil : log2(arg)
                case "exp": return exp(arg)
                default: return nil
                }
            }
        }
        return nil
    }
}

// MARK: - AI Drawing Plan Shared Between AI Tab -> 3D / 2D Tabs

enum AiDrawTarget: String {
    case space3D = "3D"
    case plane2D = "2D"
}

struct AiPageSuggestion: Identifiable, Hashable {
    let id = UUID()
    let page: Int
    let title: String
    let reason: String
}

struct AiScene3DPlan {
    var shapeKind: Shape3DKind
    var a: Double
    var b: Double
    var h: Double
    var showAxes: Bool
    var showAltitude: Bool
    var edgePoints: [EdgePoint3D]
    var xyzPoints: [XyzPoint3D]
    var lines: [CustomLine3D]
    var planes: [CustomPlane3D]
}

struct AiScene2DPlan {
    var points: [Point2DItem]
    var objects: [Object2DItem]
    var functions: [Function2DItem]
}

struct AiDrawingPlan: Identifiable {
    let id = UUID()
    let target: AiDrawTarget
    let title: String
    let problemText: String
    let drawingSummary: String
    let solutionHint: String
    let scene3D: AiScene3DPlan?
    let scene2D: AiScene2DPlan?
    let relatedPages: [AiPageSuggestion]
}

struct AiProblemStreamingPreview {
    var target: AiDrawTarget? = nil
    var title: String = ""
    var problemText: String = ""
    var drawingSummary: String = ""
    var solutionHint: String = ""
    var isBuildingGeometry: Bool = false
    var thinkingChars: Int = 0
    var rawChars: Int = 0

    var hasVisibleText: Bool {
        !title.isEmpty || !problemText.isEmpty || !drawingSummary.isEmpty || !solutionHint.isEmpty
    }
}
