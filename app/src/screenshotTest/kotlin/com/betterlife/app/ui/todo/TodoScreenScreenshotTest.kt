// 待办页的屏级截图基线。
//
// 两类东西语义完全不同，视觉上必须分得开：每日习惯用口径色 + 圆形勾选 + 分区进度环，
// 一次性待办用方框勾选。这张基线同时盯住「今天还没安排」与「今天的都完成了」的空状态文案。
package com.betterlife.app.ui.todo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.data.db.TaskEntity
import com.betterlife.app.data.db.WeeklyHabitEntity
import com.betterlife.app.tasks.TaskManager
import com.betterlife.app.ui.FourFoldScreenPreview
import com.betterlife.app.ui.PreviewScreen
import com.betterlife.app.ui.fakeEntry
import com.betterlife.app.ui.fakeTask
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.TodoViewModel

private val lowSodiumSalt = fakeEntry(
    id = "02-01",
    sec = 2,
    title = "把家里的食盐换成低钠盐",
    lens = EntryKeys.LENS_MORTALITY,
)

private val walkAfterDinner = fakeEntry(
    id = "11-03",
    sec = 11,
    title = "晚饭后走 20 分钟",
    lens = EntryKeys.LENS_MORTALITY,
)

private val bookCheckup = fakeEntry(
    id = "02-07",
    sec = 2,
    title = "把拖了两年的体检约上",
    money = EntryKeys.COST_LESS,
    time = EntryKeys.COST_MID,
    will = EntryKeys.WILL_YES,
    level = EntryKeys.GAIN_BIG,
    lens = EntryKeys.LENS_MORTALITY,
    ratio = EntryKeys.RATIO_HIGH,
)

private val changeInsurance = fakeEntry(
    id = "06-11",
    sec = 6,
    title = "把百万医疗险的续保条件看清",
    money = EntryKeys.COST_LESS,
    time = EntryKeys.COST_MID,
    will = EntryKeys.WILL_SOME,
    level = EntryKeys.GAIN_MID,
    lens = EntryKeys.LENS_MONEY,
    ratio = EntryKeys.RATIO_HIGH,
    grade = "B",
)

private val playBadminton = fakeEntry(
    id = "11-07",
    sec = 11,
    title = "打一场羽毛球",
    lens = EntryKeys.LENS_MORTALITY,
)

@PreviewTest
@FourFoldScreenPreview
@Composable
fun TodoBothSections() {
    PreviewScreen {
        TodoContent(
            state = TodoViewModel.UiState(
                daily = listOf(
                    TodoViewModel.TodoItem(
                        fakeTask(1, lowSodiumSalt.id, done = true),
                        lowSodiumSalt,
                    ),
                    TodoViewModel.TodoItem(fakeTask(2, walkAfterDinner.id), walkAfterDinner),
                ),
                once = listOf(
                    TodoViewModel.TodoItem(
                        fakeTask(3, bookCheckup.id, type = TaskEntity.TYPE_ONCE, date = null),
                        bookCheckup,
                    ),
                    TodoViewModel.TodoItem(
                        fakeTask(
                            4,
                            changeInsurance.id,
                            type = TaskEntity.TYPE_ONCE,
                            date = null,
                            done = true,
                        ),
                        changeInsurance,
                    ),
                ),
                weekly = listOf(
                    TodoViewModel.WeeklyEntry(
                        TaskManager.WeeklyItem(
                            habit = WeeklyHabitEntity(
                                entryId = playBadminton.id,
                                timesPerWeek = 3,
                                createdAt = 0L,
                            ),
                            doneCount = 1,
                            checkedToday = true,
                        ),
                        playBadminton,
                    ),
                ),
                userDailyIds = setOf(walkAfterDinner.id),
            ),
            contentPadding = PaddingValues(Spacing.space4),
            onToggle = {},
            onOpenEntry = {},
            onDelete = {},
            onSetReminder = { _, _ -> },
            onSetDueDate = { _, _ -> },
            onStartTimer = {},
            onConvertToDaily = {},
            onConvertToWeekly = { _, _ -> },
            onRemoveDailyHabit = {},
            onToggleWeekly = {},
            onRemoveWeekly = {},
            onAddCustom = { _, _, _, _ -> },
        )
    }
}
