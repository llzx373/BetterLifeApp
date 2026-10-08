package com.betterlife.app.data.backup

import com.betterlife.app.data.db.EntryNoteEntity
import com.betterlife.app.data.db.EntryStateEntity
import com.betterlife.app.data.db.ProfileEntity
import com.betterlife.app.data.db.StreakLeaveEntity
import com.betterlife.app.data.db.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 备份编解码：往返一致、版本校验、坏 JSON 拒绝、未知字段容忍、空载荷可用 */
class BackupCodecTest {

    private fun samplePayload() = BackupCodec.payloadOf(
        exportedAt = 1_700_000_000_000L,
        profile = ProfileEntity(ageRange = "36-45", goals = "health,time"),
        tasks = listOf(
            TaskEntity(
                taskId = 7,
                entryId = "02-01",
                type = TaskEntity.TYPE_ONCE,
                done = false,
                remindAtMinutes = 480,
                createdAt = 123L,
                note = null,
                dueDate = "2025-01-01",
            ),
            TaskEntity(
                taskId = 8,
                entryId = "03-02",
                type = TaskEntity.TYPE_DAILY,
                date = "2024-12-31",
                done = true,
                doneAt = 456L,
                notified = true,
                note = "完成备注",
                doneBy = "manual",
            ),
        ),
        entryStates = listOf(EntryStateEntity("02-01", EntryStateEntity.STATE_TODO, 100L)),
        weeklyHabits = emptyList(),
        customEntries = emptyList(),
        entryNotes = listOf(EntryNoteEntity("02-01", "条目备注", 200L)),
        streakLeaves = listOf(StreakLeaveEntity("02-01", "2024-12-30", 300L)),
    )

    @Test
    fun `编码解码往返后载荷一致`() {
        val payload = samplePayload()
        val decoded = BackupCodec.decode(BackupCodec.encode(payload))
        assertTrue(decoded.isSuccess)
        assertEquals(payload, decoded.getOrThrow())
    }

    @Test
    fun `往返后实体字段逐项保留`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(samplePayload())).getOrThrow()
        val task = decoded.tasks.single { it.taskId == 8L }
        assertEquals("03-02", task.entryId)
        assertEquals("2024-12-31", task.date)
        assertTrue(task.done)
        assertEquals(456L, task.doneAt)
        assertTrue(task.notified)
        assertEquals("完成备注", task.note)
        assertEquals("manual", task.doneBy)
        assertEquals("health,time", decoded.profile?.goals)
        assertEquals("条目备注", decoded.entryNotes.single().text)
    }

    @Test
    fun `DTO 映射回实体不丢字段`() {
        val entity = TaskEntity(
            taskId = 42, entryId = "11-03", type = TaskEntity.TYPE_WEEKLY,
            date = "2024-12-29", done = true, doneAt = 1L, notified = false,
            remindAtMinutes = 60, createdAt = 2L, note = "n", doneBy = "manual", dueDate = null,
        )
        assertEquals(entity, entity.toDto().toEntity())
    }

    @Test
    fun `版本不符拒绝导入`() {
        val result = BackupCodec.decode("""{"version": 5, "exportedAt": 1}""")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("version"))
    }

    @Test
    fun `损坏的 JSON 拒绝导入`() {
        assertTrue(BackupCodec.decode("这不是 JSON").isFailure)
        assertTrue(BackupCodec.decode("""{"version": 6,""").isFailure)
        assertTrue(BackupCodec.decode("").isFailure)
    }

    @Test
    fun `未知字段被忽略（新版本备份在旧 App 上可导入）`() {
        val json = """
            {
              "version": 6,
              "exportedAt": 1,
              "futureTopLevel": {"x": 1},
              "tasks": [
                {"taskId": 1, "entryId": "02-01", "type": "ONCE", "futureField": true}
              ]
            }
        """.trimIndent()
        val decoded = BackupCodec.decode(json)
        assertTrue(decoded.isSuccess)
        assertEquals(1L, decoded.getOrThrow().tasks.single().taskId)
    }

    @Test
    fun `空载荷可导出可导入`() {
        val payload = BackupCodec.payloadOf(
            exportedAt = 0L,
            profile = null,
            tasks = emptyList(),
            entryStates = emptyList(),
            weeklyHabits = emptyList(),
            customEntries = emptyList(),
            entryNotes = emptyList(),
            streakLeaves = emptyList(),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(payload))
        assertTrue(decoded.isSuccess)
        assertNull(decoded.getOrThrow().profile)
        assertTrue(decoded.getOrThrow().tasks.isEmpty())
        // 只有 version 的最小 JSON 也能解析（各列表默认空）
        assertTrue(BackupCodec.decode("""{"version": 6}""").isSuccess)
    }

    @Test
    fun `缺 version 字段拒绝导入（任意 JSON 不能蒙混过校验）`() {
        assertTrue(BackupCodec.decode("""{}""").isFailure)
        assertTrue(BackupCodec.decode("""{"exportedAt": 1}""").isFailure)
        assertTrue(BackupCodec.decode("""{"tasks": []}""").isFailure)
        // 错误提示要指出缺的是哪个字段（设置页 Snackbar 原样展示）
        val message = BackupCodec.decode("""{}""").exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("version"))
    }

    @Test
    fun `task 的 type 非法拒绝导入`() {
        val json = """{"version": 6, "tasks": [{"entryId": "02-01", "type": "HOURLY"}]}"""
        val result = BackupCodec.decode(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("type"))
    }

    @Test
    fun `task 的 date 非法拒绝导入`() {
        val bad = """{"version": 6, "tasks": [{"entryId": "02-01", "type": "DAILY", "date": "2024-13-40"}]}"""
        assertTrue(BackupCodec.decode(bad).isFailure)
        val notDate = """{"version": 6, "tasks": [{"entryId": "02-01", "type": "DAILY", "date": "昨天"}]}"""
        assertTrue(BackupCodec.decode(notDate).isFailure)
    }

    @Test
    fun `entryId 空白拒绝导入`() {
        val badTask = """{"version": 6, "tasks": [{"entryId": " ", "type": "ONCE"}]}"""
        assertTrue(BackupCodec.decode(badTask).isFailure)
        val badNote = """{"version": 6, "entryNotes": [{"entryId": "", "text": "x"}]}"""
        assertTrue(BackupCodec.decode(badNote).isFailure)
    }

    @Test
    fun `entryStates 的 state 非法拒绝导入`() {
        val json = """{"version": 6, "entryStates": [{"entryId": "02-01", "state": "MAYBE"}]}"""
        assertTrue(BackupCodec.decode(json).isFailure)
    }

    @Test
    fun `weeklyHabits 的 timesPerWeek 非正拒绝导入`() {
        val json = """{"version": 6, "weeklyHabits": [{"entryId": "02-01", "timesPerWeek": 0}]}"""
        assertTrue(BackupCodec.decode(json).isFailure)
    }

    @Test
    fun `streakLeaves 的 date 非法拒绝导入`() {
        val json = """{"version": 6, "streakLeaves": [{"entryId": "02-01", "date": "2024-02-30"}]}"""
        assertTrue(BackupCodec.decode(json).isFailure)
    }
}
