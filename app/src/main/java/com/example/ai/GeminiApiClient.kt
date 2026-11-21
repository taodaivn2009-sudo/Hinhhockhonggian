package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.docs.PDF_TOTAL_PAGES
import com.example.docs.PdfCurriculumRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Lightweight, zero-bloat Gemini Developer API client using end-user provided API key
 * via the `x-goog-api-key` header.
 * Supports:
 * 1. Live API key verification (`GET /v1beta/models`)
 * 2. Document Assistant grounded in the exact 388-page DPAD Toán 10-11-12 PDF index (+ optional PDF page image)
 * 3. Multimodal Problem Photo -> Structured 3D / 2D Geometry & Graph Drawing Plan
 */
object GeminiApiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    suspend fun verifyApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập API Key trước khi kiểm tra."))
        }
        runCatching {
            val url = URL("$BASE_URL/models?pageSize=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 20_000
                setRequestProperty("x-goog-api-key", cleanKey)
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
                if (code !in 200..299) {
                    throw IllegalStateException(formatHttpError(code, body))
                }
                val json = JSONObject(body)
                val count = json.optJSONArray("models")?.length() ?: 0
                "Kết nối API Key thành công! Đã xác thực ($count+ mô hình khả dụng)."
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun askDocumentAssistant(
        apiKey: String,
        modelId: String,
        question: String,
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap? = null,
        attachedPageNumber: Int? = null
    ): Result<AiDocsReply> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Chưa có API Key. Hãy nhập key trong Mục Key để hỏi AI."))
        }
        runCatching {
            val systemText = buildDocsSystemPrompt()
            val contents = JSONArray()

            // Include last 6 turns of conversation context
            history.filter { !it.isError }.takeLast(6).forEach { msg ->
                contents.put(
                    JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }

            val userParts = JSONArray()
            if (attachedPageBitmap != null) {
                userParts.put(bitmapToInlineDataPart(attachedPageBitmap, maxDim = 1400))
                val pageNote = attachedPageNumber?.let { " (Đính kèm hình ảnh Trang $it trong tài liệu PDF)" } ?: ""
                userParts.put(JSONObject().put("text", "$question$pageNote"))
            } else {
                userParts.put(JSONObject().put("text", question))
            }
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", userParts)
                }
            )

            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText))))
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.3)
                })
            }

            val rawResponseText = executeGenerateContent(cleanKey, modelId, payload)
            AiResponseParser.parseDocsReply(rawResponseText)
        }
    }

    suspend fun analyzeProblemToDrawing(
        apiKey: String,
        modelId: String,
        problemBitmap: Bitmap?,
        problemNote: String
    ): Result<AiDrawingPlan> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Chưa có API Key. Hãy nhập key trong Mục Key để quét đề bài."))
        }
        if (problemBitmap == null && problemNote.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Hãy chụp/chọn ảnh đề bài hoặc nhập nội dung đề bài."))
        }

        runCatching {
            val parts = JSONArray()
            if (problemBitmap != null) {
                parts.put(bitmapToInlineDataPart(problemBitmap, maxDim = 1600))
            }
            val promptLine = buildString {
                if (problemBitmap != null) {
                    append("Hãy đọc thật chính xác đề bài toán trong ảnh đính kèm và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON.")
                } else {
                    append("Hãy phân tích đề bài toán sau và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON.")
                }
                if (problemNote.isNotBlank()) {
                    append("\nNội dung / ghi chú thêm từ người dùng:\n")
                    append(problemNote.trim())
                }
            }
            parts.put(JSONObject().put("text", promptLine))

            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", buildProblemDrawSystemPrompt())))
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", parts)
                        }
                    )
                )
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val rawResponseText = executeGenerateContent(cleanKey, modelId, payload)
            AiResponseParser.parseDrawingPlan(rawResponseText)
                ?: throw IllegalStateException("AI không trả về dữ liệu dựng hình hợp lệ. Hãy thử chụp ảnh rõ nét hơn hoặc mô tả thêm đề bài.")
        }
    }

    private fun executeGenerateContent(apiKey: String, modelId: String, payload: JSONObject): String {
        val safeModel = modelId.trim().removePrefix("models/").ifEmpty { AiModelPreset.GEMINI_3_8_FLASH.modelId }
        val url = URL("$BASE_URL/models/$safeModel:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val code = conn.responseCode
            val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
            if (code !in 200..299) {
                throw IllegalStateException(formatHttpError(code, body))
            }
            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates")
            val first = candidates?.optJSONObject(0)
            val parts = first?.optJSONObject("content")?.optJSONArray("parts")
            val textBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                    if (t.isNotEmpty()) textBuilder.append(t)
                }
            }
            val result = textBuilder.toString().trim()
            if (result.isEmpty()) {
                throw IllegalStateException("AI trả về nội dung trống. Vui lòng thử lại.")
            }
            return result
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Kết nối tới AI bị quá thời gian (60 giây). Hãy kiểm tra mạng và thử lại.")
        } finally {
            conn.disconnect()
        }
    }

    private fun bitmapToInlineDataPart(source: Bitmap, maxDim: Int): JSONObject {
        val scaled = downscaleBitmap(source, maxDim)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return JSONObject().put(
            "inlineData",
            JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64)
            }
        )
    }

    internal fun downscaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun formatHttpError(code: Int, body: String): String {
        val apiMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")

        return when (code) {
            400, 401, 403 -> "API Key không hợp lệ hoặc không có quyền truy cập (HTTP $code). ${apiMsg.take(160)}"
            404 -> "Không tìm thấy mô hình đã chọn (HTTP 404). Hãy đổi sang mô hình khác trong Mục Key. ${apiMsg.take(120)}"
            429 -> "API Key đã vượt quá giới hạn lượt gọi (HTTP 429). Vui lòng đợi ít phút rồi thử lại."
            else -> "Lỗi máy chủ AI (HTTP $code). ${apiMsg.take(160)}"
        }.trim()
    }

    internal fun buildCompactTocIndex(): String {
        return buildString {
            appendLine("MỤC LỤC CHÍNH XÁC TÀI LIỆU PDF 388 TRANG (Tóm Tắt Kiến Thức Toán 10-11-12 DPAD):")
            appendLine("- Trang 1-2: Bìa & Sơ đồ tổng hợp 3 lớp")
            appendLine("- Trang 3-17: Mục lục gốc PDF")
            PdfCurriculumRepository.allTopics.forEach { t ->
                append("- [${t.grade.shortLabel}] ${t.title} (Tr. ${t.startPage}-${t.endPage})")
                if (t.subTopics.isNotEmpty()) {
                    append(" | Mục con: ")
                    append(t.subTopics.joinToString("; ") { "${it.title}=Tr.${it.page}" })
                }
                appendLine()
            }
        }
    }

    internal fun buildDocsSystemPrompt(): String = """
Bạn là Trợ lý Toán học THPT (Lớp 10, 11, 12) tích hợp trực tiếp trong ứng dụng Tool Toán.
Người dùng có sẵn cuốn tài liệu PDF "$PDF_TOTAL_PAGES trang — Tóm Tắt Kiến Thức Toán 10 - 11 - 12 (DPAD)".

${buildCompactTocIndex()}

NHIỆM VỤ:
- Trả lời ngắn gọn, rõ ràng, dễ hiểu bằng tiếng Việt, trình bày công thức toán dễ đọc bằng ký hiệu Unicode chuẩn (√, π, α, β, ², ³, ∫, ∑, ∞, ≤, ≥, ≠, ⊥, //, ∈, ℝ).
- Luôn chỉ ra chính xác các số trang liên quan trong tài liệu PDF 388 trang ở trên để người dùng bấm mở thẳng tới trang đó!
- Nếu có hình ảnh trang PDF đính kèm, hãy đọc kỹ nội dung trong ảnh để giải thích hoặc tóm tắt chính xác.

BẮT BUỘC TRẢ VỀ JSON HỢP LỆ (không bọc markdown):
{
  "answer": "Nội dung trả lời chi tiết, công thức & mẹo nhớ bằng tiếng Việt",
  "suggestedPages": [
    {"page": 121, "title": "Bảng công thức lượng giác đầy đủ", "reason": "Tổng hợp công thức cộng, nhân đôi, hạ bậc"}
  ]
}
Lưu ý: Mỗi `page` phải là số nguyên từ 1 đến $PDF_TOTAL_PAGES. Tối đa 4 trang gợi ý sát nhất.
""".trimIndent()

    internal fun buildProblemDrawSystemPrompt(): String = """
Bạn là chuyên gia Toán THPT (Hình học không gian 3D, Oxyz, Hình học phẳng Oxy và Khảo sát hàm số 2D) tích hợp trong ứng dụng Tool Toán.
Nhiệm vụ của bạn là đọc ảnh đề bài (hoặc văn bản đề bài), nhận diện xem nên vẽ trên **3D** hay **2D**, và xuất cấu hình dựng hình CHÍNH XÁC tuyệt đối theo hệ thống đỉnh của ứng dụng.

=== 1. DANH SÁCH 15 KHỐI 3D & TÊN ĐỈNH CHUẨN ===
- "pyramid3": Chóp tam giác đều S.ABC (SO ⊥ đáy). Đỉnh sẵn: S, A, B, C, O
- "pyramid3_sa": Chóp tam giác S.ABC có SA ⊥ (ABC). Đỉnh sẵn: S, A, B, C, O (=A)
- "tetra_ortho": Tứ diện vuông O.ABC (OA, OB, OC đôi một vuông góc; dùng a=OA, b=OB, h=OC). Đỉnh sẵn: O, A, B, C
- "pyramid4": Chóp tứ giác đều S.ABCD (SO ⊥ đáy). Đỉnh sẵn: S, A, B, C, D, O
- "pyramid4_sa": Chóp tứ giác S.ABCD có SA ⊥ (ABCD). Đỉnh sẵn: S, A, B, C, D, O (=A)
- "pyramid_trap": Chóp S.ABCD đáy hình thang vuông tại A, B, SA ⊥ đáy. Đỉnh sẵn: S, A, B, C, D, O
- "prism3": Lăng trụ đứng tam giác ABC.A'B'C'. Đỉnh sẵn: A, B, C, A', B', C', O
- "prism3_oblique": Lăng trụ xiên ABC.A'B'C' (A'O ⊥ đáy). Đỉnh sẵn: A, B, C, A', B', C', O
- "prism6": Lăng trụ lục giác đều. Đỉnh sẵn: A, B, C, D, E, F, A', B', C', D', E', F', O
- "cube": Hình lập phương ABCD.A'B'C'D' cạnh a. Đỉnh sẵn: A, B, C, D, A', B', C', D', O
- "box": Hình hộp chữ nhật a x b x h. Đỉnh sẵn: A, B, C, D, A', B', C', D', O
- "octahedron": Bát diện đều cạnh a. Đỉnh sẵn: S, S', A, B, C, D, O
- "cylinder": Hình trụ tròn bán kính a/2, cao h. Đỉnh sẵn: O, O', A, B
- "cone": Hình nón tròn đỉnh S, đáy tâm O, bán kính a/2, cao h. Đỉnh sẵn: S, O, A, B
- "sphere": Hình cầu tâm O, bán kính a/2. Đỉnh sẵn: O, A, B

Trong 3D:
- Dùng `edgePoints` để tạo trung điểm (ratio=0.5), điểm chia tỉ lệ (ratio=0.333, 0.25, 0.667...) trên cạnh từ `from` đến `to` (chỉ dùng tên đỉnh đã có hoặc đỉnh mới tạo trước đó).
- Dùng `xyzPoints` nếu đề bài cho tọa độ Oxyz cụ thể (và đặt `showAxes: true`).
- Dùng `lines` với `style`: "solid", "dashed", "vector_solid", "vector_dashed", kèm trường `highlighted` (true/false) và `colorHex`.
- Dùng `planes` CHỈ KHI đề bài nhắc trực tiếp tới mặt phẳng cần xét (vd: góc/khoảng cách tới `(AMN)`). Nếu đề chỉ hỏi về đoạn thẳng hoặc vectơ thì để `"planes": []`.
- Dùng `dynamicSection` chỉ khi đề bài yêu cầu vẽ thiết diện cắt khối bởi 3 điểm `{"p1":"A","p2":"M","p3":"N"}`.
- `showAltitude`: đặt `false` mặc định; chỉ đặt `true` khi đề bài nhắc trực tiếp đến đường cao nội tại của khối (vd: `SO`, `SH`, `OO'`).
- Giá trị kích thước `a`, `b`, `h` nên scale đẹp trong khoảng 2.0 đến 7.0 (nếu đề cho bằng chữ `a`, chọn a=4.0, h=4.8 hoặc theo tỉ lệ đề cho).

=== 2. QUY TẮC BẮT BUỘC VỀ NÉT ĐỨT & TÔ MÀU (TUÂN THỦ TUYỆT ĐỐI) ===
1. NÉT ĐỨT CHO ĐƯỜNG KHUẤT & VECTƠ KHUẤT:
   - Trong 3D: Dùng `style: "vector_solid"` cho mọi vectơ và `style: "solid"` cho mọi đoạn thẳng — bộ dựng hình 3D sẽ TỰ ĐỘNG kiểm tra che khuất theo đúng tọa độ không gian & góc nhìn camera để vẽ **nét đứt** khi đoạn/vectơ nằm bên trong khối hoặc bị mặt trước che khuất, và vẽ **nét liền** khi lộ ra phía trước (tự cập nhật khi xoay hình). KHÔNG ép cứng nét đứt cho vectơ/đoạn không bị che.
   - Trong 2D (Oxy): Các vectơ và đoạn thẳng chính của đề bài đặt `"dashed": false` (nét liền); chỉ đặt `"dashed": true` cho đường gióng vuông góc, đường phụ hoặc nét đứt mà đề bài ghi rõ.
2. CHỈ TÔ MÀU CÁC ĐOẠN THẲNG HOẶC VECTƠ ĐỀ BÀI NHẮC TRỰC TIẾP:
   - CHỈ đặt `"highlighted": true` và chọn màu nổi bật (`"#F43F5E"`, `"#38BDF8"`, `"#10B981"`, `"#FBBF24"`, `"#A78BFA"`) cho ĐÚNG các đoạn thẳng hoặc vectơ được đề bài nhắc trực tiếp / yêu cầu tính toán (vd: đề hỏi tính góc giữa hai vectơ `AB` và `SC`, độ dài `AM`, tổng hiệu vectơ `AB + AD + AA'`...).
   - Các đường phụ dựng thêm, đa giác/đường tròn cơ sở trong 2D, hoặc các cạnh có sẵn của khối 3D KHÔNG được đề bài nhấn mạnh: đặt `"highlighted": false` và `"colorHex": ""` (giữ nét mực trung tính đồng bộ với hình gốc, TUYỆT ĐỐI không tô màu lòe loẹt).
   - Trong 3D: KHÔNG thêm lại các cạnh sẵn có của khối vào `lines`, TRỪ KHI cạnh đó là đoạn thẳng hoặc vectơ được đề bài nhắc trực tiếp cần tô màu hoặc hiển thị mũi tên vectơ.

=== 3. HỆ THỐNG HÌNH PHẲNG & ĐỒ THỊ 2D (Oxy) ===
Chọn target="2D" cho các bài toán: khảo sát hàm số / vẽ đồ thị y=f(x), tiếp tuyến, tương giao, tam giác/tứ giác/đường tròn/Elip trên Oxy.
- `points`: danh sách điểm `{"name":"A","x":0,"y":0,"colorHex":""}`
- `objects`:
  - `kind`: "segment" | "line" | "ray" | "vector" (dùng `p1`, `p2`, `dashed`, `highlighted`, `colorHex`)
  - `kind`: "circle" (dùng `p1` là tâm, và `p2` là điểm trên đường tròn HOẶC `radius` là bán kính số dương; để `highlighted: false`, `colorHex: ""` nếu chỉ là hình nền cơ sở)
  - `kind`: "polygon" (dùng `pointNames`: ["A","B","C"]; để `highlighted: false`, `colorHex: ""` cho đa giác cơ sở)
  - `kind`: "angle" (dùng `p1`, `p2` là đỉnh góc, `p3`)
- `functions`: biểu thức chuẩn dùng biến `x` (và tham số `m` nếu có), hỗ trợ: `+ - * / ^`, `sin(x)`, `cos(x)`, `tan(x)`, `sqrt(x)`, `cbrt(x)`, `ln(x)`, `log(x)`, `log2(x)`, `log(a; x)`, `e^x`, `|x|`, `pi`. Ví dụ: `"x^3 - 3x + 1"`, `"(2x - 1)/(x - 1)"`.

=== 4. MỤC LỤC PDF ĐỂ GỢI Ý TRANG CÔNG THỨC LIÊN QUAN ===
${buildCompactTocIndex()}

BẮT BUỘC TRẢ VỀ JSON HỢP LỆ DUY NHẤT (không bọc markdown):
{
  "target": "3D" hoặc "2D",
  "title": "Tên ngắn gọn hình vẽ (vd: Chóp S.ABCD có SA ⊥ đáy & vectơ AM, SB)",
  "problemText": "Nội dung đề bài đã đọc được từ ảnh/văn bản",
  "drawingSummary": "Liệt kê rõ các hình khối, điểm phụ, đoạn thẳng/vectơ được tô màu nhấn mạnh",
  "solutionHint": "Gợi ý ngắn gọn hướng giải & công thức áp dụng",
  "scene3D": {
    "shapeKey": "pyramid4_sa",
    "a": 4.0,
    "b": 4.0,
    "h": 5.0,
    "showAxes": false,
    "showAltitude": false,
    "originPoint": "A",
    "edgePoints": [{"name":"M","from":"S","to":"C","ratio":0.5}],
    "xyzPoints": [],
    "lines": [
      {"p1":"A","p2":"M","style":"vector_solid","highlighted":true,"colorHex":"#F43F5E"},
      {"p1":"S","p2":"B","style":"vector_solid","highlighted":true,"colorHex":"#38BDF8"}
    ],
    "planes": [],
    "dynamicSection": null
  },
  "scene2D": null,
  "relatedPages": [{"page": 235, "title": "Thể tích đa diện", "reason": "Công thức thể tích khối chóp"}]
}
Chỉ điền `scene3D` khi target="3D" (để `scene2D`: null), hoặc chỉ điền `scene2D` khi target="2D" (để `scene3D`: null).
""".trimIndent()
}
