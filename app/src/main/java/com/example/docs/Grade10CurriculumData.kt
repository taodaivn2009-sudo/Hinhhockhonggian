package com.example.docs

/**
 * Exhaustive Grade 10 Mathematics formula compendium aligned with Vietnam GDPT 2018.
 * Part 1: Chapters I – V (Logic & Sets, Linear Inequalities & Optimization,
 * Triangle Trigonometry & Special Shapes, Vectors, and Unbinned Statistics).
 */
object Grade10CurriculumData {

    private val part1Chapters: List<MathChapter> = listOf(
        // ==================== CHƯƠNG I ====================
        MathChapter(
            id = "g10_c1",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương I",
            title = "Mệnh đề và Tập hợp",
            summary = "Mệnh đề kéo theo/đảo/phản đảo, phủ định ∀/∃, phép toán tập hợp, De Morgan & sai số gần đúng",
            accentHex = 0xFF10B981,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c1_logic",
                    title = "Mệnh đề logic & Phủ định lượng từ",
                    subtitle = "Quan hệ kéo theo, đảo, phản đảo, tương đương và quy tắc phủ định ∀, ∃",
                    accentHex = 0xFF10B981,
                    mnemonic = "Phủ định ∀ thành ∃, phủ định ∃ thành ∀ và đảo ngược mệnh đề P(x)",
                    formulas = listOf(
                        MathFormulaItem("g10_c1_f1", "Mệnh đề kéo theo P ⇒ Q", "Sai duy nhất khi P đúng và Q sai", note = "P: điều kiện đủ; Q: điều kiện cần"),
                        MathFormulaItem("g10_c1_f2", "Mệnh đề tương đương P ⇔ Q", "Đúng khi P và Q cùng đúng hoặc cùng sai", note = "Điều kiện cần và đủ"),
                        MathFormulaItem("g10_c1_f3", "Mệnh đề đảo & Phản đảo", "Đảo: Q ⇒ P   •   Phản đảo: Q̄ ⇒ P̄  (tương đương P ⇒ Q)"),
                        MathFormulaItem("g10_c1_f4", "Phủ định (P ⇒ Q)", "P đúng và Q sai  (P ∧ Q̄)"),
                        MathFormulaItem("g10_c1_f5", "Phủ định (∀x ∈ X, P(x))", "∃x ∈ X, P̄(x)"),
                        MathFormulaItem("g10_c1_f6", "Phủ định (∃x ∈ X, P(x))", "∀x ∈ X, P̄(x)")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c1_sets",
                    title = "Các phép toán tập hợp & Luật De Morgan",
                    subtitle = "Giao, hợp, hiệu, phần bù và công thức đếm 2 – 3 tập hợp",
                    accentHex = 0xFF06B6D4,
                    formulas = listOf(
                        MathFormulaItem("g10_c1_f7", "A ∩ B  |  A ∪ B", "{ x | x ∈ A và x ∈ B }   |   { x | x ∈ A hoặc x ∈ B }"),
                        MathFormulaItem("g10_c1_f8", "A \\ B  |  C_E A", "{ x | x ∈ A và x ∉ B }   |   E \\ A  (khi A ⊂ E)"),
                        MathFormulaItem("g10_c1_f9", "Luật De Morgan", "C_E(A ∪ B) = C_E A ∩ C_E B   ;   C_E(A ∩ B) = C_E A ∪ C_E B"),
                        MathFormulaItem("g10_c1_f10", "Đếm 2 tập hợp n(A ∪ B)", "n(A) + n(B) − n(A ∩ B)"),
                        MathFormulaItem("g10_c1_f11", "Đếm 3 tập hợp n(A ∪ B ∪ C)", "n(A) + n(B) + n(C) − n(A∩B) − n(B∩C) − n(C∩A) + n(A∩B∩C)"),
                        MathFormulaItem("g10_c1_f12", "Số tập con của tập n phần tử", "2ⁿ tập con  •  (2ⁿ − 1) tập con khác rỗng  •  C_n^k tập con k phần tử")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c1_intervals",
                    title = "Tập hợp số chuẩn & Sai số gần đúng",
                    subtitle = "Khoảng, đoạn, nửa khoảng của ℝ và sai số tuyệt đối/tương đối",
                    accentHex = 0xFF38BDF8,
                    formulas = listOf(
                        MathFormulaItem("g10_c1_f13", "Quan hệ các tập số", "ℕ* ⊂ ℕ ⊂ ℤ ⊂ ℚ ⊂ ℝ   (I = ℝ \\ ℚ: số vô tỉ)"),
                        MathFormulaItem("g10_c1_f14", "(a; b)  |  [a; b]", "a < x < b (khoảng)   |   a ≤ x ≤ b (đoạn)"),
                        MathFormulaItem("g10_c1_f15", "[a; b)  |  (−∞; b]  |  [a; +∞)", "a ≤ x < b   |   x ≤ b   |   x ≥ a"),
                        MathFormulaItem("g10_c1_f16", "Sai số tuyệt đối Δ_a", "|ā − a| ≤ d", note = "a: số gần đúng; ā: số đúng; d: độ chính xác"),
                        MathFormulaItem("g10_c1_f17", "Sai số tương đối δ_a", "Δ_a / |a| ≤ d / |a|", note = "Thường biểu diễn theo phần trăm (%)")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG II ====================
        MathChapter(
            id = "g10_c2",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương II",
            title = "Bất phương trình & Hệ BPT bậc nhất hai ẩn",
            summary = "Biểu diễn miền nghiệm trên Oxy, vị trí hai điểm so với đường bờ, Cramer 2×2 & tối ưu tuyến tính",
            accentHex = 0xFF14B8A6,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c2_linear",
                    title = "Miền nghiệm BPT & Hệ BPT bậc nhất hai ẩn",
                    subtitle = "Quy tắc xác định nửa mặt phẳng nghiệm và điểm cùng/khác phía",
                    accentHex = 0xFF14B8A6,
                    mnemonic = "Vẽ đường bờ d: ax + by = c → Thử điểm O(0;0) nếu c ≠ 0 → Gạch nửa không thỏa",
                    formulas = listOf(
                        MathFormulaItem("g10_c2_f1", "Dạng tổng quát", "ax + by < c (hoặc ≤, >, ≥)", condition = "a² + b² ≠ 0"),
                        MathFormulaItem("g10_c2_f2", "Quy ước đường bờ d", "Dấu ≤ hoặc ≥: lấy cả bờ d (nét liền) • Dấu < hoặc >: bỏ bờ d (nét đứt)"),
                        MathFormulaItem("g10_c2_f3", "Giao điểm đường bờ với trục", "Cắt Ox tại (c/a; 0)  ;  Cắt Oy tại (0; c/b)", condition = "a, b ≠ 0"),
                        MathFormulaItem("g10_c2_f4", "2 điểm M₁, M₂ cùng phía / khác phía d", "f(M₁)·f(M₂) > 0: cùng phía  •  f(M₁)·f(M₂) < 0: khác phía", note = "f(x;y) = ax + by − c"),
                        MathFormulaItem("g10_c2_f5", "Miền nghiệm hệ BPT", "Giao các miền nghiệm của từng BPT trong hệ")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c2_opt",
                    title = "Bài toán tối ưu tuyến tính & Hệ phương trình bậc nhất 2 ẩn",
                    subtitle = "Tìm cực trị F(x; y) = ax + by trên miền đa giác và công thức Cramer",
                    accentHex = 0xFF0D9488,
                    formulas = listOf(
                        MathFormulaItem("g10_c2_f6", "Cực trị hàm mục tiêu F(x; y)", "Đạt GTLN / GTNN tại một trong các đỉnh của miền đa giác nghiệm"),
                        MathFormulaItem("g10_c2_f7", "Định thức Cramer 2×2", "D = a₁b₂ − a₂b₁   ;   D_x = c₁b₂ − c₂b₁   ;   D_y = a₁c₂ − a₂c₁"),
                        MathFormulaItem("g10_c2_f8", "Nghiệm hệ (D ≠ 0)", "x = D_x / D   ;   y = D_y / D", note = "D = 0: vô số nghiệm nếu D_x = D_y = 0, ngược lại vô nghiệm")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG III ====================
        MathChapter(
            id = "g10_c3",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương III",
            title = "Hệ thức lượng trong tam giác",
            summary = "Định lý Côsin, Sin, 5 công thức diện tích, trung tuyến, phân giác, hệ thức tam giác vuông & hình đặc biệt",
            accentHex = 0xFF0EA5E9,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c3_cos_sin",
                    title = "Định lý Côsin, Định lý Sin & Nhận dạng góc",
                    subtitle = "Tam giác ABC có 3 cạnh a, b, c; ngoại tiếp R; nội tiếp r",
                    accentHex = 0xFF0EA5E9,
                    formulas = listOf(
                        MathFormulaItem("g10_c3_f1", "Định lý Côsin", "a² = b² + c² − 2bc·cos A", note = "Tương tự cho b², c²"),
                        MathFormulaItem("g10_c3_f2", "Hệ quả tính góc", "cos A = (b² + c² − a²) / (2bc)"),
                        MathFormulaItem("g10_c3_f3", "Nhận dạng góc A", "b²+c²−a² > 0: A nhọn  •  = 0: A vuông  •  < 0: A tù"),
                        MathFormulaItem("g10_c3_f4", "Định lý Sin", "a / sin A = b / sin B = c / sin C = 2R"),
                        MathFormulaItem("g10_c3_f5", "Đường trung tuyến m_a", "m_a² = (2b² + 2c² − a²) / 4"),
                        MathFormulaItem("g10_c3_f6", "Tổng bình phương 3 trung tuyến", "m_a² + m_b² + m_c² = ¾(a² + b² + c²)"),
                        MathFormulaItem("g10_c3_f7", "Đường phân giác trong l_a", "l_a = [ 2bc · cos(A/2) ] / (b + c)", note = "Tính chất chân phân giác: DB / DC = c / b")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c3_area",
                    title = "5 công thức tính diện tích tam giác & Bán kính R, r",
                    subtitle = "Nửa chu vi p = (a + b + c)/2",
                    accentHex = 0xFF38BDF8,
                    formulas = listOf(
                        MathFormulaItem("g10_c3_f8", "S (đáy – cao)", "½ a·h_a = ½ b·h_b = ½ c·h_c"),
                        MathFormulaItem("g10_c3_f9", "S (2 cạnh – góc xen)", "½ bc·sin A = ½ ac·sin B = ½ ab·sin C"),
                        MathFormulaItem("g10_c3_f10", "S (theo R)", "(a · b · c) / (4R)   ⇒   R = abc / (4S)"),
                        MathFormulaItem("g10_c3_f11", "S (theo r)", "p · r   ⇒   r = S / p"),
                        MathFormulaItem("g10_c3_f12", "S (Heron)", "√[ p(p − a)(p − b)(p − c) ]")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c3_right_special",
                    title = "Hệ thức lượng tam giác vuông & Hình đặc biệt hay thi",
                    subtitle = "Cạnh huyền a, cạnh góc vuông b, c; đường cao h; hình chiếu b', c'",
                    accentHex = 0xFF0284C7,
                    formulas = listOf(
                        MathFormulaItem("g10_c3_f13", "Hệ thức tam giác vuông", "b² = a·b'  ;  c² = a·c'  ;  h² = b'·c'  ;  a·h = b·c"),
                        MathFormulaItem("g10_c3_f14", "Nghịch đảo đường cao vuông", "1 / h² = 1 / b² + 1 / c²"),
                        MathFormulaItem("g10_c3_f15", "Tam giác đều cạnh a", "h = a√3 / 2  ;  S = a²√3 / 4  ;  R = a√3 / 3  ;  r = a√3 / 6"),
                        MathFormulaItem("g10_c3_f16", "Tam giác vuông cân cạnh a", "Cạnh huyền = a√2  ;  S = a² / 2  ;  R = a√2 / 2"),
                        MathFormulaItem("g10_c3_f17", "Tam giác cân góc đỉnh 120° (cạnh bên a)", "Đáy = a√3  ;  h = a / 2  ;  S = a²√3 / 4"),
                        MathFormulaItem("g10_c3_f18", "Hình thoi cạnh a, góc 60°", "Đường chéo: a và a√3  ;  S = a²√3 / 2")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c3_obtuse",
                    title = "Giá trị lượng giác góc tù (0° – 180°) & Bảng góc đặc biệt",
                    subtitle = "Mở rộng giá trị lượng giác cho mọi góc trong tam giác",
                    accentHex = 0xFF22D3EE,
                    formulas = listOf(
                        MathFormulaItem("g10_c3_f19", "Góc bù nhau (180° − α)", "sin(180°−α) = sin α  ;  cos, tan, cot đổi dấu (−)"),
                        MathFormulaItem("g10_c3_f20", "sin (0°, 30°, 45°, 60°, 90°)", "0   |   1/2   |   √2/2   |   √3/2   |   1"),
                        MathFormulaItem("g10_c3_f21", "cos (0°, 30°, 45°, 60°, 90°)", "1   |   √3/2   |   √2/2   |   1/2   |   0"),
                        MathFormulaItem("g10_c3_f22", "tan (0°, 30°, 45°, 60°, 90°)", "0   |   √3/3   |   1   |   √3   |   Không XĐ")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG IV ====================
        MathChapter(
            id = "g10_c4",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương IV",
            title = "Vectơ",
            summary = "Quy tắc 3 điểm, trung điểm, trọng tâm, tâm tỉ cự, phân tích vectơ, tích vô hướng & công cơ học",
            accentHex = 0xFF22C55E,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c4_ops",
                    title = "Quy tắc cộng, trừ, trung điểm, trọng tâm & tâm tỉ cự",
                    subtitle = "Các hệ thức vectơ nền tảng dùng xuyên suốt Hình học phẳng & 3D",
                    accentHex = 0xFF22C55E,
                    mnemonic = "Cộng nối đuôi (AB + BC = AC) • Trừ chung gốc đảo ngược (AB − AC = CB)",
                    formulas = listOf(
                        MathFormulaItem("g10_c4_f1", "Quy tắc 3 điểm & Quy tắc hiệu", "AB⃗ + BC⃗ = AC⃗   ;   AB⃗ − AC⃗ = CB⃗"),
                        MathFormulaItem("g10_c4_f2", "Quy tắc hình bình hành", "AB⃗ + AD⃗ = AC⃗", note = "ABCD là hình bình hành"),
                        MathFormulaItem("g10_c4_f3", "I là trung điểm AB", "IA⃗ + IB⃗ = 0⃗   ⇔   MA⃗ + MB⃗ = 2·MI⃗"),
                        MathFormulaItem("g10_c4_f4", "G là trọng tâm △ABC", "GA⃗ + GB⃗ + GC⃗ = 0⃗   ⇔   MA⃗ + MB⃗ + MC⃗ = 3·MG⃗"),
                        MathFormulaItem("g10_c4_f5", "Đường trung bình hình thang ABCD", "MN⃗ = ½(AB⃗ + DC⃗)", note = "M, N trung điểm cạnh bên AD, BC"),
                        MathFormulaItem("g10_c4_f6", "Chia đoạn theo tỉ số k (MB⃗ = k·MA⃗)", "OM⃗ = (OB⃗ − k·OA⃗) / (1 − k)", condition = "k ≠ 1"),
                        MathFormulaItem("g10_c4_f7", "Tâm tỉ cự I (αIA⃗ + βIB⃗ + γIC⃗ = 0⃗)", "α·MA⃗ + β·MB⃗ + γ·MC⃗ = (α + β + γ)·MI⃗", condition = "α + β + γ ≠ 0"),
                        MathFormulaItem("g10_c4_f8", "Thẳng hàng & Phân tích vectơ", "A, B, C thẳng hàng ⇔ AB⃗ = k·AC⃗  •  x⃗ = m·a⃗ + n·b⃗ (duy nhất)")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c4_dot",
                    title = "Tích vô hướng, Độ dài & Ứng dụng hình học – vật lý",
                    subtitle = "Bình phương vô hướng, công thức trung tuyến & công của lực",
                    accentHex = 0xFF10B981,
                    formulas = listOf(
                        MathFormulaItem("g10_c4_f9", "Tích vô hướng a⃗ · b⃗", "|a⃗| · |b⃗| · cos(a⃗, b⃗)"),
                        MathFormulaItem("g10_c4_f10", "Điều kiện vuông góc", "a⃗ ⊥ b⃗   ⇔   a⃗ · b⃗ = 0"),
                        MathFormulaItem("g10_c4_f11", "Bình phương vô hướng & Độ dài tổng/hiệu", "(a⃗)² = |a⃗|²   ;   |a⃗ ± b⃗|² = |a⃗|² ± 2(a⃗·b⃗) + |b⃗|²"),
                        MathFormulaItem("g10_c4_f12", "Hệ thức tích vô hướng theo trung tuyến", "AB⃗ · AC⃗ = AM² − BC² / 4", note = "M là trung điểm BC"),
                        MathFormulaItem("g10_c4_f13", "Định lý hình bình hành", "AC² + BD² = 2(AB² + AD²)"),
                        MathFormulaItem("g10_c4_f14", "Bất đẳng thức tam giác vectơ", "||a⃗| − |b⃗|| ≤ |a⃗ + b⃗| ≤ |a⃗| + |b⃗|"),
                        MathFormulaItem("g10_c4_f15", "Công cơ học của lực F⃗", "A = F⃗ · d⃗ = |F⃗| · |d⃗| · cos α")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG V ====================
        MathChapter(
            id = "g10_c5",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương V",
            title = "Các số đặc trưng của mẫu số liệu không ghép nhóm",
            summary = "Số trung bình, trung vị, tứ phân vị, phương sai, độ lệch chuẩn, ngoại lệ & phép đổi tuyến tính",
            accentHex = 0xFF0D9488,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c5_center",
                    title = "Đo xu thế trung tâm",
                    subtitle = "Mẫu số liệu x₁, x₂, …, xₙ (sắp xếp không giảm) hoặc bảng tần số/tần suất",
                    accentHex = 0xFF0D9488,
                    formulas = listOf(
                        MathFormulaItem("g10_c5_f1", "Số trung bình x̄", "(x₁ + x₂ + … + xₙ) / n  =  (1/n)∑nᵢxᵢ  =  ∑fᵢxᵢ"),
                        MathFormulaItem("g10_c5_f2", "Trung vị M_e (Q₂)", "x_{(n+1)/2} nếu n lẻ  •  ½(x_{n/2} + x_{n/2 + 1}) nếu n chẵn"),
                        MathFormulaItem("g10_c5_f3", "Tứ phân vị Q₁, Q₂, Q₃", "Q₂ = M_e ; Q₁ = trung vị nửa dưới ; Q₃ = trung vị nửa trên"),
                        MathFormulaItem("g10_c5_f4", "Mốt M_o", "Giá trị có tần số xuất hiện lớn nhất (mẫu có thể có nhiều mốt)")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c5_spread",
                    title = "Đo độ phân tán, Ngoại lệ & Đổi đơn vị tuyến tính",
                    subtitle = "Phương sai, độ lệch chuẩn, hệ số biến thiên và quy tắc phát hiện ngoại lệ",
                    accentHex = 0xFF14B8A6,
                    formulas = listOf(
                        MathFormulaItem("g10_c5_f5", "Khoảng biến thiên R & Tứ phân vị Δ_Q", "R = x_max − x_min   ;   Δ_Q = Q₃ − Q₁"),
                        MathFormulaItem("g10_c5_f6", "Phương sai s²", "(1/n)∑(xᵢ − x̄)²  =  (1/n)∑xᵢ² − x̄²  =  ∑fᵢxᵢ² − x̄²"),
                        MathFormulaItem("g10_c5_f7", "Phương sai mẫu hiệu chỉnh ŝ²", "[ 1 / (n − 1) ] · ∑(xᵢ − x̄)²", condition = "n ≥ 2"),
                        MathFormulaItem("g10_c5_f8", "Độ lệch chuẩn s & Hệ số biến thiên CV", "s = √(s²)   ;   CV = (s / x̄) · 100%"),
                        MathFormulaItem("g10_c5_f9", "Giá trị bất thường (ngoại lệ)", "x > Q₃ + 1,5·Δ_Q   hoặc   x < Q₁ − 1,5·Δ_Q"),
                        MathFormulaItem("g10_c5_f10", "Đổi tuyến tính yᵢ = a·xᵢ + b", "ȳ = a·x̄ + b   ;   s_y² = a²·s_x²   ;   s_y = |a|·s_x")
                    )
                )
            )
        )
    )

    val chapters: List<MathChapter> by lazy {
        part1Chapters + Grade10CurriculumPart2.chapters
    }
}
