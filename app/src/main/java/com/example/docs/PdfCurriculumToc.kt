package com.example.docs

import android.content.Context
import java.text.Normalizer

const val PDF_ASSET_PATH = "docs/toan_10_11_12_dpad.pdf"
const val PDF_TOTAL_PAGES = 388
const val PDF_BOOK_TITLE = "Tóm Tắt Kiến Thức Toán 10 – 11 – 12"
const val PDF_BOOK_SUBTITLE = "Tài liệu tổng hợp DPAD · $PDF_TOTAL_PAGES trang · Offline"

enum class PdfGrade(
    val id: String,
    val shortLabel: String,
    val title: String,
    val startPage: Int,
    val endPage: Int,
    val accentHex: Long
) {
    GRADE_10("grade_10", "Lớp 10", "Toán Lớp 10", 18, 110, 0xFF38BDF8),
    GRADE_11("grade_11", "Lớp 11", "Toán Lớp 11", 111, 255, 0xFFA78BFA),
    GRADE_12("grade_12", "Lớp 12", "Toán Lớp 12", 256, 387, 0xFF34D399)
}

enum class PdfDocsTab(val key: String, val label: String) {
    GRADE_10("grade_10", "Lớp 10"),
    GRADE_11("grade_11", "Lớp 11"),
    GRADE_12("grade_12", "Lớp 12"),
    BOOKMARKS("bookmarks", "Đánh dấu");

    fun toGradeOrNull(): PdfGrade? = when (this) {
        GRADE_10 -> PdfGrade.GRADE_10
        GRADE_11 -> PdfGrade.GRADE_11
        GRADE_12 -> PdfGrade.GRADE_12
        BOOKMARKS -> null
    }

    companion object {
        fun fromKey(key: String?): PdfDocsTab = entries.firstOrNull { it.key == key } ?: GRADE_12
    }
}

data class PdfSubTopic(
    val title: String,
    val page: Int
)

data class PdfTopicEntry(
    val id: String,
    val grade: PdfGrade,
    val indexLabel: String,
    val title: String,
    val startPage: Int,
    val endPage: Int,
    val summary: String,
    val accentHex: Long,
    val subTopics: List<PdfSubTopic> = emptyList()
) {
    val pageRangeText: String
        get() = if (startPage == endPage) "Tr. $startPage" else "Tr. $startPage – $endPage"
}

data class PdfTopicSearchHit(
    val topic: PdfTopicEntry,
    val matchedSubTopic: PdfSubTopic? = null
) {
    val targetPage: Int get() = matchedSubTopic?.page ?: topic.startPage
}

/**
 * Exact interactive Table of Contents extracted from the bundled 388-page PDF:
 * "Tóm Tắt Kiến Thức Toán 10 – 11 – 12 (DPAD)".
 * Every page number is 1-based and matches the PDF's printed page footer 1:1.
 */
object PdfCurriculumRepository {

