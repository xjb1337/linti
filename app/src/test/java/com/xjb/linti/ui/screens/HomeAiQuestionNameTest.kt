package com.xjb.linti.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAiQuestionNameTest {
    @Test fun namesFollowTopicFileAndMaterialHeadingWithFallback() {
        val fallback = "AI 出题 2026-11-14"
        assertEquals("虚构主题", defaultAiBankName(" 虚构主题 ", "", "", false, fallback))
        assertEquals("虚构资料.docx", defaultAiBankName("", "正文", "虚构资料.docx", true, fallback))
        assertEquals("虚构标题", defaultAiBankName("", "导言\n## 虚构标题\n内容", "", true, fallback))
        assertEquals("虚构材料正文", defaultAiBankName("", "虚构材料正文", "", true, fallback))
        assertEquals(fallback, defaultAiBankName("", "", "", true, fallback))
    }
}
