package com.xjb.linti.data.prefs

import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.PracticeSessionDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeSessionCodecTest {
    @Test fun roundTripPreservesAllFields() {
        val descriptor = PracticeSessionDescriptor(
            mode = PracticeMode.TOPIC,
            bankId = 7,
            bankName = "虚构题库",
            paperId = null,
            questionIds = listOf(3L, 1L, 2L),
            currentIndex = 2,
            updatedAt = 123_456L,
        )
        assertEquals(descriptor, PrefsPracticeSessionStore.decode(PrefsPracticeSessionStore.encode(descriptor)))
    }

    @Test fun roundTripPreservesNullableFieldsAndPaperId() {
        val descriptor = PracticeSessionDescriptor(
            mode = PracticeMode.PAPER,
            bankId = null,
            bankName = "虚构真题卷",
            paperId = 9,
            questionIds = emptyList(),
            currentIndex = 0,
            updatedAt = 1L,
        )
        val restored = PrefsPracticeSessionStore.decode(PrefsPracticeSessionStore.encode(descriptor))
        assertEquals(descriptor, restored)
        assertNull(restored.bankId)
    }

    @Test fun sequenceDescriptorKeepsEmptyQuestionIds() {
        val descriptor = PracticeSessionDescriptor(
            mode = PracticeMode.SEQUENCE,
            bankId = 1,
            bankName = "虚构题库",
            paperId = null,
            questionIds = emptyList(),
            currentIndex = 5,
            updatedAt = 1L,
        )
        assertEquals(descriptor, PrefsPracticeSessionStore.decode(PrefsPracticeSessionStore.encode(descriptor)))
    }
}