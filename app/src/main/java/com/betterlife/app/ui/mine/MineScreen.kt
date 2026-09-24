// 我的页:档案摘要(可在此直接增删目标) + 编辑档案/AI 问答/设置入口
package com.betterlife.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.Goal
import com.betterlife.app.data.Profile
import com.betterlife.app.data.ProfileLabels
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.viewmodel.ProfileViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineScreen(
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenChat: () -> Unit,
    vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val summary = profileSummary(profile)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Text(stringResource(R.string.mine_title), style = MaterialTheme.typography.headlineSmall)

            // 档案是用户的个人化信息,值得一点强调色,也和今日任务卡区分开
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.space4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                ) {
                    Text(stringResource(R.string.mine_profile_title), style = MaterialTheme.typography.titleSmall)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)

                    Text(
                        text = stringResource(R.string.mine_goals_hint),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                        Goal.entries.forEach { goal ->
                            val selected = goal in profile.goals
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    vm.update {
                                        it.copy(
                                            goals = if (checked) it.goals + goal else it.goals - goal,
                                        )
                                    }
                                },
                            ) { Text(stringResource(ProfileLabels.goal(goal))) }
                        }
                    }
                }
            }

            val entries = listOf(
                EntryItem(
                    titleRes = R.string.mine_edit_profile,
                    subtitleRes = null,
                    onClick = onEditProfile,
                ),
                EntryItem(
                    titleRes = R.string.mine_chat,
                    subtitleRes = R.string.mine_chat_sub,
                    onClick = onOpenChat,
                ),
                EntryItem(
                    titleRes = R.string.mine_settings,
                    subtitleRes = R.string.mine_settings_sub,
                    onClick = onOpenSettings,
                ),
            )
            entries.forEachIndexed { index, entry ->
                SegmentedListItem(
                    onClick = entry.onClick,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = entries.size),
                    supportingContent = entry.subtitleRes?.let { res -> { Text(stringResource(res)) } },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                ) {
                    Text(stringResource(entry.titleRes))
                }
            }
        }
    }
}

private data class EntryItem(
    val titleRes: Int,
    val subtitleRes: Int?,
    val onClick: () -> Unit,
)

@Composable
private fun profileSummary(p: Profile): String = buildString {
    append(stringResource(R.string.mine_age_segment, p.ageRange.key))
    ProfileLabels.occupationSuffix(p.occupation)?.let { append(stringResource(it)) }
    ProfileLabels.summaryFlags(p).forEach { append(stringResource(it)) }
}
