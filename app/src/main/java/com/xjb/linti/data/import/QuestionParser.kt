package com.xjb.linti.data.import

import com.xjb.linti.domain.ParseResult

interface QuestionParser {
    fun parse(text: String): ParseResult
}
