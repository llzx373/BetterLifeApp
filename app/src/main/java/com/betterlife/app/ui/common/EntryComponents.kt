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
import androidx.compose.ui.unit.dp
import com.betterlife.app.data.EntryDto

/** 口径 → 人性化组名(未识别的口径原样展示) */
fun lensGroupTitle(lens: String): String = when (lens) {
    "死亡率" -> "先保命"
    "金钱" -> "守住钱"
    "时间" -> "省精力"
    "自由" -> "别踩线"
    else -> lens.ifBlank { "其他" }
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
        "极高" -> SmallBadge(
            "性价比 极高",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            modifier,
        )
        "高" -> SmallBadge(
            "性价比 高",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            modifier,
        )
        "一般" -> SmallBadge(
            "性价比 一般",
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
        text = "证据 $grade",
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    )
}

/** 争议角标 */
@Composable
fun DisputeBadge(modifier: Modifier = Modifier) {
    SmallBadge(
        text = "争议",
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier,
    )
}

/** 待核实角标 */
@Composable
fun TodoBadge(modifier: Modifier = Modifier) {
    SmallBadge(
        text = "待核实",
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    )
}

private fun costLabel(entry: EntryDto): String = when {
    entry.cost.isNotBlank() -> entry.cost
    else -> buildList {
        if (entry.money == "多") add("花钱多") else if (entry.money == "少") add("花小钱")
        if (entry.time == "多") add("费时间") else if (entry.time == "中") add("费些时间")
        if (entry.will == "是") add("要毅力") else if (entry.will == "些") add("要些毅力")
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
