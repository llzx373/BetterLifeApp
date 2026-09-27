package com.betterlife.app.data.backup

import com.betterlife.app.data.db.CustomEntryEntity
import com.betterlife.app.data.db.EntryNoteEntity
import com.betterlife.app.data.db.EntryStateEntity
import com.betterlife.app.data.db.ProfileEntity
import com.betterlife.app.data.db.StreakLeaveEntity
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.data.db.WeeklyHabitEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 备份文件里的 profile 行，字段与 [ProfileEntity] 一一对应 */
@Serializable
data class ProfileDto(
    val id: Int = 1,
    val ageRange: String = "26-35",
    val gender: String = "other",
    val smoking: String = "no",
    val secondhandSmoke: Boolean = false,
    val alcohol: String = "no",
    val betelNut: Boolean = false,
    val sugaryDrinks: String = "no",
    val exercise: String = "none",
    val sleepShort: Boolean = false,
    val chronic: String = "",
    val occupation: String = "other",
    val financialStress: Boolean = false,
    val housing: String = "rent",
    val children: String = "none",
    val hasElderly: Boolean = false,
    val pregnant: Boolean = false,
    val planningAbroad: Boolean = false,
    val goals: String = "",
)

/** 备份文件里的 tasks 行，字段与 [TaskEntity] 一一对应（含显式 taskId，导入时保留主键） */
@Serializable
data class TaskDto(
    val taskId: Long = 0,
    val entryId: String,
    val type: String,
    val date: String? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val notified: Boolean = false,
    val remindAtMinutes: Int? = null,
    val createdAt: Long = 0L,
    val note: String? = null,
    val doneBy: String = "manual",
    val dueDate: String? = null,
)

@Serializable
data class EntryStateDto(
    val entryId: String,
    val state: String,
    val updatedAt: Long = 0L,
)

@Serializable
data class WeeklyHabitDto(
    val entryId: String,
    val timesPerWeek: Int,
    val createdAt: Long = 0L,
)

@Serializable
data class CustomEntryDto(
    val entryId: String,
    val title: String,
    val createdAt: Long = 0L,
)

@Serializable
data class EntryNoteDto(
    val entryId: String,
    val text: String,
    val updatedAt: Long = 0L,
)

@Serializable
data class StreakLeaveDto(
    val entryId: String,
    val date: String,
    val createdAt: Long = 0L,
)

/**
 * 备份文件整体结构。
 * 只含 7 张持久表：chat_messages 是对话流水（可随时清空）不导出；
 * DataStore 里的 API key 等敏感配置一律不出设备，也不进备份。
 * 各列表默认空、可空字段默认 null，旧备份缺字段也能解析（前向兼容）。
 */
@Serializable
data class BackupPayload(
    val version: Int = BackupCodec.VERSION,
    val exportedAt: Long = 0L,
    val profile: ProfileDto? = null,
    val tasks: List<TaskDto> = emptyList(),
    val entryStates: List<EntryStateDto> = emptyList(),
    val weeklyHabits: List<WeeklyHabitDto> = emptyList(),
    val customEntries: List<CustomEntryDto> = emptyList(),
    val entryNotes: List<EntryNoteDto> = emptyList(),
    val streakLeaves: List<StreakLeaveDto> = emptyList(),
)

// ---------- 实体 ↔ DTO 映射 ----------

fun ProfileEntity.toDto() = ProfileDto(
    id, ageRange, gender, smoking, secondhandSmoke, alcohol, betelNut, sugaryDrinks,
    exercise, sleepShort, chronic, occupation, financialStress, housing, children,
    hasElderly, pregnant, planningAbroad, goals,
)

fun ProfileDto.toEntity() = ProfileEntity(
    id, ageRange, gender, smoking, secondhandSmoke, alcohol, betelNut, sugaryDrinks,
    exercise, sleepShort, chronic, occupation, financialStress, housing, children,
    hasElderly, pregnant, planningAbroad, goals,
)

fun TaskEntity.toDto() = TaskDto(
    taskId, entryId, type, date, done, doneAt, notified, remindAtMinutes,
    createdAt, note, doneBy, dueDate,
)

fun TaskDto.toEntity() = TaskEntity(
    taskId, entryId, type, date, done, doneAt, notified, remindAtMinutes,
    createdAt, note, doneBy, dueDate,
)

fun EntryStateEntity.toDto() = EntryStateDto(entryId, state, updatedAt)
fun EntryStateDto.toEntity() = EntryStateEntity(entryId, state, updatedAt)

fun WeeklyHabitEntity.toDto() = WeeklyHabitDto(entryId, timesPerWeek, createdAt)
fun WeeklyHabitDto.toEntity() = WeeklyHabitEntity(entryId, timesPerWeek, createdAt)

fun CustomEntryEntity.toDto() = CustomEntryDto(entryId, title, createdAt)
fun CustomEntryDto.toEntity() = CustomEntryEntity(entryId, title, createdAt)

fun EntryNoteEntity.toDto() = EntryNoteDto(entryId, text, updatedAt)
fun EntryNoteDto.toEntity() = EntryNoteEntity(entryId, text, updatedAt)

fun StreakLeaveEntity.toDto() = StreakLeaveDto(entryId, date, createdAt)
fun StreakLeaveDto.toEntity() = StreakLeaveEntity(entryId, date, createdAt)

/**
 * 备份 JSON 的编解码（纯 Kotlin，无 Android 依赖，便于单元测试）。
 * 解析用 ignoreUnknownKeys：新版本多写的字段在旧版本 App 上也能导入。
 */
object BackupCodec {

    /** 备份格式版本，与数据库版本对齐；不匹配直接拒绝，避免静默错数据 */
    const val VERSION = 6

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** 从实体组装导出载荷（DTO 转换集中在这里，BackupManager 只管读写库） */
    fun payloadOf(
        exportedAt: Long,
        profile: ProfileEntity?,
        tasks: List<TaskEntity>,
        entryStates: List<EntryStateEntity>,
        weeklyHabits: List<WeeklyHabitEntity>,
        customEntries: List<CustomEntryEntity>,
        entryNotes: List<EntryNoteEntity>,
        streakLeaves: List<StreakLeaveEntity>,
    ): BackupPayload = BackupPayload(
        version = VERSION,
        exportedAt = exportedAt,
        profile = profile?.toDto(),
        tasks = tasks.map { it.toDto() },
        entryStates = entryStates.map { it.toDto() },
        weeklyHabits = weeklyHabits.map { it.toDto() },
        customEntries = customEntries.map { it.toDto() },
        entryNotes = entryNotes.map { it.toDto() },
        streakLeaves = streakLeaves.map { it.toDto() },
    )

    fun encode(payload: BackupPayload): String = json.encodeToString(BackupPayload.serializer(), payload)

    /** 解析并校验版本；JSON 损坏或版本不符都返回 failure，调用方不得继续导入 */
    fun decode(text: String): Result<BackupPayload> = runCatching {
        val payload = json.decodeFromString(BackupPayload.serializer(), text)
        require(payload.version == VERSION) {
            "备份版本不支持：需要 version=$VERSION，实际 version=${payload.version}"
        }
        payload
    }
}