    val grade10Topics: List<PdfTopicEntry> = listOf(
        PdfTopicEntry(
            id = "g10_t01", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 01",
            title = "Mệnh đề", startPage = 18, endPage = 21,
            summary = "Mệnh đề đúng/sai, chứa biến, phủ định, kéo theo, đảo, tương đương, kí hiệu ∀ và ∃.",
            accentHex = 0xFF38BDF8,
            subTopics = listOf(
                PdfSubTopic("Khái niệm & Kiến thức cũ", 18),
                PdfSubTopic("Mệnh đề chứa biến & Phủ định", 19),
                PdfSubTopic("Kéo theo, Đảo, Tương đương & ∀, ∃", 21)
            )
        ),
        PdfTopicEntry(
            id = "g10_t02", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 02",
            title = "Tập hợp", startPage = 22, endPage = 25,
            summary = "Các tập số quen thuộc, tập con, biểu đồ Ven, giao – hợp – hiệu – phần bù, tập con của ℝ.",
            accentHex = 0xFF0EA5E9,
            subTopics = listOf(
                PdfSubTopic("Kí hiệu & Các tập số", 22),
                PdfSubTopic("Tập con & Biểu đồ Ven", 23),
                PdfSubTopic("Các phép toán tập hợp", 24),
                PdfSubTopic("Các tập con của ℝ", 25)
            )
        ),
        PdfTopicEntry(
            id = "g10_t03", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 03",
            title = "Bất phương trình bậc nhất hai ẩn", startPage = 26, endPage = 28,
            summary = "Miền nghiệm BPT & hệ BPT bậc nhất hai ẩn, cực trị F = ax + by và bài toán kinh tế.",
            accentHex = 0xFF22D3EE,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa & Hệ BPT bậc nhất 2 ẩn", 26),
                PdfSubTopic("GTLN – GTNN của F = ax + by", 27),
                PdfSubTopic("Bài toán kinh tế quy hoạch tuyến tính", 28)
            )
        ),
        PdfTopicEntry(
            id = "g10_t04", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 04",
            title = "Hàm số và Đồ thị", startPage = 29, endPage = 32,
            summary = "Định nghĩa, tập xác định, tập giá trị, đồ thị hàm số và tính đồng biến – nghịch biến.",
            accentHex = 0xFF60A5FA,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa & Tập xác định", 29),
                PdfSubTopic("Đồ thị hàm số", 30),
                PdfSubTopic("Sự biến thiên của hàm số", 31)
            )
        ),
        PdfTopicEntry(
            id = "g10_t05", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 05",
            title = "Hàm số bậc hai & Định lý Vi-ét", startPage = 33, endPage = 35,
            summary = "Đỉnh, trục đối xứng, bảng biến thiên Parabol, phương trình bậc hai, Vi-ét và điều kiện nghiệm.",
            accentHex = 0xFF3B82F6,
            subTopics = listOf(
                PdfSubTopic("Đặc điểm đồ thị hàm bậc 2", 33),
                PdfSubTopic("PT bậc 2 & Hệ thức Vi-ét", 34),
                PdfSubTopic("Điều kiện nghiệm & Biểu diễn parabol", 35)
            )
        ),
        PdfTopicEntry(
            id = "g10_t06", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 06",
            title = "Giá trị lượng giác từ 0° đến 180°", startPage = 36, endPage = 38,
            summary = "Nửa đường tròn đơn vị, góc phụ – góc bù, bảng giá trị đặc biệt và hệ thức lượng giác cơ bản.",
            accentHex = 0xFF818CF8,
            subTopics = listOf(
                PdfSubTopic("Nửa đường tròn đơn vị", 36),
                PdfSubTopic("Góc phụ & Góc bù", 37),
                PdfSubTopic("Bảng giá trị đặc biệt & Hệ thức cơ bản", 38)
            )
        ),
        PdfTopicEntry(
            id = "g10_t07", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 07",
            title = "Hệ thức lượng trong tam giác vuông", startPage = 39, endPage = 39,
            summary = "Pythagore, đường cao, hình chiếu cạnh góc vuông và tỉ số lượng giác trong tam giác vuông.",
            accentHex = 0xFFA78BFA
        ),
        PdfTopicEntry(
            id = "g10_t08", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 08",
            title = "Hệ thức lượng trong tam giác tùy ý", startPage = 40, endPage = 44,
            summary = "Định lý Côsin, Định lý Sin, công thức trung tuyến – phân giác, diện tích (Heron) và đa giác đặc biệt.",
            accentHex = 0xFFC084FC,
            subTopics = listOf(
                PdfSubTopic("Định lý Côsin", 40),
                PdfSubTopic("Định lý Sin, Trung tuyến & Phân giác", 41),
                PdfSubTopic("Diện tích tam giác & Đa giác đặc biệt", 42),
                PdfSubTopic("Độ dài đặc biệt trong hình phẳng", 43)
            )
        ),
        PdfTopicEntry(
            id = "g10_t09", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 09",
            title = "Vectơ", startPage = 45, endPage = 49,
            summary = "Tổng – hiệu vectơ, tích vectơ với một số, hệ thức trung điểm, trọng tâm và điều kiện thẳng hàng.",
            accentHex = 0xFF2DD4BF,
            subTopics = listOf(
                PdfSubTopic("Khái niệm vectơ", 45),
                PdfSubTopic("Quy tắc cộng vectơ", 47),
                PdfSubTopic("Hiệu, Tích với số & Trung điểm/Trọng tâm", 48),
                PdfSubTopic("Cùng phương & Ba điểm thẳng hàng", 49)
            )
        ),
        PdfTopicEntry(
            id = "g10_t10", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 10",
            title = "Tích vô hướng của hai vectơ", startPage = 50, endPage = 53,
            summary = "Góc giữa hai vectơ, tích vô hướng, tính chất hình chiếu và các bài toán tập hợp điểm.",
            accentHex = 0xFF14B8A6,
            subTopics = listOf(
                PdfSubTopic("Góc & Công thức tích vô hướng", 50),
                PdfSubTopic("Tính chất & Hình chiếu", 51),
                PdfSubTopic("Tập hợp điểm liên quan tích vô hướng", 52)
            )
        ),
        PdfTopicEntry(
            id = "g10_t11", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 11",
            title = "Số gần đúng và Sai số", startPage = 54, endPage = 55,
            summary = "Các hàng số, sai số tuyệt đối, sai số tương đối và quy tắc làm tròn số.",
            accentHex = 0xFF34D399
        ),
        PdfTopicEntry(
            id = "g10_t12", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 12",
            title = "Thống kê không ghép nhóm", startPage = 56, endPage = 71,
            summary = "Tần số, số trung bình, trung vị, tứ phân vị, mốt, khoảng biến thiên, phương sai, độ lệch chuẩn & bấm máy.",
            accentHex = 0xFF10B981,
            subTopics = listOf(
                PdfSubTopic("Tần số, Tần suất & Số trung bình", 56),
                PdfSubTopic("Trung vị & Tứ phân vị", 58),
                PdfSubTopic("Mốt & Khoảng biến thiên", 62),
                PdfSubTopic("Khoảng tứ phân vị", 65),
                PdfSubTopic("Phương sai, Độ lệch chuẩn & Ngoại lệ", 68),
                PdfSubTopic("Hướng dẫn bấm máy tính thống kê", 71)
            )
        ),
        PdfTopicEntry(
            id = "g10_t13", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 13",
            title = "Dấu của tam thức bậc hai", startPage = 72, endPage = 75,
            summary = "Định lý xét dấu tam thức bậc hai, điều kiện luôn âm/dương với mọi x và giải BPT bậc hai.",
            accentHex = 0xFFF59E0B,
            subTopics = listOf(
                PdfSubTopic("Định lý dấu tam thức bậc 2", 72),
                PdfSubTopic("Điều kiện luôn âm/dương & BPT bậc 2", 74)
            )
        ),
        PdfTopicEntry(
            id = "g10_t14", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 14",
            title = "Phương trình quy về phương trình bậc hai", startPage = 76, endPage = 76,
            summary = "Cách giải phương trình chứa căn thức √(ax²+bx+c) = √(dx²+ex+f) và √(ax²+bx+c) = dx + e.",
            accentHex = 0xFFFBBF24
        ),
        PdfTopicEntry(
            id = "g10_t15", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 15",
            title = "Đại số tổ hợp & Nhị thức Newton", startPage = 77, endPage = 87,
            summary = "Quy tắc cộng – nhân, giai thừa, hoán vị, chỉnh hợp, tổ hợp, tam giác Pascal và khai triển Newton.",
            accentHex = 0xFFFB923C,
            subTopics = listOf(
                PdfSubTopic("Quy tắc cộng & Quy tắc nhân", 77),
                PdfSubTopic("Giai thừa & Hoán vị", 78),
                PdfSubTopic("Chỉnh hợp", 80),
                PdfSubTopic("Tổ hợp & Các công thức tính chất", 82),
                PdfSubTopic("Tam giác Pascal & Nhị thức Newton", 87)
            )
        ),
        PdfTopicEntry(
            id = "g10_t16", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 16",
            title = "Phương pháp tọa độ trong mặt phẳng (Oxy)", startPage = 88, endPage = 92,
            summary = "Biểu thức tọa độ vectơ, các loại tâm tam giác (trọng tâm, trực tâm, tâm đường tròn) & công thức nhanh.",
            accentHex = 0xFFF43F5E,
            subTopics = listOf(
                PdfSubTopic("Hệ trục tọa độ & Biểu thức tọa độ", 88),
                PdfSubTopic("Các loại tâm tam giác trong Oxy", 90),
                PdfSubTopic("Công thức nhanh hình chiếu & đối xứng", 92)
            )
        ),
        PdfTopicEntry(
            id = "g10_t17", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 17",
            title = "Phương trình đường thẳng (Oxy)", startPage = 93, endPage = 98,
            summary = "VTCP, VTPT, phương trình tổng quát – tham số – đoạn chắn, góc, khoảng cách và phân giác.",
            accentHex = 0xFFEC4899,
            subTopics = listOf(
                PdfSubTopic("VTCP, VTPT & Các dạng PT đường thẳng", 93),
                PdfSubTopic("Vị trí tương đối, Hệ số góc & Góc", 95),
                PdfSubTopic("Khoảng cách, Phân giác & Đối xứng", 97)
            )
        ),
        PdfTopicEntry(
            id = "g10_t18", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 18",
            title = "Phương trình đường tròn", startPage = 99, endPage = 101,
            summary = "Phương trình chuẩn tắc & khai triển, tiếp tuyến đường tròn, vị trí tương đối và đường tròn qua 3 điểm.",
            accentHex = 0xFFE879F9,
            subTopics = listOf(
                PdfSubTopic("Các dạng phương trình đường tròn", 99),
                PdfSubTopic("Tiếp tuyến & Vị trí tương đối", 100),
                PdfSubTopic("Đường tròn đi qua 3 điểm", 101)
            )
        ),
        PdfTopicEntry(
            id = "g10_t19", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 19",
            title = "Đường Elip", startPage = 102, endPage = 104,
            summary = "Định nghĩa, phương trình chính tắc – tham số, đặc điểm hình học, tiếp tuyến và diện tích Elip.",
            accentHex = 0xFF38BDF8,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa & PT chính tắc/tham số", 102),
                PdfSubTopic("Đặc điểm hình học", 103),
                PdfSubTopic("Tiếp tuyến & Diện tích Elip", 104)
            )
        ),
        PdfTopicEntry(
            id = "g10_t20", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 20",
            title = "Đường Hyperbol", startPage = 105, endPage = 106,
            summary = "Định nghĩa, phương trình chính tắc, tiệm cận, tiêu điểm và tương giao đường Hyperbol.",
            accentHex = 0xFF06B6D4
        ),
        PdfTopicEntry(
            id = "g10_t21", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 21",
            title = "Đường Parabol", startPage = 107, endPage = 108,
            summary = "Phương trình chính tắc y² = 2px, tiêu điểm, đường chuẩn và liên hệ với y = ax² + bx + c.",
            accentHex = 0xFF6366F1
        ),
        PdfTopicEntry(
            id = "g10_t22", grade = PdfGrade.GRADE_10, indexLabel = "Chủ đề 22",
            title = "Xác suất cổ điển (Lớp 10 & 11)", startPage = 109, endPage = 110,
            summary = "Phép thử, không gian mẫu, biến cố, định nghĩa xác suất cổ điển và quy tắc cộng – nhân xác suất.",
            accentHex = 0xFF34D399,
            subTopics = listOf(
                PdfSubTopic("Phép thử & Không gian mẫu", 109),
                PdfSubTopic("Biến cố, Định nghĩa & Quy tắc cộng/nhân", 110)
            )
        )
    )

    val grade11Topics: List<PdfTopicEntry> = listOf(
        PdfTopicEntry(
            id = "g11_t01", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 01",
            title = "Lượng giác", startPage = 111, endPage = 123,
            summary = "Độ & Radian, hệ thức Chasles, công thức cộng, nhân đôi, hạ bậc, nhân ba, tổng–tích và cung liên quan đặc biệt.",
            accentHex = 0xFFA78BFA,
            subTopics = listOf(
                PdfSubTopic("Độ, Radian & Góc lượng giác", 111),
                PdfSubTopic("Định nghĩa sin/cos/tan/cot & Dấu", 118),
                PdfSubTopic("Bảng công thức lượng giác đầy đủ", 121),
                PdfSubTopic("Cung liên quan đặc biệt (Cos đối, Sin bù...)", 122),
                PdfSubTopic("Biểu diễn theo t = tan(α/2)", 123)
            )
        ),
        PdfTopicEntry(
            id = "g11_t02", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 02",
            title = "Hàm số lượng giác", startPage = 124, endPage = 125,
            summary = "Tập xác định, tập giá trị, tính chẵn lẻ, chu kỳ tuần hoàn, sự biến thiên và đồ thị sin, cos, tan, cot.",
            accentHex = 0xFF818CF8
        ),
        PdfTopicEntry(
            id = "g11_t03", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 03",
            title = "Phương trình lượng giác", startPage = 126, endPage = 129,
            summary = "Công thức nghiệm cơ bản sin, cos, tan, cot, trường hợp đặc biệt và các dạng phương trình phổ biến.",
            accentHex = 0xFF6366F1,
            subTopics = listOf(
                PdfSubTopic("PT lượng giác cơ bản", 126),
                PdfSubTopic("Các dạng phương trình phổ biến", 127)
            )
        ),
        PdfTopicEntry(
            id = "g11_t04", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 04",
            title = "Phương pháp quy nạp toán học", startPage = 130, endPage = 131,
            summary = "Nguyên lý quy nạp toán học và các bước chứng minh mệnh đề phụ thuộc số tự nhiên n.",
            accentHex = 0xFF38BDF8
        ),
        PdfTopicEntry(
            id = "g11_t05", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 05",
            title = "Dãy số", startPage = 132, endPage = 138,
            summary = "Dãy tăng – giảm, dãy bị chặn, cách tìm số hạng tổng quát và các công thức dãy tổng đặc biệt.",
            accentHex = 0xFF0EA5E9,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa, Tăng/giảm & Bị chặn", 132),
                PdfSubTopic("Số hạng tổng quát & Dãy tổng", 135)
            )
        ),
        PdfTopicEntry(
            id = "g11_t06", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 06",
            title = "Cấp số cộng", startPage = 139, endPage = 139,
            summary = "Công sai d, số hạng tổng quát uₙ, tính chất ba số hạng liên tiếp và tổng n số hạng đầu Sₙ.",
            accentHex = 0xFF22D3EE
        ),
        PdfTopicEntry(
            id = "g11_t07", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 07",
            title = "Cấp số nhân", startPage = 140, endPage = 140,
            summary = "Công bội q, số hạng tổng quát uₙ, tính chất uₖ² = uₖ₋₁·uₖ₊₁ và tổng n số hạng đầu Sₙ.",
            accentHex = 0xFF14B8A6
        ),
        PdfTopicEntry(
            id = "g11_t08", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 08",
            title = "Giới hạn dãy số", startPage = 141, endPage = 154,
            summary = "Giới hạn 0, định lý kẹp, cấp số nhân lùi vô hạn, giới hạn vô cực, bấm máy và kỹ thuật khử dạng vô định.",
            accentHex = 0xFF34D399,
            subTopics = listOf(
                PdfSubTopic("Khái niệm & Bấm máy giới hạn dãy", 141),
                PdfSubTopic("Giới hạn 0 & Định lý kẹp", 147),
                PdfSubTopic("Giới hạn hữu hạn & CSN lùi vô hạn", 149),
                PdfSubTopic("Giới hạn vô cực & Kỹ thuật tính", 150)
            )
        ),
        PdfTopicEntry(
            id = "g11_t09", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 09",
            title = "Giới hạn hàm số & 7 Dạng vô định", startPage = 155, endPage = 168,
            summary = "Giới hạn hữu hạn/vô cực, 7 dạng vô định (0/0, ∞/∞, ∞−∞, 1^∞...), kỹ thuật khử và giới hạn một bên.",
            accentHex = 0xFF10B981,
            subTopics = listOf(
                PdfSubTopic("Khái niệm, Quy tắc & Bấm máy", 155),
                PdfSubTopic("7 dạng vô định & Kỹ thuật khử", 163),
                PdfSubTopic("Giới hạn một bên", 167)
            )
        ),
        PdfTopicEntry(
            id = "g11_t10", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 10",
            title = "Hàm số liên tục", startPage = 169, endPage = 173,
            summary = "Liên tục tại một điểm, trên khoảng/đoạn và định lý giá trị trung gian chứng minh có nghiệm.",
            accentHex = 0xFF84CC16,
            subTopics = listOf(
                PdfSubTopic("Liên tục tại một điểm", 169),
                PdfSubTopic("Liên tục trên khoảng/đoạn & Chứng minh nghiệm", 171)
            )
        ),
        PdfTopicEntry(
            id = "g11_t11", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 11",
            title = "Đạo hàm & Tiếp tuyến", startPage = 174, endPage = 180,
            summary = "Định nghĩa, số e, bảng đạo hàm đầy đủ (sơ cấp & hàm hợp), đạo hàm cấp cao và phương trình tiếp tuyến.",
            accentHex = 0xFFF59E0B,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa đạo hàm & Số e", 174),
                PdfSubTopic("Bảng đạo hàm đầy đủ", 176),
                PdfSubTopic("Quy tắc, Đạo hàm cấp cao & Tính nhanh", 177),
                PdfSubTopic("Hệ số góc & Phương trình tiếp tuyến", 178)
            )
        ),
        PdfTopicEntry(
            id = "g11_t12", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 12",
            title = "Lũy thừa & Bài toán lãi suất", startPage = 181, endPage = 183,
            summary = "Định nghĩa, điều kiện tồn tại, tính chất lũy thừa và đầy đủ công thức lãi đơn, lãi kép, trả góp ngân hàng.",
            accentHex = 0xFFFB923C,
            subTopics = listOf(
                PdfSubTopic("Tính chất & So sánh lũy thừa", 181),
                PdfSubTopic("Lãi đơn, Lãi kép & Lãi suất ngân hàng", 182)
            )
        ),
        PdfTopicEntry(
            id = "g11_t13", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 13",
            title = "Hàm số lũy thừa, Hàm số mũ & Căn bậc n", startPage = 184, endPage = 189,
            summary = "Tập xác định, đạo hàm, sự biến thiên, đồ thị hàm số lũy thừa – hàm mũ và tính chất căn bậc n.",
            accentHex = 0xFFF97316,
            subTopics = listOf(
                PdfSubTopic("Hàm số lũy thừa", 184),
                PdfSubTopic("Hàm số mũ y = aˣ", 187),
                PdfSubTopic("Căn bậc n", 189)
            )
        ),
        PdfTopicEntry(
            id = "g11_t14", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 14",
            title = "Logarit & Hàm số Logarit", startPage = 190, endPage = 194,
            summary = "Định nghĩa, công thức biến đổi – đổi cơ số logarit, đồ thị hàm số logarit và tính đối xứng qua y = x.",
            accentHex = 0xFFF43F5E,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa Logarit", 190),
                PdfSubTopic("Các tính chất & Công thức Logarit", 192),
                PdfSubTopic("Hàm số Logarit & Đối xứng qua y = x", 193)
            )
        ),
        PdfTopicEntry(
            id = "g11_t15", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 15",
            title = "Phương trình & Bất phương trình Mũ – Logarit", startPage = 195, endPage = 197,
            summary = "Tổng hợp phương pháp giải PT mũ, BPT mũ, PT logarit và BPT logarit.",
            accentHex = 0xFFEC4899,
            subTopics = listOf(
                PdfSubTopic("Phương trình & BPT Mũ", 195),
                PdfSubTopic("Phương trình & BPT Logarit", 196)
            )
        ),
        PdfTopicEntry(
            id = "g11_t16", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 16",
            title = "Hình không gian: Giao tuyến, Thiết diện & Chứng minh", startPage = 198, endPage = 213,
            summary = "Kí hiệu không gian, đồng phẳng, Menelaus, tìm giao điểm – giao tuyến – thiết diện, chứng minh // và ⊥.",
            accentHex = 0xFF8B5CF6,
            subTopics = listOf(
                PdfSubTopic("Kí hiệu, Đồng phẳng & Menelaus", 198),
                PdfSubTopic("Giao điểm, Giao tuyến & Thiết diện", 203),
                PdfSubTopic("Chứng minh Song song, Vuông góc & Hình chiếu", 208)
            )
        ),
        PdfTopicEntry(
            id = "g11_t17", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 17",
            title = "Hình không gian: Góc, Khoảng cách & Định lý", startPage = 214, endPage = 225,
            summary = "Góc đường–đường, đường–mặt, mặt–mặt, góc nhị diện; khoảng cách đến mặt phẳng và 2 đường chéo nhau.",
            accentHex = 0xFFA855F7,
            subTopics = listOf(
                PdfSubTopic("Các loại Góc & Góc nhị diện", 214),
                PdfSubTopic("Khoảng cách & 2 đường thẳng chéo nhau", 218),
                PdfSubTopic("Hệ thống định lý Vuông góc & Song song", 223)
            )
        ),
        PdfTopicEntry(
            id = "g11_t18", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 18",
            title = "Khối đa diện, Hình khối cơ bản & Thể tích", startPage = 226, endPage = 239,
            summary = "Chóp, tứ diện, lăng trụ, đa diện đều, mặt phẳng đối xứng, thể tích, tỷ lệ thể tích Simson và tam diện vuông.",
            accentHex = 0xFFD946EF,
            subTopics = listOf(
                PdfSubTopic("Chóp, Tứ diện, Lăng trụ & Hình hộp", 226),
                PdfSubTopic("Đa diện lồi/đều & Mặt phẳng đối xứng", 230),
                PdfSubTopic("Thể tích, Tỷ lệ thể tích & Công thức nhanh", 235),
                PdfSubTopic("Độ dài đặc biệt & Tam diện vuông", 239)
            )
        ),
        PdfTopicEntry(
            id = "g11_t19", grade = PdfGrade.GRADE_11, indexLabel = "Chủ đề 19",
            title = "Thống kê ghép nhóm", startPage = 240, endPage = 255,
            summary = "Quy tắc ghép nhóm & hiệu chỉnh, số trung bình, trung vị, tứ phân vị, mốt, khoảng biến thiên, phương sai & ngoại lệ.",
            accentHex = 0xFF2DD4BF,
            subTopics = listOf(
                PdfSubTopic("Ghép nhóm & Số trung bình", 240),
                PdfSubTopic("Trung vị mẫu ghép nhóm", 242),
                PdfSubTopic("Tứ phân vị mẫu ghép nhóm", 245),
                PdfSubTopic("Mốt & Khoảng biến thiên", 250),
                PdfSubTopic("Khoảng tứ phân vị, Phương sai & Ngoại lệ", 253)
            )
        )
    )

    val grade12Topics: List<PdfTopicEntry> = listOf(
        PdfTopicEntry(
            id = "g12_t01", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 01",
            title = "Tính đơn điệu của hàm số", startPage = 256, endPage = 263,
            summary = "Điều kiện đồng biến – nghịch biến, tham số m với hàm bậc 3, bậc 1/bậc 1 và hàm trị tuyệt đối |f(x)|.",
            accentHex = 0xFF34D399,
            subTopics = listOf(
                PdfSubTopic("Dấu hiệu & Xét dấu đạo hàm", 256),
                PdfSubTopic("Tham số m: Bậc 3 & Phân thức bậc 1/1", 259),
                PdfSubTopic("Đơn điệu hàm chứa trị tuyệt đối |f(x)|", 262)
            )
        ),
        PdfTopicEntry(
            id = "g12_t02", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 02",
            title = "Cực trị của hàm số", startPage = 264, endPage = 275,
            summary = "Hai dấu hiệu cực trị, số cực trị |f(x)| và f(|x|), cực trị hàm bậc 3, hàm trùng phương & công thức nhanh.",
            accentHex = 0xFF10B981,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa & Hai dấu hiệu cực trị", 264),
                PdfSubTopic("Số cực trị |f(x)|, f(|x|) & Phân thức", 267),
                PdfSubTopic("Cực trị hàm bậc 3 (đường thẳng qua 2 CTrị)", 270),
                PdfSubTopic("Cực trị hàm trùng phương & Công thức nhanh", 274)
            )
        ),
        PdfTopicEntry(
            id = "g12_t03", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 03",
            title = "Giá trị lớn nhất – Giá trị nhỏ nhất (Max – Min)", startPage = 276, endPage = 278,
            summary = "Phương pháp tìm GTLN – GTNN trên đoạn/khoảng, BĐT hữu ích và so sánh max/min của |f(x) + m|.",
            accentHex = 0xFF2DD4BF
        ),
        PdfTopicEntry(
            id = "g12_t04", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 04",
            title = "Đường tiệm cận của đồ thị hàm số", startPage = 279, endPage = 282,
            summary = "Tiệm cận đứng (TCĐ), tiệm cận ngang (TCN), tiệm cận xiên (TCX) cho hàm bậc 1/1 và bậc 2/1.",
            accentHex = 0xFF06B6D4
        ),
        PdfTopicEntry(
            id = "g12_t05", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 05",
            title = "Khảo sát sự biến thiên và Vẽ đồ thị hàm số", startPage = 283, endPage = 298,
            summary = "Nhận dạng đồ thị, tâm đối xứng, trục đối xứng & tính chất hình học của bậc 3, trùng phương, bậc 1/1 và bậc 2/1.",
            accentHex = 0xFF38BDF8,
            subTopics = listOf(
                PdfSubTopic("Hàm số bậc 3", 283),
                PdfSubTopic("Hàm số trùng phương", 286),
                PdfSubTopic("Hàm phân thức bậc 1 / bậc 1", 288),
                PdfSubTopic("Hàm phân thức bậc 2 / bậc 1", 294)
            )
        ),
        PdfTopicEntry(
            id = "g12_t06", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 06",
            title = "Tương giao & Phép biến đổi đồ thị", startPage = 299, endPage = 306,
            summary = "Tiếp xúc, tương giao, điểm cố định, tịnh tiến, đối xứng, trị tuyệt đối và co giãn đồ thị hàm số.",
            accentHex = 0xFF60A5FA,
            subTopics = listOf(
                PdfSubTopic("Tiếp xúc, Tương giao & Điểm cố định", 299),
                PdfSubTopic("Tịnh tiến, Trị tuyệt đối, Đối xứng & Co giãn", 300)
            )
        ),
        PdfTopicEntry(
            id = "g12_t07", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 07",
            title = "Nguyên hàm", startPage = 307, endPage = 321,
            summary = "Bảng nguyên hàm đầy đủ, đổi biến, lượng giác hóa, từng phần (bảng đạo nguyên), phân thức hữu tỉ & PT vi phân.",
            accentHex = 0xFF818CF8,
            subTopics = listOf(
                PdfSubTopic("Khái niệm & Bảng nguyên hàm đầy đủ", 307),
                PdfSubTopic("Đặt ẩn phụ & Đưa vào vi phân", 310),
                PdfSubTopic("Lượng giác hóa", 313),
                PdfSubTopic("Nguyên hàm từng phần & Bảng đạo nguyên", 314),
                PdfSubTopic("Nguyên hàm phân thức & PT vi phân", 318)
            )
        ),
        PdfTopicEntry(
            id = "g12_t08", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 08",
            title = "Tích phân & Ứng dụng hình học – vật lý", startPage = 322, endPage = 331,
            summary = "Tính chất, BĐT tích phân, dạng đặc biệt, diện tích hình phẳng, thể tích vật thể tròn xoay, chuyển động & Elip/Parabol.",
            accentHex = 0xFFA78BFA,
            subTopics = listOf(
                PdfSubTopic("Định nghĩa, Tính chất, Đổi biến & Từng phần", 322),
                PdfSubTopic("Các dạng tích phân đặc biệt", 324),
                PdfSubTopic("Diện tích, Thể tích & Độ dài đường cong", 326),
                PdfSubTopic("Ứng dụng vật lý & Đường tròn/Elip/Parabol", 329)
            )
        ),
        PdfTopicEntry(
            id = "g12_t09", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 09",
            title = "Vectơ trong không gian", startPage = 332, endPage = 338,
            summary = "Quy tắc hình hộp, cộng – trừ – tích vô hướng trong không gian, trọng tâm tứ diện và bài toán tổng hợp lực.",
            accentHex = 0xFFC084FC
        ),
        PdfTopicEntry(
            id = "g12_t10", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 10",
            title = "Hệ tọa độ trong không gian (Oxyz)", startPage = 339, endPage = 347,
            summary = "Tọa độ điểm/vectơ, các loại tâm tam giác trong Oxyz, kiểm tra cùng phương, hình chiếu, khoảng cách & đối xứng.",
            accentHex = 0xFFE879F9,
            subTopics = listOf(
                PdfSubTopic("Hệ trục & Biểu thức tọa độ Oxyz", 339),
                PdfSubTopic("Các loại tâm tam giác trong Oxyz", 342),
                PdfSubTopic("Hình chiếu, Khoảng cách & Đối xứng đặc biệt", 345)
            )
        ),
        PdfTopicEntry(
            id = "g12_t11", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 11",
            title = "Các loại tích vectơ & Ứng dụng Oxyz", startPage = 348, endPage = 352,
            summary = "Tích vô hướng, tích có hướng (quy tắc bàn tay phải), tích hỗn tạp và công thức tính diện tích, thể tích tứ diện/hộp.",
            accentHex = 0xFFEC4899,
            subTopics = listOf(
                PdfSubTopic("Tích vô hướng & Tích có hướng", 348),
                PdfSubTopic("Tích hỗn tạp & Ứng dụng Diện tích/Thể tích", 351)
            )
        ),
        PdfTopicEntry(
            id = "g12_t12", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 12",
            title = "Phương trình mặt cầu", startPage = 353, endPage = 354,
            summary = "Phương trình chuẩn tắc, phương trình khai triển và 7 dạng viết phương trình mặt cầu thường gặp.",
            accentHex = 0xFFF43F5E
        ),
        PdfTopicEntry(
            id = "g12_t13", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 13",
            title = "Phương trình mặt phẳng", startPage = 355, endPage = 361,
            summary = "VTPT, phương trình tổng quát, mặt phẳng đoạn chắn, mặt đặc biệt, 11 dạng viết PTMP và xét cùng/khác phía.",
            accentHex = 0xFFFB7185,
            subTopics = listOf(
                PdfSubTopic("VTPT, PTTQ & Viết nhanh qua 3 điểm", 355),
                PdfSubTopic("Đoạn chắn & Mặt phẳng đặc biệt", 357),
                PdfSubTopic("11 dạng viết phương trình mặt phẳng", 358)
            )
        ),
        PdfTopicEntry(
            id = "g12_t14", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 14",
            title = "Phương trình đường thẳng (Oxyz)", startPage = 362, endPage = 367,
            summary = "VTCP, phương trình tham số – chính tắc và 13 dạng viết phương trình đường thẳng trong không gian Oxyz.",
            accentHex = 0xFFFB923C,
            subTopics = listOf(
                PdfSubTopic("VTCP, Tham số & Chính tắc", 362),
                PdfSubTopic("13 dạng viết phương trình đường thẳng", 363)
            )
        ),
        PdfTopicEntry(
            id = "g12_t15", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 15",
            title = "Vị trí tương đối trong Oxyz", startPage = 368, endPage = 373,
            summary = "Xét vị trí tương đối giữa 2 đường thẳng, đường và mặt phẳng, đường/mặt với mặt cầu và 2 mặt phẳng.",
            accentHex = 0xFFF59E0B,
            subTopics = listOf(
                PdfSubTopic("2 đường thẳng & Đường với Mặt phẳng", 368),
                PdfSubTopic("Đường/Mặt với Mặt cầu & 2 Mặt phẳng", 372)
            )
        ),
        PdfTopicEntry(
            id = "g12_t16", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 16",
            title = "Hình chiếu, Đối xứng & Tổng hợp Góc – Khoảng cách", startPage = 374, endPage = 377,
            summary = "Tìm hình chiếu vuông góc, điểm đối xứng, tổng hợp công thức góc và khoảng cách 2 đường chéo nhau Oxyz.",
            accentHex = 0xFFEAB308,
            subTopics = listOf(
                PdfSubTopic("Hình chiếu & Điểm đối xứng", 374),
                PdfSubTopic("Tổng hợp Góc & Khoảng cách Oxyz", 375)
            )
        ),
        PdfTopicEntry(
            id = "g12_t17", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 17",
            title = "Tâm tỷ cự & Cực trị hình học Oxyz", startPage = 378, endPage = 385,
            summary = "Tổ hợp tuyến tính vectơ, tổng bình phương độ dài, 5 bất đẳng thức quen thuộc và các bài toán cực trị Oxyz.",
            accentHex = 0xFF22C55E
        ),
        PdfTopicEntry(
            id = "g12_t18", grade = PdfGrade.GRADE_12, indexLabel = "Chủ đề 18",
            title = "Xác suất có điều kiện & Công thức Bayes", startPage = 386, endPage = 387,
            summary = "Công thức xác suất có điều kiện P(A|B), công thức xác suất toàn phần, định lý Bayes và sơ đồ cây.",
            accentHex = 0xFF14B8A6
        )
    )

    val allTopics: List<PdfTopicEntry> = grade10Topics + grade11Topics + grade12Topics

    fun topicsFor(grade: PdfGrade): List<PdfTopicEntry> = when (grade) {
        PdfGrade.GRADE_10 -> grade10Topics
        PdfGrade.GRADE_11 -> grade11Topics
        PdfGrade.GRADE_12 -> grade12Topics
    }

    fun topicForPage(page1Based: Int): PdfTopicEntry? =
        allTopics.firstOrNull { page1Based in it.startPage..it.endPage }

    fun sectionLabelForPage(page1Based: Int): String = when {
        page1Based <= 2 -> "Bìa & Tổng quan 3 lớp"
        page1Based in 3..17 -> "Mục lục chi tiết PDF"
        page1Based >= 388 -> "Trang cuối tài liệu"
        else -> topicForPage(page1Based)?.let { "${it.grade.shortLabel} · ${it.title}" } ?: "Trang $page1Based"
    }

    fun searchTopics(query: String): List<PdfTopicSearchHit> {
        val norm = normalizeVi(query).trim()
        if (norm.isEmpty()) return emptyList()
        val tokens = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val results = mutableListOf<PdfTopicSearchHit>()
        for (topic in allTopics) {
            // 1. Prefer a subtopic whose own title directly matches all search tokens
            val directSubMatch = topic.subTopics.firstOrNull { sub ->
                val ownText = normalizeVi("${sub.title} trang ${sub.page}")
                tokens.all { ownText.contains(it) }
            }
            // 2. Otherwise allow combined topic + subtopic matching only when the subtopic itself contributes a match
            val combinedSubMatch = directSubMatch ?: topic.subTopics.firstOrNull { sub ->
                val ownText = normalizeVi(sub.title)
                val combinedText = normalizeVi("${sub.title} ${topic.title} ${topic.grade.shortLabel} trang ${sub.page}")
                tokens.any { ownText.contains(it) } && tokens.all { combinedText.contains(it) }
            }
            val topicText = normalizeVi("${topic.title} ${topic.summary} ${topic.grade.shortLabel} ${topic.indexLabel} trang ${topic.startPage}")
            val topicMatch = tokens.all { topicText.contains(it) }

            if (combinedSubMatch != null) {
                results.add(PdfTopicSearchHit(topic = topic, matchedSubTopic = combinedSubMatch))
            } else if (topicMatch) {
                results.add(PdfTopicSearchHit(topic = topic, matchedSubTopic = null))
            }
        }
        return results
    }

    fun normalizeVi(input: String): String {
        val replaced = input.replace('đ', 'd').replace('Đ', 'D')
        val decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
    }
}

