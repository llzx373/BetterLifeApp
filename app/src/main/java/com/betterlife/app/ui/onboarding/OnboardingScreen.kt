// 引导页:四步分步向导采集档案,完成后保存并进入今日页;已建档时作为编辑页复用
package com.betterlife.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.data.AgeRange
import com.betterlife.app.data.Alcohol
import com.betterlife.app.data.Children
import com.betterlife.app.data.Chronic
import com.betterlife.app.data.Exercise
import com.betterlife.app.data.Gender
import com.betterlife.app.data.Goal
import com.betterlife.app.data.Housing
import com.betterlife.app.data.Occupation
import com.betterlife.app.data.Profile
import com.betterlife.app.data.Smoking
import com.betterlife.app.data.SugaryDrinks
import com.betterlife.app.ui.util.collectAsStateWithLifecycle
import com.betterlife.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

private const val STEP_COUNT = 4

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    val editMode = onboardingDone // 已完成引导 => 本次是编辑档案

    val pagerState = rememberPagerState(pageCount = { STEP_COUNT })
    val scope = rememberCoroutineScope()

    LaunchedEffect(saved) {
        if (saved) onFinished()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (editMode) "编辑档案" else "先了解一下你",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "第 ${pagerState.currentPage + 1} 步,共 $STEP_COUNT 步" +
                    if (editMode) "" else ",除年龄段外都可跳过",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / STEP_COUNT },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (page) {
                        0 -> StepBasics(profile) { vm.update(it) }
                        1 -> StepHabits(profile) { vm.update(it) }
                        2 -> StepFamily(profile) { vm.update(it) }
                        3 -> StepGoals(profile) { vm.update(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) { Text("上一步") }
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage < STEP_COUNT - 1) {
                    // 第 2 步起(索引 >= 1)允许跳过;第 1 步必须选年龄段,直接给"下一步"
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(STEP_COUNT - 1) }
                    }) { Text(if (pagerState.currentPage == 0) "跳到目标" else "跳过本步") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) { Text("下一步") }
                } else {
                    Button(onClick = { vm.save(markOnboardingDone = true) }) {
                        Text(if (editMode) "保存" else "生成我的建议")
                    }
                }
            }
        }
    }
}

// ---------- 通用小部件 ----------

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SingleChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChoiceChips(
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ---------- 四步内容 ----------

@Composable
private fun StepBasics(profile: Profile, update: ((Profile) -> Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel("年龄段(必选)")
        SingleChoiceChips(
            options = AgeRange.entries.map { it to it.key },
            selected = profile.ageRange,
            onSelect = { v -> update { it.copy(ageRange = v) } },
        )
        FieldLabel("性别")
        SingleChoiceChips(
            options = listOf(Gender.MALE to "男", Gender.FEMALE to "女", Gender.OTHER to "其他"),
            selected = profile.gender,
            onSelect = { v -> update { it.copy(gender = v) } },
        )
        FieldLabel("职业")
        SingleChoiceChips(
            options = listOf(Occupation.PROGRAMMER to "程序员", Occupation.STUDENT to "学生", Occupation.OTHER to "其他"),
            selected = profile.occupation,
            onSelect = { v -> update { it.copy(occupation = v) } },
        )
        FieldLabel("居住")
        SingleChoiceChips(
            options = listOf(Housing.RENT to "租房", Housing.OWN to "自有住房", Housing.FAMILY to "住家里"),
            selected = profile.housing,
            onSelect = { v -> update { it.copy(housing = v) } },
        )
        SwitchRow("目前经济压力大", profile.financialStress) { v -> update { it.copy(financialStress = v) } }
    }
}

@Composable
private fun StepHabits(profile: Profile, update: ((Profile) -> Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel("吸烟")
        SingleChoiceChips(
            options = listOf(Smoking.YES to "吸", Smoking.QUIT to "已戒", Smoking.NO to "不吸"),
            selected = profile.smoking,
            onSelect = { v -> update { it.copy(smoking = v) } },
        )
        SwitchRow("家里或车里有人抽烟", profile.secondhandSmoke) { v -> update { it.copy(secondhandSmoke = v) } }
        FieldLabel("饮酒")
        SingleChoiceChips(
            options = listOf(Alcohol.OFTEN to "经常", Alcohol.SOMETIMES to "偶尔", Alcohol.NO to "不喝"),
            selected = profile.alcohol,
            onSelect = { v -> update { it.copy(alcohol = v) } },
        )
        SwitchRow("嚼槟榔", profile.betelNut) { v -> update { it.copy(betelNut = v) } }
        FieldLabel("含糖饮料")
        SingleChoiceChips(
            options = listOf(SugaryDrinks.DAILY to "每天", SugaryDrinks.SOMETIMES to "偶尔", SugaryDrinks.NO to "不喝"),
            selected = profile.sugaryDrinks,
            onSelect = { v -> update { it.copy(sugaryDrinks = v) } },
        )
        FieldLabel("运动")
        SingleChoiceChips(
            options = listOf(
                Exercise.NONE to "几乎不运动",
                Exercise.LOW to "每周不到 150 分钟",
                Exercise.OK to "每周 150 分钟以上",
            ),
            selected = profile.exercise,
            onSelect = { v -> update { it.copy(exercise = v) } },
        )
        SwitchRow("经常睡不够 7 小时", profile.sleepShort) { v -> update { it.copy(sleepShort = v) } }
        FieldLabel("慢性病(可多选,没有就不选)")
        MultiChoiceChips(
            options = listOf(
                Chronic.HYPERTENSION to "高血压",
                Chronic.DIABETES to "糖尿病",
                Chronic.KIDNEY to "肾病",
                Chronic.HEART to "心脏病",
                Chronic.OTHER to "其他",
            ),
            selected = profile.chronic,
            onToggle = { v ->
                update {
                    it.copy(chronic = if (v in it.chronic) it.chronic - v else it.chronic + v)
                }
            },
        )
    }
}

@Composable
private fun StepFamily(profile: Profile, update: ((Profile) -> Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel("孩子")
        SingleChoiceChips(
            options = listOf(Children.NONE to "没有", Children.BABY to "婴幼儿", Children.SCHOOL to "学龄"),
            selected = profile.children,
            onSelect = { v -> update { it.copy(children = v) } },
        )
        SwitchRow("家里有老人", profile.hasElderly) { v -> update { it.copy(hasElderly = v) } }
        SwitchRow("自己或配偶怀孕或备孕", profile.pregnant) { v -> update { it.copy(pregnant = v) } }
        SwitchRow("有出国计划", profile.planningAbroad) { v -> update { it.copy(planningAbroad = v) } }
    }
}

@Composable
private fun StepGoals(profile: Profile, update: ((Profile) -> Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel("你最想改善什么?(可多选)")
        Text(
            "选了目标,建议会更贴你的情况;不选也有通用推荐。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MultiChoiceChips(
            options = listOf(
                Goal.HEALTH to "健康长寿",
                Goal.MONEY to "守住钱",
                Goal.TIME to "省时间精力",
                Goal.CAREER to "职业发展",
                Goal.FAMILY to "家庭",
                Goal.RELAX to "放松",
            ),
            selected = profile.goals,
            onToggle = { v ->
                update { it.copy(goals = if (v in it.goals) it.goals - v else it.goals + v) }
            },
        )
    }
}
