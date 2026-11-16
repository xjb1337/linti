package com.xjb.linti.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.AppContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class BankDeleteTest {
    private lateinit var db: LintiDatabase
    private lateinit var container: AppContainer

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, LintiDatabase::class.java).allowMainThreadQueries().build()
        container = AppContainer(app, db)
    }

    @After fun close() = db.close()

    private suspend fun seedBank(bankId: Long, questionId: Long) {
        db.questionBankDao().insert(QuestionBankEntity(id = bankId, name = "虚构题库$bankId", createdAt = 1, updatedAt = 1))
        db.questionDao().insert(QuestionEntity(
            id = questionId, bankId = bankId, sourceNumber = "1", type = "SINGLE", stem = "虚构题干$questionId",
            optionsJson = """[{"label":"A","text":"甲"},{"label":"B","text":"乙"}]""",
            answer = "A", explanation = "虚构解析", sourceFileName = "虚构来源", createdAt = 1, updatedAt = 1,
        ))
    }

    @Test fun deletingBankCascadesDependentsAndKeepsOtherBanks() = runBlocking {
        seedBank(1, 11)
        seedBank(2, 22)
        db.favoriteDao().upsert(FavoriteEntity(questionId = 11, createdAt = 1))
        db.noteDao().upsert(NoteEntity(questionId = 11, content = "虚构笔记", createdAt = 1, updatedAt = 1))
        db.chapterDao().insert(ChapterEntity(bankId = 1, name = "虚构章节", sortOrder = 0, createdAt = 1, updatedAt = 1))
        db.practiceProgressDao().upsert(PracticeProgressEntity(bankId = 1, mode = "SEQUENCE", currentIndex = 3, answeredCount = 3, lastOpenedAt = 1))

        container.repository.deleteBank(1)

        assertNull(db.questionBankDao().getById(1))
        assertTrue(db.questionDao().getByBank(1).isEmpty())
        assertNull(db.favoriteDao().get(11))
        assertNull(db.noteDao().get(11))
        assertTrue(db.chapterDao().getByBank(1).isEmpty())
        assertNull(db.practiceProgressDao().get(1, "SEQUENCE"))
        assertEquals(1, db.questionBankDao().count())
        assertEquals(1, db.questionDao().getByBank(2).size)
    }
}