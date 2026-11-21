package com.example.docs

import android.content.Context
import com.example.engine.ShapeType
import java.text.Normalizer

enum class MathGrade(
    val id: String,
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val accentHex: Long
) {
    GRADE_10(
        id = "grade_10",
        shortLabel = "Lớp 10",
        title = "Toán Lớp 10",
        subtitle = "Mệnh đề, Hệ thức lượng, Vectơ, Hàm bậc hai, Oxy, Tổ hợp & Xác suất",
        accentHex = 0xFF10B981
    ),
    GRADE_11(
        id = "grade_11",
        shortLabel = "Lớp 11",
        title = "Toán Lớp 11",
        subtitle = "Lượng giác, Cấp số, Giới hạn, Mũ – Lôgarit, Không gian & Đạo hàm",
        accentHex = 0xFFF59E0B
    ),
    GRADE_12(
        id = "grade_12",
        shortLabel = "Lớp 12",
        title = "Toán Lớp 12",
        subtitle = "Khảo sát hàm số, Nguyên hàm – Tích phân, Oxyz & Xác suất Bayes",
        accentHex = 0xFF6366F1
    )
}

enum class DocsOverviewTab(val label: String) {
    GRADE_10("Lớp 10"),
    GRADE_11("Lớp 11"),
    GRADE_12("Lớp 12"),
    FAVORITES("Yêu thích");

    fun toGradeOrNull(): MathGrade? = when (this) {
        GRADE_10 -> MathGrade.GRADE_10
        GRADE_11 -> MathGrade.GRADE_11
        GRADE_12 -> MathGrade.GRADE_12
        FAVORITES -> null
    }

    companion object {
        fun fromGrade(grade: MathGrade): DocsOverviewTab = when (grade) {
            MathGrade.GRADE_10 -> GRADE_10
            MathGrade.GRADE_11 -> GRADE_11
            MathGrade.GRADE_12 -> GRADE_12
        }
    }
}

sealed class DocQuickAction {
    abstract val label: String

    data class Plot2D(
        override val label: String,
        val expression: String
    ) : DocQuickAction()

    data class Open3D(
        override val label: String,
        val shape: ShapeType,
        val showAxes: Boolean = false
    ) : DocQuickAction()
}

data class MathFormulaItem(
    val id: String,
    val lhs: String,
    val rhs: String,
    val condition: String? = null,
    val note: String? = null
)

data class MathFormulaGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentHex: Long,
    val formulas: List<MathFormulaItem>,
    val mnemonic: String? = null,
    val specialKind: SpecialAngleKind? = null
)

data class MathChapter(
    val id: String,
    val grade: MathGrade,
    val chapterNumber: String,
    val title: String,
    val summary: String,
    val accentHex: Long,
    val groups: List<MathFormulaGroup>,
    val quickActions: List<DocQuickAction> = emptyList(),
    val hasTrigInteractiveTools: Boolean = false
) {
    val formulaCount: Int get() = groups.sumOf { it.formulas.size }
}

data class FormulaSearchHit(
    val chapter: MathChapter,
    val group: MathFormulaGroup,
    val formula: MathFormulaItem
)

/**
 * Central repository aggregating the complete Vietnamese GDPT 2018 high-school math
 * curriculum across Grades 10, 11, and 12 (24 chapters total), with Vietnamese
 * diacritic-insensitive full-text search and favorite formula resolution.
 */
object HighSchoolMathRepository {

    val allChapters: List<MathChapter> by lazy {
        Grade10CurriculumData.chapters +
            Grade11CurriculumData.chapters +
            Grade12CurriculumData.chapters
    }

    private val chapterMap: Map<String, MathChapter> by lazy {
        allChapters.associateBy { it.id }
    }

    private val allHits: List<FormulaSearchHit> by lazy {
        allChapters.flatMap { chapter ->
            chapter.groups.flatMap { group ->
                group.formulas.map { formula ->
                    FormulaSearchHit(chapter, group, formula)
                }
            }
        }
    }

    fun chaptersFor(grade: MathGrade): List<MathChapter> =
        allChapters.filter { it.grade == grade }

    fun chapterById(chapterId: String): MathChapter? = chapterMap[chapterId]

    fun totalFormulaCount(grade: MathGrade): Int =
        chaptersFor(grade).sumOf { it.formulaCount }

    fun totalFormulaCountAllGrades(): Int = allHits.size

    fun favoriteHits(favoriteIds: Set<String>): List<FormulaSearchHit> {
        if (favoriteIds.isEmpty()) return emptyList()
        return allHits.filter { it.formula.id in favoriteIds }
    }

    fun normalizeForSearch(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        val noDiacritics = decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
        return noDiacritics
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('·', '*')
            .replace('×', '*')
            .replace("π", "pi")
            .replace("α", "a")
            .replace("β", "b")
            .replace("φ", "phi")
            .replace("Δ", "delta")
            .replace("²", "2")
            .replace("³", "3")
            .replace("⃗", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Searches across all 3 grades (chapter titles, group headings, formula LHS/RHS,
     * conditions, notes, and mnemonics) supporting Vietnamese queries with or without accents.
     */
    fun searchFormulas(query: String): List<FormulaSearchHit> {
        val q = normalizeForSearch(query)
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        return allHits.filter { hit ->
            val searchable = buildString {
                append(hit.chapter.grade.shortLabel).append(' ')
                append(hit.chapter.chapterNumber).append(' ')
                append(hit.chapter.title).append(' ')
                append(hit.group.title).append(' ')
                append(hit.group.subtitle).append(' ')
                hit.group.mnemonic?.let { append(it).append(' ') }
                append(hit.formula.lhs).append(' ')
                append(hit.formula.rhs).append(' ')
                hit.formula.condition?.let { append(it).append(' ') }
                hit.formula.note?.let { append(it) }
            }
            val normalizedHaystack = normalizeForSearch(searchable)
            tokens.all { token -> normalizedHaystack.contains(token) }
        }
    }
}

/**
 * Local SharedPreferences store for bookmarked favorite formulas and last visited grade.
 */
class MathDocsPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadFavoriteIds(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun saveFavoriteIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, ids.toSet()).apply()
    }

    fun toggleFavorite(formulaId: String): Set<String> {
        val updated = loadFavoriteIds().toMutableSet()
        if (!updated.add(formulaId)) {
            updated.remove(formulaId)
        }
        saveFavoriteIds(updated)
        return updated
    }

    fun loadLastTab(): DocsOverviewTab {
        val saved = prefs.getString(KEY_LAST_TAB, DocsOverviewTab.GRADE_10.name)
        return DocsOverviewTab.entries.firstOrNull { it.name == saved } ?: DocsOverviewTab.GRADE_10
    }

    fun saveLastTab(tab: DocsOverviewTab) {
        prefs.edit().putString(KEY_LAST_TAB, tab.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "tool_toan_math_docs_prefs"
        private const val KEY_FAVORITES = "favorite_formula_ids"
        private const val KEY_LAST_TAB = "last_selected_docs_tab"
    }
}