/**
 * Persists reading progress, bookmarked PDF pages, active grade tab, and night reading filter.
 */
class PdfDocsPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tool_toan_pdf_docs_v1", Context.MODE_PRIVATE)

    fun loadLastPage(): Int = prefs.getInt(KEY_LAST_PAGE, 1).coerceIn(1, PDF_TOTAL_PAGES)

    fun saveLastPage(page1Based: Int) {
        prefs.edit().putInt(KEY_LAST_PAGE, page1Based.coerceIn(1, PDF_TOTAL_PAGES)).apply()
    }

    fun loadBookmarks(): Set<Int> {
        val raw = prefs.getStringSet(KEY_BOOKMARKS, emptySet()).orEmpty()
        return raw.mapNotNull { it.toIntOrNull()?.takeIf { p -> p in 1..PDF_TOTAL_PAGES } }.toSortedSet()
    }

    fun saveBookmarks(pages: Set<Int>) {
        val encoded = pages.filter { it in 1..PDF_TOTAL_PAGES }.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_BOOKMARKS, encoded).apply()
    }

    fun toggleBookmark(page1Based: Int): Set<Int> {
        val safePage = page1Based.coerceIn(1, PDF_TOTAL_PAGES)
        val current = loadBookmarks().toMutableSet()
        if (!current.add(safePage)) {
            current.remove(safePage)
        }
        saveBookmarks(current)
        return current.toSortedSet()
    }

    fun loadLastTab(): PdfDocsTab = PdfDocsTab.fromKey(prefs.getString(KEY_LAST_TAB, PdfDocsTab.GRADE_12.key))

    fun saveLastTab(tab: PdfDocsTab) {
        prefs.edit().putString(KEY_LAST_TAB, tab.key).apply()
    }

    fun loadNightFilter(): Boolean = prefs.getBoolean(KEY_NIGHT_FILTER, false)

    fun saveNightFilter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_FILTER, enabled).apply()
    }

    companion object {
        private const val KEY_LAST_PAGE = "last_page_1based"
        private const val KEY_BOOKMARKS = "bookmarked_pages"
        private const val KEY_LAST_TAB = "last_tab"
        private const val KEY_NIGHT_FILTER = "night_filter"
    }
}
