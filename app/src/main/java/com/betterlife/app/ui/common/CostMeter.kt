// 性价比可视化:一个紧凑的图形表达「这条建议花多少、赚多少」。
//
// 成本侧(左):钱/时间/毅力 各 2 格,用中性色(它是量,不是语义);
// 收益侧(右):影响程度 3 格;
// 性价比档用形状区分:极高=填充胶囊,高=描边胶囊,一般=纯文字 —— 色盲用户也能分辨。
//
// 纯图形组件,必须带 semantics 标签(无障碍底线)。
package com.betterlife.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.recommend.CostMeterModel

private val BarWidth = 4.dp
private val BarHeight = 8.dp

@Composable
fun CostMeter(entry: EntryDto, modifier: Modifier = Modifier) {
    val desc = stringResource(
        R.string.cost_meter_desc,
        entry.money.ifBlank { "-" },
        entry.time.ifBlank { "-" },
        entry.will.ifBlank { "-" },
        entry.level.ifBlank { "-" },
        entry.ratio.ifBlank { "-" },
    )

    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CostItem(CostMeterModel.moneyLevel(entry))
        CostItem(CostMeterModel.timeLevel(entry))
        CostItem(CostMeterModel.willLevel(entry))
        VerticalDivider(
            modifier = Modifier.height(16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        GainBars(CostMeterModel.gainLevel(entry))
        RatioMark(entry.ratio)
    }
}

/** 单项成本的 2 格 */
@Composable
private fun CostItem(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(CostMeterModel.MAX_COST_LEVEL) { i ->
            Bar(
                filled = i < level,
                filledColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 收益的 3 格 */
@Composable
private fun GainBars(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(CostMeterModel.MAX_GAIN_LEVEL) { i ->
            Bar(
                filled = i < level,
                filledColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Bar(filled: Boolean, filledColor: Color) {
    Box(
        Modifier
            .size(BarWidth, BarHeight)
            .clip(RoundedCornerShape(2.dp))
            .background(if (filled) filledColor else MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 性价比档:形状区分,不只靠颜色 */
@Composable
private fun RatioMark(ratio: String) {
    when (ratio) {
        EntryKeys.RATIO_VERY_HIGH -> Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            Text(
                stringResource(R.string.ratio_short_very_high),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }

        EntryKeys.RATIO_HIGH -> Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Text(
                stringResource(R.string.ratio_short_high),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }

        EntryKeys.RATIO_NORMAL -> Text(
            stringResource(R.string.ratio_short_normal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
