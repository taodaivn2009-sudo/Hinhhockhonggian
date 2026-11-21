package com.example.docs

import com.example.engine.ShapeType

/**
 * Exhaustive Grade 11 Mathematics formula compendium aligned with Vietnam GDPT 2018.
 * Part 2: Chapters VI – IX (Exponentials & Logarithms + Real-World Formulas,
 * 3D Perpendicularity + Distances + Volumes + Circumsphere & Round Solids,
 * Probability Rules + Bernoulli, and Complete Derivatives + Tangents + Higher-Order).
 */
object Grade11CurriculumPart2 {

    val chapters: List<MathChapter> = listOf(
        // ==================== CHƯƠNG VI ====================
        MathChapter(
            id = "g11_c6",
            grade = MathGrade.GRADE_11,
            chapterNumber = "Chương VI",
            title = "Hàm số mũ và Hàm số lôgarit",
            summary = "Lũy thừa, căn bậc n, mọi công thức lôgarit, đồ thị, PT–BPT & bài toán thực tế (lãi kép, phóng xạ, pH, dB)",
            accentHex = 0xFFEC4899,
            quickActions = listOf(
                DocQuickAction.Plot2D("Vẽ hàm mũ y = 2^x", "2^x"),
                DocQuickAction.Plot2D("Vẽ hàm lôgarit y = ln(x)", "ln(x)")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g11_c6_pow",
                    title = "Lũy thừa số mũ thực & Căn bậc n",
                    subtitle = "Với a, b > 0 và α, β ∈ ℝ",
                    accentHex = 0xFFEC4899,
                    formulas = listOf(
                        MathFormulaItem("g11_c6_f1", "a^α · a^β   |   a^α / a^β", "a^{α + β}   |   a^{α − β}"),
                        MathFormulaItem("g11_c6_f2", "(a^α)^β   |   (ab)^α   |   (a/b)^α", "a^{αβ}   |   a^α·b^α   |   a^α/b^α"),
                        MathFormulaItem("g11_c6_f3", "a^{−n}   |   a^{m/n}", "1 / aⁿ   |   ⁿ√(a^m)", condition = "a > 0, n ≥ 2"),
                        MathFormulaItem("g11_c6_f4", "Căn bậc n của tích & căn lồng", "ⁿ√(ab) = ⁿ√a · ⁿ√b   ;   ᵐ√(ⁿ√a) = ᵐⁿ√a"),
                        MathFormulaItem("g11_c6_f5", "ⁿ√(aⁿ)", "|a| nếu n chẵn   •   a nếu n lẻ")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c6_log",
                    title = "Toàn bộ công thức Lôgarit & Đổi cơ số",
                    subtitle = "Điều kiện 0 < a, b, c ≠ 1 và biểu thức lấy log dương",
                    accentHex = 0xFFF472B6,
                    mnemonic = "Log tích thành tổng • Log thương thành hiệu • Mũ trên ra tử, mũ cơ số ra mẫu",
                    formulas = listOf(
                        MathFormulaItem("g11_c6_f6", "Định nghĩa & Giá trị cơ bản", "log_a b = α ⇔ a^α = b   •   log_a 1 = 0  ;  log_a a = 1  ;  log_a(1/b) = −log_a b"),
                        MathFormulaItem("g11_c6_f7", "Lôgarit tích & thương", "log_a(xy) = log_a x + log_a y   ;   log_a(x/y) = log_a x − log_a y"),
                        MathFormulaItem("g11_c6_f8", "Lôgarit lũy thừa & Cơ số lũy thừa", "log_a(b^α) = α·log_a b   ;   log_{a^β} b = (1/β)·log_a b"),
                        MathFormulaItem("g11_c6_f9", "Đổi cơ số & Quy tắc dây chuyền", "log_a b = log_c b / log_c a = 1 / log_b a   ;   log_a b · log_b c = log_a c"),
                        MathFormulaItem("g11_c6_f10", "Đồng nhất thức mũ – log", "a^{log_a b} = b   ;   log_a(a^α) = α   ;   a^{log_c b} = b^{log_c a}"),
                        MathFormulaItem("g11_c6_f11", "Log thập phân & tự nhiên", "log x = lg x = log₁₀ x   ;   ln x = log_e x  (e ≈ 2,71828)")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c6_eq",
                    title = "Đồ thị, Phương trình & Bất phương trình Mũ – Lôgarit",
                    subtitle = "Chú ý đảo chiều bất đẳng thức khi 0 < a < 1",
                    accentHex = 0xFFDB2777,
                    formulas = listOf(
                        MathFormulaItem("g11_c6_f12", "Hàm mũ y = a^x", "TXĐ: ℝ • TGT: (0; +∞) • Qua (0; 1) • TCN: y = 0 • Đồng biến khi a > 1"),
                        MathFormulaItem("g11_c6_f13", "Hàm lôgarit y = log_a x", "TXĐ: (0; +∞) • TGT: ℝ • Qua (1; 0) • TCĐ: x = 0 • Đối xứng y = a^x qua y = x"),
                        MathFormulaItem("g11_c6_f14", "PT cơ bản a^x = b  |  log_a x = b", "x = log_a b (b > 0)   |   x = a^b (x > 0)"),
                        MathFormulaItem("g11_c6_f15", "BPT mũ a^{f(x)} > a^{g(x)}", "f(x) > g(x) nếu a > 1   •   f(x) < g(x) nếu 0 < a < 1"),
                        MathFormulaItem("g11_c6_f16", "BPT lôgarit log_a f(x) > log_a g(x)", "f(x) > g(x) > 0 nếu a > 1   •   0 < f(x) < g(x) nếu 0 < a < 1")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c6_realworld",
                    title = "Công thức thực tế (Lãi kép, Phóng xạ, pH, Âm thanh, Richter)",
                    subtitle = "Các mô hình bài toán thực tiễn trọng tâm trong đề thi GDPT 2018",
                    accentHex = 0xFFBE185D,
                    formulas = listOf(
                        MathFormulaItem("g11_c6_f17", "Lãi kép gửi 1 lần & Gửi đều mỗi kỳ", "T = A(1 + r)ⁿ   •   Gửi đều đầu kỳ: T = (A/r)·[(1+r)ⁿ − 1]·(1 + r)"),
                        MathFormulaItem("g11_c6_f18", "Tăng trưởng liên tục & Bán rã phóng xạ", "N(t) = N₀ · e^{kt}   ;   m(t) = m₀ · (½)^{t / T}", note = "T: chu kỳ bán rã"),
                        MathFormulaItem("g11_c6_f19", "Độ pH & Cường độ âm (dB) & Richter", "pH = −log[H⁺]   ;   L = 10·log(I / I₀) dB   ;   M = log(A / A₀)")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG VII ====================
        MathChapter(
            id = "g11_c7",
            grade = MathGrade.GRADE_11,
            chapterNumber = "Chương VII",
            title = "Quan hệ vuông góc, Thể tích & Khối tròn xoay",
            summary = "Vuông góc, góc nhị diện, đổi khoảng cách, thể tích đa diện, mặt cầu ngoại tiếp & Nón–Trụ–Cầu",
            accentHex = 0xFFF59E0B,
            quickActions = listOf(
                DocQuickAction.Open3D("Mở 3D Chóp SA ⊥ đáy", ShapeType.PYRAMID4_SA, showAxes = false),
                DocQuickAction.Open3D("Mở 3D Hình nón tròn", ShapeType.CONE, showAxes = false),
                DocQuickAction.Open3D("Mở 3D Hình trụ tròn", ShapeType.CYLINDER, showAxes = false)
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g11_c7_perp",
                    title = "Định lý vuông góc, Góc & Hình chiếu diện tích",
                    subtitle = "Các quy tắc chứng minh vuông góc và xác định góc trong không gian",
                    accentHex = 0xFFF59E0B,
                    formulas = listOf(
                        MathFormulaItem("g11_c7_f1", "Điều kiện d ⊥ (P)", "d ⊥ a, d ⊥ b với a, b cắt nhau trong (P)"),
                        MathFormulaItem("g11_c7_f2", "Định lý 3 đường vuông góc", "a ⊂ (P), b' là hình chiếu của b lên (P): a ⊥ b  ⇔  a ⊥ b'"),
                        MathFormulaItem("g11_c7_f3", "Giao tuyến 2 mặt cùng ⊥ (R)", "(P) ⊥ (R), (Q) ⊥ (R) và (P) ∩ (Q) = d  ⇒  d ⊥ (R)"),
                        MathFormulaItem("g11_c7_f4", "Góc giữa d và (P) & Góc nhị diện", "φ = ∠(d, d') ∈ [0°; 90°]   •   Góc phẳng nhị diện [P, d, Q] ∈ [0°; 180°]"),
                        MathFormulaItem("g11_c7_f5", "Công thức diện tích hình chiếu", "S' = S · cos φ", note = "φ là góc giữa 2 mặt phẳng chứa hình và hình chiếu")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c7_dist",
                    title = "Khoảng cách trong không gian & Mẹo đổi điểm",
                    subtitle = "Tính nhanh khoảng cách từ điểm đến mặt và khoảng cách 2 đường chéo nhau",
                    accentHex = 0xFFEAB308,
                    formulas = listOf(
                        MathFormulaItem("g11_c7_f6", "Đổi điểm song song / cắt nhau", "AB // (P) ⇒ d(A) = d(B)   •   AB ∩ (P) = I ⇒ d(A)/d(B) = IA/IB"),
                        MathFormulaItem("g11_c7_f7", "Tứ diện vuông tại O (OA, OB, OC ⊥)", "1 / d²(O; (ABC)) = 1/OA² + 1/OB² + 1/OC²"),
                        MathFormulaItem("g11_c7_f8", "Khoảng cách 2 đường chéo nhau a, b", "Đoạn vuông góc chung MN  =  d(a; (P))  (với b ⊂ (P) và a // (P))"),
                        MathFormulaItem("g11_c7_f9", "Liên hệ Thể tích – Khoảng cách", "d(A; (BCD)) = 3 · V_{ABCD} / S_{BCD}")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c7_vol",
                    title = "Thể tích đa diện & Bán kính Mặt cầu ngoại tiếp tính nhanh",
                    subtitle = "B: diện tích đáy, h: chiều cao, l: cạnh bên",
                    accentHex = 0xFFD97706,
                    formulas = listOf(
                        MathFormulaItem("g11_c7_f10", "Thể tích Chóp & Lăng trụ", "V_chóp = ⅓ B·h   ;   V_lăng trụ = B·h   ;   V_hộp = abc"),
                        MathFormulaItem("g11_c7_f11", "Thể tích Chóp cụt đều", "V = ⅓ h · (B + B' + √(B · B'))"),
                        MathFormulaItem("g11_c7_f12", "Tỉ số thể tích Simson (chóp tam giác)", "V_{S.A'B'C'} / V_{S.ABC} = (SA'/SA) · (SB'/SB) · (SC'/SC)"),
                        MathFormulaItem("g11_c7_f13", "Tứ diện đều cạnh a", "h = a√6 / 3   ;   V = a³√2 / 12   ;   R_mc = a√6 / 4"),
                        MathFormulaItem("g11_c7_f14", "Tứ diện vuông OA=a, OB=b, OC=c", "V = (1/6)abc   ;   R_mc = ½ √(a² + b² + c²)"),
                        MathFormulaItem("g11_c7_f15", "Mặt cầu ngoại tiếp Chóp cạnh bên SA ⊥ đáy", "R = √[ R_đáy² + (SA / 2)² ]"),
                        MathFormulaItem("g11_c7_f16", "Mặt cầu ngoại tiếp Chóp đều / Cạnh bên bằng nhau", "R = l² / (2h)", note = "l: độ dài cạnh bên; h: chiều cao chóp"),
                        MathFormulaItem("g11_c7_f17", "Chóp có mặt bên ⊥ đáy (giao tuyến d)", "R = √(R_đáy² + R_bên² − d² / 4)")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c7_round",
                    title = "Khối tròn xoay: Hình Nón, Hình Trụ, Hình Cầu & Nón cụt",
                    subtitle = "Bán kính r, chiều cao h, đường sinh l",
                    accentHex = 0xFFF97316,
                    formulas = listOf(
                        MathFormulaItem("g11_c7_f18", "Hình Nón (l² = r² + h²)", "S_xq = πrl  ;  S_tp = πrl + πr²  ;  V = ⅓ πr²h"),
                        MathFormulaItem("g11_c7_f19", "Hình Trụ (l = h)", "S_xq = 2πrh  ;  S_tp = 2πrh + 2πr²  ;  V = πr²h"),
                        MathFormulaItem("g11_c7_f20", "Hình Cầu (bán kính R)", "S_cầu = 4πR²   ;   V_cầu = (4/3) πR³"),
                        MathFormulaItem("g11_c7_f21", "Hình Nón cụt (r₁, r₂, h, l)", "S_xq = π(r₁ + r₂)l   ;   V = ⅓ πh(r₁² + r₂² + r₁r₂)")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG VIII ====================
        MathChapter(
            id = "g11_c8",
            grade = MathGrade.GRADE_11,
            chapterNumber = "Chương VIII",
            title = "Các quy tắc tính xác suất",
            summary = "Quy tắc cộng 2–3 biến cố, hiệu biến cố, biến cố độc lập, công thức Bernoulli & kỳ vọng",
            accentHex = 0xFF14B8A6,
            groups = listOf(
                MathFormulaGroup(
                    id = "g11_c8_rules",
                    title = "Quy tắc cộng, Quy tắc nhân & Biến cố độc lập",
                    subtitle = "Áp dụng cho 2 hoặc nhiều biến cố trong cùng phép thử",
                    accentHex = 0xFF14B8A6,
                    formulas = listOf(
                        MathFormulaItem("g11_c8_f1", "Quy tắc cộng tổng quát", "P(A ∪ B) = P(A) + P(B) − P(A ∩ B)"),
                        MathFormulaItem("g11_c8_f2", "Quy tắc cộng 3 biến cố", "P(A∪B∪C) = P(A)+P(B)+P(C) − P(A∩B) − P(B∩C) − P(C∩A) + P(A∩B∩C)"),
                        MathFormulaItem("g11_c8_f3", "Xác suất hiệu P(A \\ B)", "P(A ∩ B̄) = P(A) − P(A ∩ B)"),
                        MathFormulaItem("g11_c8_f4", "Quy tắc nhân (A, B độc lập)", "P(A ∩ B) = P(A) · P(B)", note = "A, B độc lập ⇒ (Ā, B), (A, B̄), (Ā, B̄) cũng độc lập"),
                        MathFormulaItem("g11_c8_f5", "Ít nhất 1 trong n biến cố độc lập xảy ra", "P(A₁ ∪ … ∪ Aₙ) = 1 − P(Ā₁) · P(Ā₂) · … · P(Āₙ)")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c8_bernoulli",
                    title = "Công thức Bernoulli & Biến ngẫu nhiên rời rạc",
                    subtitle = "Lặp lại n phép thử độc lập có xác suất thành công p (chuyên đề mở rộng)",
                    accentHex = 0xFF0D9488,
                    formulas = listOf(
                        MathFormulaItem("g11_c8_f6", "Công thức Bernoulli (đúng k lần)", "P(X = k) = C_n^k · p^k · (1 − p)^{n − k}", condition = "0 ≤ k ≤ n"),
                        MathFormulaItem("g11_c8_f7", "Kỳ vọng E(X) & Phương sai V(X)", "E(X) = ∑ xᵢ pᵢ   ;   V(X) = ∑ xᵢ² pᵢ − [E(X)]²", note = "Với phân bố nhị thức B(n, p): E(X) = np ; V(X) = np(1−p)")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG IX ====================
        MathChapter(
            id = "g11_c9",
            grade = MathGrade.GRADE_11,
            chapterNumber = "Chương IX",
            title = "Đạo hàm",
            summary = "Định nghĩa, quy tắc tính, bảng đạo hàm sơ cấp/hợp, 3 dạng tiếp tuyến, đạo hàm cấp cao & vi phân",
            accentHex = 0xFFF97316,
            quickActions = listOf(
                DocQuickAction.Plot2D("Khảo sát đạo hàm y = x³ − 3x", "x^3 - 3*x")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g11_c9_rules",
                    title = "Định nghĩa, Quy tắc tính & Công thức đạo hàm nhanh",
                    subtitle = "Các quy tắc đại số, hàm hợp và phân thức hữu tỉ",
                    accentHex = 0xFFF97316,
                    formulas = listOf(
                        MathFormulaItem("g11_c9_f1", "Định nghĩa f'(x₀)", "lim_{x→x₀} [ f(x) − f(x₀) ] / (x − x₀)  =  lim_{Δx→0} (Δy / Δx)"),
                        MathFormulaItem("g11_c9_f2", "(u ± v)'  |  (u · v)'", "u' ± v'   |   u'v + uv'"),
                        MathFormulaItem("g11_c9_f3", "(u / v)'  |  [f(u)]'", "(u'v − uv') / v² (v ≠ 0)   |   f'(u) · u'"),
                        MathFormulaItem("g11_c9_f4", "Đạo hàm nhanh bậc 1 / bậc 1", "[ (ax + b)/(cx + d) ]' = (ad − bc) / (cx + d)²"),
                        MathFormulaItem("g11_c9_f5", "Đạo hàm nhanh bậc 2 / bậc 1", "[ (ax² + bx + c)/(dx + e) ]' = (adx² + 2aex + be − cd) / (dx + e)²"),
                        MathFormulaItem("g11_c9_f6", "Đạo hàm trị tuyệt đối (|u|)'", "(u · u') / |u|", condition = "u ≠ 0")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c9_table",
                    title = "Bảng đạo hàm đầy đủ (Sơ cấp & Hàm hợp u = u(x))",
                    subtitle = "Tra nhanh mọi đạo hàm lũy thừa, lượng giác, mũ và lôgarit",
                    accentHex = 0xFFF59E0B,
                    mnemonic = "Đạo hàm hàm co- (cos, cot) luôn mang dấu trừ (−); hàm hợp luôn nhân thêm u'",
                    formulas = listOf(
                        MathFormulaItem("g11_c9_f7", "(c)'  |  (x)'", "0   |   1", note = "c là hằng số"),
                        MathFormulaItem("g11_c9_f8", "(xⁿ)'  |  (uⁿ)'", "n·x^{n−1}   |   n·u^{n−1}·u'"),
                        MathFormulaItem("g11_c9_f9", "(√x)'  |  (√u)'", "1 / (2√x)   |   u' / (2√u)", condition = "x, u > 0"),
                        MathFormulaItem("g11_c9_f10", "(1/x)'  |  (1/u)'", "−1 / x²   |   −u' / u²"),
                        MathFormulaItem("g11_c9_f11", "(sin x)'  |  (sin u)'", "cos x   |   u' · cos u"),
                        MathFormulaItem("g11_c9_f12", "(cos x)'  |  (cos u)'", "−sin x   |   −u' · sin u"),
                        MathFormulaItem("g11_c9_f13", "(tan x)'  |  (tan u)'", "1 / cos²x   |   u' / cos²u"),
                        MathFormulaItem("g11_c9_f14", "(cot x)'  |  (cot u)'", "−1 / sin²x   |   −u' / sin²u"),
                        MathFormulaItem("g11_c9_f15", "(e^x)'  |  (e^u)'", "e^x   |   u' · e^u"),
                        MathFormulaItem("g11_c9_f16", "(a^x)'  |  (a^u)'", "a^x · ln a   |   u' · a^u · ln a", condition = "0 < a ≠ 1"),
                        MathFormulaItem("g11_c9_f17", "(ln x)'  |  (ln |u|)'", "1 / x   |   u' / u"),
                        MathFormulaItem("g11_c9_f18", "(log_a x)'  |  (log_a |u|)'", "1 / (x·ln a)   |   u' / (u·ln a)")
                    )
                ),
                MathFormulaGroup(
                    id = "g11_c9_tangent_high",
                    title = "3 dạng Tiếp tuyến, Đạo hàm cấp cao & Vi phân",
                    subtitle = "Tiếp xúc 2 đồ thị, đạo hàm cấp n và ý nghĩa cơ học",
                    accentHex = 0xFFEA580C,
                    formulas = listOf(
                        MathFormulaItem("g11_c9_f19", "PT tiếp tuyến tại M(x₀; y₀) ∈ (C)", "y = f'(x₀)(x − x₀) + f(x₀)"),
                        MathFormulaItem("g11_c9_f20", "Tiếp tuyến biết hệ số góc k / qua A(x_A; y_A)", "Giải f'(x₀) = k   •   Qua A: giải f'(x₀)(x_A − x₀) + f(x₀) = y_A"),
                        MathFormulaItem("g11_c9_f21", "Điều kiện 2 đồ thị (C₁), (C₂) tiếp xúc", "Hệ { f(x) = g(x)  và  f'(x) = g'(x) } có nghiệm"),
                        MathFormulaItem("g11_c9_f22", "Đạo hàm cấp n đặc biệt", "(e^{ax})^{(n)} = aⁿe^{ax}  ;  (sin x)^{(n)} = sin(x + nπ/2)  ;  (cos x)^{(n)} = cos(x + nπ/2)"),
                        MathFormulaItem("g11_c9_f23", "Đạo hàm cấp n của 1/(ax + b)", "(−1)ⁿ · n! · aⁿ / (ax + b)^{n + 1}"),
                        MathFormulaItem("g11_c9_f24", "Vi phân & Ý nghĩa cơ học", "df(x) = f'(x)dx ; f(x₀+Δx) ≈ f(x₀)+f'(x₀)Δx  •  v = s', a = v' = s''")
                    )
                )
            )
        )
    )
}
