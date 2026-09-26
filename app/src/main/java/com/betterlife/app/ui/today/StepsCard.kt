// 今日步数卡片：今日页问候语下方的信息位。
//
// 视觉层级刻意比今日任务卡低一档（surfaceContainerLowest，任务卡是 surfaceContainerLow）：
// 它是「顺便看一眼」的信息，不是今天要完成的动作。
// 只渲染 Available / Unauthorized 两态；Loading 与 Unavailable 由调用方决定不渲染。
package com.betterlife.app.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.betterlife.app.R
import com.betterlife.app.data.health.StepsSource
import com.betterlife.app.data.health.StepsState
import com.betterlife.app.ui.theme.Spacing

@Composable
internal fun StepsCard(
    state: StepsState,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.fillMaxWidth(),
    ) {
        when (state) {
            is StepsState.Available -> StepsCountRow(state)
            is StepsState.Unauthorized -> AuthorizeRow(onAuthorize)
            // Loading / Unavailable 不该到这里（调用方已过滤），兜底渲染空
            else -> Unit
        }
    }
}

@Composable
private fun StepsCountRow(state: StepsState.Available) {
    val title = stringResource(R.string.steps_card_title)
    val sourceLabel = stringResource(
        when (state.source) {
            StepsSource.HEALTH_CONNECT -> R.string.steps_source_health_connect
            StepsSource.SENSOR -> R.string.steps_source_sensor
        },
    )
    Row(
        modifier = Modifier
            .padding(Spacing.space4)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title ${state.steps} $sourceLabel"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Spacing.space6),
        )
        Spacer(Modifier.width(Spacing.space3))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 等宽数字：步数跳动时卡片不横移
            Text(
                text = state.steps.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
        Text(
            text = sourceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuthorizeRow(onAuthorize: () -> Unit) {
    Row(
        modifier = Modifier.padding(Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.steps_authorize_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.space3))
        FilledTonalButton(onClick = onAuthorize) {
            Text(stringResource(R.string.steps_authorize))
        }
    }
}
