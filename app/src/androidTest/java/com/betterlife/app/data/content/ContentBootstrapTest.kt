package com.betterlife.app.data.content

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.db.AppDatabase
import com.betterlife.app.data.db.EntryNoteEntity
import com.betterlife.app.data.db.EntryStateEntity
import com.betterlife.app.data.db.StreakLeaveEntity
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.data.db.WeeklyHabitEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * ContentBootstrap 的设备端集成测试：真实 assets 播种进 Room + SS-NN→key 迁移改写五张表。
 *
 * 注意：androidTest 不在 CI 上跑（ci.yml 只跑 testDebugUnitTest 与截图测试），
 * 本机无模拟器时仅保证编译通过；有设备时 `connectedDebugAndroidTest` 执行。
 *
 * DataStore 处理：迁移标记存在 targetContext 的 settings.preferences_pb 里，
 * 测试前删掉该文件保证可重复运行（测试进程里 App 未启动，DataStore 尚未初始化）。
 */
@RunWith(AndroidJUnit4::class)
class ContentBootstrapTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        // 清掉迁移标记，保证每次运行都走完整的迁移路径
        File(context.filesDir, "datastore/settings.preferences_pb").delete()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bootstrap() = ContentBootstrap(context, db, SettingsStore(context))

    /** 预置旧版本地数据：五张表各一行 SS-NN，一行 custom: 自定义任务 */
    private fun seedLegacyRows() = runBlocking {
        db.taskDao().insert(TaskEntity(entryId = "02-01", type = TaskEntity.TYPE_DAILY, date = "2025-01-01"))
        db.taskDao().insert(TaskEntity(entryId = "custom:x", type = TaskEntity.TYPE_ONCE))
        db.entryStateDao().upsert(EntryStateEntity("02-01", EntryStateEntity.STATE_FAVORITE, 0L))
        db.weeklyHabitDao().upsert(WeeklyHabitEntity("02-01", timesPerWeek = 3, createdAt = 0L))
        db.entryNoteDao().upsert(EntryNoteEntity("02-01", "note", 0L))
        db.streakLeaveDao().upsert(StreakLeaveEntity("02-01", "2025-01-01", 0L))
    }

    @Test
    fun 首次启动播种真实内容包() = runBlocking {
        bootstrap().ensureReady()

        val bundled = BundledContent.entriesFile(context)
        val meta = db.contentDao().meta()
        assertNotNull("播种后 meta 应存在", meta)
        assertEquals(bundled.contentVersion, meta!!.contentVersion)
        assertEquals(bundled.sections.size, db.contentDao().allSections().size)
        assertEquals(bundled.entries.size, db.contentDao().allEntries().size)
    }

    @Test
    fun 旧SSNN条目id被改写成稳定key而custom行不动() = runBlocking {
        seedLegacyRows()
        bootstrap().ensureReady()

        val key = BundledContent.legacyIdToKey(context).getValue("02-01")
        val taskEntryIds = db.taskDao().all().map { it.entryId }
        assertTrue("tasks 里的 02-01 应改写成 $key: $taskEntryIds", key in taskEntryIds)
        assertTrue("custom 行不应被动", "custom:x" in taskEntryIds)
        assertEquals(key, db.entryStateDao().allStates().single().entryId)
        assertEquals(key, db.weeklyHabitDao().all().single().entryId)
        assertEquals(key, db.entryNoteDao().all().single().entryId)
        assertEquals(key, db.streakLeaveDao().all().single().entryId)
    }

    @Test
    fun ensureReady幂等可重复调用() = runBlocking {
        val bootstrap = bootstrap()
        bootstrap.ensureReady()
        val first = db.contentDao().allEntries()
        bootstrap.ensureReady()
        assertEquals(first, db.contentDao().allEntries())
    }
}
