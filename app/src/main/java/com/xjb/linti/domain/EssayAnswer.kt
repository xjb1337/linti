package com.xjb.linti.domain

/** 自评与用户文字一起保存在既有作答记录中。 */
object EssayAnswer {
    private const val PENDING = "[未自评]"
    private const val CORRECT = "[自评:答对]"
    private const val WRONG = "[自评:没答对]"
    fun pending(text: String): String = PENDING + text
    fun encode(text: String, correct: Boolean): String = (if (correct) CORRECT else WRONG) + text
    fun isCorrect(answer: String): Boolean = answer.startsWith(CORRECT)
    fun isAssessed(answer: String): Boolean = answer.startsWith(CORRECT) || answer.startsWith(WRONG)
    fun text(answer: String): String = when {
        answer.startsWith(PENDING) -> answer.removePrefix(PENDING)
        answer.startsWith(CORRECT) -> answer.removePrefix(CORRECT)
        answer.startsWith(WRONG) -> answer.removePrefix(WRONG)
        else -> answer
    }
}
