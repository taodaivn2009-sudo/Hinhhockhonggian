package com.example.docs

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TrigDocCategory(val shortLabel: String) {
    ALL("Tất cả"),
    BASIC("Cơ bản"),
    UNIT_CIRCLE("Đường tròn LG"),
    ADDITION("Công thức cộng"),
    DOUBLE_TRIPLE("Nhân đôi – Ba"),
    POWER_REDUCE("Hạ bậc"),
    TRANSFORM("Biến đổi"),
    SPECIAL_ANGLES("Góc liên quan")
}

enum class SpecialAngleKind {
    OPPOSITE,       // α và -α
    SUPPLEMENTARY,  // α và π - α
    COMPLEMENTARY,  // α và π/2 - α
    PLUS_PI         // α và π + α
}

data class TrigFormulaItem(
    val lhs: String,
    val rhs: String,
    val condition: String? = null,
    val note: String? = null
)

data class TrigFormulaGroup(
    val id: String,
    val category: TrigDocCategory,
    val title: String,
    val subtitle: String,
    val accentHex: Long,
    val formulas: List<TrigFormulaItem>,
    val mnemonic: String? = null,
    val specialKind: SpecialAngleKind? = null
)

data class UnitCircleAngle(
    val degrees: Int,
    val radLabel: String,
    val sinLabel: String,
    val cosLabel: String,
    val tanLabel: String,
    val cotLabel: String,
    val quadrantLabel: String
) {
    val radians: Double get() = degrees * PI / 180.0
    val cosVal: Double get() = cos(radians)
    val sinVal: Double get() = sin(radians)
}

object TrigReferenceRepository {

    const val CHAPTER_BADGE = "Chương I"
    const val DOCUMENT_TITLE = "Hàm Số Lượng Giác"
    const val DOCUMENT_SUBTITLE = "Tổng hợp đầy đủ Các Công Thức Lượng Giác, Đường tròn lượng giác, Bảng xét dấu & Góc liên quan đặc biệt"

    val specialCircleAngles: List<UnitCircleAngle> = listOf(
        UnitCircleAngle(0, "0", "0", "1", "0", "Không XĐ", "Trục Ox (+)"),
        UnitCircleAngle(30, "π/6", "1/2", "√3/2", "√3/3", "√3", "Góc phần tư I"),
        UnitCircleAngle(45, "π/4", "√2/2", "√2/2", "1", "1", "Góc phần tư I"),
        UnitCircleAngle(60, "π/3", "√3/2", "1/2", "√3", "√3/3", "Góc phần tư I"),
        UnitCircleAngle(90, "π/2", "1", "0", "Không XĐ", "0", "Trục Oy (+)"),
        UnitCircleAngle(120, "2π/3", "√3/2", "-1/2", "-√3", "-√3/3", "Góc phần tư II"),
        UnitCircleAngle(135, "3π/4", "√2/2", "-√2/2", "-1", "-1", "Góc phần tư II"),
        UnitCircleAngle(150, "5π/6", "1/2", "-√3/2", "-√3/3", "-√3", "Góc phần tư II"),
        UnitCircleAngle(180, "π", "0", "-1", "0", "Không XĐ", "Trục Ox (−)"),
        UnitCircleAngle(210, "7π/6", "-1/2", "-√3/2", "√3/3", "√3", "Góc phần tư III"),
        UnitCircleAngle(225, "5π/4", "-√2/2", "-√2/2", "1", "1", "Góc phần tư III"),
        UnitCircleAngle(240, "4π/3", "-√3/2", "-1/2", "√3", "√3/3", "Góc phần tư III"),
        UnitCircleAngle(270, "3π/2", "-1", "0", "Không XĐ", "0", "Trục Oy (−)"),
        UnitCircleAngle(300, "5π/3", "-√3/2", "1/2", "-√3", "-√3/3", "Góc phần tư IV"),
        UnitCircleAngle(315, "7π/4", "-√2/2", "√2/2", "-1", "-1", "Góc phần tư IV"),
        UnitCircleAngle(330, "11π/6", "-1/2", "√3/2", "-√3/3", "-√3", "Góc phần tư IV")
    )

