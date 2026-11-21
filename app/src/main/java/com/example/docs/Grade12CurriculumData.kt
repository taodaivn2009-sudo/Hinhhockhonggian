package com.example.docs

import com.example.engine.ShapeType

/**
 * Complete Grade 12 Mathematics formula compendium aligned with Vietnam GDPT 2018
 * (6 chapters: Function Analysis & Derivatives, 3D Vectors & Oxyz Coordinates,
 * Dispersion of Grouped Data, Antiderivatives & Integrals, Analytic Geometry in Oxyz
 * [Planes, Lines, Spheres], and Conditional Probability & Bayes' Theorem).
 */
object Grade12CurriculumData {

    val chapters: List<MathChapter> = listOf(
        // ==================== CHƯƠNG I ====================
        MathChapter(
            id = "g12_c1",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương I",
            title = "Ứng dụng đạo hàm để khảo sát & vẽ đồ thị hàm số",
            summary = "Tính đơn điệu, cực trị, GTLN – GTNN, 3 đường tiệm cận (TCĐ, TCN, TCX) & hàm bậc ba/phân thức",
            accentHex = 0xFF6366F1,
            quickActions = listOf(
                DocQuickAction.Plot2D("Khảo sát y = x³ − 3x + 1", "x^3 - 3*x + 1"),
                DocQuickAction.Plot2D("Tiệm cận xiên (x²−2x+2)/(x−1)", "(x^2 - 2*x + 2)/(x - 1)")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c1_mono_ext",
                    title = "Tính đơn điệu, Cực trị & GTLN – GTNN",
                    subtitle = "Liên hệ dấu f'(x), f''(x) với biến thiên và điểm cực trị",
                    accentHex = 0xFF6366F1,
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f1", "Hàm đồng biến / nghịch biến trên K", "f'(x) ≥ 0 (đồng biến) • f'(x) ≤ 0 (nghịch biến), dấu = tại hữu hạn điểm"),
                        MathFormulaItem("g12_c1_f2", "Dấu hiệu 1 tìm cực trị", "f'(x) đổi dấu từ (+) → (−) qua x₀: Cực đại • (−) → (+): Cực tiểu"),
                        MathFormulaItem("g12_c1_f3", "Dấu hiệu 2 (f'(x₀) = 0)", "f''(x₀) < 0 ⇒ Cực đại   ;   f''(x₀) > 0 ⇒ Cực tiểu"),
                        MathFormulaItem("g12_c1_f4", "GTLN – GTNN trên đoạn [a; b]", "So sánh f(a), f(b) và các giá trị f(xᵢ) tại điểm f'(xᵢ) = 0 ∈ (a; b)")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c1_asymp",
                    title = "Đường tiệm cận của đồ thị hàm số",
                    subtitle = "Tiệm cận đứng, tiệm cận ngang và tiệm cận xiên (GDPT 2018)",
                    accentHex = 0xFF818CF8,
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f5", "Tiệm cận đứng (TCĐ) x = x₀", "lim_{x→x₀⁺} f(x) = ±∞   hoặc   lim_{x→x₀⁻} f(x) = ±∞"),
                        MathFormulaItem("g12_c1_f6", "Tiệm cận ngang (TCN) y = y₀", "lim_{x→+∞} f(x) = y₀   hoặc   lim_{x→−∞} f(x) = y₀"),
                        MathFormulaItem("g12_c1_f7", "Tiệm cận xiên (TCX) y = ax + b", "a = lim_{x→±∞} [f(x)/x] (a ≠ 0)   ;   b = lim_{x→±∞} [f(x) − ax]"),
                        MathFormulaItem("g12_c1_f8", "Hàm nhất biến y = (ax + b)/(cx + d)", "y' = (ad − bc)/(cx + d)² • TCĐ: x = −d/c • TCN: y = a/c • Tâm I(−d/c; a/c)")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c1_cubic",
                    title = "Hàm bậc ba y = ax³ + bx² + cx + d (a ≠ 0)",
                    subtitle = "Điều kiện có cực trị, điểm uốn và tâm đối xứng",
                    accentHex = 0xFFA855F7,
                    formulas = listOf(
                        MathFormulaItem("g12_c1_f9", "Đạo hàm y'", "3ax² + 2bx + c   (Δ' = b² − 3ac)"),
                        MathFormulaItem("g12_c1_f10", "Có 2 cực trị / Không có cực trị", "b² − 3ac > 0 (2 cực trị)   •   b² − 3ac ≤ 0 (đơn điệu trên ℝ)"),
                        MathFormulaItem("g12_c1_f11", "Tâm đối xứng (Điểm uốn U)", "x_U = −b / (3a)   (nghiệm của y'' = 6ax + 2b = 0)")
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
            summary = "Quy tắc hình hộp, tọa độ điểm & vectơ 3D, tích vô hướng, tích có hướng & ứng dụng",
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
                        MathFormulaItem("g12_c2_f1", "Quy tắc hình hộp", "AB⃗ + AD⃗ + AA'⃗ = AC'⃗"),
                        MathFormulaItem("g12_c2_f2", "Vectơ AB⃗ & Độ dài AB", "AB⃗ = (x_B−x_A; y_B−y_A; z_B−z_A)  ;  AB = √[(x_B−x_A)²+(y_B−y_A)²+(z_B−z_A)²]"),
                        MathFormulaItem("g12_c2_f3", "Trung điểm I & Trọng tâm △ABC", "I((x_A+x_B)/2; …)   ;   G((x_A+x_B+x_C)/3; …)"),
                        MathFormulaItem("g12_c2_f4", "Trọng tâm tứ diện ABCD", "G_td( (x_A+x_B+x_C+x_D)/4 ; … )"),
                        MathFormulaItem("g12_c2_f5", "Tích vô hướng a⃗ · b⃗", "x₁x₂ + y₁y₂ + z₁z₂   (a⃗ ⊥ b⃗ ⇔ a⃗·b⃗ = 0)"),
                        MathFormulaItem("g12_c2_f6", "cos(a⃗, b⃗)", "(x₁x₂ + y₁y₂ + z₁z₂) / [ √(x₁²+y₁²+z₁²) · √(x₂²+y₂²+z₂²) ]")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c2_cross",
                    title = "Tích có hướng [a⃗, b⃗] & Ứng dụng hình học",
                    subtitle = "Tính vectơ pháp tuyến, diện tích tam giác và thể tích khối đa diện",
                    accentHex = 0xFFA855F7,
                    formulas = listOf(
                        MathFormulaItem("g12_c2_f7", "[a⃗, b⃗]", "( y₁z₂ − z₁y₂ ; z₁x₂ − x₁z₂ ; x₁y₂ − y₁x₂ )"),
                        MathFormulaItem("g12_c2_f8", "Diện tích △ABC", "S_{ABC} = ½ · |[AB⃗, AC⃗]|"),
                        MathFormulaItem("g12_c2_f9", "Thể tích tứ diện ABCD", "V_{ABCD} = ⅙ · |[AB⃗, AC⃗] · AD⃗|"),
                        MathFormulaItem("g12_c2_f10", "Thể tích khối hộp ABCD.A'B'C'D'", "V = |[AB⃗, AD⃗] · AA'⃗|"),
                        MathFormulaItem("g12_c2_f11", "Điều kiện 3 vectơ đồng phẳng", "[a⃗, b⃗] · c⃗ = 0")
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
            summary = "Khoảng biến thiên, khoảng tứ phân vị, phương sai & độ lệch chuẩn mẫu số liệu ghép nhóm",
            accentHex = 0xFF06B6D4,
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c3_disp",
                    title = "Đo mức độ phân tán mẫu số liệu ghép nhóm",
                    subtitle = "Mẫu có k nhóm [a₁; a₂), …, [a_k; a_{k+1}), giá trị đại diện c_i, tần số m_i, tổng n",
                    accentHex = 0xFF06B6D4,
                    formulas = listOf(
                        MathFormulaItem("g12_c3_f1", "Khoảng biến thiên R", "a_{k+1} − a₁", note = "Đầu mút phải nhóm cuối trừ đầu mút trái nhóm đầu"),
                        MathFormulaItem("g12_c3_f2", "Khoảng tứ phân vị Δ_Q", "Q₃ − Q₁", note = "Không bị ảnh hưởng bởi giá trị ngoại lệ"),
                        MathFormulaItem("g12_c3_f3", "Phương sai ghép nhóm s²", "(1/n) ∑ m_i(c_i − x̄)²  =  (1/n) ∑ m_i c_i² − x̄²"),
                        MathFormulaItem("g12_c3_f4", "Độ lệch chuẩn s", "√(s²)", note = "Cùng đơn vị đo với số liệu; s càng nhỏ dữ liệu càng ổn định")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG IV ====================
        MathChapter(
            id = "g12_c4",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương IV",
            title = "Nguyên hàm và Tích phân",
            summary = "Bảng nguyên hàm đầy đủ, tích phân Newton–Leibniz, diện tích hình phẳng & thể tích tròn xoay",
            accentHex = 0xFF4F46E5,
            quickActions = listOf(
                DocQuickAction.Plot2D("Vẽ hình phẳng y = x² − 4", "x^2 - 4")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c4_anti_table",
                    title = "Bảng nguyên hàm cơ bản & Mở rộng (ax + b)",
                    subtitle = "C ∈ ℝ là hằng số tùy ý; với hàm hợp tuyến tính ax + b nhớ nhân thêm 1/a",
                    accentHex = 0xFF4F46E5,
                    mnemonic = "Nguyên hàm ngược với Đạo hàm • Thay x bằng (ax + b) luôn chia hệ số a",
                    formulas = listOf(
                        MathFormulaItem("g12_c4_f1", "∫ k dx", "kx + C"),
                        MathFormulaItem("g12_c4_f2", "∫ x^α dx", "x^{α+1} / (α + 1) + C", condition = "α ≠ −1"),
                        MathFormulaItem("g12_c4_f3", "∫ (1 / x) dx", "ln|x| + C", condition = "x ≠ 0"),
                        MathFormulaItem("g12_c4_f4", "∫ (1 / (ax + b)) dx", "(1/a) · ln|ax + b| + C", condition = "a ≠ 0"),
                        MathFormulaItem("g12_c4_f5", "∫ e^x dx   |   ∫ e^{ax+b} dx", "e^x + C   |   (1/a)·e^{ax+b} + C"),
                        MathFormulaItem("g12_c4_f6", "∫ a^x dx", "a^x / ln a + C", condition = "0 < a ≠ 1"),
                        MathFormulaItem("g12_c4_f7", "∫ sin x dx   |   ∫ cos x dx", "−cos x + C   |   sin x + C"),
                        MathFormulaItem("g12_c4_f8", "∫ (1 / cos²x) dx", "tan x + C"),
                        MathFormulaItem("g12_c4_f9", "∫ (1 / sin²x) dx", "−cot x + C")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c4_integral",
                    title = "Tích phân xác định & Tính chất",
                    subtitle = "Công thức Newton – Leibniz với F(x) là một nguyên hàm của f(x)",
                    accentHex = 0xFF6366F1,
                    formulas = listOf(
                        MathFormulaItem("g12_c4_f10", "∫_a^b f(x) dx", "F(b) − F(a)"),
                        MathFormulaItem("g12_c4_f11", "Đảo cận & Chèn cận", "∫_a^b f = −∫_b^a f   ;   ∫_a^b f = ∫_a^c f + ∫_c^b f"),
                        MathFormulaItem("g12_c4_f12", "Tính tuyến tính", "∫_a^b [k·f(x) ± m·g(x)] dx = k∫_a^b f(x)dx ± m∫_a^b g(x)dx")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c4_apps",
                    title = "Ứng dụng hình học của tích phân",
                    subtitle = "Diện tích hình phẳng, thể tích vật thể và khối tròn xoay quanh trục Ox",
                    accentHex = 0xFF3B82F6,
                    formulas = listOf(
                        MathFormulaItem("g12_c4_f13", "Diện tích phẳng (giữa y = f(x) và Ox)", "S = ∫_a^b |f(x)| dx"),
                        MathFormulaItem("g12_c4_f14", "Diện tích phẳng (giữa 2 đồ thị f, g)", "S = ∫_a^b |f(x) − g(x)| dx"),
                        MathFormulaItem("g12_c4_f15", "Thể tích vật thể có thiết diện S(x)", "V = ∫_a^b S(x) dx"),
                        MathFormulaItem("g12_c4_f16", "Thể tích khối tròn xoay quanh Ox", "V = π · ∫_a^b [f(x)]² dx")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG V ====================
        MathChapter(
            id = "g12_c5",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương V",
            title = "Phương trình Mặt phẳng, Đường thẳng & Mặt cầu",
            summary = "Mặt phẳng, đường thẳng Oxyz, công thức 3 loại góc, khoảng cách & mặt cầu",
            accentHex = 0xFF7C3AED,
            quickActions = listOf(
                DocQuickAction.Open3D("Mở 3D Hình cầu Oxyz", ShapeType.SPHERE, showAxes = true),
                DocQuickAction.Open3D("Mở 3D Chóp Oxyz", ShapeType.PYRAMID4_SA, showAxes = true)
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c5_plane",
                    title = "Phương trình mặt phẳng (P)",
                    subtitle = "Qua M₀(x₀; y₀; z₀) có VTPT n⃗(A; B; C) ≠ 0⃗",
                    accentHex = 0xFF7C3AED,
                    formulas = listOf(
                        MathFormulaItem("g12_c5_f1", "PT tổng quát (P)", "A(x−x₀) + B(y−y₀) + C(z−z₀) = 0  ⇔  Ax + By + Cz + D = 0"),
                        MathFormulaItem("g12_c5_f2", "PT mặt phẳng đoạn chắn", "x/a + y/b + z/c = 1", condition = "abc ≠ 0", note = "Qua A(a;0;0), B(0;b;0), C(0;0;c)"),
                        MathFormulaItem("g12_c5_f3", "Khoảng cách d(M; (P))", "|Ax_M + By_M + Cz_M + D| / √(A² + B² + C²)"),
                        MathFormulaItem("g12_c5_f4", "Khoảng cách 2 mặt phẳng song song", "|D₁ − D₂| / √(A² + B² + C²)", note = "Đồng nhất bộ hệ số (A; B; C)")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c5_line_angles",
                    title = "Đường thẳng & 3 công thức Góc trong Oxyz",
                    subtitle = "Đường thẳng Δ qua M₀(x₀; y₀; z₀) có VTCP u⃗(a; b; c)",
                    accentHex = 0xFF8B5CF6,
                    mnemonic = "Đường–Đường và Mặt–Mặt dùng COS • Đường–Mặt (khác loại) dùng SIN",
                    formulas = listOf(
                        MathFormulaItem("g12_c5_f5", "PT tham số Δ", "x = x₀ + at  ;  y = y₀ + bt  ;  z = z₀ + ct  (t ∈ ℝ)"),
                        MathFormulaItem("g12_c5_f6", "PT chính tắc Δ", "(x − x₀)/a = (y − y₀)/b = (z − z₀)/c", condition = "abc ≠ 0"),
                        MathFormulaItem("g12_c5_f7", "Góc giữa 2 đường thẳng", "cos(Δ₁, Δ₂) = |u₁⃗ · u₂⃗| / (|u₁⃗| · |u₂⃗|)"),
                        MathFormulaItem("g12_c5_f8", "Góc giữa đường thẳng & mặt phẳng", "sin(Δ, (P)) = |u⃗ · n⃗| / (|u⃗| · |n⃗|)"),
                        MathFormulaItem("g12_c5_f9", "Góc giữa 2 mặt phẳng", "cos((P), (Q)) = |n₁⃗ · n₂⃗| / (|n₁⃗| · |n₂⃗|)"),
                        MathFormulaItem("g12_c5_f10", "Khoảng cách d(M; Δ)", "|[M₀M⃗, u⃗]| / |u⃗|")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c5_sphere",
                    title = "Phương trình mặt cầu (S)",
                    subtitle = "Tâm I(a; b; c), bán kính R và vị trí tương đối với mặt phẳng",
                    accentHex = 0xFFA855F7,
                    formulas = listOf(
                        MathFormulaItem("g12_c5_f11", "PT chính tắc (S)", "(x − a)² + (y − b)² + (z − c)² = R²"),
                        MathFormulaItem("g12_c5_f12", "PT khai triển (S)", "x² + y² + z² − 2ax − 2by − 2cz + d = 0", condition = "a² + b² + c² − d > 0", note = "R = √(a² + b² + c² − d)"),
                        MathFormulaItem("g12_c5_f13", "Mặt phẳng (P) cắt (S)", "Bán kính đường tròn giao tuyến r = √(R² − d²)", condition = "d = d(I; (P)) < R"),
                        MathFormulaItem("g12_c5_f14", "Diện tích & Thể tích mặt cầu", "S = 4πR²   ;   V = (4/3)πR³")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG VI ====================
        MathChapter(
            id = "g12_c6",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương VI",
            title = "Xác suất có điều kiện & Công thức Bayes",
            summary = "Xác suất có điều kiện P(A|B), quy tắc nhân, công thức xác suất toàn phần & Bayes",
            accentHex = 0xFF9333EA,
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c6_bayes",
                    title = "Xác suất có điều kiện, Toàn phần & Bayes",
                    subtitle = "Trọng tâm mới của chương trình Toán 12 GDPT 2018",
                    accentHex = 0xFF9333EA,
                    mnemonic = "Vẽ sơ đồ cây 2 nhánh (B và B̄) → Nhân dọc theo nhánh, cộng ngang các nhánh cùng kết quả A",
                    formulas = listOf(
                        MathFormulaItem("g12_c6_f1", "Xác suất có điều kiện P(A|B)", "P(A ∩ B) / P(B)", condition = "P(B) > 0", note = "Xác suất xảy ra A khi biết B đã xảy ra"),
                        MathFormulaItem("g12_c6_f2", "Công thức nhân xác suất", "P(A ∩ B) = P(B) · P(A|B) = P(A) · P(B|A)"),
                        MathFormulaItem("g12_c6_f3", "Công thức xác suất toàn phần", "P(A) = P(B)·P(A|B) + P(B̄)·P(A|B̄)", condition = "0 < P(B) < 1"),
                        MathFormulaItem("g12_c6_f4", "Công thức Bayes", "P(B|A) = [ P(B) · P(A|B) ] / [ P(B)·P(A|B) + P(B̄)·P(A|B̄) ]", condition = "P(A) > 0"),
                        MathFormulaItem("g12_c6_f5", "Dạng rút gọn Bayes", "P(B|A) = [ P(B) · P(A|B) ] / P(A)")
                    )
                )
            )
        )
    )
}
