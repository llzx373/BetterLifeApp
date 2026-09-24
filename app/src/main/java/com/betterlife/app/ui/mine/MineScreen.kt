// 我的页:档案摘要 + 编辑档案/AI 问答/设置入口
package com.betterlife.app.ui.mine

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.Exercise
import com.betterlife.app.data.Goal
import com.betterlife.app.data.Occupation
import com.betterlife.app.data.Profile
import com.betterlife.app.data.Smoking
import com.betterlife.app.viewmodel.ProfileViewModel

@Composable
private fun goalLabel(goal: Goal): String = stringResource(
    when (goal) {
        Goal.HEALTH -> R.string.goal_health
        Goal.MONEY -> R.string.goal_money
        Goal.TIME -> R.string.goal_time
        Goal.CAREER -> R.string.goal_career
        Goal.FAMILY -> R.string.goal_family
        Goal.RELAX -> R.string.goal_relax
    },
)

@Composable
private fun profileSummary(p: Profile): String = buildString {
    append(stringResource(R.string.mine_age_segment, p.ageRange.key))
    when (p.occupation) {
        Occupation.PROGRAMMER -> append(stringResource(R.string.mine_suffix_programmer))
        Occupation.STUDENT -> append(stringResource(R.string.mine_suffix_student))
        else -> {}
    }
    if (p.smoking == Smoking.YES) append(stringResource(R.string.mine_suffix_smoking))
    if (p.exercise == Exercise.NONE) append(stringResource(R.string.mine_suffix_no_exercise))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineScreen(
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenChat: () -> Unit,
    vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.mine_title), style = MaterialTheme.typography.headlineSmall)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mine_profile_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        profileSummary(profile),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (profile.goals.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            profile.goals.forEach { goal ->
                                SuggestionChip(onClick = {}, label = { Text(goalLabel(goal)) })
                            }
                        }
                    }
                }
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_edit_profile)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onEditProfile),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_chat)) },
                supportingContent = { Text(stringResource(R.string.mine_chat_sub)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenChat),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_settings)) },
                supportingContent = { Text(stringResource(R.string.mine_settings_sub)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenSettings),
            )
            HorizontalDivider()
        }
    }
}
