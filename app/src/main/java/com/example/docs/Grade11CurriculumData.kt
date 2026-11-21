package com.example.docs

import com.example.engine.ShapeType

/**
 * Complete Grade 11 Mathematics formula compendium aligned with Vietnam GDPT 2018
 * (9 chapters: Trigonometry + Interactive Tools, Sequences/AP/GP, Grouped Statistics,
 * Parallelism in 3D, Limits & Continuity, Exponentials & Logarithms, Perpendicularity &
 * Volumes in 3D, Probability Rules, and Derivatives).
 */
object Grade11CurriculumData {

    private val trigInheritedGroups: List<MathFormulaGroup> by lazy {
        TrigReferenceRepository.formulaGroups.map { grp ->
            MathFormulaGroup(
                id = "g11_c1_${grp.id}",
                title = grp.title,
                subtitle = grp.subtitle,
                accentHex = grp.accentHex,
                mnemonic = grp.mnemonic,
                specialKind = grp.specialKind,
                formulas = grp.formulas.mapIndexed { idx, item ->
                    MathFormulaItem(
                        id = "g11_c1_${grp.id}_${idx + 1}",
                        lhs = item.lhs,
                        rhs = item.rhs,
                        condition = item.condition,
                        note = item.note
                    )
                }
            )
        }
    }

