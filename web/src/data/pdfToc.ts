export type PdfGradeId = 'grade_10' | 'grade_11' | 'grade_12';

export interface PdfSubTopic {
  title: string;
  page: number;
}

export interface PdfTopicEntry {
  id: string;
  grade: PdfGradeId;
  indexLabel: string;
  title: string;
  startPage: number;
  endPage: number;
  summary: string;
  accentHex: string;
  subTopics: PdfSubTopic[];
}

export interface PdfTopicSearchHit {
  topic: PdfTopicEntry;
  matchedSubTopic: PdfSubTopic | null;
  targetPage: number;
}

export const PDF_TOTAL_PAGES = 388;
export const PDF_BOOK_TITLE = 'Tóm Tắt Kiến Thức Toán 10 – 11 – 12';
export const PDF_BOOK_SUBTITLE = 'Tài liệu tổng hợp DPAD · 59 chuyên đề · 388 trang';

export const PDF_GRADES: { id: PdfGradeId; shortLabel: string; title: string; range: string; accent: string }[] = [
  { id: 'grade_10', shortLabel: 'Lớp 10', title: 'Toán Lớp 10', range: 'Tr. 18 – 110', accent: '#38BDF8' },
  { id: 'grade_11', shortLabel: 'Lớp 11', title: 'Toán Lớp 11', range: 'Tr. 111 – 255', accent: '#A78BFA' },
  { id: 'grade_12', shortLabel: 'Lớp 12', title: 'Toán Lớp 12', range: 'Tr. 256 – 387', accent: '#34D399' }
];

