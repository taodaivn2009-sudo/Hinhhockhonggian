package com.example.docs

import com.example.engine.ShapeType

/**
 * Exhaustive Grade 12 Mathematics formula compendium aligned with Vietnam GDPT 2018
 * (Part 1: Chapters I–III; Part 2 in [Grade12CurriculumPart2]: Chapters IV–VI).
 */
object Grade12CurriculumData {

    private val part1Chapters: List<MathChapter> = listOf(
        // ==================== CHƯƠNG I ====================
        MathChapter(
            id = "g12_c1",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương I",
            title = "Ứng dụng đạo hàm để khảo sát & vẽ đồ thị hàm số",
            summary = "Đơn điệu, cực trị, GTLN – GTNN, 3 tiệm cận (TCĐ/TCN/TCX), bậc ba, bậc 2/bậc 1 & hàm trị tuyệt đối",
            accentHex = 0xFF6366F1,
            quickActions = listOf(
                DocQuickAction.Plot2D("Khảo sát y = x³ − 3x + 1", "x^3 - 3*x + 1"),
                DocQuickAction.Plot2D("Tiệm cận xiên (x²−2x+2)/(x−1)", "(x^2 - 2*x + 2)/(x - 1)")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c1_mono_ext",
                    title = "Tính đơn điệu, Cực trị & GTLN – GTNN",
                    subtitle = "Liên hệ dấu f'(x), f''(x), hàm hợp và bài toán chứa tham số m",
                    accentHex = 0xFF6366F1,
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f1", "Hàm đồng biến / nghịch biến trên K", "f'(x) ≥ 0 (đồng biến) • f'(x) ≤ 0 (nghịch biến), dấu = tại hữu hạn điểm"),
                        MathFormulaItem("g12_c1_f1a", "Đạo hàm hàm hợp khảo sát y = f(u(x))", "y' = u'(x) · f'(u(x))", note = "Dùng xét đơn điệu và số cực trị hàm hợp"),
                        MathFormulaItem("g12_c1_f1b", "Hàm bậc 3 đơn điệu trên ℝ (chứa m)", "Đồng biến: a > 0, b² − 3ac ≤ 0   •   Nghịch biến: a < 0, b² − 3ac ≤ 0", note = "Nếu hệ số a chứa m nhớ xét riêng trường hợp a = 0"),
                        MathFormulaItem("g12_c1_f1c", "Nhất biến (ax+b)/(cx+d) đơn điệu trên (α; β)", "Đồng biến: ad − bc > 0 và −d/c ∉ (α; β)   •   Nghịch biến: ad − bc < 0 và −d/c ∉ (α; β)"),
                        MathFormulaItem("g12_c1_f2", "Dấu hiệu 1 tìm cực trị", "f'(x) đổi dấu (+) → (−) qua x₀: Cực đại • (−) → (+): Cực tiểu", note = "x₀ có thể là điểm f'(x₀) = 0 hoặc f'(x₀) không xác định nhưng f liên tục tại x₀"),
                        MathFormulaItem("g12_c1_f3", "Dấu hiệu 2 (tại f'(x₀) = 0)", "f''(x₀) < 0 ⇒ Cực đại   ;   f''(x₀) > 0 ⇒ Cực tiểu"),
                        MathFormulaItem("g12_c1_f4", "GTLN – GTNN trên đoạn [a; b]", "So sánh f(a), f(b) và các f(xᵢ) tại nghiệm f'(xᵢ) = 0 ∈ (a; b)"),
                        MathFormulaItem("g12_c1_f4a", "GTLN – GTNN trên khoảng (a; b)", "Lập bảng biến thiên trên (a; b) và xét giới hạn tại 2 đầu mút a⁺, b⁻")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c1_asymp",
                    title = "Đường tiệm cận của đồ thị hàm số",
                    subtitle = "Tiệm cận đứng (TCĐ), tiệm cận ngang (TCN) và tiệm cận xiên (TCX)",
                    accentHex = 0xFF818CF8,
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f5", "Tiệm cận đứng (TCĐ) x = x₀", "lim_{x→x₀⁺} f(x) = ±∞   hoặc   lim_{x→x₀⁻} f(x) = ±∞"),
                        MathFormulaItem("g12_c1_f6", "Tiệm cận ngang (TCN) y = y₀", "lim_{x→+∞} f(x) = y₀   hoặc   lim_{x→−∞} f(x) = y₀"),
                        MathFormulaItem("g12_c1_f7", "Tiệm cận xiên (TCX) y = ax + b", "a = lim_{x→±∞} [f(x)/x] (a ≠ 0)   ;   b = lim_{x→±∞} [f(x) − ax]"),
                        MathFormulaItem("g12_c1_f7a", "Mẹo tìm nhanh TCN phân thức P(x)/Q(x)", "deg P < deg Q ⇒ y = 0  •  deg P = deg Q ⇒ y = a_n/b_n  •  deg P > deg Q ⇒ không có TCN"),
                        MathFormulaItem("g12_c1_f7b", "Điều kiện TCĐ phân thức P(x)/Q(x)", "x₀ là nghiệm mẫu Q(x₀) = 0 và không bị rút gọn hết bởi tử P(x)", note = "Nếu cả tử và mẫu đều có nhân tử (x − x₀), cần rút gọn rồi mới kết luận")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c1_cubic",
                    title = "Hàm bậc ba & Hàm trùng phương (Công thức nhanh)",
                    subtitle = "y = ax³ + bx² + cx + d (a ≠ 0) và y = ax⁴ + bx² + c (a ≠ 0)",
                    accentHex = 0xFFA855F7,
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f9", "Đạo hàm & Cực trị bậc ba", "y' = 3ax² + 2bx + c  (Δ' = b² − 3ac)  •  Có 2 cực trị ⇔ b² − 3ac > 0"),
                        MathFormulaItem("g12_c1_f10", "Vi-ét cho 2 hoành độ cực trị x₁, x₂", "x₁ + x₂ = −2b/(3a)   ;   x₁x₂ = c/(3a)   ;   (x₁ − x₂)² = 4(b² − 3ac)/(9a²)"),
                        MathFormulaItem("g12_c1_f11", "Tâm đối xứng (Điểm uốn U)", "x_U = −b / (3a)   (nghiệm của y'' = 6ax + 2b = 0)"),
                        MathFormulaItem("g12_c1_f11a", "Đường thẳng đi qua 2 điểm cực trị", "Chia đa thức y cho y' được thương q(x) và dư r(x) ⇒ PT là y = r(x)", note = "Cụ thể: y = (2/3)[c − b²/(3a)]x + d − bc/(9a)"),
                        MathFormulaItem("g12_c1_f11b", "Hàm trùng phương y = ax⁴ + bx² + c", "3 cực trị ⇔ a·b < 0   •   1 cực trị ⇔ a·b ≥ 0   •   Trục đối xứng Oy", note = "3 cực trị lập thành tam giác cân tại A(0; c) với y_A − y_B = b²/(4a)")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c1_rational",
                    title = "Hàm nhất biến & Hàm bậc hai trên bậc nhất",
                    subtitle = "Hai dạng hàm phân thức trọng tâm khảo sát đồ thị GDPT 2018",
                    accentHex = 0xFF4F46E5,
                    mnemonic = "Tung độ cực trị hàm bậc 2/bậc 1: lấy đạo hàm tử chia đạo hàm mẫu (2ax₀ + b)/d",
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f8", "Hàm nhất biến y = (ax + b)/(cx + d)", "y' = (ad − bc)/(cx + d)² • TCĐ: x = −d/c • TCN: y = a/c • Tâm I(−d/c; a/c)"),
                        MathFormulaItem("g12_c1_f8a", "Hàm bậc 2/bậc 1: y = (ax² + bx + c)/(dx + e)", "Chia đa thức: y = px + q + r/(dx + e)", condition = "ad ≠ 0, r ≠ 0"),
                        MathFormulaItem("g12_c1_f8b", "Tiệm cận & Tâm đối xứng bậc 2/bậc 1", "TCĐ: x = −e/d  •  TCX: y = px + q  •  Tâm đối xứng I = TCĐ ∩ TCX"),
                        MathFormulaItem("g12_c1_f8c", "Cực trị hàm bậc 2/bậc 1", "y' = [adx² + 2aex + (be − cd)] / (dx + e)²   •   Có 2 cực trị ⇔ p·r > 0"),
                        MathFormulaItem("g12_c1_f8d", "Tính nhanh tung độ cực trị bậc 2/bậc 1", "y(x₀) = (2ax₀ + b) / d   (với x₀ là nghiệm của y' = 0)", note = "Cũng là PT đường thẳng đi qua 2 điểm cực trị: y = (2a/d)x + b/d")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c1_abs_inter",
                    title = "Tương giao & Đếm nhanh cực trị hàm trị tuyệt đối",
                    subtitle = "Các công thức giải nhanh bài toán đồ thị và tham số m",
                    accentHex = 0xFFEC4899,
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f12", "Phương trình hoành độ giao điểm", "f(x) = g(x)  ⇒  Số nghiệm thực = Số giao điểm của (C₁) và (C₂)"),
                        MathFormulaItem("g12_c1_f13", "Số cực trị của y = f(|x|)", "Số cực trị = 2a + 1", note = "Với a là số điểm cực trị dương (x > 0) của hàm y = f(x)"),
                        MathFormulaItem("g12_c1_f14", "Số cực trị của y = |f(x)|", "Số cực trị = a + b", note = "a: số cực trị của f(x) • b: số nghiệm bội lẻ (cắt ngang Ox) của f(x) = 0"),
                        MathFormulaItem("g12_c1_f15", "Biến đổi tịnh tiến tham số m", "y = f(|x + m|) giữ nguyên số cực trị của f(|x|) • y = |f(x) + m| phụ thuộc số giao điểm với y = −m")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG II ====================
        MathChapter(
            id = "g12_c2",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương II",
            title = "Vectơ và Hệ trục tọa độ trong không gian (Oxyz)",
            summary = "Quy tắc hình hộp, tọa độ Oxyz, hình chiếu – đối xứng, tích vô hướng, tích có hướng & thể tích",
            accentHex = 0xFF8B5CF6,
            quickActions = listOf(
                DocQuickAction.Open3D("Mở Hình lập phương Oxyz", ShapeType.CUBE, showAxes = true),
                DocQuickAction.Open3D("Mở Tứ diện vuông Oxyz", ShapeType.TETRA_ORTHO, showAxes = true)
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c2_coords",
                    title = "Quy tắc hình hộp & Tọa độ trong Oxyz",
                    subtitle = "Cho A(x_A; y_A; z_A), B(x_B; y_B; z_B), a⃗(x₁; y₁; z₁), b⃗(x₂; y₂; z₂)",
                    accentHex = 0xFF8B5CF6,
                    formulas = listOf(
                        MathFormulaItem("g12_c2_f1", "Quy tắc hình hộp & Phân tích i⃗, j⃗, k⃗", "AB⃗ + AD⃗ + AA'⃗ = AC'⃗   •   u⃗ = x·i⃗ + y·j⃗ + z·k⃗ ⇔ u⃗ = (x; y; z)"),
                        MathFormulaItem("g12_c2_f2", "Vectơ AB⃗ & Độ dài AB", "AB⃗ = (x_B−x_A; y_B−y_A; z_B−z_A)  ;  AB = √[(x_B−x_A)²+(y_B−y_A)²+(z_B−z_A)²]"),
                        MathFormulaItem("g12_c2_f2a", "Điều kiện 2 vectơ cùng phương", "a⃗ = k·b⃗  ⇔  x₁/x₂ = y₁/y₂ = z₁/z₂  (mẫu ≠ 0)"),
                        MathFormulaItem("g12_c2_f3", "Trung điểm I & Trọng tâm △ABC", "I((x_A+x_B)/2; …)   ;   G((x_A+x_B+x_C)/3; …)"),
                        MathFormulaItem("g12_c2_f4", "Trọng tâm tứ diện ABCD", "G_td( (x_A+x_B+x_C+x_D)/4 ; … )"),
                        MathFormulaItem("g12_c2_f4a", "Điểm M chia đoạn AB theo tỉ số k", "MA⃗ = k·MB⃗ (k ≠ 1)  ⇒  x_M = (x_A − k·x_B)/(1 − k)  (tương tự y_M, z_M)"),
                        MathFormulaItem("g12_c2_f4b", "Tâm đường tròn nội tiếp △ABC", "I = (a·A + b·B + c·C) / (a + b + c)", note = "Với a = BC, b = CA, c = AB")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c2_proj_sym",
                    title = "Hình chiếu, Đối xứng & Khoảng cách tới trục/mặt tọa độ",
                    subtitle = "Cho điểm M(x₀; y₀; z₀) trong hệ trục Oxyz",
                    accentHex = 0xFF6366F1,
                    mnemonic = "Chiếu lên đâu giữ nguyên tọa độ đó, còn lại = 0 • Đối xứng qua đâu giữ nguyên đó, còn lại đổi dấu",
                    formulas = listOf(
                        MathFormulaItem("g12_c2_p1", "Hình chiếu của M lên mặt tọa độ", "Lên (Oxy): (x₀; y₀; 0) • (Oyz): (0; y₀; z₀) • (Oxz): (x₀; 0; z₀)"),
                        MathFormulaItem("g12_c2_p2", "Hình chiếu của M lên trục tọa độ", "Lên Ox: (x₀; 0; 0) • Oy: (0; y₀; 0) • Oz: (0; 0; z₀)"),
                        MathFormulaItem("g12_c2_p3", "Đối xứng của M qua mặt tọa độ", "Qua (Oxy): (x₀; y₀; −z₀) • (Oyz): (−x₀; y₀; z₀) • (Oxz): (x₀; −y₀; z₀)"),
                        MathFormulaItem("g12_c2_p4", "Đối xứng của M qua trục & gốc O", "Qua Ox: (x₀; −y₀; −z₀) • Oy: (−x₀; y₀; −z₀) • Oz: (−x₀; −y₀; z₀) • O: (−x₀; −y₀; −z₀)"),
                        MathFormulaItem("g12_c2_p5", "Khoảng cách tới mặt & trục tọa độ", "d(M,(Oxy)) = |z₀|   •   d(M,Ox) = √(y₀²+z₀²)  ;  d(M,Oy) = √(x₀²+z₀²)  ;  d(M,Oz) = √(x₀²+y₀²)")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c2_cross",
                    title = "Tích vô hướng, Tích có hướng [a⃗, b⃗] & Ứng dụng",
                    subtitle = "Góc hai vectơ, pháp tuyến, diện tích, thể tích và điều kiện đồng phẳng",
                    accentHex = 0xFFA855F7,
                    formulas = listOf(
                        MathFormulaItem("g12_c2_f5", "Tích vô hướng a⃗ · b⃗", "x₁x₂ + y₁y₂ + z₁z₂   (a⃗ ⊥ b⃗ ⇔ a⃗·b⃗ = 0)"),
                        MathFormulaItem("g12_c2_f6", "cos(a⃗, b⃗)", "(x₁x₂ + y₁y₂ + z₁z₂) / [ √(x₁²+y₁²+z₁²) · √(x₂²+y₂²+z₂²) ]"),
                        MathFormulaItem("g12_c2_f7", "Tích có hướng [a⃗, b⃗]", "( y₁z₂ − z₁y₂ ; z₁x₂ − x₁z₂ ; x₁y₂ − y₁x₂ )", note = "[a⃗, b⃗] vuông góc với cả a⃗ và b⃗"),
                        MathFormulaItem("g12_c2_f7a", "Độ dài tích có hướng & Cùng phương", "|[a⃗, b⃗]| = |a⃗|·|b⃗|·sin(a⃗, b⃗)   •   a⃗, b⃗ cùng phương ⇔ [a⃗, b⃗] = 0⃗"),
                        MathFormulaItem("g12_c2_f8", "Diện tích △ABC & Hình bình hành ABCD", "S_{△ABC} = ½ · |[AB⃗, AC⃗]|   ;   S_{ABCD} = |[AB⃗, AD⃗]|"),
                        MathFormulaItem("g12_c2_f9", "Thể tích tứ diện ABCD", "V_{ABCD} = ⅙ · |[AB⃗, AC⃗] · AD⃗|"),
                        MathFormulaItem("g12_c2_f10", "Thể tích khối hộp ABCD.A'B'C'D'", "V = |[AB⃗, AD⃗] · AA'⃗|"),
                        MathFormulaItem("g12_c2_f11", "Điều kiện đồng phẳng (3 vectơ / 4 điểm)", "[a⃗, b⃗] · c⃗ = 0   •   A, B, C, D đồng phẳng ⇔ [AB⃗, AC⃗] · AD⃗ = 0")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG III ====================
        MathChapter(
            id = "g12_c3",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương III",
            title = "Các số đặc trưng đo độ phân tán (Mẫu ghép nhóm)",
            summary = "Khoảng biến thiên, khoảng tứ phân vị, phương sai, độ lệch chuẩn theo tần số/tần suất & quy tắc biến đổi",
            accentHex = 0xFF06B6D4,
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c3_disp",
                    title = "Đo mức độ phân tán mẫu số liệu ghép nhóm",
                    subtitle = "k nhóm [a₁; a₂), …, [a_k; a_{k+1}), giá trị đại diện cᵢ, tần số mᵢ, tần suất fᵢ = mᵢ/n",
                    accentHex = 0xFF06B6D4,
                    formulas = listOf(
                        MathFormulaItem("g12_c3_f1", "Khoảng biến thiên R", "a_{k+1} − a₁", note = "Đầu mút phải nhóm cuối (m_k > 0) trừ đầu mút trái nhóm đầu (m₁ > 0)"),
                        MathFormulaItem("g12_c3_f2", "Khoảng tứ phân vị Δ_Q", "Q₃ − Q₁", note = "Đo độ phân tán 50% dữ liệu giữa, không bị ảnh hưởng bởi giá trị ngoại lệ"),
                        MathFormulaItem("g12_c3_f3", "Phương sai ghép nhóm (theo tần số mᵢ)", "s² = (1/n) ∑ mᵢ(cᵢ − x̄)²  =  (1/n) ∑ mᵢ cᵢ² − x̄²"),
                        MathFormulaItem("g12_c3_f3a", "Phương sai ghép nhóm (theo tần suất fᵢ)", "s² = ∑ fᵢ(cᵢ − x̄)²  =  ∑ fᵢ cᵢ² − x̄²   (với x̄ = ∑ fᵢ cᵢ)"),
                        MathFormulaItem("g12_c3_f4", "Độ lệch chuẩn s", "s = √(s²)", note = "Cùng đơn vị đo với số liệu; s càng nhỏ dữ liệu càng ổn định/đồng đều"),
                        MathFormulaItem("g12_c3_f5", "Hệ số biến thiên CV (so sánh 2 mẫu)", "CV = (s / |x̄|) · 100%", note = "Dùng khi hai mẫu có số trung bình hoặc đơn vị đo khác nhau"),
                        MathFormulaItem("g12_c3_f6", "Tính chất biến đổi yᵢ = k·xᵢ + b", "R_y = |k|R_x  ;  Δ_{Q,y} = |k|Δ_{Q,x}  ;  s_y = |k|s_x  ;  s_y² = k²s_x²", note = "Cộng cùng một hằng số b không làm thay đổi độ phân tán")
                    )
                )
            )
        )
    )

    val chapters: List<MathChapter> = part1Chapters + Grade12CurriculumPart2.chapters
}
