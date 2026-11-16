package com.xjb.linti.domain

/**
 * 轻量"上次练习"指针：进程被杀后凭它重建会话。
 * SEQUENCE 的题目集合与作答由题库进度 + 本轮作答背填恢复，故 questionIds 留空；
 * 其余模式保存本轮题目 ID 以恢复同一轮练习。
 */
data class PracticeSessionDescriptor(
    val mode: PracticeMode,
    val bankId: Long?,
    val bankName: String,
    val paperId: Long?,
    val questionIds: List<Long>,
    val currentIndex: Int,
    val updatedAt: Long,
) {
    val totalCount: Int get() = questionIds.size
}