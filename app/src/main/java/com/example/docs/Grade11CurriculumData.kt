package com.example.docs

import com.example.engine.ShapeType

/**
 * Exhaustive Grade 11 Mathematics formula compendium aligned with Vietnam GDPT 2018.
 * Part 1: Chapters I – V (Trigonometry + Interactive Tools + Universal/Equation formulas,
 * Sequences/AP/GP + Series & Compound Interest, Grouped Statistics, 3D Parallelism,
 * and Limits & Continuity).
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

    private val part1Chapters: List<MathChapter> by lazy {
        listOf(
            // ==================== CHƯƠNG I ====================
            MathChapter(
                id = "g11_c1",
                grade = MathGrade.GRADE_11,
                chapterNumber = "Chương I",
                title = "Hàm số lượng giác & Phương trình lượng giác",
                summary = "Đầy đủ mọi công thức lượng giác, công thức theo t = tan(a/2), Đường tròn LG tương tác & mọi dạng PT LG",
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
                        id = "g11_c1_advanced",
                        title = "Công thức nâng cao: Chia đôi, Theo t = tan(a/2), Hạ bậc 3 & Góc + π/2",
                        subtitle = "Các biến đổi rút gọn thường xuất hiện trong đề thi đại học & tích phân",
                        accentHex = 0xFFEAB308,
                        formulas = listOf(
                            MathFormulaItem("g11_c1_adv1", "Biểu diễn theo t = tan(a/2)", "sin a = 2t/(1+t²)  ;  cos a = (1−t²)/(1+t²)  ;  tan a = 2t/(1−t²)"),
                            MathFormulaItem("g11_c1_adv2", "Công thức góc chia đôi", "tan(a/2) = sin a / (1 + cos a) = (1 − cos a) / sin a"),
                            MathFormulaItem("g11_c1_adv3", "Hạ bậc ba", "sin³a = (3sin a − sin 3a) / 4   ;   cos³a = (3cos a + cos 3a) / 4"),
                            MathFormulaItem("g11_c1_adv4", "Hằng đẳng thức bậc 4 & bậc 6", "sin⁴a + cos⁴a = 1 − ½sin²2a   ;   sin⁶a + cos⁶a = 1 − ¾sin²2a"),
                            MathFormulaItem("g11_c1_adv5", "Hợp góc sin x ± cos x", "sin x ± cos x = √2·sin(x ± π/4) = ±√2·cos(x ∓ π/4)"),
                            MathFormulaItem("g11_c1_adv6", "Cung hơn kém π/2 (α + π/2)", "sin(α+π/2) = cos α ; cos(α+π/2) = −sin α ; tan(α+π/2) = −cot α")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c1_functions",
                        title = "Tính chất 4 hàm số lượng giác",
                        subtitle = "Tập xác định, tập giá trị, tính chẵn lẻ và chu kỳ tuần hoàn",
                        accentHex = 0xFF0EA5E9,
                        formulas = listOf(
                            MathFormulaItem("g11_c1_fn1", "y = sin x", "TXĐ: ℝ • TGT: [−1; 1] • Hàm lẻ • Chu kỳ T = 2π"),
                            MathFormulaItem("g11_c1_fn2", "y = cos x", "TXĐ: ℝ • TGT: [−1; 1] • Hàm chẵn • Chu kỳ T = 2π"),
                            MathFormulaItem("g11_c1_fn3", "y = tan x", "TXĐ: ℝ \\ {π/2 + kπ} • TGT: ℝ • Hàm lẻ • Chu kỳ T = π"),
                            MathFormulaItem("g11_c1_fn4", "y = cot x", "TXĐ: ℝ \\ {kπ} • TGT: ℝ • Hàm lẻ • Chu kỳ T = π"),
                            MathFormulaItem("g11_c1_fn5", "Chu kỳ hàm hợp sin(ax+b), tan(ax+b)", "T = 2π/|a| (sin, cos)   ;   T = π/|a| (tan, cot)")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c1_equations",
                        title = "Phương trình lượng giác cơ bản & Thường gặp",
                        subtitle = "Công thức nghiệm tổng quát (k ∈ ℤ), giá trị đặc biệt và phương trình cổ điển",
                        accentHex = 0xFFF97316,
                        formulas = listOf(
                            MathFormulaItem("g11_c1_eq1", "sin x = sin α", "x = α + k2π   hoặc   x = π − α + k2π"),
                            MathFormulaItem("g11_c1_eq2", "cos x = cos α", "x = α + k2π   hoặc   x = −α + k2π"),
                            MathFormulaItem("g11_c1_eq3", "tan x = tan α  |  cot x = cot α", "x = α + kπ", condition = "cos x ≠ 0 (tan) ; sin x ≠ 0 (cot)"),
                            MathFormulaItem("g11_c1_eq4", "sin x = 0 / 1 / −1", "x = kπ   |   x = π/2 + k2π   |   x = −π/2 + k2π"),
                            MathFormulaItem("g11_c1_eq5", "cos x = 0 / 1 / −1", "x = π/2 + kπ   |   x = k2π   |   x = π + k2π"),
                            MathFormulaItem("g11_c1_eq6", "PT bậc nhất a·sin x + b·cos x = c", "Có nghiệm ⇔ a² + b² ≥ c²  •  Chia 2 vế cho √(a² + b²)"),
                            MathFormulaItem("g11_c1_eq7", "PT đối xứng theo sin x ± cos x", "Đặt t = sin x ± cos x (|t| ≤ √2) ⇒ sin x·cos x = ±(t² − 1) / 2"),
                            MathFormulaItem("g11_c1_eq8", "PT đẳng cấp a·sin²x + b·sin x cos x + c·cos²x = d", "Xét cos x = 0; với cos x ≠ 0 chia 2 vế cho cos²x đưa về tan x")
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
                summary = "Tính đơn điệu/bị chặn, CSC, CSN, tổng CSN lùi vô hạn, các tổng dãy đặc biệt & lãi kép",
                accentHex = 0xFFF97316,
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c2_seq",
                        title = "Đại cương dãy số & Các tổng dãy đặc biệt",
                        subtitle = "Xét tăng/giảm, bị chặn và các công thức tổng hay dùng",
                        accentHex = 0xFFFB923C,
                        formulas = listOf(
                            MathFormulaItem("g11_c2_f0a", "Dãy tăng / Dãy giảm", "u_{n+1} − u_n > 0 (tăng)  •  u_{n+1} − u_n < 0 (giảm)"),
                            MathFormulaItem("g11_c2_f0b", "Dãy bị chặn", "∃m, M ∈ ℝ sao cho m ≤ u_n ≤ M, ∀n ∈ ℕ*"),
                            MathFormulaItem("g11_c2_f0c", "Tổng 1 + 2 + … + n", "n(n + 1) / 2"),
                            MathFormulaItem("g11_c2_f0d", "Tổng bình phương 1² + 2² + … + n²", "n(n + 1)(2n + 1) / 6"),
                            MathFormulaItem("g11_c2_f0e", "Tổng lập phương 1³ + 2³ + … + n³", "[ n(n + 1) / 2 ]²"),
                            MathFormulaItem("g11_c2_f0f", "Tổng sai phân ∑ 1/[k(k+1)]", "1/(1·2) + 1/(2·3) + … + 1/[n(n+1)] = n / (n + 1)")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c2_ap",
                        title = "Cấp số cộng (CSC — công sai d)",
                        subtitle = "Mỗi số hạng (từ thứ 2) bằng số hạng trước cộng thêm d",
                        accentHex = 0xFFF97316,
                        formulas = listOf(
                            MathFormulaItem("g11_c2_f1", "Định nghĩa & Hiệu 2 số hạng bất kỳ", "u_{n+1} = u_n + d   ;   u_n − u_m = (n − m)d"),
                            MathFormulaItem("g11_c2_f2", "Số hạng tổng quát u_n", "u₁ + (n − 1)d", condition = "n ≥ 2"),
                            MathFormulaItem("g11_c2_f3", "Tính chất trung bình cộng", "u_k = (u_{k−1} + u_{k+1}) / 2   ;   u_k + u_{n−k+1} = u₁ + u_n"),
                            MathFormulaItem("g11_c2_f4", "Tổng n số hạng đầu S_n", "n(u₁ + u_n) / 2  =  n[2u₁ + (n − 1)d] / 2")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c2_gp",
                        title = "Cấp số nhân (CSN — công bội q)",
                        subtitle = "Tổng n số hạng, tích n số hạng, CSN lùi vô hạn & lãi kép",
                        accentHex = 0xFFF59E0B,
                        formulas = listOf(
                            MathFormulaItem("g11_c2_f5", "Định nghĩa & Tỉ số 2 số hạng bất kỳ", "u_{n+1} = u_n · q   ;   u_n / u_m = q^{n−m}"),
                            MathFormulaItem("g11_c2_f6", "Số hạng tổng quát u_n", "u₁ · q^{n−1}", condition = "n ≥ 2"),
                            MathFormulaItem("g11_c2_f7", "Tính chất trung bình nhân", "u_k² = u_{k−1} · u_{k+1}", condition = "k ≥ 2"),
                            MathFormulaItem("g11_c2_f8", "Tổng n số hạng đầu S_n", "u₁ · (1 − qⁿ) / (1 − q)", condition = "q ≠ 1", note = "Nếu q = 1 thì S_n = n·u₁"),
                            MathFormulaItem("g11_c2_f9", "Tổng CSN lùi vô hạn S", "u₁ / (1 − q)", condition = "|q| < 1"),
                            MathFormulaItem("g11_c2_f10", "Tích n số hạng đầu & Lãi kép", "P_n = (u₁·u_n)^{n/2} (u_i > 0)   •   Lãi kép: A_n = A₀·(1 + r)ⁿ")
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
                summary = "Hiệu chỉnh đầu mút, tần số tích lũy, số trung bình, nội suy Trung vị, Tứ phân vị & Mốt",
                accentHex = 0xFFEA580C,
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c3_grouped",
                        title = "Các số đặc trưng của mẫu ghép nhóm",
                        subtitle = "Nhóm [u_m; u_{m+1}) có giá trị đại diện c_m = (u_m + u_{m+1})/2 và tần số n_m",
                        accentHex = 0xFFEA580C,
                        formulas = listOf(
                            MathFormulaItem("g11_c3_f1", "Hiệu chỉnh đầu mút rời rạc", "Đổi [k₁; k₂] (số nguyên) thành [k₁ − 0,5; k₂ + 0,5)"),
                            MathFormulaItem("g11_c3_f2", "Tần số tích lũy cf_k", "cf_k = n₁ + n₂ + … + n_k", note = "Dùng tìm nhóm đầu tiên có cf ≥ n/4, n/2, 3n/4"),
                            MathFormulaItem("g11_c3_f3", "Số trung bình x̄", "(m₁c₁ + m₂c₂ + … + m_k c_k) / n  =  ∑ f_i · c_i", note = "n = ∑ m_i"),
                            MathFormulaItem("g11_c3_f4", "Trung vị M_e (Q₂)", "u_m + [ (n/2 − C) / n_m ] · (u_{m+1} − u_m)", note = "C: tổng tần số các nhóm trước nhóm chứa M_e"),
                            MathFormulaItem("g11_c3_f5", "Tứ phân vị Q₁", "u_p + [ (n/4 − C) / n_p ] · (u_{p+1} − u_p)", note = "Nhóm p chứa phần tử thứ n/4"),
                            MathFormulaItem("g11_c3_f6", "Tứ phân vị Q₃", "u_q + [ (3n/4 − C) / n_q ] · (u_{q+1} − u_q)", note = "Nhóm q chứa phần tử thứ 3n/4"),
                            MathFormulaItem("g11_c3_f7", "Mốt M_o", "u_m + [ (n_m − n_{m−1}) / ((n_m − n_{m−1}) + (n_m − n_{m+1})) ] · (u_{m+1} − u_m)", note = "Quy ước n₀ = 0 nếu m = 1, n_{k+1} = 0 nếu m = k")
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
                summary = "Xác định mặt phẳng, giao tuyến, đường // mặt, mặt // mặt, Thales, hình lăng trụ/chóp cụt & phép chiếu",
                accentHex = 0xFFD97706,
                quickActions = listOf(
                    DocQuickAction.Open3D("Mở 3D Lăng trụ đứng", ShapeType.PRISM3, showAxes = false),
                    DocQuickAction.Open3D("Mở 3D Chóp tứ giác", ShapeType.PYRAMID4, showAxes = false)
                ),
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c4_parallel",
                        title = "Các định lý song song cốt lõi",
                        subtitle = "Nền tảng dựng giao tuyến và thiết diện song song trong hình không gian",
                        accentHex = 0xFFD97706,
                        formulas = listOf(
                            MathFormulaItem("g11_c4_f1", "4 cách xác định 1 mặt phẳng", "3 điểm không thẳng hàng • 1 điểm + 1 đường • 2 đường cắt nhau • 2 đường song song"),
                            MathFormulaItem("g11_c4_f2", "Giao tuyến 3 mặt phẳng", "Ba giao tuyến đôi một song song hoặc đồng quy"),
                            MathFormulaItem("g11_c4_f3", "Giao tuyến chứa 2 đường song song", "a ⊂ (P), b ⊂ (Q), a // b  ⇒  (P) ∩ (Q) = d song song a, b (hoặc trùng)"),
                            MathFormulaItem("g11_c4_f4", "Điều kiện d // (P)", "d ⊄ (P), d // d' và d' ⊂ (P)  ⇒  d // (P)"),
                            MathFormulaItem("g11_c4_f5", "Giao tuyến khi d // (P)", "d // (P), d ⊂ (Q) và (P) ∩ (Q) = Δ  ⇒  Δ // d"),
                            MathFormulaItem("g11_c4_f6", "Điều kiện (P) // (Q)", "a, b ⊂ (P) cắt nhau và a // (Q), b // (Q)  ⇒  (P) // (Q)"),
                            MathFormulaItem("g11_c4_f7", "Mặt phẳng thứ 3 cắt (P)//(Q)", "(R) cắt (P), (Q) theo 2 giao tuyến a, b  ⇒  a // b"),
                            MathFormulaItem("g11_c4_f8", "Định lý Thales không gian", "3 mặt phẳng song song chắn trên 2 cát tuyến các đoạn tương ứng tỉ lệ")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c4_solids",
                        title = "Hình lăng trụ, Hình hộp, Chóp cụt & Phép chiếu song song",
                        subtitle = "Tính chất bảo toàn tỉ số của phép chiếu song song",
                        accentHex = 0xFFF59E0B,
                        formulas = listOf(
                            MathFormulaItem("g11_c4_f9", "Hình lăng trụ & Hình hộp", "Các mặt bên là hình bình hành ; 4 đường chéo hình hộp cắt nhau tại trung điểm mỗi đường"),
                            MathFormulaItem("g11_c4_f10", "Tỉ số diện tích 2 đáy chóp cụt", "S_đáy nhỏ / S_đáy lớn = k²", note = "k là tỉ số đồng dạng các cạnh tương ứng"),
                            MathFormulaItem("g11_c4_f11", "Tính chất phép chiếu song song", "Bảo toàn thẳng hàng, thứ tự, song song và tỉ số đoạn thẳng A'B'/C'D' = AB/CD (khi AB // CD)")
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
                summary = "Giới hạn dãy số, định lý kẹp, giới hạn hàm số, các giới hạn chuẩn e & lượng giác, liên hợp & liên tục",
                accentHex = 0xFFFB7185,
                quickActions = listOf(
                    DocQuickAction.Plot2D("Vẽ hàm phân thức y = (2x+1)/(x−1)", "(2*x + 1)/(x - 1)")
                ),
                groups = listOf(
                    MathFormulaGroup(
                        id = "g11_c5_seq_lim",
                        title = "Giới hạn dãy số & Hàm số cơ bản",
                        subtitle = "Các giới hạn chuẩn, định lý kẹp và phân thức bậc p/q tại vô cực",
                        accentHex = 0xFFFB7185,
                        formulas = listOf(
                            MathFormulaItem("g11_c5_f1", "lim (1 / n^k)  |  lim (c / x^k)", "0", condition = "k ∈ ℕ*, x → ±∞"),
                            MathFormulaItem("g11_c5_f2", "lim qⁿ", "0  (khi |q| < 1)   ;   +∞  (khi q > 1)"),
                            MathFormulaItem("g11_c5_f3", "Định lý kẹp (Sandwich)", "|u_n| ≤ v_n và lim v_n = 0  ⇒  lim u_n = 0"),
                            MathFormulaItem("g11_c5_f4", "Phân thức P_p(x) / Q_q(x) (x → ±∞)", "p < q ⇒ 0  •  p = q ⇒ a_p / b_q  •  p > q ⇒ ±∞"),
                            MathFormulaItem("g11_c5_f5", "Liên hợp bậc 2 & bậc 3", "√A − √B = (A−B)/(√A+√B)   ;   ∛A − ∛B = (A−B)/(∛A²+∛AB+∛B²)"),
                            MathFormulaItem("g11_c5_f6", "Khử dạng 0/0 và ∞/∞", "0/0: phân tích nhân tử (x−x₀) / liên hợp  •  ∞/∞: chia lũy thừa bậc cao nhất")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c5_special_lim",
                        title = "Các giới hạn đặc biệt (Lượng giác, Số e, Mũ – Log)",
                        subtitle = "Rất hay dùng để tính nhanh giới hạn và chứng minh đạo hàm",
                        accentHex = 0xFFF43F5E,
                        formulas = listOf(
                            MathFormulaItem("g11_c5_f7", "lim_{x→0} (sin x / x)  &  (tan x / x)", "1   (tổng quát: lim sin(ax)/(bx) = a/b)"),
                            MathFormulaItem("g11_c5_f8", "lim_{x→0} ((1 − cos x) / x²)", "1 / 2"),
                            MathFormulaItem("g11_c5_f9", "Giới hạn định nghĩa số e", "lim_{x→±∞} (1 + 1/x)^x = e   ;   lim_{x→0} (1 + x)^{1/x} = e"),
                            MathFormulaItem("g11_c5_f10", "Giới hạn mũ & lôgarit tại 0", "lim_{x→0} ((e^x − 1) / x) = 1   ;   lim_{x→0} (ln(1 + x) / x) = 1")
                        )
                    ),
                    MathFormulaGroup(
                        id = "g11_c5_cont",
                        title = "Hàm số liên tục & Chứng minh phương trình có nghiệm",
                        subtitle = "Điều kiện liên tục tại điểm, trên khoảng và định lý giá trị trung gian",
                        accentHex = 0xFFE11D48,
                        formulas = listOf(
                            MathFormulaItem("g11_c5_f11", "Liên tục tại x₀", "lim_{x→x₀⁺} f(x) = lim_{x→x₀⁻} f(x) = f(x₀)"),
                            MathFormulaItem("g11_c5_f12", "Hàm sơ cấp cơ bản", "Đa thức, phân thức hữu tỉ, lượng giác liên tục trên từng khoảng xác định"),
                            MathFormulaItem("g11_c5_f13", "Định lý giá trị trung gian", "f liên tục trên [a; b] và f(a)·f(b) < 0  ⇒  ∃c ∈ (a; b): f(c) = 0")
                        )
                    )
                )
            )
        )
    }

    val chapters: List<MathChapter> by lazy {
        part1Chapters + Grade11CurriculumPart2.chapters
    }
}
