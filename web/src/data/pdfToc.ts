export type GradeId = 'grade_10' | 'grade_11' | 'grade_12';

export interface PdfTopicItem {
  id: string;
  grade: GradeId;
  indexLabel: string;
  title: string;
  startPage: number;
  endPage: number;
  summary: string;
}

export const PDF_TOTAL_PAGES = 388;
export const PDF_DEFAULT_URL = (import.meta.env.VITE_PDF_URL as string | undefined) || '/docs/toan_10_11_12_dpad.pdf';

export const PDF_CURRICULUM_TOPICS: PdfTopicItem[] = [
  // Lớp 10
  { id: 'g10_01', grade: 'grade_10', indexLabel: 'CĐ 01', title: 'Mệnh đề & Tập hợp', startPage: 18, endPage: 25, summary: 'Mệnh đề, phủ định, kéo theo, giao – hợp – hiệu – phần bù, tập con của ℝ.' },
  { id: 'g10_02', grade: 'grade_10', indexLabel: 'CĐ 02', title: 'BPT & Hệ BPT bậc nhất hai ẩn', startPage: 26, endPage: 28, summary: 'Miền nghiệm, cực trị F = ax + by và bài toán quy hoạch tuyến tính.' },
  { id: 'g10_03', grade: 'grade_10', indexLabel: 'CĐ 03', title: 'Hàm số, Đồ thị & Hàm số bậc hai', startPage: 29, endPage: 44, summary: 'Tập xác định, biến thiên, Parabol, dấu tam thức bậc hai.' },
  { id: 'g10_04', grade: 'grade_10', indexLabel: 'CĐ 04', title: 'Hệ thức lượng trong tam giác', startPage: 45, endPage: 56, summary: 'Định lí cosin, định lí sin, công thức diện tích tam giác.' },
  { id: 'g10_05', grade: 'grade_10', indexLabel: 'CĐ 05', title: 'Vectơ & Tọa độ trong mặt phẳng Oxy', startPage: 57, endPage: 82, summary: 'Tổng hiệu vectơ, tích vô hướng, đường thẳng, đường tròn, ba đường conic.' },
  { id: 'g10_06', grade: 'grade_10', indexLabel: 'CĐ 06', title: 'Đại số tổ hợp & Xác suất', startPage: 83, endPage: 110, summary: 'Quy tắc đếm, hoán vị, chỉnh hợp, tổ hợp, nhị thức Newton, xác suất cổ điển.' },

  // Lớp 11
  { id: 'g11_01', grade: 'grade_11', indexLabel: 'CĐ 01', title: 'Hàm số & Phương trình Lượng giác', startPage: 111, endPage: 136, summary: 'Công thức lượng giác, đồ thị sin/cos/tan/cot, phương trình lượng giác cơ bản.' },
  { id: 'g11_02', grade: 'grade_11', indexLabel: 'CĐ 02', title: 'Dãy số, Cấp số cộng & Cấp số nhân', startPage: 137, endPage: 152, summary: 'Số hạng tổng quát, tổng n số hạng đầu, tính tăng giảm & bị chặn.' },
  { id: 'g11_03', grade: 'grade_11', indexLabel: 'CĐ 03', title: 'Giới hạn & Hàm số liên tục', startPage: 153, endPage: 174, summary: 'Giới hạn dãy số, giới hạn hàm số, dạng vô định, tính liên tục tại điểm.' },
  { id: 'g11_04', grade: 'grade_11', indexLabel: 'CĐ 04', title: 'Hàm số Mũ & Logarit', startPage: 175, endPage: 198, summary: 'Lũy thừa, quy tắc logarit, phương trình & bất phương trình mũ – logarit.' },
  { id: 'g11_05', grade: 'grade_11', indexLabel: 'CĐ 05', title: 'Quan hệ Song song & Vuông góc trong 3D', startPage: 199, endPage: 234, summary: 'Giao tuyến, thiết diện, góc giữa đường – mặt, khoảng cách, thể tích khối đa diện.' },
  { id: 'g11_06', grade: 'grade_11', indexLabel: 'CĐ 06', title: 'Đạo hàm & Quy tắc tính đạo hàm', startPage: 235, endPage: 255, summary: 'Ý nghĩa hình học, tiếp tuyến, đạo hàm hàm hợp, đạo hàm cấp hai.' },

  // Lớp 12
  { id: 'g12_01', grade: 'grade_12', indexLabel: 'CĐ 01', title: 'Ứng dụng Đạo hàm & Khảo sát Hàm số', startPage: 256, endPage: 294, summary: 'Đơn điệu, cực trị, GTLN–GTNN, đường tiệm cận, bảng biến thiên & tương giao.' },
  { id: 'g12_02', grade: 'grade_12', indexLabel: 'CĐ 02', title: 'Vectơ & Hệ trục tọa độ Oxyz', startPage: 295, endPage: 318, summary: 'Tọa độ điểm, tích có hướng, phương trình mặt phẳng, đường thẳng & mặt cầu.' },
  { id: 'g12_03', grade: 'grade_12', indexLabel: 'CĐ 03', title: 'Nguyên hàm – Tích phân & Ứng dụng', startPage: 319, endPage: 352, summary: 'Bảng nguyên hàm, phương pháp đổi biến/từng phần, diện tích hình phẳng & thể tích vật thể tròn xoay.' },
  { id: 'g12_04', grade: 'grade_12', indexLabel: 'CĐ 04', title: 'Thống kê mẫu số liệu ghép nhóm', startPage: 353, endPage: 368, summary: 'Khoảng biến thiên, tứ phân vị, phương sai và độ lệch chuẩn.' },
  { id: 'g12_05', grade: 'grade_12', indexLabel: 'CĐ 05', title: 'Xác suất có điều kiện & Công thức Bayes', startPage: 369, endPage: 387, summary: 'Công thức nhân xác suất, xác suất toàn phần, định lí Bayes & sơ đồ hình cây.' }
];

export function normalizeVietnamese(input: string): string {
  return input
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd');
}
