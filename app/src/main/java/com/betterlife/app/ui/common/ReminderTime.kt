// 单任务提醒时间:格式化 + 两个展示组件,待办页(可点设置)与今日页(只读)共用
package com.betterlife.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.betterlife.app.R
import com.betterlife.app.ui.theme.Spacing

/** 一天内分钟数 → "08:30"。只含数字与冒号,可以留在代码里(不属 UI 文案) */
internal fun formatMinutesOfDay(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * 待办页任务行的提醒入口。设了时间显示「闹钟 + 时间」并作为整体一个触控区(≥48dp);
 * 没设只显示弱化的闹钟图标。点击弹 TimePicker 由调用方处理。
 */
@Composable
fun ReminderBellButton(
    minutes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        if (minutes != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = stringResource(
                        R.string.task_reminder_edit_desc,
                        formatMinutesOfDay(minutes),
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Spacing.space4),
                )
                Text(
                    text = formatMinutesOfDay(minutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Icon(
                Icons.Filled.Alarm,
                contentDescription = stringResource(R.string.task_reminder_set_desc),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 今日页任务卡上的只读提醒时间:图标 + 文字,不只靠颜色区分 */
@Composable
fun ReminderTimeLabel(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Alarm,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Spacing.space4),
        )
        Spacer(Modifier.width(Spacing.space1))
        Text(
            text = formatMinutesOfDay(minutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
