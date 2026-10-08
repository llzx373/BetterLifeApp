package com.betterlife.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.betterlife.app.R

/**
 * 「加入计划」选择弹窗：一次性待办 / 每日习惯 / 每周习惯（选每周再补一步「一周几次」）。
 * 详情页工具栏与今日页推荐行的「加入待办」共用：加一个条目之前先问清节奏，
 * 不再静默加成一次性待办。
 */
@Composable
fun AddPlanDialog(
    onAddOnce: () -> Unit,
    onAddDaily: () -> Unit,
    onAddWeekly: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickWeeklyTimes by rememberSaveable { mutableStateOf(false) }
    if (!pickWeeklyTimes) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.detail_add_plan_title)) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
            text = {
                Column {
                    TextButton(onClick = onAddOnce) {
                        Text(stringResource(R.string.todo_once_title))
                    }
                    TextButton(onClick = onAddDaily) {
                        Text(stringResource(R.string.todo_daily_title))
                    }
                    TextButton(onClick = { pickWeeklyTimes = true }) {
                        Text(stringResource(R.string.todo_weekly_title))
                    }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.todo_weekly_times_dialog_title)) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
            text = {
                Column {
                    (1..7).forEach { times ->
                        TextButton(onClick = { onAddWeekly(times) }) {
                            Text(stringResource(R.string.todo_weekly_times_option, times))
                        }
                    }
                }
            },
        )
    }
}
