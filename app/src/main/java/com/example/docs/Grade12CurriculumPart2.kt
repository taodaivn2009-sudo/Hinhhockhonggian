package com.example.docs

import com.example.engine.ShapeType

/**
 * Exhaustive Grade 12 Mathematics formula compendium aligned with Vietnam GDPT 2018
 * (Part 2: Chapters IV–VI — Antiderivatives & Integrals, Analytic Geometry Oxyz,
 * and Conditional Probability & Bayes' Theorem).
 */
internal object Grade12CurriculumPart2 {

    val chapters: List<MathChapter> = listOf(
        // ==================== CHƯƠNG IV ====================
        MathChapter(
            id = "g12_c4",
            grade = MathGrade.GRADE_12,
            chapterNumber = "Chương IV",
            title = "Nguyên hàm và Tích phân",
            summary = "Bảng nguyên hàm đầy đủ + (ax+b), đổi biến, từng phần, tích phân đối xứng, diện tích, thể tích & vật lý",
            accentHex = 0xFF4F46E5,
            quickActions = listOf(
                DocQuickAction.Plot2D("Vẽ hình phẳng y = x² − 4", "x^2 - 4"),
                DocQuickAction.Plot2D("Vẽ hàm chẵn y = cos(x)", "cos(x)")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c4_anti_table",
                    title = "Bảng nguyên hàm cơ bản & Mở rộng (ax + b)",
                    subtitle = "C ∈ ℝ là hằng số tùy ý; thay x bằng (ax + b) luôn nhân thêm 1/a (a ≠ 0)",
                    accentHex = 0xFF4F46E5,
                    mnemonic = "Nguyên hàm ngược Đạo hàm • Hàm hợp tuyến tính (ax + b) luôn chia cho hệ số a",
                    formulas = listOf(
                        MathFormulaItem("g12_c4_f1", "∫ k dx   |   ∫ 0 dx", "kx + C   |   C"),
                        MathFormulaItem("g12_c4_f2", "∫ x^α dx   |   ∫ (ax+b)^α dx", "x^{α+1}/(α+1) + C   |   (1/a)·(ax+b)^{α+1}/(α+1) + C", condition = "α ≠ −1"),
                        MathFormulaItem("g12_c4_f3", "∫ (1 / x) dx", "ln|x| + C", condition = "x ≠ 0"),
                        MathFormulaItem("g12_c4_f4", "∫ (1 / (ax + b)) dx", "(1/a) · ln|ax + b| + C", condition = "a ≠ 0"),
                        MathFormulaItem("g12_c4_f4a", "∫ (1 / x²) dx   |   ∫ 1/(ax+b)² dx", "−1/x + C   |   −1 / [a(ax + b)] + C"),
                        MathFormulaItem("g12_c4_f4b", "∫ (1 / √x) dx   |   ∫ 1/√(ax+b) dx", "2√x + C   |   (2/a)·√(ax + b) + C"),
                        MathFormulaItem("g12_c4_f5", "∫ e^x dx   |   ∫ e^{ax+b} dx", "e^x + C   |   (1/a)·e^{ax+b} + C"),
                        MathFormulaItem("g12_c4_f6", "∫ a^x dx   |   ∫ a^{kx+b} dx", "a^x / ln a + C   |   a^{kx+b} / (k·ln a) + C", condition = "0 < a ≠ 1"),
                        MathFormulaItem("g12_c4_f7", "∫ sin x dx   |   ∫ sin(ax+b) dx", "−cos x + C   |   −(1/a)·cos(ax + b) + C"),
                        MathFormulaItem("g12_c4_f7a", "∫ cos x dx   |   ∫ cos(ax+b) dx", "sin x + C   |   (1/a)·sin(ax + b) + C"),
                        MathFormulaItem("g12_c4_f8", "∫ (1 / cos²x) dx   |   ∫ 1/cos²(ax+b) dx", "tan x + C   |   (1/a)·tan(ax + b) + C"),
                        MathFormulaItem("g12_c4_f9", "∫ (1 / sin²x) dx   |   ∫ 1/sin²(ax+b) dx", "−cot x + C   |   −(1/a)·cot(ax + b) + C")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c4_anti_adv",
                    title = "Nguyên hàm phân thức, lượng giác & logarit thường gặp",
                    subtitle = "Các công thức giải nhanh hay xuất hiện trong đề thi THPT",
                    accentHex = 0xFF4338CA,
                    formulas = listOf(
                        MathFormulaItem("g12_c4_a1", "∫ 1/(x² − a²) dx", "(1 / (2a)) · ln|(x − a)/(x + a)| + C", condition = "a ≠ 0"),
                        MathFormulaItem("g12_c4_a2", "∫ 1/[(x − x₁)(x − x₂)] dx", "(1 / (x₁ − x₂)) · ln|(x − x₁)/(x − x₂)| + C", condition = "x₁ ≠ x₂"),
                        MathFormulaItem("g12_c4_a3", "∫ 1/(x² + a²) dx", "(1/a) · arctan(x/a) + C", condition = "a ≠ 0", note = "Với tích phân xác định đặt x = a·tan t"),
                        MathFormulaItem("g12_c4_a4", "∫ 1/√(a² − x²) dx", "arcsin(x/a) + C", condition = "a > 0, |x| < a", note = "Với tích phân xác định đặt x = a·sin t"),
                        MathFormulaItem("g12_c4_a5", "∫ tan x dx   |   ∫ cot x dx", "−ln|cos x| + C   |   ln|sin x| + C"),
                        MathFormulaItem("g12_c4_a6", "∫ ln x dx", "x·ln x − x + C", condition = "x > 0")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c4_integral",
                    title = "Tích phân xác định, Phương pháp tính & Tích phân đối xứng",
                    subtitle = "Newton – Leibniz, đổi biến số, từng phần và các tính chất đặc biệt",
                    accentHex = 0xFF6366F1,
                    mnemonic = "Thứ tự ưu tiên đặt u khi Từng phần: Nhất Lô – Nhì Đa – Tam Lượng – Tứ Mũ",
                    formulas = listOf(
                        MathFormulaItem("g12_c4_f10", "Công thức Newton – Leibniz", "∫_a^b f(x) dx = F(b) − F(a)   (với F'(x) = f(x))"),
                        MathFormulaItem("g12_c4_f11", "Đảo cận, Chèn cận & Cận trùng", "∫_a^a f = 0  ;  ∫_a^b f = −∫_b^a f  ;  ∫_a^b f = ∫_a^c f + ∫_c^b f"),
                        MathFormulaItem("g12_c4_f12", "Tính tuyến tính & BĐT tích phân", "∫_a^b (kf ± mg) = k∫_a^b f ± m∫_a^b g   •   f ≥ g trên [a;b] ⇒ ∫_a^b f ≥ ∫_a^b g"),
                        MathFormulaItem("g12_c4_f12a", "Phương pháp đổi biến số", "Đặt u = u(x) ⇒ du = u'(x)dx • Nhớ đổi cận: x = a → u(a), x = b → u(b)"),
                        MathFormulaItem("g12_c4_f12b", "Phương pháp tích phân từng phần", "∫_a^b u dv = [u·v]_a^b − ∫_a^b v du"),
                        MathFormulaItem("g12_c4_f12c", "Tích phân hàm chẵn / lẻ trên [−a; a]", "f lẻ ⇒ ∫_{−a}^a f(x)dx = 0   •   f chẵn ⇒ ∫_{−a}^a f(x)dx = 2∫_0^a f(x)dx"),
                        MathFormulaItem("g12_c4_f12d", "Tích phân hàm chẵn chia mũ", "∫_{−a}^a [ f(x) / (b^x + 1) ] dx = ∫_0^a f(x) dx", condition = "f chẵn, b > 0"),
                        MathFormulaItem("g12_c4_f12e", "Tính chất đổi biến t = a + b − x", "∫_a^b f(x) dx = ∫_a^b f(a + b − x) dx", note = "Rất mạnh với tích phân lượng giác cận [0; π/2] hoặc [0; π]")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c4_apps",
                    title = "Ứng dụng Hình học & Vật lý của Tích phân",
                    subtitle = "Diện tích hình phẳng, công thức nhanh Parabol/Elip, thể tích tròn xoay & chuyển động",
                    accentHex = 0xFF3B82F6,
                    formulas = listOf(
                        MathFormulaItem("g12_c4_f13", "Diện tích phẳng (giữa y = f(x) và Ox)", "S = ∫_a^b |f(x)| dx"),
                        MathFormulaItem("g12_c4_f14", "Diện tích phẳng (giữa 2 đồ thị f, g)", "S = ∫_a^b |f(x) − g(x)| dx", note = "Giải f(x) = g(x) tìm giao điểm để tách khoảng phá dấu trị tuyệt đối"),
                        MathFormulaItem("g12_c4_f14a", "Công thức nhanh diện tích Parabol & Elip", "Parabol: S = (2/3)·b·h = |a|·|x₁ − x₂|³ / 6   •   Elip: S = πab", note = "b: bề rộng đáy dây cung, h: chiều cao đỉnh Parabol"),
                        MathFormulaItem("g12_c4_f15", "Thể tích vật thể có thiết diện S(x)", "V = ∫_a^b S(x) dx"),
                        MathFormulaItem("g12_c4_f16", "Thể tích khối tròn xoay quanh Ox", "V = π · ∫_a^b [f(x)]² dx"),
                        MathFormulaItem("g12_c4_f16a", "Thể tích hình vành khăn quanh Ox", "V = π · ∫_a^b | f²(x) − g²(x) | dx", condition = "f(x) và g(x) cùng dấu trên [a; b]"),
                        MathFormulaItem("g12_c4_f17", "Ứng dụng Vật lý (Chuyển động thẳng)", "v(t) = v(t₀) + ∫_{t₀}^t a(τ) dτ   •   Quãng đường s = ∫_{t₁}^{t₂} |v(t)| dt")
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
            summary = "Mặt phẳng, đường thẳng, vị trí tương đối, 3 công thức góc, khoảng cách 2 đường chéo nhau & mặt cầu",
            accentHex = 0xFF7C3AED,
            quickActions = listOf(
                DocQuickAction.Open3D("Mở 3D Hình cầu Oxyz", ShapeType.SPHERE, showAxes = true),
                DocQuickAction.Open3D("Mở 3D Chóp Oxyz", ShapeType.PYRAMID4_SA, showAxes = true)
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c5_plane",
                    title = "Phương trình mặt phẳng (P) & Hình chiếu điểm",
                    subtitle = "Qua M₀(x₀; y₀; z₀) có VTPT n⃗(A; B; C) ≠ 0⃗",
                    accentHex = 0xFF7C3AED,
                    formulas = listOf(
                        MathFormulaItem("g12_c5_f1", "PT tổng quát (P)", "A(x−x₀) + B(y−y₀) + C(z−z₀) = 0  ⇔  Ax + By + Cz + D = 0"),
                        MathFormulaItem("g12_c5_f2", "PT mặt phẳng đoạn chắn", "x/a + y/b + z/c = 1", condition = "abc ≠ 0", note = "Qua A(a;0;0), B(0;b;0), C(0;0;c)"),
                        MathFormulaItem("g12_c5_f2a", "PT 3 mặt phẳng tọa độ", "(Oxy): z = 0  •  (Oyz): x = 0  •  (Oxz): y = 0"),
                        MathFormulaItem("g12_c5_f2b", "Vị trí tương đối 2 mặt phẳng (P₁), (P₂)", "(P₁) ∥ (P₂): A₁/A₂ = B₁/B₂ = C₁/C₂ ≠ D₁/D₂   •   (P₁) ⊥ (P₂) ⇔ n₁⃗·n₂⃗ = 0"),
                        MathFormulaItem("g12_c5_f3", "Khoảng cách d(M; (P))", "|Ax_M + By_M + Cz_M + D| / √(A² + B² + C²)"),
                        MathFormulaItem("g12_c5_f4", "Khoảng cách 2 mặt phẳng song song", "|D₁ − D₂| / √(A² + B² + C²)", note = "Đồng nhất bộ hệ số (A; B; C) trước khi áp dụng"),
                        MathFormulaItem("g12_c5_f4a", "Hình chiếu H của M lên (P) & Đối xứng M'", "H = M + t·n⃗  với  t = −(Ax_M + By_M + Cz_M + D)/(A²+B²+C²)  •  M' = 2H − M")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c5_line_angles",
                    title = "Đường thẳng, Vị trí tương đối, Góc & Khoảng cách Oxyz",
                    subtitle = "Đường thẳng Δ qua M₀(x₀; y₀; z₀) có VTCP u⃗(a; b; c)",
                    accentHex = 0xFF8B5CF6,
                    mnemonic = "Đường–Đường và Mặt–Mặt dùng COS • Đường–Mặt (khác loại) dùng SIN",
                    formulas = listOf(
                        MathFormulaItem("g12_c5_f5", "PT tham số Δ", "x = x₀ + at  ;  y = y₀ + bt  ;  z = z₀ + ct  (t ∈ ℝ)"),
                        MathFormulaItem("g12_c5_f6", "PT chính tắc Δ", "(x − x₀)/a = (y − y₀)/b = (z − z₀)/c", condition = "abc ≠ 0"),
                        MathFormulaItem("g12_c5_f6a", "Vị trí tương đối Δ và (P)", "Cắt: u⃗·n⃗ ≠ 0  •  Song song: u⃗·n⃗ = 0, M₀ ∉ (P)  •  Δ ⊂ (P): u⃗·n⃗ = 0, M₀ ∈ (P)"),
                        MathFormulaItem("g12_c5_f6b", "Vị trí tương đối 2 đường thẳng Δ₁, Δ₂", "Chéo nhau ⇔ [u₁⃗, u₂⃗] · M₁M₂⃗ ≠ 0   •   Cắt nhau ⇔ [u₁⃗, u₂⃗] ≠ 0⃗ và [u₁⃗, u₂⃗] · M₁M₂⃗ = 0"),
                        MathFormulaItem("g12_c5_f7", "Góc giữa 2 đường thẳng", "cos(Δ₁, Δ₂) = |u₁⃗ · u₂⃗| / (|u₁⃗| · |u₂⃗|)"),
                        MathFormulaItem("g12_c5_f8", "Góc giữa đường thẳng & mặt phẳng", "sin(Δ, (P)) = |u⃗ · n⃗| / (|u⃗| · |n⃗|)"),
                        MathFormulaItem("g12_c5_f9", "Góc giữa 2 mặt phẳng", "cos((P), (Q)) = |n₁⃗ · n₂⃗| / (|n₁⃗| · |n₂⃗|)"),
                        MathFormulaItem("g12_c5_f10", "Khoảng cách điểm tới đường thẳng d(M; Δ)", "|[M₀M⃗, u⃗]| / |u⃗|"),
                        MathFormulaItem("g12_c5_f10a", "Khoảng cách 2 đường thẳng song song", "d(Δ₁, Δ₂) = |[M₁M₂⃗, u₁⃗]| / |u₁⃗|", note = "M₁ ∈ Δ₁, M₂ ∈ Δ₂"),
                        MathFormulaItem("g12_c5_f10b", "Khoảng cách 2 đường thẳng chéo nhau", "d(Δ₁, Δ₂) = |[u₁⃗, u₂⃗] · M₁M₂⃗| / |[u₁⃗, u₂⃗]|", note = "Công thức vàng Oxyz")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c5_sphere",
                    title = "Phương trình mặt cầu (S) & Tương giao",
                    subtitle = "Tâm I(a; b; c), bán kính R và quan hệ với mặt phẳng, đường thẳng",
                    accentHex = 0xFFA855F7,
                    formulas = listOf(
                        MathFormulaItem("g12_c5_f11", "PT chính tắc (S)", "(x − a)² + (y − b)² + (z − c)² = R²"),
                        MathFormulaItem("g12_c5_f12", "PT khai triển (S)", "x² + y² + z² − 2ax − 2by − 2cz + d = 0", condition = "a² + b² + c² − d > 0", note = "Tâm I(a; b; c), R = √(a² + b² + c² − d)"),
                        MathFormulaItem("g12_c5_f12a", "Mặt cầu đường kính AB", "Tâm I = (A + B)/2   ;   R = AB / 2"),
                        MathFormulaItem("g12_c5_f13", "Mặt phẳng (P) cắt / tiếp xúc (S)", "d < R ⇒ đường tròn giao tuyến r = √(R² − d²)   •   d = R ⇒ (P) tiếp xúc (S)", note = "Với d = d(I; (P)); d > R thì không cắt"),
                        MathFormulaItem("g12_c5_f13a", "Mặt phẳng tiếp diện tại M₀ ∈ (S)", "(x₀ − a)(x − x₀) + (y₀ − b)(y − y₀) + (z₀ − c)(z − z₀) = 0", note = "Đi qua M₀ và nhận IM₀⃗ làm VTPT"),
                        MathFormulaItem("g12_c5_f13b", "Đường thẳng Δ cắt (S) theo dây cung AB", "AB = 2·√(R² − d²)   (với d = d(I; Δ) < R)"),
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
            summary = "Xác suất có điều kiện, quy tắc nhân chuỗi, xác suất toàn phần tổng quát, Bayes & bài toán xét nghiệm y khoa",
            accentHex = 0xFF9333EA,
            groups = listOf(
                MathFormulaGroup(
                    id = "g12_c6_cond",
                    title = "Xác suất có điều kiện & Quy tắc nhân",
                    subtitle = "Tính chất cơ bản của P(A|B) và quy tắc nhân nhiều biến cố",
                    accentHex = 0xFF9333EA,
                    formulas = listOf(
                        MathFormulaItem("g12_c6_f1", "Xác suất có điều kiện P(A|B)", "P(A ∩ B) / P(B)", condition = "P(B) > 0", note = "Xác suất xảy ra A khi biết B đã xảy ra"),
                        MathFormulaItem("g12_c6_f1a", "Biến cố đối có điều kiện", "P(Ā | B) = 1 − P(A | B)"),
                        MathFormulaItem("g12_c6_f1b", "Điều kiện độc lập theo P(A|B)", "A, B độc lập  ⇔  P(A | B) = P(A)  (với P(B) > 0)"),
                        MathFormulaItem("g12_c6_f1c", "Quy tắc cộng có điều kiện", "P(A₁ ∪ A₂ | B) = P(A₁ | B) + P(A₂ | B) − P(A₁ ∩ A₂ | B)"),
                        MathFormulaItem("g12_c6_f2", "Công thức nhân xác suất (2 biến cố)", "P(A ∩ B) = P(B) · P(A|B) = P(A) · P(B|A)"),
                        MathFormulaItem("g12_c6_f2a", "Công thức nhân chuỗi (3 biến cố)", "P(A ∩ B ∩ C) = P(A) · P(B | A) · P(C | A ∩ B)")
                    )
                ),
                MathFormulaGroup(
                    id = "g12_c6_bayes",
                    title = "Xác suất toàn phần, Công thức Bayes & Xét nghiệm y khoa",
                    subtitle = "Trọng tâm mới của chương trình Toán 12 GDPT 2018",
                    accentHex = 0xFFA855F7,
                    mnemonic = "Vẽ sơ đồ cây các nhánh nguyên nhân (Bᵢ) → Nhân dọc theo nhánh, cộng ngang các nhánh cùng kết quả A",
                    formulas = listOf(
                        MathFormulaItem("g12_c6_f3", "Công thức xác suất toàn phần (2 nhánh)", "P(A) = P(B)·P(A|B) + P(B̄)·P(A|B̄)", condition = "0 < P(B) < 1"),
                        MathFormulaItem("g12_c6_f4", "Công thức Bayes (2 nhánh)", "P(B|A) = [ P(B) · P(A|B) ] / [ P(B)·P(A|B) + P(B̄)·P(A|B̄) ]", condition = "P(A) > 0"),
                        MathFormulaItem("g12_c6_f5", "Dạng rút gọn Bayes", "P(B|A) = [ P(B) · P(A|B) ] / P(A)"),
                        MathFormulaItem("g12_c6_f6", "Xác suất toàn phần tổng quát (n nhánh)", "P(A) = ∑_{i=1}^n P(Bᵢ) · P(A | Bᵢ)", note = "B₁, …, Bₙ đôi một xung khắc và B₁ ∪ … ∪ Bₙ = Ω"),
                        MathFormulaItem("g12_c6_f7", "Công thức Bayes tổng quát (n nhánh)", "P(B_k | A) = [ P(B_k) · P(A | B_k) ] / ∑_{i=1}^n P(Bᵢ) · P(A | Bᵢ)"),
                        MathFormulaItem("g12_c6_f8", "Ứng dụng xét nghiệm y khoa", "Độ nhạy = P(+|Bệnh)  •  Độ đặc hiệu = P(−|Không bệnh)  •  Dương tính giả = 1 − Độ đặc hiệu", note = "Xác suất thực sự mắc bệnh khi có kết quả dương tính: P(Bệnh | +) tính bằng Bayes")
                    )
                )
            )
        )
    )
}
