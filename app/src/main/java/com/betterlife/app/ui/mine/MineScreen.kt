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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.data.Goal
import com.betterlife.app.data.Profile
import com.betterlife.app.viewmodel.ProfileViewModel

private fun goalLabel(goal: Goal): String = when (goal) {
    Goal.HEALTH -> "健康长寿"
    Goal.MONEY -> "守住钱"
    Goal.TIME -> "省时间精力"
    Goal.CAREER -> "职业发展"
    Goal.FAMILY -> "家庭"
    Goal.RELAX -> "放松"
}

private fun profileSummary(p: Profile): String = buildString {
    append(p.ageRange.key).append(" 岁段")
    when (p.occupation) {
        com.betterlife.app.data.Occupation.PROGRAMMER -> append(" · 程序员")
        com.betterlife.app.data.Occupation.STUDENT -> append(" · 学生")
        else -> {}
    }
    if (p.smoking == com.betterlife.app.data.Smoking.YES) append(" · 吸烟")
    if (p.exercise == com.betterlife.app.data.Exercise.NONE) append(" · 几乎不运动")
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
            Text("我的", style = MaterialTheme.typography.headlineSmall)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("我的档案", style = MaterialTheme.typography.titleSmall)
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
                headlineContent = { Text("编辑档案") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onEditProfile),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("AI 问答") },
                supportingContent = { Text("基于你的档案和 601 条建议回答") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenChat),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("设置") },
                supportingContent = { Text("API Key、提醒、关于") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenSettings),
            )
            HorizontalDivider()
        }
    }
}