export const PDF_TOC_TOPICS: PdfTopicEntry[] = [
  // ==================== LỚP 10 (22 CHỦ ĐỀ) ====================
  {
    id: 'g10_t01', grade: 'grade_10', indexLabel: 'Chủ đề 01',
    title: 'Mệnh đề', startPage: 18, endPage: 21,
    summary: 'Mệnh đề đúng/sai, chứa biến, phủ định, kéo theo, đảo, tương đương, kí hiệu ∀ và ∃.',
    accentHex: '#38BDF8',
    subTopics: [
      { title: 'Khái niệm & Kiến thức cũ', page: 18 },
      { title: 'Mệnh đề chứa biến & Phủ định', page: 19 },
      { title: 'Kéo theo, Đảo, Tương đương & ∀, ∃', page: 21 }
    ]
  },
  {
    id: 'g10_t02', grade: 'grade_10', indexLabel: 'Chủ đề 02',
    title: 'Tập hợp', startPage: 22, endPage: 25,
    summary: 'Các tập số quen thuộc, tập con, biểu đồ Ven, giao – hợp – hiệu – phần bù, tập con của ℝ.',
    accentHex: '#0EA5E9',
    subTopics: [
      { title: 'Kí hiệu & Các tập số', page: 22 },
      { title: 'Tập con & Biểu đồ Ven', page: 23 },
      { title: 'Các phép toán tập hợp', page: 24 },
      { title: 'Các tập con của ℝ', page: 25 }
    ]
  },
  {
    id: 'g10_t03', grade: 'grade_10', indexLabel: 'Chủ đề 03',
    title: 'Bất phương trình bậc nhất hai ẩn', startPage: 26, endPage: 28,
    summary: 'Miền nghiệm BPT & hệ BPT bậc nhất hai ẩn, cực trị F = ax + by và bài toán kinh tế.',
    accentHex: '#22D3EE',
    subTopics: [
      { title: 'Định nghĩa & Hệ BPT bậc nhất 2 ẩn', page: 26 },
      { title: 'GTLN – GTNN của F = ax + by', page: 27 },
      { title: 'Bài toán kinh tế quy hoạch tuyến tính', page: 28 }
    ]
  },
  {
    id: 'g10_t04', grade: 'grade_10', indexLabel: 'Chủ đề 04',
    title: 'Hàm số và Đồ thị', startPage: 29, endPage: 32,
    summary: 'Định nghĩa, tập xác định, tập giá trị, đồ thị hàm số và tính đồng biến – nghịch biến.',
    accentHex: '#60A5FA',
    subTopics: [
      { title: 'Định nghĩa & Tập xác định', page: 29 },
      { title: 'Đồ thị hàm số', page: 30 },
      { title: 'Sự biến thiên của hàm số', page: 31 }
    ]
  },
  {
    id: 'g10_t05', grade: 'grade_10', indexLabel: 'Chủ đề 05',
    title: 'Hàm số bậc hai & Định lý Vi-ét', startPage: 33, endPage: 35,
    summary: 'Đỉnh, trục đối xứng, bảng biến thiên Parabol, phương trình bậc hai, Vi-ét và điều kiện nghiệm.',
    accentHex: '#3B82F6',
    subTopics: [
      { title: 'Đặc điểm đồ thị hàm bậc 2', page: 33 },
      { title: 'PT bậc 2 & Hệ thức Vi-ét', page: 34 },
      { title: 'Điều kiện nghiệm & Biểu diễn parabol', page: 35 }
    ]
  },
  {
    id: 'g10_t06', grade: 'grade_10', indexLabel: 'Chủ đề 06',
    title: 'Giá trị lượng giác từ 0° đến 180°', startPage: 36, endPage: 38,
    summary: 'Nửa đường tròn đơn vị, góc phụ – góc bù, bảng giá trị đặc biệt và hệ thức lượng giác cơ bản.',
    accentHex: '#818CF8',
    subTopics: [
      { title: 'Nửa đường tròn đơn vị', page: 36 },
      { title: 'Góc phụ & Góc bù', page: 37 },
      { title: 'Bảng giá trị đặc biệt & Hệ thức cơ bản', page: 38 }
    ]
  },
  {
    id: 'g10_t07', grade: 'grade_10', indexLabel: 'Chủ đề 07',
    title: 'Hệ thức lượng trong tam giác vuông', startPage: 39, endPage: 39,
    summary: 'Pythagore, đường cao, hình chiếu cạnh góc vuông và tỉ số lượng giác trong tam giác vuông.',
    accentHex: '#A78BFA',
    subTopics: []
  },
  {
    id: 'g10_t08', grade: 'grade_10', indexLabel: 'Chủ đề 08',
    title: 'Hệ thức lượng trong tam giác tùy ý', startPage: 40, endPage: 44,
    summary: 'Định lý Côsin, Định lý Sin, công thức trung tuyến – phân giác, diện tích (Heron) và đa giác đặc biệt.',
    accentHex: '#C084FC',
    subTopics: [
      { title: 'Định lý Côsin', page: 40 },
      { title: 'Định lý Sin, Trung tuyến & Phân giác', page: 41 },
      { title: 'Diện tích tam giác & Đa giác đặc biệt', page: 42 },
      { title: 'Độ dài đặc biệt trong hình phẳng', page: 43 }
    ]
  },
  {
    id: 'g10_t09', grade: 'grade_10', indexLabel: 'Chủ đề 09',
    title: 'Vectơ', startPage: 45, endPage: 49,
    summary: 'Tổng – hiệu vectơ, tích vectơ với một số, hệ thức trung điểm, trọng tâm và điều kiện thẳng hàng.',
    accentHex: '#2DD4BF',
    subTopics: [
      { title: 'Khái niệm vectơ', page: 45 },
      { title: 'Quy tắc cộng vectơ', page: 47 },
      { title: 'Hiệu, Tích với số & Trung điểm/Trọng tâm', page: 48 },
      { title: 'Cùng phương & Ba điểm thẳng hàng', page: 49 }
    ]
  },
  {
    id: 'g10_t10', grade: 'grade_10', indexLabel: 'Chủ đề 10',
    title: 'Tích vô hướng của hai vectơ', startPage: 50, endPage: 53,
    summary: 'Góc giữa hai vectơ, tích vô hướng, tính chất hình chiếu và các bài toán tập hợp điểm.',
    accentHex: '#14B8A6',
    subTopics: [
      { title: 'Góc & Công thức tích vô hướng', page: 50 },
      { title: 'Tính chất & Hình chiếu', page: 51 },
      { title: 'Tập hợp điểm liên quan tích vô hướng', page: 52 }
    ]
  },
  {
    id: 'g10_t11', grade: 'grade_10', indexLabel: 'Chủ đề 11',
    title: 'Số gần đúng và Sai số', startPage: 54, endPage: 55,
    summary: 'Các hàng số, sai số tuyệt đối, sai số tương đối và quy tắc làm tròn số.',
    accentHex: '#34D399',
    subTopics: []
  },
  {
    id: 'g10_t12', grade: 'grade_10', indexLabel: 'Chủ đề 12',
    title: 'Thống kê không ghép nhóm', startPage: 56, endPage: 71,
    summary: 'Tần số, số trung bình, trung vị, tứ phân vị, mốt, khoảng biến thiên, phương sai, độ lệch chuẩn & bấm máy.',
    accentHex: '#10B981',
    subTopics: [
      { title: 'Tần số, Tần suất & Số trung bình', page: 56 },
      { title: 'Trung vị & Tứ phân vị', page: 58 },
      { title: 'Mốt & Khoảng biến thiên', page: 62 },
      { title: 'Khoảng tứ phân vị', page: 65 },
      { title: 'Phương sai, Độ lệch chuẩn & Ngoại lệ', page: 68 },
      { title: 'Hướng dẫn bấm máy tính thống kê', page: 71 }
    ]
  },
  {
    id: 'g10_t13', grade: 'grade_10', indexLabel: 'Chủ đề 13',
    title: 'Dấu của tam thức bậc hai', startPage: 72, endPage: 75,
    summary: 'Định lý xét dấu tam thức bậc hai, điều kiện luôn âm/dương với mọi x và giải BPT bậc hai.',
    accentHex: '#F59E0B',
    subTopics: [
      { title: 'Định lý dấu tam thức bậc 2', page: 72 },
      { title: 'Điều kiện luôn âm/dương & BPT bậc 2', page: 74 }
    ]
  },
  {
    id: 'g10_t14', grade: 'grade_10', indexLabel: 'Chủ đề 14',
    title: 'Phương trình quy về phương trình bậc hai', startPage: 76, endPage: 76,
    summary: 'Cách giải phương trình chứa căn thức √(ax²+bx+c) = √(dx²+ex+f) và √(ax²+bx+c) = dx + e.',
    accentHex: '#FBBF24',
    subTopics: []
  },
  {
    id: 'g10_t15', grade: 'grade_10', indexLabel: 'Chủ đề 15',
    title: 'Đại số tổ hợp & Nhị thức Newton', startPage: 77, endPage: 87,
    summary: 'Quy tắc cộng – nhân, giai thừa, hoán vị, chỉnh hợp, tổ hợp, tam giác Pascal và khai triển Newton.',
    accentHex: '#FB923C',
    subTopics: [
      { title: 'Quy tắc cộng & Quy tắc nhân', page: 77 },
      { title: 'Giai thừa & Hoán vị', page: 78 },
      { title: 'Chỉnh hợp', page: 80 },
      { title: 'Tổ hợp & Các công thức tính chất', page: 82 },
      { title: 'Tam giác Pascal & Nhị thức Newton', page: 87 }
    ]
  },
  {
    id: 'g10_t16', grade: 'grade_10', indexLabel: 'Chủ đề 16',
    title: 'Phương pháp tọa độ trong mặt phẳng (Oxy)', startPage: 88, endPage: 92,
    summary: 'Biểu thức tọa độ vectơ, các loại tâm tam giác (trọng tâm, trực tâm, tâm đường tròn) & công thức nhanh.',
    accentHex: '#F43F5E',
    subTopics: [
      { title: 'Hệ trục tọa độ & Biểu thức tọa độ', page: 88 },
      { title: 'Các loại tâm tam giác trong Oxy', page: 90 },
      { title: 'Công thức nhanh hình chiếu & đối xứng', page: 92 }
    ]
  },
  {
    id: 'g10_t17', grade: 'grade_10', indexLabel: 'Chủ đề 17',
    title: 'Phương trình đường thẳng (Oxy)', startPage: 93, endPage: 98,
    summary: 'VTCP, VTPT, phương trình tổng quát – tham số – đoạn chắn, góc, khoảng cách và phân giác.',
    accentHex: '#EC4899',
    subTopics: [
      { title: 'VTCP, VTPT & Các dạng PT đường thẳng', page: 93 },
      { title: 'Vị trí tương đối, Hệ số góc & Góc', page: 95 },
      { title: 'Khoảng cách, Phân giác & Đối xứng', page: 97 }
    ]
  },
  {
    id: 'g10_t18', grade: 'grade_10', indexLabel: 'Chủ đề 18',
    title: 'Phương trình đường tròn', startPage: 99, endPage: 101,
    summary: 'Phương trình chuẩn tắc & khai triển, tiếp tuyến đường tròn, vị trí tương đối và đường tròn qua 3 điểm.',
    accentHex: '#E879F9',
    subTopics: [
      { title: 'Các dạng phương trình đường tròn', page: 99 },
      { title: 'Tiếp tuyến & Vị trí tương đối', page: 100 },
      { title: 'Đường tròn đi qua 3 điểm', page: 101 }
    ]
  },
  {
    id: 'g10_t19', grade: 'grade_10', indexLabel: 'Chủ đề 19',
    title: 'Đường Elip', startPage: 102, endPage: 104,
    summary: 'Định nghĩa, phương trình chính tắc – tham số, đặc điểm hình học, tiếp tuyến và diện tích Elip.',
    accentHex: '#38BDF8',
    subTopics: [
      { title: 'Định nghĩa & PT chính tắc/tham số', page: 102 },
      { title: 'Đặc điểm hình học', page: 103 },
      { title: 'Tiếp tuyến & Diện tích Elip', page: 104 }
    ]
  },
  {
    id: 'g10_t20', grade: 'grade_10', indexLabel: 'Chủ đề 20',
    title: 'Đường Hyperbol', startPage: 105, endPage: 106,
    summary: 'Định nghĩa, phương trình chính tắc, tiệm cận, tiêu điểm và tương giao đường Hyperbol.',
    accentHex: '#06B6D4',
    subTopics: []
  },
  {
    id: 'g10_t21', grade: 'grade_10', indexLabel: 'Chủ đề 21',
    title: 'Đường Parabol', startPage: 107, endPage: 108,
    summary: 'Phương trình chính tắc y² = 2px, tiêu điểm, đường chuẩn và liên hệ với y = ax² + bx + c.',
    accentHex: '#6366F1',
    subTopics: []
  },
  {
    id: 'g10_t22', grade: 'grade_10', indexLabel: 'Chủ đề 22',
    title: 'Xác suất cổ điển (Lớp 10 & 11)', startPage: 109, endPage: 110,
    summary: 'Phép thử, không gian mẫu, biến cố, định nghĩa xác suất cổ điển và quy tắc cộng – nhân xác suất.',
    accentHex: '#34D399',
    subTopics: [
      { title: 'Phép thử & Không gian mẫu', page: 109 },
      { title: 'Biến cố, Định nghĩa & Quy tắc cộng/nhân', page: 110 }
    ]
  },

  // ==================== LỚP 11 (19 CHỦ ĐỀ) ====================
  {
    id: 'g11_t01', grade: 'grade_11', indexLabel: 'Chủ đề 01',
    title: 'Lượng giác', startPage: 111, endPage: 123,
    summary: 'Độ & Radian, hệ thức Chasles, công thức cộng, nhân đôi, hạ bậc, nhân ba, tổng–tích và cung liên quan đặc biệt.',
    accentHex: '#A78BFA',
    subTopics: [
      { title: 'Độ, Radian & Góc lượng giác', page: 111 },
      { title: 'Định nghĩa sin/cos/tan/cot & Dấu', page: 118 },
      { title: 'Bảng công thức lượng giác đầy đủ', page: 121 },
      { title: 'Cung liên quan đặc biệt (Cos đối, Sin bù...)', page: 122 },
      { title: 'Biểu diễn theo t = tan(α/2)', page: 123 }
    ]
  },
  {
    id: 'g11_t02', grade: 'grade_11', indexLabel: 'Chủ đề 02',
    title: 'Hàm số lượng giác', startPage: 124, endPage: 125,
    summary: 'Tập xác định, tập giá trị, tính chẵn lẻ, chu kỳ tuần hoàn, sự biến thiên và đồ thị sin, cos, tan, cot.',
    accentHex: '#818CF8',
    subTopics: []
  },
  {
    id: 'g11_t03', grade: 'grade_11', indexLabel: 'Chủ đề 03',
    title: 'Phương trình lượng giác', startPage: 126, endPage: 129,
    summary: 'Công thức nghiệm cơ bản sin, cos, tan, cot, trường hợp đặc biệt và các dạng phương trình phổ biến.',
    accentHex: '#6366F1',
    subTopics: [
      { title: 'PT lượng giác cơ bản', page: 126 },
      { title: 'Các dạng phương trình phổ biến', page: 127 }
    ]
  },
  {
    id: 'g11_t04', grade: 'grade_11', indexLabel: 'Chủ đề 04',
    title: 'Phương pháp quy nạp toán học', startPage: 130, endPage: 131,
    summary: 'Nguyên lý quy nạp toán học và các bước chứng minh mệnh đề phụ thuộc số tự nhiên n.',
    accentHex: '#38BDF8',
    subTopics: []
  },
  {
    id: 'g11_t05', grade: 'grade_11', indexLabel: 'Chủ đề 05',
    title: 'Dãy số', startPage: 132, endPage: 138,
    summary: 'Dãy tăng – giảm, dãy bị chặn, cách tìm số hạng tổng quát và các công thức dãy tổng đặc biệt.',
    accentHex: '#0EA5E9',
    subTopics: [
      { title: 'Định nghĩa, Tăng/giảm & Bị chặn', page: 132 },
      { title: 'Số hạng tổng quát & Dãy tổng', page: 135 }
    ]
  },
  {
    id: 'g11_t06', grade: 'grade_11', indexLabel: 'Chủ đề 06',
    title: 'Cấp số cộng', startPage: 139, endPage: 139,
    summary: 'Công sai d, số hạng tổng quát uₙ, tính chất ba số hạng liên tiếp và tổng n số hạng đầu Sₙ.',
    accentHex: '#22D3EE',
    subTopics: []
  },
  {
    id: 'g11_t07', grade: 'grade_11', indexLabel: 'Chủ đề 07',
    title: 'Cấp số nhân', startPage: 140, endPage: 140,
    summary: 'Công bội q, số hạng tổng quát uₙ, tính chất uₖ² = uₖ₋₁·uₖ₊₁ và tổng n số hạng đầu Sₙ.',
    accentHex: '#14B8A6',
    subTopics: []
  },
  {
    id: 'g11_t08', grade: 'grade_11', indexLabel: 'Chủ đề 08',
    title: 'Giới hạn dãy số', startPage: 141, endPage: 154,
    summary: 'Giới hạn 0, định lý kẹp, cấp số nhân lùi vô hạn, giới hạn vô cực, bấm máy và kỹ thuật khử dạng vô định.',
    accentHex: '#34D399',
    subTopics: [
      { title: 'Khái niệm & Bấm máy giới hạn dãy', page: 141 },
      { title: 'Giới hạn 0 & Định lý kẹp', page: 147 },
      { title: 'Giới hạn hữu hạn & CSN lùi vô hạn', page: 149 },
      { title: 'Giới hạn vô cực & Kỹ thuật tính', page: 150 }
    ]
  },
  {
    id: 'g11_t09', grade: 'grade_11', indexLabel: 'Chủ đề 09',
    title: 'Giới hạn hàm số & 7 Dạng vô định', startPage: 155, endPage: 168,
    summary: 'Giới hạn hữu hạn/vô cực, 7 dạng vô định (0/0, ∞/∞, ∞−∞, 1^∞...), kỹ thuật khử và giới hạn một bên.',
    accentHex: '#10B981',
    subTopics: [
      { title: 'Khái niệm, Quy tắc & Bấm máy', page: 155 },
      { title: '7 dạng vô định & Kỹ thuật khử', page: 163 },
      { title: 'Giới hạn một bên', page: 167 }
    ]
  },
  {
    id: 'g11_t10', grade: 'grade_11', indexLabel: 'Chủ đề 10',
    title: 'Hàm số liên tục', startPage: 169, endPage: 173,
    summary: 'Liên tục tại một điểm, trên khoảng/đoạn và định lý giá trị trung gian chứng minh có nghiệm.',
    accentHex: '#84CC16',
    subTopics: [
      { title: 'Liên tục tại một điểm', page: 169 },
      { title: 'Liên tục trên khoảng/đoạn & Chứng minh nghiệm', page: 171 }
    ]
  },
  {
    id: 'g11_t11', grade: 'grade_11', indexLabel: 'Chủ đề 11',
    title: 'Đạo hàm & Tiếp tuyến', startPage: 174, endPage: 180,
    summary: 'Định nghĩa, số e, bảng đạo hàm đầy đủ (sơ cấp & hàm hợp), đạo hàm cấp cao và phương trình tiếp tuyến.',
    accentHex: '#F59E0B',
    subTopics: [
      { title: 'Định nghĩa đạo hàm & Số e', page: 174 },
      { title: 'Bảng đạo hàm đầy đủ', page: 176 },
      { title: 'Quy tắc, Đạo hàm cấp cao & Tính nhanh', page: 177 },
      { title: 'Hệ số góc & Phương trình tiếp tuyến', page: 178 }
    ]
  },
  {
    id: 'g11_t12', grade: 'grade_11', indexLabel: 'Chủ đề 12',
    title: 'Lũy thừa & Bài toán lãi suất', startPage: 181, endPage: 183,
    summary: 'Định nghĩa, điều kiện tồn tại, tính chất lũy thừa và đầy đủ công thức lãi đơn, lãi kép, trả góp ngân hàng.',
    accentHex: '#FB923C',
    subTopics: [
      { title: 'Tính chất & So sánh lũy thừa', page: 181 },
      { title: 'Lãi đơn, Lãi kép & Lãi suất ngân hàng', page: 182 }
    ]
  },
  {
    id: 'g11_t13', grade: 'grade_11', indexLabel: 'Chủ đề 13',
    title: 'Hàm số lũy thừa, Hàm số mũ & Căn bậc n', startPage: 184, endPage: 189,
    summary: 'Tập xác định, đạo hàm, sự biến thiên, đồ thị hàm số lũy thừa – hàm mũ và tính chất căn bậc n.',
    accentHex: '#F97316',
    subTopics: [
      { title: 'Hàm số lũy thừa', page: 184 },
      { title: 'Hàm số mũ y = aˣ', page: 187 },
      { title: 'Căn bậc n', page: 189 }
    ]
  },
  {
    id: 'g11_t14', grade: 'grade_11', indexLabel: 'Chủ đề 14',
    title: 'Logarit & Hàm số Logarit', startPage: 190, endPage: 194,
    summary: 'Định nghĩa, công thức biến đổi – đổi cơ số logarit, đồ thị hàm số logarit và tính đối xứng qua y = x.',
    accentHex: '#F43F5E',
    subTopics: [
      { title: 'Định nghĩa Logarit', page: 190 },
      { title: 'Các tính chất & Công thức Logarit', page: 192 },
      { title: 'Hàm số Logarit & Đối xứng qua y = x', page: 193 }
    ]
  },
  {
    id: 'g11_t15', grade: 'grade_11', indexLabel: 'Chủ đề 15',
    title: 'Phương trình & Bất phương trình Mũ – Logarit', startPage: 195, endPage: 197,
    summary: 'Tổng hợp phương pháp giải PT mũ, BPT mũ, PT logarit và BPT logarit.',
    accentHex: '#EC4899',
    subTopics: [
      { title: 'Phương trình & BPT Mũ', page: 195 },
      { title: 'Phương trình & BPT Logarit', page: 196 }
    ]
  },
  {
    id: 'g11_t16', grade: 'grade_11', indexLabel: 'Chủ đề 16',
    title: 'Hình không gian: Giao tuyến, Thiết diện & Chứng minh', startPage: 198, endPage: 213,
    summary: 'Kí hiệu không gian, đồng phẳng, Menelaus, tìm giao điểm – giao tuyến – thiết diện, chứng minh // và ⊥.',
    accentHex: '#8B5CF6',
    subTopics: [
      { title: 'Kí hiệu, Đồng phẳng & Menelaus', page: 198 },
      { title: 'Giao điểm, Giao tuyến & Thiết diện', page: 203 },
      { title: 'Chứng minh Song song, Vuông góc & Hình chiếu', page: 208 }
    ]
  },
  {
    id: 'g11_t17', grade: 'grade_11', indexLabel: 'Chủ đề 17',
    title: 'Hình không gian: Góc, Khoảng cách & Định lý', startPage: 214, endPage: 225,
    summary: 'Góc đường–đường, đường–mặt, mặt–mặt, góc nhị diện; khoảng cách đến mặt phẳng và 2 đường chéo nhau.',
    accentHex: '#A855F7',
    subTopics: [
      { title: 'Các loại Góc & Góc nhị diện', page: 214 },
      { title: 'Khoảng cách & 2 đường thẳng chéo nhau', page: 218 },
      { title: 'Hệ thống định lý Vuông góc & Song song', page: 223 }
    ]
  },
  {
    id: 'g11_t18', grade: 'grade_11', indexLabel: 'Chủ đề 18',
    title: 'Khối đa diện, Hình khối cơ bản & Thể tích', startPage: 226, endPage: 239,
    summary: 'Chóp, tứ diện, lăng trụ, đa diện đều, mặt phẳng đối xứng, thể tích, tỷ lệ thể tích Simson và tam diện vuông.',
    accentHex: '#D946EF',
    subTopics: [
      { title: 'Chóp, Tứ diện, Lăng trụ & Hình hộp', page: 226 },
      { title: 'Đa diện lồi/đều & Mặt phẳng đối xứng', page: 230 },
      { title: 'Thể tích, Tỷ lệ thể tích & Công thức nhanh', page: 235 },
      { title: 'Độ dài đặc biệt & Tam diện vuông', page: 239 }
    ]
  },
  {
    id: 'g11_t19', grade: 'grade_11', indexLabel: 'Chủ đề 19',
    title: 'Thống kê ghép nhóm', startPage: 240, endPage: 255,
    summary: 'Quy tắc ghép nhóm & hiệu chỉnh, số trung bình, trung vị, tứ phân vị, mốt, khoảng biến thiên, phương sai & ngoại lệ.',
    accentHex: '#2DD4BF',
    subTopics: [
      { title: 'Ghép nhóm & Số trung bình', page: 240 },
      { title: 'Trung vị mẫu ghép nhóm', page: 242 },
      { title: 'Tứ phân vị mẫu ghép nhóm', page: 245 },
      { title: 'Mốt & Khoảng biến thiên', page: 250 },
      { title: 'Khoảng tứ phân vị, Phương sai & Ngoại lệ', page: 253 }
    ]
  },

  // ==================== LỚP 12 (18 CHỦ ĐỀ) ====================
  {
    id: 'g12_t01', grade: 'grade_12', indexLabel: 'Chủ đề 01',
    title: 'Tính đơn điệu của hàm số', startPage: 256, endPage: 263,
    summary: 'Điều kiện đồng biến – nghịch biến, tham số m với hàm bậc 3, bậc 1/bậc 1 và hàm trị tuyệt đối |f(x)|.',
    accentHex: '#34D399',
    subTopics: [
      { title: 'Dấu hiệu & Xét dấu đạo hàm', page: 256 },
      { title: 'Tham số m: Bậc 3 & Phân thức bậc 1/1', page: 259 },
      { title: 'Đơn điệu hàm chứa trị tuyệt đối |f(x)|', page: 262 }
    ]
  },
  {
    id: 'g12_t02', grade: 'grade_12', indexLabel: 'Chủ đề 02',
    title: 'Cực trị của hàm số', startPage: 264, endPage: 275,
    summary: 'Hai dấu hiệu cực trị, số cực trị |f(x)| và f(|x|), cực trị hàm bậc 3, hàm trùng phương & công thức nhanh.',
    accentHex: '#10B981',
    subTopics: [
      { title: 'Định nghĩa & Hai dấu hiệu cực trị', page: 264 },
      { title: 'Số cực trị |f(x)|, f(|x|) & Phân thức', page: 267 },
      { title: 'Cực trị hàm bậc 3 (đường thẳng qua 2 CTrị)', page: 270 },
      { title: 'Cực trị hàm trùng phương & Công thức nhanh', page: 274 }
    ]
  },
  {
    id: 'g12_t03', grade: 'grade_12', indexLabel: 'Chủ đề 03',
    title: 'Giá trị lớn nhất – Giá trị nhỏ nhất (Max – Min)', startPage: 276, endPage: 278,
    summary: 'Phương pháp tìm GTLN – GTNN trên đoạn/khoảng, BĐT hữu ích và so sánh max/min của |f(x) + m|.',
    accentHex: '#2DD4BF',
    subTopics: []
  },
  {
    id: 'g12_t04', grade: 'grade_12', indexLabel: 'Chủ đề 04',
    title: 'Đường tiệm cận của đồ thị hàm số', startPage: 279, endPage: 282,
    summary: 'Tiệm cận đứng (TCĐ), tiệm cận ngang (TCN), tiệm cận xiên (TCX) cho hàm bậc 1/1 và bậc 2/1.',
    accentHex: '#06B6D4',
    subTopics: []
  },
  {
    id: 'g12_t05', grade: 'grade_12', indexLabel: 'Chủ đề 05',
    title: 'Khảo sát sự biến thiên và Vẽ đồ thị hàm số', startPage: 283, endPage: 298,
    summary: 'Nhận dạng đồ thị, tâm đối xứng, trục đối xứng & tính chất hình học của bậc 3, trùng phương, bậc 1/1 và bậc 2/1.',
    accentHex: '#38BDF8',
    subTopics: [
      { title: 'Hàm số bậc 3', page: 283 },
      { title: 'Hàm số trùng phương', page: 286 },
      { title: 'Hàm phân thức bậc 1 / bậc 1', page: 288 },
      { title: 'Hàm phân thức bậc 2 / bậc 1', page: 294 }
    ]
  },
  {
    id: 'g12_t06', grade: 'grade_12', indexLabel: 'Chủ đề 06',
    title: 'Tương giao & Phép biến đổi đồ thị', startPage: 299, endPage: 306,
    summary: 'Tiếp xúc, tương giao, điểm cố định, tịnh tiến, đối xứng, trị tuyệt đối và co giãn đồ thị hàm số.',
    accentHex: '#60A5FA',
    subTopics: [
      { title: 'Tiếp xúc, Tương giao & Điểm cố định', page: 299 },
      { title: 'Tịnh tiến, Trị tuyệt đối, Đối xứng & Co giãn', page: 300 }
    ]
  },
  {
    id: 'g12_t07', grade: 'grade_12', indexLabel: 'Chủ đề 07',
    title: 'Nguyên hàm', startPage: 307, endPage: 321,
    summary: 'Bảng nguyên hàm đầy đủ, đổi biến, lượng giác hóa, từng phần (bảng đạo nguyên), phân thức hữu tỉ & PT vi phân.',
    accentHex: '#818CF8',
    subTopics: [
      { title: 'Khái niệm & Bảng nguyên hàm đầy đủ', page: 307 },
      { title: 'Đặt ẩn phụ & Đưa vào vi phân', page: 310 },
      { title: 'Lượng giác hóa', page: 313 },
      { title: 'Nguyên hàm từng phần & Bảng đạo nguyên', page: 314 },
      { title: 'Nguyên hàm phân thức & PT vi phân', page: 318 }
    ]
  },
  {
    id: 'g12_t08', grade: 'grade_12', indexLabel: 'Chủ đề 08',
    title: 'Tích phân & Ứng dụng hình học – vật lý', startPage: 322, endPage: 331,
    summary: 'Tính chất, BĐT tích phân, dạng đặc biệt, diện tích hình phẳng, thể tích vật thể tròn xoay, chuyển động & Elip/Parabol.',
    accentHex: '#A78BFA',
    subTopics: [
      { title: 'Định nghĩa, Tính chất, Đổi biến & Từng phần', page: 322 },
      { title: 'Các dạng tích phân đặc biệt', page: 324 },
      { title: 'Diện tích, Thể tích & Độ dài đường cong', page: 326 },
      { title: 'Ứng dụng vật lý & Đường tròn/Elip/Parabol', page: 329 }
    ]
  },
  {
    id: 'g12_t09', grade: 'grade_12', indexLabel: 'Chủ đề 09',
    title: 'Vectơ trong không gian', startPage: 332, endPage: 338,
    summary: 'Quy tắc hình hộp, cộng – trừ – tích vô hướng trong không gian, trọng tâm tứ diện và bài toán tổng hợp lực.',
    accentHex: '#C084FC',
    subTopics: []
  },
  {
    id: 'g12_t10', grade: 'grade_12', indexLabel: 'Chủ đề 10',
    title: 'Hệ tọa độ trong không gian (Oxyz)', startPage: 339, endPage: 347,
    summary: 'Tọa độ điểm/vectơ, các loại tâm tam giác trong Oxyz, kiểm tra cùng phương, hình chiếu, khoảng cách & đối xứng.',
    accentHex: '#E879F9',
    subTopics: [
      { title: 'Hệ trục & Biểu thức tọa độ Oxyz', page: 339 },
      { title: 'Các loại tâm tam giác trong Oxyz', page: 342 },
      { title: 'Hình chiếu, Khoảng cách & Đối xứng đặc biệt', page: 345 }
    ]
  },
  {
    id: 'g12_t11', grade: 'grade_12', indexLabel: 'Chủ đề 11',
    title: 'Các loại tích vectơ & Ứng dụng Oxyz', startPage: 348, endPage: 352,
    summary: 'Tích vô hướng, tích có hướng (quy tắc bàn tay phải), tích hỗn tạp và công thức tính diện tích, thể tích tứ diện/hộp.',
    accentHex: '#EC4899',
    subTopics: [
      { title: 'Tích vô hướng & Tích có hướng', page: 348 },
      { title: 'Tích hỗn tạp & Ứng dụng Diện tích/Thể tích', page: 351 }
    ]
  },
  {
    id: 'g12_t12', grade: 'grade_12', indexLabel: 'Chủ đề 12',
    title: 'Phương trình mặt cầu', startPage: 353, endPage: 354,
    summary: 'Phương trình chuẩn tắc, phương trình khai triển và 7 dạng viết phương trình mặt cầu thường gặp.',
    accentHex: '#F43F5E',
    subTopics: []
  },
  {
    id: 'g12_t13', grade: 'grade_12', indexLabel: 'Chủ đề 13',
    title: 'Phương trình mặt phẳng', startPage: 355, endPage: 361,
    summary: 'VTPT, phương trình tổng quát, mặt phẳng đoạn chắn, mặt đặc biệt, 11 dạng viết PTMP và xét cùng/khác phía.',
    accentHex: '#FB7185',
    subTopics: [
      { title: 'VTPT, PTTQ & Viết nhanh qua 3 điểm', page: 355 },
      { title: 'Đoạn chắn & Mặt phẳng đặc biệt', page: 357 },
      { title: '11 dạng viết phương trình mặt phẳng', page: 358 }
    ]
  },
  {
    id: 'g12_t14', grade: 'grade_12', indexLabel: 'Chủ đề 14',
    title: 'Phương trình đường thẳng (Oxyz)', startPage: 362, endPage: 367,
    summary: 'VTCP, phương trình tham số – chính tắc và 13 dạng viết phương trình đường thẳng trong không gian Oxyz.',
    accentHex: '#FB923C',
    subTopics: [
      { title: 'VTCP, Tham số & Chính tắc', page: 362 },
      { title: '13 dạng viết phương trình đường thẳng', page: 363 }
    ]
  },
  {
    id: 'g12_t15', grade: 'grade_12', indexLabel: 'Chủ đề 15',
    title: 'Vị trí tương đối trong Oxyz', startPage: 368, endPage: 373,
    summary: 'Xét vị trí tương đối giữa 2 đường thẳng, đường và mặt phẳng, đường/mặt với mặt cầu và 2 mặt phẳng.',
    accentHex: '#F59E0B',
    subTopics: [
      { title: '2 đường thẳng & Đường với Mặt phẳng', page: 368 },
      { title: 'Đường/Mặt với Mặt cầu & 2 Mặt phẳng', page: 372 }
    ]
  },
  {
    id: 'g12_t16', grade: 'grade_12', indexLabel: 'Chủ đề 16',
    title: 'Hình chiếu, Đối xứng & Tổng hợp Góc – Khoảng cách', startPage: 374, endPage: 377,
    summary: 'Tìm hình chiếu vuông góc, điểm đối xứng, tổng hợp công thức góc và khoảng cách 2 đường chéo nhau Oxyz.',
    accentHex: '#EAB308',
    subTopics: [
      { title: 'Hình chiếu & Điểm đối xứng', page: 374 },
      { title: 'Tổng hợp Góc & Khoảng cách Oxyz', page: 375 }
    ]
  },
  {
    id: 'g12_t17', grade: 'grade_12', indexLabel: 'Chủ đề 17',
    title: 'Tâm tỷ cự & Cực trị hình học Oxyz', startPage: 378, endPage: 385,
    summary: 'Tổ hợp tuyến tính vectơ, tổng bình phương độ dài, 5 bất đẳng thức quen thuộc và các bài toán cực trị Oxyz.',
    accentHex: '#22C55E',
    subTopics: []
  },
  {
    id: 'g12_t18', grade: 'grade_12', indexLabel: 'Chủ đề 18',
    title: 'Xác suất có điều kiện & Công thức Bayes', startPage: 386, endPage: 387,
    summary: 'Công thức xác suất có điều kiện P(A|B), công thức xác suất toàn phần, định lý Bayes và sơ đồ cây.',
    accentHex: '#14B8A6',
    subTopics: []
  }
];

