package com.example

import com.example.ai.AiDrawTarget
import com.example.ai.AiResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStreamingParserTest {

    @Test
    fun extractStreamingAnswerPreview_streamsIncrementallyAcrossVietnameseAndEscapes() {
        val fullJson = """{"answer": "Công thức khoảng cách:\n• d = |Ax + By + C| / \u221a(A² + B²)\n\"Ghi nhớ\" trang 94.", "suggestedPages": [{"page": 94}]}"""

        var prev = ""
        val milestones = mutableListOf<String>()
        // Simulate realistic 4-char SSE network chunks
        for (end in 4..fullJson.length step 4) {
            val chunk = fullJson.substring(0, end)
            val preview = AiResponseParser.extractStreamingAnswerPreview(chunk)
            if (preview.isNotEmpty()) {
                assertTrue("Preview must grow monotonically without regressing", preview.length >= prev.length)
                prev = preview
                milestones.add(preview)
            }
        }

        assertTrue("Expected multiple progressive streaming updates", milestones.size > 10)
        val finalPreview = AiResponseParser.extractStreamingAnswerPreview(fullJson)
        assertEquals(
            "Công thức khoảng cách:\n• d = |Ax + By + C| / √(A² + B²)\n\"Ghi nhớ\" trang 94.",
            finalPreview
        )
    }

    @Test
    fun extractStreamingAnswerPreview_handlesMarkdownFenceAndPlainTextFallback() {
        // Incomplete opening fence should wait cleanly (no ugly ``` shown)
        assertEquals("", AiResponseParser.extractStreamingAnswerPreview("```"))
        assertEquals("", AiResponseParser.extractStreamingAnswerPreview("```json"))

        // Fenced JSON streams properly once content begins
        val fenced = "```json\n{\"answer\": \"Đạo hàm sin(x) = cos(x)"
        assertEquals("Đạo hàm sin(x) = cos(x)", AiResponseParser.extractStreamingAnswerPreview(fenced))

        // Plain-text non-JSON model streams immediately from the very first token
        val plain = "Công thức lượng giác cơ bản: sin²x + cos²x = 1"
        assertEquals(plain, AiResponseParser.extractStreamingAnswerPreview(plain))
    }

    @Test
    fun extractStreamingProblemPreview_progressivelyExtractsAllFieldsAndGeometryPhase() {
        val stage1 = """{"target": "3D", "title": "Chóp S.ABCD có SA ⊥ đáy"""
        val p1 = AiResponseParser.extractStreamingProblemPreview(stage1)
        assertEquals(AiDrawTarget.SPACE_3D, p1.target)
        assertEquals("Chóp S.ABCD có SA ⊥ đáy", p1.title)
        assertEquals("", p1.problemText)
        assertFalse(p1.isBuildingGeometry)
        assertTrue(p1.hasVisibleText)

        val stage2 = """$stage1", "problemText": "Cho hình chóp S.ABCD đáy vuông cạnh 4", "drawingSummary": "Dựng chóp pyramid4_sa, trung điểm M trên SC"""
        val p2 = AiResponseParser.extractStreamingProblemPreview(stage2)
        assertEquals("Cho hình chóp S.ABCD đáy vuông cạnh 4", p2.problemText)
        assertEquals("Dựng chóp pyramid4_sa, trung điểm M trên SC", p2.drawingSummary)
        assertFalse(p2.isBuildingGeometry)

        val stage3 = """$stage2", "solutionHint": "Dùng hệ trục Oxyz với gốc tại A", "scene3D": {"shapeKey": "pyramid4_sa""""
        val p3 = AiResponseParser.extractStreamingProblemPreview(stage3)
        assertEquals("Dùng hệ trục Oxyz với gốc tại A", p3.solutionHint)
        assertTrue(p3.isBuildingGeometry)
    }

    @Test
    fun extractStreamingProblemPreview_tracksThinkingAndPlainTextFallback() {
        val thinkingOnly = AiResponseParser.extractStreamingProblemPreview("", thinkingChars = 128)
        assertNull(thinkingOnly.target)
        assertFalse(thinkingOnly.hasVisibleText)
        assertEquals(128, thinkingOnly.thinkingChars)

        val plainText = AiResponseParser.extractStreamingProblemPreview("Đồ thị hàm bậc 3 có 2 cực trị")
        assertTrue(plainText.hasVisibleText)
        assertEquals("Đồ thị hàm bậc 3 có 2 cực trị", plainText.solutionHint)
    }
}
