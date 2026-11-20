# Nhật ký thay đổi — Tool Toán

## Bản cập nhật mới nhất

### Sửa lỗi đo đạc (2D & 3D)
- Khắc phục lỗi làm tròn sai số khi dựng tam giác đều 2D (đảm bảo chính xác cạnh `4` và góc `60°`).
- Sửa lỗi chia 0 / sai số tọa độ khi bật hút lưới ở mức zoom rất sâu hoặc rất xa; nâng cấp toàn bộ phép đo độ dài, diện tích, chu vi, phương trình đường thẳng/đường tròn và góc lên độ chính xác `Double`.
- Bổ sung bộ **Đo nhanh khoảng cách & góc 2D** ngay trong tab **Đo đạc** (tính độ dài, trung điểm, vectơ, phương trình, góc độ/radian và nút nối đoạn / ghim góc 1 chạm).
- Hiển thị đầy đủ độ dài các cạnh đa giác, chu vi & diện tích đường tròn, tia cạnh góc nét đứt và ký hiệu **góc vuông `∟`** chuẩn toán học; tự ẩn nhãn đo khi hình quá nhỏ để tránh đè chữ.
- Sửa phép tính chiều cao thực `h = d(đỉnh, mặt đáy)` trong không gian 3D khi kéo đỉnh tự do không ràng buộc.

### Giao diện gọn gàng & trực quan hơn
- Tự động thu gọn thanh công cụ 2D khi mở bảng điều khiển, đồng bộ thanh tab kiểu Material 3 và tay kéo đóng bảng giữa 2 không gian **2D** và **3D**.
- Thư viện hàm mẫu theo nhóm (**Mũ & Loga**, **Đa thức**, **Phân thức & Căn**, **Lượng giác**, **Đặc biệt**) có thể thu gọn/mở rộng gọn gàng; danh sách điểm hiển thị dạng lưới 2 cột tiết kiệm diện tích.
- Thanh điều khiển nhanh hai bên và thanh tiêu đề gọn nhẹ, thoáng mắt hơn.

### Phiên bản & Phát hành tự động
- Tự động tăng phiên bản `Tool Toán v1 → v2 → v3...` kèm đồng bộ `versionCode` / `versionName` vào file APK và tự đính kèm nhật ký thay đổi vào mô tả GitHub Release.
