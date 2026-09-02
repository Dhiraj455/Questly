package com.example.questly.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckInDaoTest {
    private lateinit var db: QuestlyDatabase
    private lateinit var dao: CheckInDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), QuestlyDatabase::class.java
        ).build()
        dao = db.checkInDao()
    }

    @After fun tearDown() = db.close()

    @Test fun insertedCheckInIsObserved() = runTest {
        dao.insert(CheckInEntity("c1", "park-1", 1000L))
        assertEquals("c1", dao.observeAll().first().single().id)
    }

    @Test fun lastForCheckpointReturnsMostRecent() = runTest {
        dao.insert(CheckInEntity("c1", "park-1", 1000L))
        dao.insert(CheckInEntity("c2", "park-1", 2000L))
        assertEquals(2000L, dao.lastForCheckpoint("park-1")?.timestampMillis)
    }
}
