// 里程碑日签分享卡(N6):连签天数 + 日期 + 一条书摘。
//
// 入口只有统计页里程碑行的「分享」按钮——不弹窗、不红点、不加水印/二维码,
// 用户想晒才晒(§1 规则 4:回顾而非竞争)。绘制与分享走 ShareCard 抽出的通用管线
// (shareCardImage),卡片本体同时被离屏渲染与截图预览复用。
package com.betterlife.app.ui.stats

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.betterlife.app.R
import com.betterlife.app.stats.MilestoneShareData
import com.betterlife.app.ui.library.shareCardImage
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.today.formatFullDateZh

/** 生成日签 PNG 并分享;失败抛异常,由调用方回退到纯文本分享。必须在主线程调用 */
suspend fun shareMilestoneCard(context: Context, host: View, data: MilestoneShareData) {
    shareCardImage(context, host, "milestone-${data.date}", milestoneShareText(context, data)) {
        MilestoneShareCardContent(data)
    }
}

/** 日签的纯文本形态:既是不认图片目标的兜底,也是分享失败时的回退 */
internal fun milestoneShareText(context: Context, data: MilestoneShareData): String = buildString {
    append(context.getString(R.string.milestone_share_text, formatFullDateZh(data.date), data.streakDays))
    if (!data.quoteTitle.isNullOrBlank()) {
        appendLine()
        appendLine()
        append(data.quoteTitle)
        if (!data.quoteHuman.isNullOrBlank()) {
            appendLine()
            append(data.quoteHuman)
        }
    }
}

/** 日签卡本体:应用名 + 日期,连签天数是主角,下面一条书摘;没有书摘时整段缺席 */
@Composable
internal fun MilestoneShareCardContent(data: MilestoneShareData, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(Spacing.space6),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatFullDateZh(data.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.milestone_card_streak, data.streakDays),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            if (!data.quoteTitle.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(data.quoteTitle, style = MaterialTheme.typography.titleLarge)
                if (!data.quoteHuman.isNullOrBlank()) {
                    Text(
                        data.quoteHuman,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
