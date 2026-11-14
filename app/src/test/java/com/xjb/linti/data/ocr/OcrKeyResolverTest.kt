package com.xjb.linti.data.ocr

import com.xjb.linti.data.prefs.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrKeyResolverTest {

    @Test
    fun `official base url requires https and exact deepseek host`() {
        assertTrue(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com"))
        assertTrue(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com/"))
        assertTrue(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com/v1"))
        assertTrue(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com/v1/"))
        assertTrue(OcrKeyResolver.isOfficialDeepSeekBaseUrl("HTTPS://API.DEEPSEEK.COM"))

        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl("http://api.deepseek.com"))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com.evil.test"))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://deepseek.proxy.example.com"))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com/v1/chat"))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com/v1?x=1"))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl("https://api.deepseek.com:8443"))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl(""))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl(null))
        assertFalse(OcrKeyResolver.isOfficialDeepSeekBaseUrl("not a url"))
    }

    @Test
    fun `dedicated key always wins`() {
        assertEquals(
            OcrKeySource.Dedicated,
            OcrKeyResolver.resolve(
                provider = AiProvider.OPENAI_COMPATIBLE,
                baseUrl = "https://proxy.example.com/v1",
                aiKeyPresent = true,
                dedicatedKeyPresent = true,
            ),
        )
    }

    @Test
    fun `ai key reused only for official deepseek config`() {
        assertEquals(
            OcrKeySource.ReuseAi,
            OcrKeyResolver.resolve(
                provider = AiProvider.DEEPSEEK,
                baseUrl = "https://api.deepseek.com",
                aiKeyPresent = true,
                dedicatedKeyPresent = false,
            ),
        )
        // 第三方中转 / 其他服务商：绝不把 Key 发给 DeepSeek
        assertEquals(
            OcrKeySource.None,
            OcrKeyResolver.resolve(
                provider = AiProvider.DEEPSEEK,
                baseUrl = "https://deepseek-proxy.example.com",
                aiKeyPresent = true,
                dedicatedKeyPresent = false,
            ),
        )
        assertEquals(
            OcrKeySource.None,
            OcrKeyResolver.resolve(
                provider = AiProvider.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com/v1",
                aiKeyPresent = true,
                dedicatedKeyPresent = false,
            ),
        )
        assertEquals(
            OcrKeySource.None,
            OcrKeyResolver.resolve(
                provider = AiProvider.DEEPSEEK,
                baseUrl = "http://api.deepseek.com",
                aiKeyPresent = true,
                dedicatedKeyPresent = false,
            ),
        )
    }

    @Test
    fun `no key anywhere means none`() {
        assertEquals(
            OcrKeySource.None,
            OcrKeyResolver.resolve(
                provider = AiProvider.DEEPSEEK,
                baseUrl = "https://api.deepseek.com",
                aiKeyPresent = false,
                dedicatedKeyPresent = false,
            ),
        )
    }
}