    /** Quadrant Sign Table (I, II, III, IV) matching the poster's bottom table. */
    data class QuadrantSignRow(val fnName: String, val q1: String, val q2: String, val q3: String, val q4: String)

    val quadrantSignRows: List<QuadrantSignRow> = listOf(
        QuadrantSignRow("cos α", "+", "−", "−", "+"),
        QuadrantSignRow("sin α", "+", "+", "−", "−"),
        QuadrantSignRow("tan α", "+", "−", "+", "−"),
        QuadrantSignRow("cot α", "+", "−", "+", "−")
    )

    val formulaGroups: List<TrigFormulaGroup> = listOf(
        // 1. Công thức cơ bản
        TrigFormulaGroup(
            id = "basic",
            category = TrigDocCategory.BASIC,
            title = "Công thức lượng giác cơ bản",
            subtitle = "6 hằng đẳng thức nền tảng & công thức đổi đơn vị góc",
            accentHex = 0xFF38BDF8,
            formulas = listOf(
                TrigFormulaItem("sin²α + cos²α", "1"),
                TrigFormulaItem("tan α · cot α", "1", condition = "α ≠ kπ/2, k ∈ ℤ"),
                TrigFormulaItem("1 + tan²α", "1 / cos²α", condition = "α ≠ π/2 + kπ, k ∈ ℤ"),
                TrigFormulaItem("1 + cot²α", "1 / sin²α", condition = "α ≠ kπ, k ∈ ℤ"),
                TrigFormulaItem("tan α", "sin α / cos α", condition = "cos α ≠ 0"),
                TrigFormulaItem("cot α", "cos α / sin α", condition = "sin α ≠ 0"),
                TrigFormulaItem("1 rad", "(180 / π)° ≈ 57,3°", note = "Đổi rad → độ"),
                TrigFormulaItem("1°", "(π / 180) rad", note = "Đổi độ → rad")
            )
        ),

        // 2. Công thức cộng
        TrigFormulaGroup(
            id = "addition",
            category = TrigDocCategory.ADDITION,
            title = "Công thức cộng",
            subtitle = "Khai triển sin, cos, tan của tổng và hiệu hai góc (a ± b)",
            accentHex = 0xFF10B981,
            mnemonic = "Sin thì sin cos cos sin • Cos thì cos cos sin sin dấu trừ",
            formulas = listOf(
                TrigFormulaItem("sin(a ± b)", "sin a · cos b ± sin b · cos a"),
                TrigFormulaItem("cos(a ± b)", "cos a · cos b ∓ sin a · sin b"),
                TrigFormulaItem("tan(a ± b)", "(tan a ± tan b) / (1 ∓ tan a · tan b)")
            )
        ),

        // 3. Công thức nhân đôi - nhân ba
        TrigFormulaGroup(
            id = "double_triple",
            category = TrigDocCategory.DOUBLE_TRIPLE,
            title = "Công thức nhân đôi – nhân ba",
            subtitle = "Biểu diễn góc 2a, 3a theo các giá trị lượng giác của góc a",
            accentHex = 0xFFF59E0B,
            formulas = listOf(
                TrigFormulaItem("sin 2a", "2 sin a · cos a"),
                TrigFormulaItem("cos 2a", "cos²a − sin²a = 2cos²a − 1 = 1 − 2sin²a"),
                TrigFormulaItem("tan 2a", "(2 tan a) / (1 − tan²a)"),
                TrigFormulaItem("sin 3a", "3 sin a − 4 sin³a"),
                TrigFormulaItem("cos 3a", "4 cos³a − 3 cos a"),
                TrigFormulaItem("tan 3a", "(3 tan a − tan³a) / (1 − 3 tan²a)")
            )
        ),

        // 4. Công thức hạ bậc
        TrigFormulaGroup(
            id = "power_reduce",
            category = TrigDocCategory.POWER_REDUCE,
            title = "Công thức hạ bậc",
            subtitle = "Đưa bậc hai về bậc nhất của góc nhân đôi 2a",
            accentHex = 0xFFFB7185,
            formulas = listOf(
                TrigFormulaItem("cos²a", "(1 + cos 2a) / 2"),
                TrigFormulaItem("sin²a", "(1 − cos 2a) / 2")
            )
        ),

        // 5. Biến đổi tổng thành tích
        TrigFormulaGroup(
            id = "sum_to_product",
            category = TrigDocCategory.TRANSFORM,
            title = "Công thức biến đổi tổng thành tích",
            subtitle = "Đặt u = a − b, v = a + b để rút gọn tổng / hiệu hai hàm lượng giác",
            accentHex = 0xFF2DD4BF,
            mnemonic = "Cos cộng cos bằng 2 cos cos • Cos trừ cos bằng trừ 2 sin sin • Sin cộng sin bằng 2 sin cos • Sin trừ sin bằng 2 cos sin",
            formulas = listOf(
                TrigFormulaItem("cos u + cos v", "2 cos((u + v)/2) · cos((u − v)/2)"),
                TrigFormulaItem("cos u − cos v", "−2 sin((u + v)/2) · sin((u − v)/2)"),
                TrigFormulaItem("sin u + sin v", "2 sin((u + v)/2) · cos((u − v)/2)"),
                TrigFormulaItem("sin u − sin v", "2 cos((u + v)/2) · sin((u − v)/2)")
            )
        ),

        // 6. Biến đổi tích thành tổng
        TrigFormulaGroup(
            id = "product_to_sum",
            category = TrigDocCategory.TRANSFORM,
            title = "Công thức biến đổi tích thành tổng",
            subtitle = "Chuyển tích sin/cos sang tổng hiệu để dễ tính tích phân & rút gọn",
            accentHex = 0xFFF97316,
            formulas = listOf(
                TrigFormulaItem("cos a · cos b", "½ [ cos(a − b) + cos(a + b) ]"),
                TrigFormulaItem("sin a · sin b", "½ [ cos(a − b) − cos(a + b) ]"),
                TrigFormulaItem("sin a · cos b", "½ [ sin(a − b) + sin(a + b) ]")
            )
        ),

        // 7a. Góc đối nhau
        TrigFormulaGroup(
            id = "angle_opposite",
            category = TrigDocCategory.SPECIAL_ANGLES,
            title = "Góc đối nhau (α và −α)",
            subtitle = "Hai điểm M(α) và N(−α) đối xứng qua trục Ox",
            accentHex = 0xFFF43F5E,
            mnemonic = "Cos đối — chỉ cos giữ nguyên dấu",
            specialKind = SpecialAngleKind.OPPOSITE,
            formulas = listOf(
                TrigFormulaItem("cos(−α)", "cos α"),
                TrigFormulaItem("sin(−α)", "−sin α"),
                TrigFormulaItem("tan(−α)", "−tan α"),
                TrigFormulaItem("cot(−α)", "−cot α")
            )
        ),

        // 7b. Góc bù nhau
        TrigFormulaGroup(
            id = "angle_supplementary",
            category = TrigDocCategory.SPECIAL_ANGLES,
            title = "Góc bù nhau (α và π − α)",
            subtitle = "Hai điểm M(α) và N(π − α) đối xứng qua trục Oy",
            accentHex = 0xFF38BDF8,
            mnemonic = "Sin bù — chỉ sin giữ nguyên dấu",
            specialKind = SpecialAngleKind.SUPPLEMENTARY,
            formulas = listOf(
                TrigFormulaItem("sin(π − α)", "sin α"),
                TrigFormulaItem("cos(π − α)", "−cos α"),
                TrigFormulaItem("tan(π − α)", "−tan α"),
                TrigFormulaItem("cot(π − α)", "−cot α")
            )
        ),

        // 7c. Góc phụ nhau
        TrigFormulaGroup(
            id = "angle_complementary",
            category = TrigDocCategory.SPECIAL_ANGLES,
            title = "Góc phụ nhau (α và π/2 − α)",
            subtitle = "Hai điểm M(α) và N(π/2 − α) đối xứng qua phân giác y = x",
            accentHex = 0xFFA855F7,
            mnemonic = "Phụ chéo — sin ↔ cos, tan ↔ cot",
            specialKind = SpecialAngleKind.COMPLEMENTARY,
            formulas = listOf(
                TrigFormulaItem("sin(π/2 − α)", "cos α"),
                TrigFormulaItem("cos(π/2 − α)", "sin α"),
                TrigFormulaItem("tan(π/2 − α)", "cot α"),
                TrigFormulaItem("cot(π/2 − α)", "tan α")
            )
        ),

        // 7d. Góc hơn kém π
        TrigFormulaGroup(
            id = "angle_plus_pi",
            category = TrigDocCategory.SPECIAL_ANGLES,
            title = "Góc hơn kém π (α và π + α)",
            subtitle = "Hai điểm M(α) và N(π + α) đối xứng qua gốc tọa độ O",
            accentHex = 0xFF3B82F6,
            mnemonic = "Khác pi tang, cotang — tan và cot giữ nguyên dấu",
            specialKind = SpecialAngleKind.PLUS_PI,
            formulas = listOf(
                TrigFormulaItem("sin(π + α)", "−sin α"),
                TrigFormulaItem("cos(π + α)", "−cos α"),
                TrigFormulaItem("tan(π + α)", "tan α"),
                TrigFormulaItem("cot(π + α)", "cot α")
            )
        )
    )

