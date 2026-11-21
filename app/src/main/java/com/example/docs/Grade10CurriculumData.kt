package com.example.docs

/**
 * Complete Grade 10 Mathematics formula compendium aligned with Vietnam GDPT 2018
 * (9 chapters covering Logic/Sets, Linear Inequalities, Triangle Trigonometry, Vectors,
 * Unbinned Statistics, Quadratic Functions, Analytic Plane Geometry Oxy & Conics,
 * Combinatorics, and Classical Probability).
 */
object Grade10CurriculumData {

    val chapters: List<MathChapter> = listOf(
        // ==================== CHƯƠNG I ====================
        MathChapter(
            id = "g10_c1",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương I",
            title = "Mệnh đề và Tập hợp",
            summary = "Mệnh đề logic, phủ định lượng từ ∀/∃, phép toán tập hợp & tập con của ℝ",
            accentHex = 0xFF10B981,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c1_logic",
                    title = "Mệnh đề & Phủ định lượng từ",
                    subtitle = "Quan hệ kéo theo, đảo, tương đương và quy tắc phủ định ∀, ∃",
                    accentHex = 0xFF10B981,
                    mnemonic = "Phủ định ∀ thành ∃, phủ định ∃ thành ∀ và đảo ngược mệnh đề P(x)",
                    formulas = listOf(
                        MathFormulaItem("g10_c1_f1", "P ⇒ Q", "Sai duy nhất khi P đúng và Q sai", note = "P: điều kiện đủ; Q: điều kiện cần"),
                        MathFormulaItem("g10_c1_f2", "P ⇔ Q", "Đúng khi P và Q cùng đúng hoặc cùng sai", note = "Điều kiện cần và đủ"),
                        MathFormulaItem("g10_c1_f3", "Phủ định (∀x ∈ X, P(x))", "∃x ∈ X, P̄(x)"),
                        MathFormulaItem("g10_c1_f4", "Phủ định (∃x ∈ X, P(x))", "∀x ∈ X, P̄(x)")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c1_sets",
                    title = "Các phép toán trên tập hợp",
                    subtitle = "Giao, hợp, hiệu, phần bù và công thức đếm phần tử",
                    accentHex = 0xFF06B6D4,
                    formulas = listOf(
                        MathFormulaItem("g10_c1_f5", "A ∩ B", "{ x | x ∈ A và x ∈ B }", note = "Giao hai tập hợp"),
                        MathFormulaItem("g10_c1_f6", "A ∪ B", "{ x | x ∈ A hoặc x ∈ B }", note = "Hợp hai tập hợp"),
                        MathFormulaItem("g10_c1_f7", "A \\ B", "{ x | x ∈ A và x ∉ B }", note = "Hiệu của A và B"),
                        MathFormulaItem("g10_c1_f8", "C_E A", "E \\ A", condition = "A ⊂ E", note = "Phần bù của A trong E"),
                        MathFormulaItem("g10_c1_f9", "n(A ∪ B)", "n(A) + n(B) − n(A ∩ B)", note = "Công thức đếm số phần tử"),
                        MathFormulaItem("g10_c1_f10", "Số tập con của tập n phần tử", "2ⁿ")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c1_intervals",
                    title = "Các tập con thường dùng của ℝ",
                    subtitle = "Khoảng, đoạn và nửa khoảng trên trục số",
                    accentHex = 0xFF38BDF8,
                    formulas = listOf(
                        MathFormulaItem("g10_c1_f11", "(a; b)", "{ x ∈ ℝ | a < x < b }", note = "Khoảng"),
                        MathFormulaItem("g10_c1_f12", "[a; b]", "{ x ∈ ℝ | a ≤ x ≤ b }", note = "Đoạn"),
                        MathFormulaItem("g10_c1_f13", "[a; b)  /  (a; b]", "a ≤ x < b   /   a < x ≤ b", note = "Nửa khoảng")
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
            summary = "Xác định miền nghiệm trên mặt phẳng Oxy và bài toán tối ưu tuyến tính",
            accentHex = 0xFF14B8A6,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c2_linear",
                    title = "Bất phương trình bậc nhất hai ẩn",
                    subtitle = "Biểu diễn nửa mặt phẳng nghiệm và cực trị biểu thức F(x; y)",
                    accentHex = 0xFF14B8A6,
                    mnemonic = "Vẽ đường bờ d: ax + by = c → Thử điểm M₀(x₀; y₀) ∉ d (thường chọn O(0;0)) → Gạch bỏ nửa không thỏa",
                    formulas = listOf(
                        MathFormulaItem("g10_c2_f1", "Dạng tổng quát", "ax + by < c (hoặc ≤, >, ≥)", condition = "a² + b² ≠ 0"),
                        MathFormulaItem("g10_c2_f2", "Miền nghiệm hệ BPT", "Giao các miền nghiệm của từng BPT trong hệ"),
                        MathFormulaItem("g10_c2_f3", "Cực trị F(x; y) = ax + by", "Đạt GTLN / GTNN tại một trong các đỉnh của miền đa giác nghiệm")
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
            summary = "Định lý Côsin, Định lý Sin, 5 công thức diện tích tam giác & đường trung tuyến",
            accentHex = 0xFF0EA5E9,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c3_cos_sin",
                    title = "Định lý Côsin & Định lý Sin",
                    subtitle = "Liên hệ giữa ba cạnh a, b, c và ba góc A, B, C của tam giác ABC",
                    accentHex = 0xFF0EA5E9,
                    formulas = listOf(
                        MathFormulaItem("g10_c3_f1", "a²", "b² + c² − 2bc·cos A", note = "Tương tự cho b², c²"),
                        MathFormulaItem("g10_c3_f2", "cos A", "(b² + c² − a²) / (2bc)", note = "Hệ quả tính góc khi biết 3 cạnh"),
                        MathFormulaItem("g10_c3_f3", "a / sin A = b / sin B = c / sin C", "2R", note = "R: bán kính đường tròn ngoại tiếp"),
                        MathFormulaItem("g10_c3_f4", "m_a²", "(2b² + 2c² − a²) / 4", note = "Độ dài đường trung tuyến ứng với cạnh a")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c3_area",
                    title = "5 công thức tính diện tích tam giác",
                    subtitle = "Nửa chu vi p = (a + b + c)/2, bán kính ngoại tiếp R, nội tiếp r",
                    accentHex = 0xFF38BDF8,
                    formulas = listOf(
                        MathFormulaItem("g10_c3_f5", "S (đáy – cao)", "½ · a · h_a = ½ · b · h_b = ½ · c · h_c"),
                        MathFormulaItem("g10_c3_f6", "S (2 cạnh – góc xen)", "½ · b · c · sin A = ½ · a · c · sin B = ½ · a · b · sin C"),
                        MathFormulaItem("g10_c3_f7", "S (theo R)", "(a · b · c) / (4R)"),
                        MathFormulaItem("g10_c3_f8", "S (theo r)", "p · r", note = "p = (a + b + c) / 2"),
                        MathFormulaItem("g10_c3_f9", "S (Heron)", "√[ p(p − a)(p − b)(p − c) ]")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c3_obtuse",
                    title = "Giá trị lượng giác hai góc bù nhau (0° – 180°)",
                    subtitle = "Mở rộng góc tù trong tam giác",
                    accentHex = 0xFF22D3EE,
                    formulas = listOf(
                        MathFormulaItem("g10_c3_f10", "sin(180° − α)", "sin α"),
                        MathFormulaItem("g10_c3_f11", "cos(180° − α)", "−cos α"),
                        MathFormulaItem("g10_c3_f12", "tan(180° − α)", "−tan α", condition = "α ≠ 90°"),
                        MathFormulaItem("g10_c3_f13", "cot(180° − α)", "−cot α", condition = "0° < α < 180°")
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
            summary = "Quy tắc 3 điểm, trung điểm, trọng tâm, tích với số và tích vô hướng",
            accentHex = 0xFF22C55E,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c4_ops",
                    title = "Quy tắc cộng, trừ, trung điểm & trọng tâm",
                    subtitle = "Các hệ thức vectơ nền tảng trong hình học phẳng",
                    accentHex = 0xFF22C55E,
                    mnemonic = "Cộng nối đuôi (AB + BC = AC) • Trừ chung gốc đảo ngược (AB − AC = CB)",
                    formulas = listOf(
                        MathFormulaItem("g10_c4_f1", "Quy tắc 3 điểm", "AB⃗ + BC⃗ = AC⃗"),
                        MathFormulaItem("g10_c4_f2", "Quy tắc hiệu", "AB⃗ − AC⃗ = CB⃗  (hay OB⃗ − OA⃗ = AB⃗)"),
                        MathFormulaItem("g10_c4_f3", "Quy tắc hình bình hành", "AB⃗ + AD⃗ = AC⃗", note = "ABCD là hình bình hành"),
                        MathFormulaItem("g10_c4_f4", "I là trung điểm AB", "IA⃗ + IB⃗ = 0⃗   ⇔   MA⃗ + MB⃗ = 2·MI⃗"),
                        MathFormulaItem("g10_c4_f5", "G là trọng tâm △ABC", "GA⃗ + GB⃗ + GC⃗ = 0⃗   ⇔   MA⃗ + MB⃗ + MC⃗ = 3·MG⃗"),
                        MathFormulaItem("g10_c4_f6", "A, B, C thẳng hàng", "AB⃗ = k · AC⃗", condition = "k ∈ ℝ")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c4_dot",
                    title = "Tích vô hướng của hai vectơ",
                    subtitle = "Góc, điều kiện vuông góc và bình phương vô hướng",
                    accentHex = 0xFF10B981,
                    formulas = listOf(
                        MathFormulaItem("g10_c4_f7", "a⃗ · b⃗", "|a⃗| · |b⃗| · cos(a⃗, b⃗)"),
                        MathFormulaItem("g10_c4_f8", "a⃗ ⊥ b⃗", "a⃗ · b⃗ = 0"),
                        MathFormulaItem("g10_c4_f9", "(a⃗)²", "|a⃗|²", note = "Bình phương vô hướng bằng bình phương độ dài"),
                        MathFormulaItem("g10_c4_f10", "|a⃗ ± b⃗|²", "|a⃗|² ± 2(a⃗ · b⃗) + |b⃗|²")
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
            summary = "Số trung bình, trung vị, tứ phân vị, khoảng biến thiên, phương sai & độ lệch chuẩn",
            accentHex = 0xFF0D9488,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c5_center",
                    title = "Đo xu thế trung tâm",
                    subtitle = "Mẫu số liệu x₁, x₂, …, xₙ đã sắp xếp không giảm",
                    accentHex = 0xFF0D9488,
                    formulas = listOf(
                        MathFormulaItem("g10_c5_f1", "Số trung bình x̄", "(x₁ + x₂ + … + xₙ) / n"),
                        MathFormulaItem("g10_c5_f2", "Trung vị M_e (Q₂)", "Giá trị đứng giữa nếu n lẻ; trung bình cộng 2 số giữa nếu n chẵn"),
                        MathFormulaItem("g10_c5_f3", "Tứ phân vị Q₁, Q₃", "Q₁ = trung vị nửa dưới; Q₃ = trung vị nửa trên"),
                        MathFormulaItem("g10_c5_f4", "Mốt M_o", "Giá trị có tần số xuất hiện lớn nhất trong mẫu")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c5_spread",
                    title = "Đo độ phân tán & Giá trị ngoại lệ",
                    subtitle = "Đánh giá mức độ chênh lệch quanh giá trị trung tâm",
                    accentHex = 0xFF14B8A6,
                    formulas = listOf(
                        MathFormulaItem("g10_c5_f5", "Khoảng biến thiên R", "x_max − x_min"),
                        MathFormulaItem("g10_c5_f6", "Khoảng tứ phân vị Δ_Q", "Q₃ − Q₁"),
                        MathFormulaItem("g10_c5_f7", "Phương sai s²", "(1/n) ∑(xᵢ − x̄)²  =  (1/n) ∑xᵢ² − x̄²"),
                        MathFormulaItem("g10_c5_f8", "Độ lệch chuẩn s", "√(s²)"),
                        MathFormulaItem("g10_c5_f9", "Giá trị bất thường (ngoại lệ)", "x > Q₃ + 1,5·Δ_Q   hoặc   x < Q₁ − 1,5·Δ_Q")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG VI ====================
        MathChapter(
            id = "g10_c6",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương VI",
            title = "Hàm số, Đồ thị và Ứng dụng",
            summary = "Hàm số bậc hai (Parabol), dấu tam thức bậc hai, định lý Vi-ét & PT quy về bậc hai",
            accentHex = 0xFF06B6D4,
            quickActions = listOf(
                DocQuickAction.Plot2D("Vẽ Parabol y = x² − 2x − 3", "x^2 - 2*x - 3"),
                DocQuickAction.Plot2D("Vẽ y = −x² + 4x", "-x^2 + 4*x")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c6_quadratic",
                    title = "Hàm số bậc hai y = ax² + bx + c (a ≠ 0)",
                    subtitle = "Tọa độ đỉnh, trục đối xứng và chiều biến thiên của Parabol",
                    accentHex = 0xFF06B6D4,
                    formulas = listOf(
                        MathFormulaItem("g10_c6_f1", "Tọa độ đỉnh I", "I( −b/(2a) ; −Δ/(4a) )", note = "Δ = b² − 4ac"),
                        MathFormulaItem("g10_c6_f2", "Trục đối xứng", "x = −b / (2a)"),
                        MathFormulaItem("g10_c6_f3", "Bề lõm Parabol", "a > 0: hướng lên (GTNN tại I) • a < 0: hướng xuống (GTLN tại I)"),
                        MathFormulaItem("g10_c6_f4", "Định lý Vi-ét", "x₁ + x₂ = −b/a   ;   x₁ · x₂ = c/a", condition = "Δ ≥ 0")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c6_sign",
                    title = "Định lý dấu tam thức bậc hai f(x) = ax² + bx + c",
                    subtitle = "Xét dấu & giải bất phương trình bậc hai một ẩn",
                    accentHex = 0xFF38BDF8,
                    mnemonic = "Trong trái – Ngoài cùng (khi Δ > 0 có hai nghiệm phân biệt x₁ < x₂)",
                    formulas = listOf(
                        MathFormulaItem("g10_c6_f5", "Δ < 0", "f(x) luôn cùng dấu với a với mọi x ∈ ℝ"),
                        MathFormulaItem("g10_c6_f6", "Δ = 0", "f(x) cùng dấu với a với mọi x ≠ −b/(2a)"),
                        MathFormulaItem("g10_c6_f7", "Δ > 0 (x₁ < x₂)", "Trong (x₁; x₂): trái dấu a • Ngoài: cùng dấu a"),
                        MathFormulaItem("g10_c6_f8", "f(x) > 0, ∀x ∈ ℝ", "a > 0  và  Δ < 0", note = "Tương tự f(x) ≥ 0 ⇔ a > 0 và Δ ≤ 0"),
                        MathFormulaItem("g10_c6_f9", "f(x) < 0, ∀x ∈ ℝ", "a < 0  và  Δ < 0", note = "Tương tự f(x) ≤ 0 ⇔ a < 0 và Δ ≤ 0")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c6_radicals",
                    title = "Phương trình quy về bậc hai (Chứa căn)",
                    subtitle = "Điều kiện bình phương hai vế khử căn bậc hai",
                    accentHex = 0xFF2DD4BF,
                    formulas = listOf(
                        MathFormulaItem("g10_c6_f10", "√A = √B", "⇔  A = B  và  A ≥ 0 (hoặc B ≥ 0)"),
                        MathFormulaItem("g10_c6_f11", "√A = B", "⇔  B ≥ 0  và  A = B²")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG VII ====================
        MathChapter(
            id = "g10_c7",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương VII",
            title = "Phương pháp tọa độ trong mặt phẳng (Oxy)",
            summary = "Tọa độ vectơ, đường thẳng, góc, khoảng cách, đường tròn và 3 đường Conic",
            accentHex = 0xFF0284C7,
            quickActions = listOf(
                DocQuickAction.Plot2D("Vẽ đường thẳng y = 2x − 1", "2*x - 1"),
                DocQuickAction.Plot2D("Vẽ nửa đường tròn R = 3", "sqrt(9 - x^2)")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c7_vec",
                    title = "Tọa độ điểm & Vectơ trong Oxy",
                    subtitle = "Cho A(x_A; y_A), B(x_B; y_B), u⃗(x₁; y₁), v⃗(x₂; y₂)",
                    accentHex = 0xFF0284C7,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f1", "AB⃗ & Độ dài AB", "AB⃗ = (x_B − x_A; y_B − y_A)  ;  AB = √[(x_B−x_A)² + (y_B−y_A)²]"),
                        MathFormulaItem("g10_c7_f2", "Trung điểm I & Trọng tâm G", "x_I = (x_A+x_B)/2  ;  x_G = (x_A+x_B+x_C)/3 (tương tự y)"),
                        MathFormulaItem("g10_c7_f3", "Tích vô hướng u⃗ · v⃗", "x₁x₂ + y₁y₂"),
                        MathFormulaItem("g10_c7_f4", "cos(u⃗, v⃗)", "(x₁x₂ + y₁y₂) / [ √(x₁²+y₁²) · √(x₂²+y₂²) ]")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c7_line",
                    title = "Đường thẳng, Góc & Khoảng cách",
                    subtitle = "VTCP u⃗(u₁; u₂), VTPT n⃗(a; b) với n⃗ ⊥ u⃗",
                    accentHex = 0xFF38BDF8,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f5", "PT tổng quát Δ", "a(x − x₀) + b(y − y₀) = 0   ⇔   ax + by + c = 0"),
                        MathFormulaItem("g10_c7_f6", "PT tham số Δ", "x = x₀ + u₁t  ;  y = y₀ + u₂t  (t ∈ ℝ)"),
                        MathFormulaItem("g10_c7_f7", "PT đoạn chắn", "x/a + y/b = 1", condition = "a, b ≠ 0", note = "Qua A(a;0) và B(0;b)"),
                        MathFormulaItem("g10_c7_f8", "Khoảng cách d(M₀; Δ)", "|ax₀ + by₀ + c| / √(a² + b²)"),
                        MathFormulaItem("g10_c7_f9", "cos(Δ₁, Δ₂)", "|a₁a₂ + b₁b₂| / [ √(a₁²+b₁²) · √(a₂²+b₂²) ]")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c7_circle",
                    title = "Phương trình đường tròn",
                    subtitle = "Tâm I(a; b), bán kính R và tiếp tuyến tại điểm M₀",
                    accentHex = 0xFF10B981,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f10", "PT chính tắc (C)", "(x − a)² + (y − b)² = R²"),
                        MathFormulaItem("g10_c7_f11", "PT khai triển (C)", "x² + y² − 2ax − 2by + c = 0", condition = "a² + b² − c > 0", note = "R = √(a² + b² − c)"),
                        MathFormulaItem("g10_c7_f12", "Tiếp tuyến tại M₀(x₀; y₀) ∈ (C)", "(x₀ − a)(x − x₀) + (y₀ − b)(y − y₀) = 0")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c7_conic",
                    title = "Ba đường Conic: Elip, Hypebol & Parabol",
                    subtitle = "Phương trình chính tắc và hệ thức tiêu cự",
                    accentHex = 0xFF14B8A6,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f13", "Elip (E)", "x²/a² + y²/b² = 1", condition = "a > b > 0", note = "c² = a² − b²; tiêu điểm F₁,₂(±c; 0)"),
                        MathFormulaItem("g10_c7_f14", "Hypebol (H)", "x²/a² − y²/b² = 1", condition = "a, b > 0", note = "c² = a² + b²; |MF₁ − MF₂| = 2a"),
                        MathFormulaItem("g10_c7_f15", "Parabol (P)", "y² = 2px", condition = "p > 0", note = "Tiêu điểm F(p/2; 0); đường chuẩn x = −p/2")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG VIII ====================
        MathChapter(
            id = "g10_c8",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương VIII",
            title = "Đại số tổ hợp",
            summary = "Quy tắc cộng – nhân, Hoán vị, Chỉnh hợp, Tổ hợp và Nhị thức Newton",
            accentHex = 0xFF059669,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c8_comb",
                    title = "Hoán vị, Chỉnh hợp & Tổ hợp",
                    subtitle = "Phân biệt sắp thứ tự (P, A) và không thứ tự (C)",
                    accentHex = 0xFF059669,
                    mnemonic = "Chọn tất cả sắp xếp → Hoán vị (P) • Chọn k có thứ tự → Chỉnh hợp (A) • Chọn k không thứ tự → Tổ hợp (C)",
                    formulas = listOf(
                        MathFormulaItem("g10_c8_f1", "Hoán vị P_n", "n! = 1 · 2 · … · n", note = "Quy ước 0! = 1"),
                        MathFormulaItem("g10_c8_f2", "Chỉnh hợp A_n^k", "n! / (n − k)!", condition = "1 ≤ k ≤ n"),
                        MathFormulaItem("g10_c8_f3", "Tổ hợp C_n^k", "n! / [ k! · (n − k)! ]", condition = "0 ≤ k ≤ n"),
                        MathFormulaItem("g10_c8_f4", "Tính chất đối xứng & Pascal", "C_n^k = C_n^{n−k}   ;   C_n^k + C_n^{k+1} = C_{n+1}^{k+1}")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c8_newton",
                    title = "Nhị thức Newton",
                    subtitle = "Khai triển (a + b)ⁿ và số hạng tổng quát",
                    accentHex = 0xFF10B981,
                    formulas = listOf(
                        MathFormulaItem("g10_c8_f5", "(a + b)ⁿ", "∑_{k=0..n} C_n^k · a^{n−k} · b^k", note = "Có n + 1 số hạng"),
                        MathFormulaItem("g10_c8_f6", "Số hạng thứ k + 1", "T_{k+1} = C_n^k · a^{n−k} · b^k"),
                        MathFormulaItem("g10_c8_f7", "(a + b)⁴", "a⁴ + 4a³b + 6a²b² + 4ab³ + b⁴"),
                        MathFormulaItem("g10_c8_f8", "(a + b)⁵", "a⁵ + 5a⁴b + 10a³b² + 10a²b³ + 5ab⁴ + b⁵"),
                        MathFormulaItem("g10_c8_f9", "Tổng hệ số C_n^0 + C_n^1 + … + C_n^n", "2ⁿ")
                    )
                )
            )
        ),

        // ==================== CHƯƠNG IX ====================
        MathChapter(
            id = "g10_c9",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương IX",
            title = "Tính xác suất theo định nghĩa cổ điển",
            summary = "Không gian mẫu Ω, công thức xác suất cổ điển, biến cố đối & quy tắc cộng xung khắc",
            accentHex = 0xFF0F766E,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c9_prob",
                    title = "Định nghĩa cổ điển & Tính chất xác suất",
                    subtitle = "Các kết quả đồng khả năng trong không gian mẫu hữu hạn Ω",
                    accentHex = 0xFF0F766E,
                    formulas = listOf(
                        MathFormulaItem("g10_c9_f1", "Xác suất cổ điển P(A)", "n(A) / n(Ω)", note = "0 ≤ P(A) ≤ 1"),
                        MathFormulaItem("g10_c9_f2", "Biến cố chắc chắn & không thể", "P(Ω) = 1   ;   P(∅) = 0"),
                        MathFormulaItem("g10_c9_f3", "Xác suất biến cố đối P(Ā)", "1 − P(A)", note = "Dùng khi đếm trực tiếp quá nhiều trường hợp"),
                        MathFormulaItem("g10_c9_f4", "A, B xung khắc (A ∩ B = ∅)", "P(A ∪ B) = P(A) + P(B)")
                    )
                )
            )
        )
    )
}
