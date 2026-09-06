package com.omidgame.mench.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real Room instrumentation against an in-memory database (not a fake
 * DAO) — verifies actual SQL/schema behavior, not just Kotlin logic.
 */
@RunWith(RobolectricTestRunner::class)
class UserDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getSelfOnce returns the stored user`() = runTest {
        val user = UserEntity(
            id = "user-1",
            phoneE164 = "+15551234567",
            displayName = "Test User",
            username = null,
            avatarUrl = null,
            updatedAtEpochMillis = 1_000L,
        )

        dao.upsert(user)
        val result = dao.getSelfOnce()

        assertThat(result).isEqualTo(user)
    }

    @Test
    fun `upsert with same id replaces the previous row rather than duplicating it`() = runTest {
        dao.upsert(
            UserEntity("user-1", "+15551234567", "Old Name", null, null, 1_000L),
        )
        dao.upsert(
            UserEntity("user-1", "+15551234567", "New Name", null, null, 2_000L),
        )

        val result = dao.getSelfOnce()
        assertThat(result?.displayName).isEqualTo("New Name")
    }

    @Test
    fun `clear removes cached user so an offline-only account leaves no trace`() = runTest {
        dao.upsert(UserEntity("user-1", "+15551234567", null, null, null, 1_000L))
        dao.clear()

        assertThat(dao.getSelfOnce()).isNull()
    }
}