export function normalizeVi(input: string): string {
  return input
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

export function topicForPage(page: number): PdfTopicEntry | null {
  return PDF_TOC_TOPICS.find((t) => page >= t.startPage && page <= t.endPage) || null;
}

export function sectionLabelForPage(page: number): string {
  if (page <= 2) return 'Bìa & Tổng quan 3 lớp';
  if (page >= 3 && page <= 17) return 'Mục lục chi tiết PDF';
  if (page >= 388) return 'Trang cuối tài liệu';
  const t = topicForPage(page);
  if (!t) return `Trang ${page}`;
  const g = PDF_GRADES.find((x) => x.id === t.grade)?.shortLabel || '';
  return `${g} · ${t.title}`;
}

export function searchPdfTopics(query: string): PdfTopicSearchHit[] {
  const norm = normalizeVi(query).trim();
  if (!norm) return [];
  const tokens = norm.split(/\s+/).filter(Boolean);
  if (tokens.length === 0) return [];

  const results: PdfTopicSearchHit[] = [];
  for (const topic of PDF_TOC_TOPICS) {
    const gradeLabel = PDF_GRADES.find((g) => g.id === topic.grade)?.shortLabel || '';
    const directSub = topic.subTopics.find((sub) => {
      const ownText = normalizeVi(`${sub.title} trang ${sub.page}`);
      return tokens.every((tk) => ownText.includes(tk));
    });
    const combinedSub =
      directSub ||
      topic.subTopics.find((sub) => {
        const ownText = normalizeVi(sub.title);
        const combined = normalizeVi(`${sub.title} ${topic.title} ${gradeLabel} trang ${sub.page}`);
        return tokens.some((tk) => ownText.includes(tk)) && tokens.every((tk) => combined.includes(tk));
      });
    const topicText = normalizeVi(
      `${topic.title} ${topic.summary} ${gradeLabel} ${topic.indexLabel} trang ${topic.startPage}`
    );
    const topicMatch = tokens.every((tk) => topicText.includes(tk));

    if (combinedSub) {
      results.push({ topic, matchedSubTopic: combinedSub, targetPage: combinedSub.page });
    } else if (topicMatch) {
      results.push({ topic, matchedSubTopic: null, targetPage: topic.startPage });
    }
  }
  return results;
}
