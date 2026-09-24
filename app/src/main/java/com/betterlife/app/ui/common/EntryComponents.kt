// 条目通用展示组件:性价比/证据等级/争议徽标、成本标签、口径人性化组名
package com.betterlife.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betterlife.app.R
import com.betterlife.app.data.EntryDto
import com.betterlife.app.data.EntryKeys

/** 口径 → 人性化组名(未识别的口径原样展示) */
@Composable
fun lensGroupTitle(lens: String): String = when (lens) {
    EntryKeys.LENS_MORTALITY -> stringResource(R.string.lens_life)
    EntryKeys.LENS_MONEY -> stringResource(R.string.lens_money)
    EntryKeys.LENS_TIME -> stringResource(R.string.lens_time)
    EntryKeys.LENS_FREEDOM -> stringResource(R.string.lens_line)
    else -> lens.ifBlank { stringResource(R.string.lens_other) }
}

@Composable
private fun SmallBadge(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 性价比徽标:极高醒目,高次之,一般弱化 */
@Composable
fun RatioBadge(ratio: String, modifier: Modifier = Modifier) {
    when (ratio) {
        EntryKeys.RATIO_VERY_HIGH -> SmallBadge(
            stringResource(R.string.ratio_very_high),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            modifier,
        )
        EntryKeys.RATIO_HIGH -> SmallBadge(
            stringResource(R.string.ratio_high),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            modifier,
        )
        EntryKeys.RATIO_NORMAL -> SmallBadge(
            stringResource(R.string.ratio_normal),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            modifier,
        )
        else -> if (ratio.isNotBlank()) SmallBadge(
            ratio,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            modifier,
        )
    }
}

/** 证据等级徽标 A/B/C */
@Composable
fun GradeBadge(grade: String, modifier: Modifier = Modifier) {
    if (grade.isBlank()) return
    SmallBadge(
        text = stringResource(R.string.grade_badge, grade),
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    )
}

/** 争议角标 */
@Composable
fun DisputeBadge(modifier: Modifier = Modifier) {
    SmallBadge(
        text = stringResource(R.string.badge_dispute),
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier,
    )
}

/** 待核实角标 */
@Composable
fun TodoBadge(modifier: Modifier = Modifier) {
    SmallBadge(
        text = stringResource(R.string.badge_todo),
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    )
}

@Composable
private fun costLabel(entry: EntryDto): String = when {
    entry.cost.isNotBlank() -> entry.cost
    else -> buildList {
        if (entry.money == EntryKeys.COST_MORE) add(stringResource(R.string.cost_money_high))
        else if (entry.money == EntryKeys.COST_LESS) add(stringResource(R.string.cost_money_low))
        if (entry.time == EntryKeys.COST_MORE) add(stringResource(R.string.cost_time_high))
        else if (entry.time == EntryKeys.COST_MID) add(stringResource(R.string.cost_time_mid))
        if (entry.will == EntryKeys.WILL_YES) add(stringResource(R.string.cost_will_high))
        else if (entry.will == EntryKeys.WILL_SOME) add(stringResource(R.string.cost_will_some))
    }.joinToString(" · ")
}

/** 成本标签(钱/时间/毅力),用 AssistChip 呈现 */
@Composable
fun CostChips(entry: EntryDto, modifier: Modifier = Modifier) {
    val label = costLabel(entry)
    if (label.isBlank()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {},
            label = { Text(label) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