    val chapters: List<MathChapter> by lazy {
        listOf(
            // ==================== CHƯƠNG I ====================
            MathChapter(
                id = "g11_c1",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương I",
                title = "Hàm số lượng giác & Phương trình lượng giác",
                summary = "Trọn bộ công thức lượng giác, Đường tròn LG tương tác, Bảng dấu & PT lượng giác cơ bản",
                accentHex = 0xFFF59E0B,
                hasTrigInteractiveTools = true,
                quickActions = listOf(
                    DocQuickAction.Plot2D("Vẽ y = sin(x)", "sin(x)"),
                    DocQuickAction.Plot2D("Vẽ y = cos(x)", "cos(x)"),
                    DocQuickAction.Plot2D("Vẽ y = tan(x)", "tan(x)"),
                    DocQuickAction.Plot2D("Vẽ y = sin(2x)", "sin(2*x)")
                ),
                groups = trigInheritedGroups + listOf(
                    MathFormulaGroup(
                        id = "g11_c1_functions",
                        title = "Tính chất 4 hàm số lượng giác",
                        subtitle = "Tập xác định, tập giá trị, tính chẵn lẻ và chu kỳ tuần hoàn",
                        accentHex = 0xFF0EA5E9,
                        formulas = listOf(
                            MathFormulaItem("g11_c1_fn1", "y = sin x", "TXĐ: ℝ • TGT: [−1; 1] • Hàm lẻ • Chu kỳ T = 2π"),
                            MathFormulaItem("g11_c1_fn2", "y = cos x", "TXĐ: ℝ • TGT: [−1; 1] • Hàm chẵn • Chu kỳ T = 2π"),
                            MathFormulaItem("g11_c1_fn3", "y = tan x", "TXĐ: ℝ \\ {π/2 + kπ} • TGT: ℝ • Hàm lẻ • Chu kỳ T = π"),
                            MathFormulaItem("g11_c1_fn4", "y = cot x", "TXĐ: ℝ \\ {kπ} • TGT: ℝ • Hàm lẻ • Chu kỳ T = π")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c1_equations",
                        title = "Phương trình lượng giác cơ bản",
                        subtitle = "Công thức nghiệm tổng quát (k ∈ ℤ) và các giá trị đặc biệt",
                        accentHex = 0xFFF97316,
                        formulas = listOf(
                            MathFormulaItem("g11_c1_eq1", "sin x = sin α", "x = α + k2π   hoặc   x = π − α + k2π"),
                            MathFormulaItem("g11_c1_eq2", "cos x = cos α", "x = α + k2π   hoặc   x = −α + k2π"),
                            MathFormulaItem("g11_c1_eq3", "tan x = tan α", "x = α + kπ", condition = "cos x ≠ 0"),
                            MathFormulaItem("g11_c1_eq4", "cot x = cot α", "x = α + kπ", condition = "sin x ≠ 0"),
                            MathFormulaItem("g11_c1_eq5", "sin x = 0 / 1 / −1", "x = kπ   |   x = π/2 + k2π   |   x = −π/2 + k2π"),
                            MathFormulaItem("g11_c1_eq6", "cos x = 0 / 1 / −1", "x = π/2 + kπ   |   x = k2π   |   x = π + k2π")
                        )
                    )
                )
            ),

            // ==================== CHƯƠNG II ====================
            MathChapter(
                id = "g11_c2",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương II",
                title = "Dãy số, Cấp số cộng và Cấp số nhân",
                summary = "Số hạng tổng quát, tính chất trung bình, tổng n số hạng đầu & CSN lùi vô hạn",
                accentHex = 0xFFF97316,
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c2_ap",
                        title = "Cấp số cộng (CSC — công sai d)",
                        subtitle = "Mỗi số hạng (từ thứ 2) bằng số hạng trước cộng thêm d",
                        accentHex = 0xFFF97316,
                        formulas = listOf(
                            MathFormulaItem("g11_c2_f1", "Định nghĩa", "u_{n+1} = u_n + d", note = "d: công sai"),
                            MathFormulaItem("g11_c2_f2", "Số hạng tổng quát u_n", "u₁ + (n − 1)d", condition = "n ≥ 2"),
                            MathFormulaItem("g11_c2_f3", "Tính chất 3 số hạng liên tiếp", "u_k = (u_{k−1} + u_{k+1}) / 2", condition = "k ≥ 2"),
                            MathFormulaItem("g11_c2_f4", "Tổng n số hạng đầu S_n", "n(u₁ + u_n) / 2  =  n[2u₁ + (n − 1)d] / 2")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c2_gp",
                        title = "Cấp số nhân (CSN — công bội q)",
                        subtitle = "Mỗi số hạng (từ thứ 2) bằng số hạng trước nhân với q",
                        accentHex = 0xFFF59E0B,
                        formulas = listOf(
                            MathFormulaItem("g11_c2_f5", "Định nghĩa", "u_{n+1} = u_n · q", note = "q: công bội"),
                            MathFormulaItem("g11_c2_f6", "Số hạng tổng quát u_n", "u₁ · q^{n−1}", condition = "n ≥ 2"),
                            MathFormulaItem("g11_c2_f7", "Tính chất 3 số hạng liên tiếp", "u_k² = u_{k−1} · u_{k+1}", condition = "k ≥ 2"),
                            MathFormulaItem("g11_c2_f8", "Tổng n số hạng đầu S_n", "u₁ · (1 − qⁿ) / (1 − q)", condition = "q ≠ 1", note = "Nếu q = 1 thì S_n = n·u₁"),
                            MathFormulaItem("g11_c2_f9", "Tổng CSN lùi vô hạn S", "u₁ / (1 − q)", condition = "|q| < 1")
                        )
                    )
                )
            ),

            // ==================== CHƯƠNG III ====================
            MathChapter(
                id = "g11_c3",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương III",
                title = "Mẫu số liệu ghép nhóm (Xu thế trung tâm)",
                summary = "Số trung bình ghép nhóm, nội suy Trung vị, Tứ phân vị Q₁, Q₂, Q₃ & Mốt",
                accentHex = 0xFFEA580C,
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c3_grouped",
                        title = "Các số đặc trưng của mẫu ghép nhóm",
                        subtitle = "Nhóm [u_m; u_{m+1}) có giá trị đại diện c_m = (u_m + u_{m+1})/2 và tần số n_m",
                        accentHex = 0xFFEA580C,
                        formulas = listOf(
                            MathFormulaItem("g11_c3_f1", "Số trung bình x̄", "(m₁c₁ + m₂c₂ + … + m_k c_k) / n", note = "n = m₁ + … + m_k"),
                            MathFormulaItem("g11_c3_f2", "Trung vị M_e (Q₂)", "u_m + [ (n/2 − C) / n_m ] · (u_{m+1} − u_m)", note = "C: tổng tần số các nhóm trước nhóm chứa M_e"),
                            MathFormulaItem("g11_c3_f3", "Tứ phân vị Q₁", "u_p + [ (n/4 − C) / n_p ] · (u_{p+1} − u_p)", note = "Nhóm p chứa phần tử thứ n/4"),
                            MathFormulaItem("g11_c3_f4", "Tứ phân vị Q₃", "u_q + [ (3n/4 − C) / n_q ] · (u_{q+1} − u_q)", note = "Nhóm q chứa phần tử thứ 3n/4"),
                            MathFormulaItem("g11_c3_f5", "Mốt M_o", "u_m + [ (n_m − n_{m−1}) / ((n_m − n_{m−1}) + (n_m − n_{m+1})) ] · (u_{m+1} − u_m)", note = "Nhóm m có tần số lớn nhất")
                        )
                    )
                )
            ),

