# Tool Toán Web (Cloudflare Pages + Functions)

Phiên bản Web đồng hành của **Tool Toán**, chạy trực tiếp trên trình duyệt máy tính và điện thoại (hỗ trợ cài đặt PWA), triển khai tự động qua GitHub + Cloudflare Pages mà không cần VPS.

## Cấu hình triển khai trên Cloudflare Pages

1. Đẩy mã nguồn lên GitHub.
2. Vào **Cloudflare Dashboard → Workers & Pages → Create → Pages → Connect to Git**.
3. Chọn repository và điền đúng 4 thông số sau:
   - **Root directory (path):** `web`
   - **Build command:** `npm run build`
   - **Build output directory:** `dist`
   - **Environment variables:** `NODE_VERSION` = `20`
4. Nhấn **Save and Deploy**. Từ giờ mỗi lần bạn `git push` lên nhánh chính, Cloudflare tự động build và cập nhật web mới.

## Lưu ý về Sổ tay PDF (> 25 MB)

Cloudflare Pages giới hạn kích thước mỗi tệp tĩnh tối đa **25 MiB**:
- Nếu tệp `app/src/main/assets/docs/toan_10_11_12_dpad.pdf` ≤ 24.5 MB, script `scripts/prepare-pdf.mjs` sẽ tự động sao chép vào `web/public/docs/` lúc build.
- Nếu tệp lớn hơn 24.5 MB, hãy tải tệp PDF đó lên **Cloudflare R2** (miễn phí 10 GB), bật Public URL và thêm biến môi trường `VITE_PDF_URL` trong Cloudflare Pages (hoặc dán link trực tiếp trong tab **Tài liệu**).

## Proxy AI tích hợp (`/api/ai-proxy`)

Thư mục `web/functions/api/ai-proxy.js` tự động chạy dưới dạng **Cloudflare Pages Function**, giúp trình duyệt gọi mượt 7 nhà cung cấp AI (Google Gemini, OpenAI, OpenRouter, Groq, DeepSeek, Anthropic Claude, Custom OpenAI URL) dạng streaming SSE mà không bị chặn CORS. API Key của người dùng chỉ lưu cục bộ trong `localStorage` của trình duyệt.
