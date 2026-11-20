# Nhật ký thay đổi — Tool Toán

## Bản cập nhật mới nhất

### Cập nhật đè trực tiếp không cần gỡ ứng dụng
- Cố định chữ ký số APK (khôi phục từ `debug.keystore.base64` trong mọi lần build tự động) và đồng bộ chính xác `versionCode` với bộ nhớ đệm Gradle để Android cho phép bấm **Cập nhật** trực tiếp giữ nguyên toàn bộ bài vẽ và cài đặt.
- *Lưu ý chuyển đổi 1 lần duy nhất:* Nếu máy bạn đang cài bản cũ (được ký bằng khóa ngẫu nhiên trước đây), hãy gỡ bản cũ 1 lần cuối để cài bản này; từ các bản sau trở đi chỉ cần tải APK mới và bấm **Cập nhật** trực tiếp.

### Sửa lỗi logic (2D & 3D)
- **Bộ phân tích hàm số 2D:** Hỗ trợ nhập số thập phân dấu phẩy kiểu Việt Nam (VD: `0,5x^2 + 1,5x`, `log(0,5; x)`), hỗ trợ thêm cú pháp `log_(1/2)(x)` và sửa lỗi thiếu dấu ngoặc khi hiển thị công thức lũy thừa của biểu thức âm như `(-x)^2`.
- **Hoàn tác (Undo) & Nhãn hàm:** Khắc phục lỗi tạo bước hoàn tác thừa khi chỉ chạm điểm mà chưa kéo (ở cả 2D và 3D); sửa lỗi trùng tên `f2(x)` sau khi xóa hàm số; tự động cập nhật bảng dò tọa độ `f(x)`/`f'(x)` theo thời gian thực khi sửa hoặc bật/tắt hàm.
- **Lưu trạng thái & Tương tác:** Lưu tự động vị trí góc nhìn 2D sau khi kéo/zoom, lưu tự động độ nhạy 3D sau khi chỉnh thanh trượt, đồng bộ bán kính chạm khi chấm điểm trên cạnh 3D, sửa tỷ lệ khung hình khi pan/zoom 3D và khắc phục lỗi đọc trường `null` trong tệp JSON 2D.

### Tối ưu hiệu năng 60fps mượt mà hơn
- **Đồ thị 2D:** Thêm bộ nhớ đệm LRU cho biểu thức và đạo hàm ký hiệu, lưu đệm theo khung nhìn cho thuật toán tìm nghiệm/cực trị/giao điểm, và tái sử dụng toàn bộ `Paint`/`Path`/`RectF`/`Shader` để loại bỏ hiện tượng khựng khung hình khi kéo thả và zoom vô hạn.
- **Không gian 3D:** Loại bỏ việc dựng lại lưới 3D trùng lặp (giảm 50% CPU khi kéo đỉnh và trượt tham số), không dựng lại hình khi chỉ bật/tắt lưới, nhãn, trục hoặc độ nhạy, đồng thời tái sử dụng bộ nhớ đồ họa khi vẽ mặt cong trụ/nón/cầu.
- **Tối ưu lưu đĩa:** Gom nhóm (debounce) thao tác ghi phiên làm việc khi gõ công thức hoặc chỉnh độ nhạy.

---

## Các phiên bản trước

### Sửa lỗi đo đạc & Giao diện gọn gàng
- Khắc phục sai số tam giác đều 2D, nâng cấp độ chính xác `Double` cho phép đo 2D, bổ sung bộ **Đo nhanh khoảng cách & góc 2D**, ký hiệu góc vuông `∟` và chiều cao thực 3D `h = d(đỉnh, mặt đáy)`.
- Đồng bộ thanh tab Material 3, tự thu gọn thanh công cụ 2D khi mở bảng, phân nhóm thư viện hàm số (**Mũ & Loga**, **Đa thức**, **Phân thức & Căn**, **Lượng giác**, **Đặc biệt**) và tự động tăng phiên bản phát hành `Tool Toán v1 → v2 → v3...`.