            // ==================== CHƯƠNG IV ====================
            MathChapter(
                id = "g11_c4",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương IV",
                title = "Quan hệ song song trong không gian",
                summary = "Giao tuyến, chứng minh đường // mặt, mặt // mặt, định lý Thales & phép chiếu song song",
                accentHex = 0xFFD97706,
                quickActions = listOf(
                    DocQuickAction.Open3D("Mở 3D Lăng trụ đứng", ShapeType.PRISM3, showAxes = false),
                    DocQuickAction.Open3D("Mở 3D Chóp tứ giác", ShapeType.PYRAMID4, showAxes = false)
                ),
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c4_parallel",
                        title = "Các định lý song song cốt lõi",
                        subtitle = "Cơ sở dựng giao tuyến và thiết diện song song",
                        accentHex = 0xFFD97706,
                        formulas = listOf(
                            MathFormulaItem("g11_c4_f1", "Giao tuyến 3 mặt phẳng", "Ba giao tuyến đôi một song song hoặc đồng quy"),
                            MathFormulaItem("g11_c4_f2", "Điều kiện d // (P)", "d ⊄ (P), d // d' và d' ⊂ (P)  ⇒  d // (P)"),
                            MathFormulaItem("g11_c4_f3", "Giao tuyến khi d // (P)", "d // (P), d ⊂ (Q) và (P) ∩ (Q) = Δ  ⇒  Δ // d"),
                            MathFormulaItem("g11_c4_f4", "Điều kiện (P) // (Q)", "a, b ⊂ (P) cắt nhau và a // (Q), b // (Q)  ⇒  (P) // (Q)"),
                            MathFormulaItem("g11_c4_f5", "Mặt phẳng thứ 3 cắt (P)//(Q)", "(R) cắt (P), (Q) theo 2 giao tuyến a, b  ⇒  a // b"),
                            MathFormulaItem("g11_c4_f6", "Định lý Thales không gian", "Ba mặt phẳng song song chắn trên 2 cát tuyến các đoạn thẳng tương ứng tỉ lệ")
                        )
                    )
                )
            ),

            // ==================== CHƯƠNG V ====================
            MathChapter(
                id = "g11_c5",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương V",
                title = "Giới hạn và Hàm số liên tục",
                summary = "Giới hạn dãy số, giới hạn hàm số một bên / vô cực, khử dạng vô định & tính liên tục",
                accentHex = 0xFFFB7185,
                quickActions = listOf(
                    DocQuickAction.Plot2D("Vẽ hàm phân thức y = (2x+1)/(x−1)", "(2*x + 1)/(x - 1)")
                ),
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c5_seq_lim",
                        title = "Giới hạn dãy số & Hàm số cơ bản",
                        subtitle = "Các giới hạn chuẩn và quy tắc tìm giới hạn tại vô cực",
                        accentHex = 0xFFFB7185,
                        formulas = listOf(
                            MathFormulaItem("g11_c5_f1", "lim (1 / n^k)", "0", condition = "k nguyên dương"),
                            MathFormulaItem("g11_c5_f2", "lim qⁿ", "0  (nếu |q| < 1)   ;   +∞  (nếu q > 1)"),
                            MathFormulaItem("g11_c5_f3", "lim_{x→±∞} (c / x^k)", "0", condition = "k nguyên dương, c là hằng số"),
                            MathFormulaItem("g11_c5_f4", "Dạng vô định 0/0", "Phân tích nhân tử chung (x − x₀) hoặc nhân lượng liên hợp"),
                            MathFormulaItem("g11_c5_f5", "Dạng vô định ∞/∞", "Chia cả tử và mẫu cho lũy thừa bậc cao nhất của n hoặc x"),
                            MathFormulaItem("g11_c5_f6", "Giới hạn lượng giác đặc biệt", "lim_{x→0} (sin x / x) = 1")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c5_cont",
                        title = "Hàm số liên tục & Chứng minh có nghiệm",
                        subtitle = "Điều kiện liên tục tại điểm x₀ và hệ quả giá trị trung gian",
                        accentHex = 0xFFF43F5E,
                        formulas = listOf(
                            MathFormulaItem("g11_c5_f7", "Liên tục tại x₀", "lim_{x→x₀⁺} f(x) = lim_{x→x₀⁻} f(x) = f(x₀)"),
                            MathFormulaItem("g11_c5_f8", "Định lý giá trị trung gian", "f liên tục trên [a; b] và f(a)·f(b) < 0  ⇒  ∃c ∈ (a; b): f(c) = 0")
                        )
                    )
                )
            ),

            // ==================== CHƯƠNG VI ====================
            MathChapter(
                id = "g11_c6",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương VI",
                title = "Hàm số mũ và Hàm số lôgarit",
                summary = "Phép tính lũy thừa, công thức lôgarit, đổi cơ số, PT & BPT mũ – lôgarit",
                accentHex = 0xFFEC4899,
                quickActions = listOf(
                    DocQuickAction.Plot2D("Vẽ hàm mũ y = 2^x", "2^x"),
                    DocQuickAction.Plot2D("Vẽ hàm lôgarit y = ln(x)", "ln(x)")
                ),
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c6_pow",
                        title = "Công thức Lũy thừa số mũ thực",
                        subtitle = "Với a, b > 0 và α, β ∈ ℝ",
                        accentHex = 0xFFEC4899,
                        formulas = listOf(
                            MathFormulaItem("g11_c6_f1", "a^α · a^β   |   a^α / a^β", "a^{α + β}   |   a^{α − β}"),
                            MathFormulaItem("g11_c6_f2", "(a^α)^β", "a^{α · β}"),
                            MathFormulaItem("g11_c6_f3", "(ab)^α   |   (a/b)^α", "a^α · b^α   |   a^α / b^α"),
                            MathFormulaItem("g11_c6_f4", "a^{−n}   |   a^{m/n}", "1 / aⁿ   |   ⁿ√(a^m)", condition = "a > 0, n ≥ 2")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c6_log",
                        title = "Công thức Lôgarit & Đổi cơ số",
                        subtitle = "Điều kiện cơ số 0 < a ≠ 1 và biểu thức trong log dương",
                        accentHex = 0xFFF472B6,
                        mnemonic = "Log tích thành tổng • Log thương thành hiệu • Số mũ trên nhảy ra tử, mũ cơ số nhảy ra mẫu",
                        formulas = listOf(
                            MathFormulaItem("g11_c6_f5", "Định nghĩa", "log_a b = α   ⇔   a^α = b", condition = "0 < a ≠ 1, b > 0"),
                            MathFormulaItem("g11_c6_f6", "Lôgarit tích & thương", "log_a(xy) = log_a x + log_a y   ;   log_a(x/y) = log_a x − log_a y"),
                            MathFormulaItem("g11_c6_f7", "Lôgarit lũy thừa", "log_a(b^α) = α·log_a b   ;   log_{a^β} b = (1/β)·log_a b"),
                            MathFormulaItem("g11_c6_f8", "Đổi cơ số", "log_a b = log_c b / log_c a = 1 / log_b a", condition = "0 < a, b, c ≠ 1"),
                            MathFormulaItem("g11_c6_f9", "Đồng nhất thức mũ–log", "a^{log_a b} = b   ;   log_a(a^α) = α   ;   ln x = log_e x")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c6_eq",
                        title = "Phương trình & Bất phương trình Mũ – Lôgarit",
                        subtitle = "Chú ý đảo chiều bất đẳng thức khi 0 < a < 1",
                        accentHex = 0xFFDB2777,
                        formulas = listOf(
                            MathFormulaItem("g11_c6_f10", "a^x = b  (b > 0)", "x = log_a b"),
                            MathFormulaItem("g11_c6_f11", "log_a x = b", "x = a^b", condition = "x > 0"),
                            MathFormulaItem("g11_c6_f12", "a^{f(x)} > a^{g(x)}", "f(x) > g(x) nếu a > 1  •  f(x) < g(x) nếu 0 < a < 1"),
                            MathFormulaItem("g11_c6_f13", "log_a f(x) > log_a g(x)", "f(x) > g(x) > 0 nếu a > 1  •  0 < f(x) < g(x) nếu 0 < a < 1")
                        )
                    )
                )
            ),

            // ==================== CHƯƠNG VII ====================
            MathChapter(
                id = "g11_c7",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương VII",
                title = "Quan hệ vuông góc & Thể tích không gian",
                summary = "Đường ⊥ mặt, định lý 3 đường vuông góc, góc nhị diện, khoảng cách & công thức thể tích",
                accentHex = 0xFFF59E0B,
                quickActions = listOf(
                    DocQuickAction.Open3D("Mở 3D Chóp SA ⊥ đáy", ShapeType.PYRAMID4_SA, showAxes = false),
                    DocQuickAction.Open3D("Mở 3D Hình lập phương", ShapeType.CUBE, showAxes = false)
                ),
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c7_perp",
                        title = "Định lý vuông góc, Góc & Khoảng cách",
                        subtitle = "Các quy tắc dựng góc và chân đường vuông góc",
                        accentHex = 0xFFF59E0B,
                        formulas = listOf(
                            MathFormulaItem("g11_c7_f1", "Điều kiện d ⊥ (P)", "d ⊥ a, d ⊥ b với a, b cắt nhau trong (P)"),
                            MathFormulaItem("g11_c7_f2", "Định lý 3 đường vuông góc", "a ⊂ (P), b' là hình chiếu của b lên (P): a ⊥ b  ⇔  a ⊥ b'"),
                            MathFormulaItem("g11_c7_f3", "Góc giữa d và (P)", "Bằng góc giữa d và hình chiếu d' của d lên (P)  (0° ≤ φ ≤ 90°)"),
                            MathFormulaItem("g11_c7_f4", "Góc phẳng nhị diện [P, d, Q]", "Góc giữa 2 tia Ox ⊂ (P), Oy ⊂ (Q) cùng vuông góc cạnh d tại O"),
                            MathFormulaItem("g11_c7_f5", "Khoảng cách d(A; (P))", "AH với H là hình chiếu vuông góc của A lên (P)")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c7_vol",
                        title = "Thể tích các khối đa diện & Khối tròn xoay cơ bản",
                        subtitle = "B: diện tích đáy, h: chiều cao vuông góc đáy",
                        accentHex = 0xFFEAB308,
                        formulas = listOf(
                            MathFormulaItem("g11_c7_f6", "Thể tích khối chóp", "V = ⅓ · B · h"),
                            MathFormulaItem("g11_c7_f7", "Thể tích khối lăng trụ", "V = B · h"),
                            MathFormulaItem("g11_c7_f8", "Thể tích khối chóp cụt đều", "V = ⅓ · h · (B + B' + √(B · B'))"),
                            MathFormulaItem("g11_c7_f9", "Tỉ số thể tích chóp tam giác", "V_{S.A'B'C'} / V_{S.ABC} = (SA'/SA) · (SB'/SB) · (SC'/SC)"),
                            MathFormulaItem("g11_c7_f10", "Thể tích Hình hộp / Lập phương", "V_hộp = a · b · c   ;   V_lập phương = a³")
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
                summary = "Biến cố hợp, biến cố giao, biến cố độc lập, quy tắc cộng tổng quát & quy tắc nhân",
                accentHex = 0xFF14B8A6,
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c8_rules",
                        title = "Quy tắc cộng & Quy tắc nhân xác suất",
                        subtitle = "Áp dụng cho hai biến cố A và B bất kỳ",
                        accentHex = 0xFF14B8A6,
                        formulas = listOf(
                            MathFormulaItem("g11_c8_f1", "Quy tắc cộng tổng quát", "P(A ∪ B) = P(A) + P(B) − P(A ∩ B)"),
                            MathFormulaItem("g11_c8_f2", "A, B xung khắc", "P(A ∪ B) = P(A) + P(B)", condition = "A ∩ B = ∅"),
                            MathFormulaItem("g11_c8_f3", "Quy tắc nhân (A, B độc lập)", "P(A ∩ B) = P(A) · P(B)"),
                            MathFormulaItem("g11_c8_f4", "Biến cố đối khi độc lập", "A, B độc lập ⇒ (Ā, B), (A, B̄), (Ā, B̄) cũng độc lập")
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
                summary = "Quy tắc tính đạo hàm, bảng đạo hàm sơ cấp & hàm hợp, tiếp tuyến & đạo hàm cấp hai",
                accentHex = 0xFFF97316,
                quickActions = listOf(
                    DocQuickAction.Plot2D("Khảo sát đạo hàm y = x³ − 3x", "x^3 - 3*x")
                ),
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c9_rules",
                        title = "Định nghĩa, Quy tắc tính & Tiếp tuyến",
                        subtitle = "Các quy tắc đại số, hàm hợp và phương trình tiếp tuyến",
                        accentHex = 0xFFF97316,
                        formulas = listOf(
                            MathFormulaItem("g11_c9_f1", "(u ± v)'", "u' ± v'"),
                            MathFormulaItem("g11_c9_f2", "(u · v)'", "u'v + uv'"),
                            MathFormulaItem("g11_c9_f3", "(u / v)'", "(u'v − uv') / v²", condition = "v ≠ 0"),
                            MathFormulaItem("g11_c9_f4", "Đạo hàm hàm hợp [f(u)]'", "f'(u) · u'"),
                            MathFormulaItem("g11_c9_f5", "PT tiếp tuyến tại M(x₀; y₀)", "y = f'(x₀)(x − x₀) + f(x₀)"),
                            MathFormulaItem("g11_c9_f6", "Ý nghĩa cơ học (Đạo hàm cấp 2)", "Vận tốc v(t) = s'(t)   ;   Gia tốc a(t) = v'(t) = s''(t)")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c9_table",
                        title = "Bảng đạo hàm đầy đủ (Sơ cấp & Hàm hợp u = u(x))",
                        subtitle = "Tra nhanh mọi đạo hàm lũy thừa, lượng giác, mũ và lôgarit",
                        accentHex = 0xFFF59E0B,
                        mnemonic = "Đạo hàm hàm co- (cos, cot) luôn có dấu trừ (−); hàm hợp luôn nhân thêm u'",
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
                            MathFormulaItem("g11_c9_f18", "(log_a x)'  |  (log_a u)'", "1 / (x·ln a)   |   u' / (u·ln a)")
                        )
                    )
                )
            )
        )
    }
}
