package com.example.docs

/**
 * Exhaustive Grade 10 Mathematics formula compendium aligned with Vietnam GDPT 2018.
 * Part 2: Chapters VI – IX (Functions & Quadratics, Plane Analytic Geometry Oxy & Conics,
 * Combinatorics & Newton Binomial, and Classical Probability).
 */
object Grade10CurriculumPart2 {

    val chapters: List<MathChapter> = listOf(
        // ==================== CHƯƠNG VI ====================
        MathChapter(
            id = "g10_c6",
            grade = MathGrade.GRADE_10,
            chapterNumber = "Chương VI",
            title = "Hàm số, Đồ thị và Ứng dụng",
            summary = "TXĐ, chẵn/lẻ, tịnh tiến đồ thị, Parabol, dấu tam thức, Vi-ét đối xứng, BĐT Cauchy & Bunhiacopxki",
            accentHex = 0xFF06B6D4,
            quickActions = listOf(
                DocQuickAction.Plot2D("Vẽ Parabol y = x² − 2x − 3", "x^2 - 2*x - 3"),
                DocQuickAction.Plot2D("Vẽ y = −x² + 4x", "-x^2 + 4*x")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c6_general",
                    title = "Đại cương hàm số, Chẵn – Lẻ & Phép biến đổi đồ thị",
                    subtitle = "Điều kiện xác định, hàm bậc nhất và các phép tịnh tiến/đối xứng đồ thị",
                    accentHex = 0xFF06B6D4,
                    formulas = listOf(
                        MathFormulaItem("g10_c6_f1", "Điều kiện tập xác định", "1/A ⇒ A ≠ 0  •  √A ⇒ A ≥ 0  •  1/√A ⇒ A > 0"),
                        MathFormulaItem("g10_c6_f2", "Hàm chẵn / Hàm lẻ (∀x ∈ D ⇒ −x ∈ D)", "Chẵn: f(−x) = f(x) (đối xứng Oy)  •  Lẻ: f(−x) = −f(x) (đối xứng O)"),
                        MathFormulaItem("g10_c6_f3", "Hàm bậc nhất y = ax + b (a ≠ 0)", "a > 0: đồng biến ; a < 0: nghịch biến  •  d₁ ⊥ d₂ ⇔ a₁a₂ = −1"),
                        MathFormulaItem("g10_c6_f4", "Tịnh tiến y = f(x − p) + q", "Dịch đồ thị f(x) sang phải p đơn vị và lên trên q đơn vị"),
                        MathFormulaItem("g10_c6_f5", "Đồ thị y = |f(x)|  &  y = f(|x|)", "|f(x)|: giữ trên Ox, lật dưới lên  •  f(|x|): giữ phải Oy, lấy đối xứng sang trái")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c6_quadratic",
                    title = "Hàm số bậc hai, Nghiệm bậc hai & Hệ thức Vi-ét",
                    subtitle = "Parabol y = ax² + bx + c (a ≠ 0), Δ = b² − 4ac, Δ' = b'² − ac",
                    accentHex = 0xFF0EA5E9,
                    formulas = listOf(
                        MathFormulaItem("g10_c6_f6", "Đỉnh I & Trục đối xứng Parabol", "I( −b/(2a) ; −Δ/(4a) )   ;   Trục đối xứng: x = −b / (2a)"),
                        MathFormulaItem("g10_c6_f7", "Công thức nghiệm bậc hai", "x₁,₂ = (−b ± √Δ) / (2a)  =  (−b' ± √Δ') / a", condition = "Δ ≥ 0, b' = b/2"),
                        MathFormulaItem("g10_c6_f8", "Định lý Vi-ét (S, P)", "S = x₁ + x₂ = −b/a   ;   P = x₁ · x₂ = c/a", condition = "Δ ≥ 0"),
                        MathFormulaItem("g10_c6_f9", "Biểu thức đối xứng theo S, P", "x₁²+x₂² = S²−2P  ;  x₁³+x₂³ = S³−3SP  ;  |x₁−x₂| = √(S²−4P) = √Δ/|a|"),
                        MathFormulaItem("g10_c6_f10", "So sánh dấu 2 nghiệm", "Trái dấu ⇔ ac < 0  •  2 nghiệm dương ⇔ Δ≥0, S>0, P>0  •  2 âm ⇔ Δ≥0, S<0, P>0")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c6_sign",
                    title = "Dấu tam thức bậc hai & PT quy về bậc hai",
                    subtitle = "Điều kiện tam thức luôn dương/âm trên ℝ và khử căn bậc hai",
                    accentHex = 0xFF38BDF8,
                    mnemonic = "Trong trái – Ngoài cùng (khi Δ > 0 có hai nghiệm phân biệt x₁ < x₂)",
                    formulas = listOf(
                        MathFormulaItem("g10_c6_f11", "Dấu tam thức f(x) = ax² + bx + c", "Δ < 0: cùng dấu a ∀x  •  Δ = 0: cùng dấu a ∀x ≠ −b/(2a)  •  Δ > 0: trong trái, ngoài cùng"),
                        MathFormulaItem("g10_c6_f12", "f(x) > 0, ∀x ∈ ℝ  (hoặc ≥ 0)", "a > 0 và Δ < 0   (≥ 0 thì Δ ≤ 0)"),
                        MathFormulaItem("g10_c6_f13", "f(x) < 0, ∀x ∈ ℝ  (hoặc ≤ 0)", "a < 0 và Δ < 0   (≤ 0 thì Δ ≤ 0)"),
                        MathFormulaItem("g10_c6_f14", "√A = √B   |   √A = B", "A = B và A ≥ 0   |   B ≥ 0 và A = B²"),
                        MathFormulaItem("g10_c6_f15", "√A < B   |   √A > B", "(A ≥ 0, B > 0, A < B²)   |   (B < 0, A ≥ 0) hoặc (B ≥ 0, A > B²)")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c6_ineq",
                    title = "Bất đẳng thức kinh điển (AM–GM & Bunhiacopxki)",
                    subtitle = "Công cụ tìm cực trị đại số xuyên suốt cấp 3",
                    accentHex = 0xFF2DD4BF,
                    formulas = listOf(
                        MathFormulaItem("g10_c6_f16", "Cauchy (AM–GM) 2 & 3 số", "a + b ≥ 2√(ab)   ;   a + b + c ≥ 3∛(abc)", condition = "a, b, c ≥ 0", note = "Dấu = khi các số bằng nhau"),
                        MathFormulaItem("g10_c6_f17", "Hệ quả Cauchy hay dùng", "a² + b² ≥ 2ab  ;  (a+b)² ≥ 4ab  ;  1/a + 1/b ≥ 4/(a+b)  (a,b > 0)"),
                        MathFormulaItem("g10_c6_f18", "Bunhiacopxki (Cauchy–Schwarz)", "(ax + by)² ≤ (a² + b²)(x² + y²)", note = "Dấu = khi a/x = b/y")
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
            summary = "Tọa độ vectơ, diện tích tam giác, đường thẳng, phân giác, đường tròn & chi tiết 3 đường Conic",
            accentHex = 0xFF0284C7,
            quickActions = listOf(
                DocQuickAction.Plot2D("Vẽ đường thẳng y = 2x − 1", "2*x - 1"),
                DocQuickAction.Plot2D("Vẽ nửa đường tròn R = 3", "sqrt(9 - x^2)")
            ),
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c7_vec",
                    title = "Tọa độ điểm, Vectơ & Diện tích trong Oxy",
                    subtitle = "Cho A(x_A; y_A), B(x_B; y_B), u⃗(x₁; y₁), v⃗(x₂; y₂)",
                    accentHex = 0xFF0284C7,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f1", "AB⃗ & Độ dài AB", "AB⃗ = (x_B−x_A; y_B−y_A)  ;  AB = √[(x_B−x_A)² + (y_B−y_A)²]"),
                        MathFormulaItem("g10_c7_f2", "Trung điểm I & Trọng tâm G", "I((x_A+x_B)/2; (y_A+y_B)/2)  ;  G((x_A+x_B+x_C)/3; (y_A+y_B+y_C)/3)"),
                        MathFormulaItem("g10_c7_f3", "Tích vô hướng, Vuông góc & Cùng phương", "u⃗·v⃗ = x₁x₂+y₁y₂  •  u⃗⊥v⃗ ⇔ x₁x₂+y₁y₂=0  •  u⃗//v⃗ ⇔ x₁y₂−x₂y₁=0"),
                        MathFormulaItem("g10_c7_f4", "cos(u⃗, v⃗)", "(x₁x₂ + y₁y₂) / [ √(x₁²+y₁²) · √(x₂²+y₂²) ]"),
                        MathFormulaItem("g10_c7_f5", "Diện tích △ABC theo tọa độ", "S = ½ · | (x_B − x_A)(y_C − y_A) − (x_C − x_A)(y_B − y_A) |")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c7_line",
                    title = "Đường thẳng, Vị trí tương đối, Góc, Khoảng cách & Phân giác",
                    subtitle = "VTCP u⃗(u₁; u₂) ↔ VTPT n⃗(−u₂; u₁) = (a; b)",
                    accentHex = 0xFF38BDF8,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f6", "PT tổng quát & Đoạn chắn", "a(x−x₀)+b(y−y₀)=0 ⇔ ax+by+c=0   ;   Đoạn chắn: x/a + y/b = 1"),
                        MathFormulaItem("g10_c7_f7", "PT tham số & Chính tắc", "x = x₀ + u₁t, y = y₀ + u₂t   ;   (x − x₀)/u₁ = (y − y₀)/u₂"),
                        MathFormulaItem("g10_c7_f8", "Vị trí tương đối Δ₁, Δ₂", "Cắt: a₁/a₂≠b₁/b₂ • Song song: a₁/a₂=b₁/b₂≠c₁/c₂ • Trùng: =c₁/c₂ • Vuông: a₁a₂+b₁b₂=0"),
                        MathFormulaItem("g10_c7_f9", "Khoảng cách d(M₀; Δ)", "|ax₀ + by₀ + c| / √(a² + b²)"),
                        MathFormulaItem("g10_c7_f10", "Góc giữa Δ₁ và Δ₂", "cos(Δ₁, Δ₂) = |a₁a₂ + b₁b₂| / [ √(a₁²+b₁²) · √(a₂²+b₂²) ]"),
                        MathFormulaItem("g10_c7_f11", "PT 2 đường phân giác góc (Δ₁, Δ₂)", "(a₁x + b₁y + c₁)/√(a₁²+b₁²) = ± (a₂x + b₂y + c₂)/√(a₂²+b₂²)")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c7_circle",
                    title = "Đường tròn, Tiếp tuyến & Dây cung",
                    subtitle = "Tâm I(a; b), bán kính R",
                    accentHex = 0xFF10B981,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f12", "PT chính tắc (C)", "(x − a)² + (y − b)² = R²"),
                        MathFormulaItem("g10_c7_f13", "PT khai triển (C)", "x² + y² − 2ax − 2by + c = 0", condition = "a² + b² − c > 0", note = "R = √(a² + b² − c)"),
                        MathFormulaItem("g10_c7_f14", "Tiếp tuyến tại M₀(x₀; y₀) ∈ (C)", "(x₀ − a)(x − x₀) + (y₀ − b)(y − y₀) = 0"),
                        MathFormulaItem("g10_c7_f15", "Đường thẳng Δ với (C)", "Tiếp xúc ⇔ d(I; Δ) = R  •  Cắt theo dây cung AB = 2√(R² − d²)")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c7_conic",
                    title = "Ba đường Conic: Elip, Hypebol & Parabol",
                    subtitle = "Đầy đủ tiêu điểm, tâm sai e, bán kính qua tiêu, tiệm cận và đường chuẩn",
                    accentHex = 0xFF14B8A6,
                    formulas = listOf(
                        MathFormulaItem("g10_c7_f16", "Elip (E): x²/a² + y²/b² = 1", "c² = a² − b² ; Trục lớn 2a, nhỏ 2b ; Tâm sai e = c/a < 1 ; F₁,₂(±c; 0)", condition = "a > b > 0"),
                        MathFormulaItem("g10_c7_f17", "Bán kính qua tiêu & Đường chuẩn Elip", "MF₁ = a + (c/a)x  ;  MF₂ = a − (c/a)x  ;  Đường chuẩn: x = ± a/e"),
                        MathFormulaItem("g10_c7_f18", "Hypebol (H): x²/a² − y²/b² = 1", "c² = a² + b² ; Trục thực 2a, ảo 2b ; Tâm sai e = c/a > 1 ; F₁,₂(±c; 0)", condition = "a, b > 0"),
                        MathFormulaItem("g10_c7_f19", "Tiệm cận & Bán kính qua tiêu Hypebol", "2 tiệm cận: y = ±(b/a)x  ;  MF₁,₂ = |a ± (c/a)x|  ;  |MF₁ − MF₂| = 2a"),
                        MathFormulaItem("g10_c7_f20", "Parabol (P): y² = 2px", "Tiêu điểm F(p/2; 0) ; Đường chuẩn x = −p/2 ; MF = x + p/2 ; e = 1", condition = "p > 0")
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
            summary = "Quy tắc cộng–nhân, Hoán vị (thường/lặp/vòng), Chỉnh hợp, Tổ hợp, Nhị thức Newton & tổng hệ số",
            accentHex = 0xFF059669,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c8_comb",
                    title = "Quy tắc đếm, Hoán vị, Chỉnh hợp & Tổ hợp",
                    subtitle = "Phân biệt sắp thứ tự (P, A), không thứ tự (C), lặp và vòng tròn",
                    accentHex = 0xFF059669,
                    mnemonic = "Chia trường hợp → Cộng • Chia bước liên tiếp → Nhân • Có thứ tự → A, P • Không thứ tự → C",
                    formulas = listOf(
                        MathFormulaItem("g10_c8_f1", "Quy tắc cộng & Quy tắc nhân", "m + n cách (2 phương án rời nhau)   •   m · n cách (2 công đoạn liên tiếp)"),
                        MathFormulaItem("g10_c8_f2", "Hoán vị P_n & Hoán vị vòng tròn", "P_n = n! = 1·2·…·n  (0! = 1)   •   Xếp bàn tròn: (n − 1)!"),
                        MathFormulaItem("g10_c8_f3", "Hoán vị lặp", "n! / (k₁! · k₂! · … · k_m!)", note = "k₁ + … + k_m = n"),
                        MathFormulaItem("g10_c8_f4", "Chỉnh hợp A_n^k & Chỉnh hợp lặp", "A_n^k = n! / (n − k)!  (1 ≤ k ≤ n)   •   Chỉnh hợp lặp: n^k"),
                        MathFormulaItem("g10_c8_f5", "Tổ hợp C_n^k", "n! / [ k! · (n − k)! ]", condition = "0 ≤ k ≤ n"),
                        MathFormulaItem("g10_c8_f6", "Hệ thức đối xứng & Công thức Pascal", "C_n^k = C_n^{n−k}   ;   C_n^k + C_n^{k+1} = C_{n+1}^{k+1}"),
                        MathFormulaItem("g10_c8_f7", "Số đường chéo đa giác lồi n đỉnh", "C_n^2 − n = n(n − 3) / 2")
                    )
                ),
                MathFormulaGroup(
                    id = "g10_c8_newton",
                    title = "Nhị thức Newton & Các tổng tổ hợp kinh điển",
                    subtitle = "Khai triển (a + b)ⁿ và công thức rút gọn tổng hệ số hay thi",
                    accentHex = 0xFF10B981,
                    formulas = listOf(
                        MathFormulaItem("g10_c8_f8", "Khai triển (a + b)ⁿ", "∑_{k=0..n} C_n^k · a^{n−k} · b^k", note = "Số hạng tổng quát T_{k+1} = C_n^k · a^{n−k} · b^k"),
                        MathFormulaItem("g10_c8_f9", "(a + b)⁴  &  (a + b)⁵", "a⁴+4a³b+6a²b²+4ab³+b⁴   |   a⁵+5a⁴b+10a³b²+10a²b³+5ab⁴+b⁵"),
                        MathFormulaItem("g10_c8_f10", "Tổng tất cả & Đan dấu", "C_n^0 + C_n^1 + … + C_n^n = 2ⁿ   ;   C_n^0 − C_n^1 + … + (−1)ⁿC_n^n = 0"),
                        MathFormulaItem("g10_c8_f11", "Tổng chỉ số chẵn / lẻ", "C_n^0 + C_n^2 + … = C_n^1 + C_n^3 + … = 2^{n−1}"),
                        MathFormulaItem("g10_c8_f12", "Hệ thức rút gọn đạo hàm / nguyên hàm", "k·C_n^k = n·C_{n−1}^{k−1}   ;   C_n^1 + 2C_n^2 + … + nC_n^n = n·2^{n−1}"),
                        MathFormulaItem("g10_c8_f13", "Hệ thức chia (k + 1)", "C_n^k / (k + 1) = C_{n+1}^{k+1} / (n + 1)")
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
            summary = "Không gian mẫu Ω, biến cố đối, De Morgan, công thức chọn nhóm (siêu bội) & mẫu hay gặp",
            accentHex = 0xFF0F766E,
            groups = listOf(
                MathFormulaGroup(
                    id = "g10_c9_prob",
                    title = "Định nghĩa cổ điển, Phép toán biến cố & Mẫu chuẩn",
                    subtitle = "Các kết quả đồng khả năng trong không gian mẫu hữu hạn Ω",
                    accentHex = 0xFF0F766E,
                    formulas = listOf(
                        MathFormulaItem("g10_c9_f1", "Xác suất cổ điển P(A)", "n(A) / n(Ω)", note = "0 ≤ P(A) ≤ 1 ; P(Ω) = 1 ; P(∅) = 0"),
                        MathFormulaItem("g10_c9_f2", "Biến cố đối P(Ā)", "1 − P(A)", note = "Hiệu quả nhất với bài toán 'có ít nhất 1...'"),
                        MathFormulaItem("g10_c9_f3", "A, B xung khắc (A ∩ B = ∅)", "P(A ∪ B) = P(A) + P(B)"),
                        MathFormulaItem("g10_c9_f4", "Luật De Morgan cho biến cố", "overline(A ∪ B) = Ā ∩ B̄   ;   overline(A ∩ B) = Ā ∪ B̄"),
                        MathFormulaItem("g10_c9_f5", "Chọn k vật có đúng r vật loại A", "P = (C_m^r · C_{n−m}^{k−r}) / C_n^k", note = "Tổng n vật gồm m loại A và (n − m) loại khác"),
                        MathFormulaItem("g10_c9_f6", "Không gian mẫu chuẩn", "Tung n đồng xu: 2ⁿ  •  Gieo n xúc xắc: 6ⁿ  •  Rút k lá bài: C_{52}^k")
                    )
                )
            )
        )
    )
}
