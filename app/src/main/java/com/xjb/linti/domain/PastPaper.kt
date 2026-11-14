package com.xjb.linti.domain

enum class PastPaperSource(val label: String) {
    OFFICIAL("官方发布"), RECALLED("考生回忆"), UNVERIFIED("未核实"),
}

data class PastPaper(
    val id: Long = 0,
    val bankId: Long,
    val year: Int,
    val name: String,
    val source: PastPaperSource = PastPaperSource.UNVERIFIED,
    val sourceNote: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val questionCount: Int = 0,
)

data class PastPaperItem(val paperId: Long, val questionId: Long, val position: Int, val originalNumber: String)

fun groupPastPapersByYear(papers: List<PastPaper>): Map<Int, List<PastPaper>> =
    papers.sortedWith(compareByDescending<PastPaper> { it.year }.thenBy { it.name }.thenBy { it.id }).groupBy { it.year }
