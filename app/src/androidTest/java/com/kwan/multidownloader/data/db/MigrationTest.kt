package com.kwan.multidownloader.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Version 1 databases (before errorKind) must open as version 2 with every row kept. Uses its own test DB file. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun v1_to_v2_keeps_rows_and_adds_error_kind() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO downloads (id, sourceUrl, platform, title, type, quality, status, progress, error, createdAt) " +
                    "VALUES ('a', 'https://youtu.be/a', 'YOUTUBE', 'Song', 'AUDIO_MP3', '192k', 'FAILED', 0, 'Private video', 1)"
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true).use { db ->
            db.query("SELECT title, error, errorKind FROM downloads WHERE id = 'a'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Song", c.getString(0))
                assertEquals("Private video", c.getString(1))
                assertTrue(c.isNull(2))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
