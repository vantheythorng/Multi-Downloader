package com.kwan.multidownloader.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kwan.multidownloader.domain.MediaType
import com.kwan.multidownloader.domain.Platform
import com.kwan.multidownloader.domain.Status
import com.kwan.multidownloader.engine.ErrorKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory database, so the app's real download history is never touched. */
@RunWith(AndroidJUnit4::class)
class DownloadDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.downloadDao()
    }

    @After fun tearDown() = db.close()

    private fun entity(id: String, status: Status = Status.QUEUED, createdAt: Long = 0) = DownloadEntity(
        id = id, sourceUrl = "https://youtu.be/$id", platform = Platform.YOUTUBE, title = null, uploader = null,
        thumbnailUrl = null, durationSec = null, type = MediaType.AUDIO_MP3, quality = "192k", status = status,
        createdAt = createdAt,
    )

    @Test fun observe_by_status_is_newest_first() = runBlocking {
        dao.upsert(entity("old", Status.DONE, createdAt = 1))
        dao.upsert(entity("new", Status.DONE, createdAt = 2))
        dao.upsert(entity("active", Status.DOWNLOADING, createdAt = 3))
        assertEquals(listOf("new", "old"), dao.observeByStatuses(Status.COMPLETED).first().map { it.id })
        assertEquals(1, dao.countByStatuses(listOf(Status.DOWNLOADING)))
        assertEquals(listOf("active"), dao.idsByStatuses(listOf(Status.DOWNLOADING)))
    }

    @Test fun late_progress_never_overrides_pause_or_cancel() = runBlocking {
        dao.upsert(entity("a", Status.PAUSED))
        dao.upsert(entity("b", Status.CANCELLED))
        dao.updateProgress("a", Status.DOWNLOADING, 50f, "1MiB/s", 10)
        dao.updateProgress("b", Status.DOWNLOADING, 50f, "1MiB/s", 10)
        assertEquals(Status.PAUSED, dao.get("a")!!.status)
        assertEquals(Status.CANCELLED, dao.get("b")!!.status)
    }

    @Test fun progress_updates_an_active_download() = runBlocking {
        dao.upsert(entity("a", Status.QUEUED))
        dao.updateProgress("a", Status.DOWNLOADING, 42f, "2MiB/s", 7)
        val row = dao.get("a")!!
        assertEquals(Status.DOWNLOADING, row.status)
        assertEquals(42f, row.progress)
        assertEquals("2MiB/s", row.speed)
    }

    @Test fun failure_keeps_kind_and_retry_or_done_clears_it() = runBlocking {
        dao.upsert(entity("a", Status.DOWNLOADING))
        dao.markFailed("a", "Login required for this content", ErrorKind.LOGIN_REQUIRED)
        dao.get("a")!!.let {
            assertEquals(Status.FAILED, it.status)
            assertEquals(ErrorKind.LOGIN_REQUIRED, it.errorKind)
        }
        dao.updateStatus("a", Status.QUEUED)
        dao.get("a")!!.let {
            assertNull(it.error)
            assertNull(it.errorKind)
        }
        dao.markFailed("a", "x", ErrorKind.NETWORK)
        dao.markDone("a", "content://media/1", "audio/mpeg", 1234, 99)
        dao.get("a")!!.let {
            assertEquals(Status.DONE, it.status)
            assertEquals(100f, it.progress)
            assertNull(it.errorKind)
            assertEquals("content://media/1", it.contentUri)
        }
    }

    @Test fun metadata_fills_in_a_quick_download() = runBlocking {
        dao.upsert(entity("a"))
        dao.updateMetadata("a", "Song", "Artist", "https://i/x.jpg", 200)
        dao.get("a")!!.let {
            assertEquals("Song", it.title)
            assertEquals("Artist", it.uploader)
            assertEquals(200, it.durationSec)
        }
    }
}
