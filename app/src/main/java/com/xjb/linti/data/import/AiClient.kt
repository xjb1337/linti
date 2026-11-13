package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion

interface AiClient {
    suspend fun parseQuestions(text: String): List<DraftQuestion>
}

class AiConfigurationException(message: String) : IllegalArgumentException(message)