    private fun normalizeSearch(text: String): String = text
        .lowercase()
        .replace('−', '-')
        .replace('–', '-')
        .replace('—', '-')
        .replace('·', '*')
        .replace('×', '*')
        .replace("π", "pi")
        .replace("²", "2")
        .replace("³", "3")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun filterGroups(category: TrigDocCategory, query: String): List<TrigFormulaGroup> {
        val q = normalizeSearch(query)
        return formulaGroups.filter { grp ->
            val catOk = category == TrigDocCategory.ALL || grp.category == category
            if (!catOk) return@filter false
            if (q.isEmpty()) return@filter true
            normalizeSearch(grp.title).contains(q) ||
                normalizeSearch(grp.subtitle).contains(q) ||
                (grp.mnemonic?.let { normalizeSearch(it).contains(q) } == true) ||
                grp.formulas.any { f ->
                    normalizeSearch(f.lhs).contains(q) ||
                        normalizeSearch(f.rhs).contains(q) ||
                        (f.condition?.let { normalizeSearch(it).contains(q) } == true) ||
                        (f.note?.let { normalizeSearch(it).contains(q) } == true)
                }
        }
    }

    fun degreesToRadians(deg: Double): Double = deg * PI / 180.0
    fun radiansToDegrees(rad: Double): Double = rad * 180.0 / PI

    /** Formats a degree value cleanly as a reduced multiple of π when exact (e.g., 150° -> "5π/6"). */
    fun formatDegreesAsPiFraction(deg: Double): String? {
        if (!deg.isFinite()) return null
        val ratio = deg / 180.0
        for (den in listOf(1, 2, 3, 4, 6, 8, 12)) {
            val numDouble = ratio * den
            val numInt = numDouble.roundToInt()
            if (abs(numDouble - numInt) < 1e-4) {
                if (numInt == 0) return "0"
                val g = gcd(abs(numInt), den)
                val n = numInt / g
                val d = den / g
                val sign = if (n < 0) "−" else ""
                val absN = abs(n)
                val numPart = if (absN == 1) "π" else "${absN}π"
                return if (d == 1) "$sign$numPart" else "$sign$numPart/$d"
            }
        }
        return null
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0) 1 else x
    }
}
