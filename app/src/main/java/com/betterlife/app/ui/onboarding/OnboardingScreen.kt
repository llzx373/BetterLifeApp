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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
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
                text = stringResource(
                    if (editMode) R.string.onboarding_title_edit else R.string.onboarding_title_new,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.onboarding_progress, pagerState.currentPage + 1, STEP_COUNT) +
                    if (editMode) "" else stringResource(R.string.onboarding_skippable_hint),
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
                    }) { Text(stringResource(R.string.onboarding_prev)) }
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage < STEP_COUNT - 1) {
                    // 第 2 步起(索引 >= 1)允许跳过;第 1 步必须选年龄段,直接给"下一步"
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(STEP_COUNT - 1) }
                    }) {
                        Text(
                            stringResource(
                                if (pagerState.currentPage == 0) R.string.onboarding_jump
                                else R.string.onboarding_skip,
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) { Text(stringResource(R.string.onboarding_next)) }
                } else {
                    Button(onClick = { vm.save(markOnboardingDone = true) }) {
                        Text(
                            stringResource(
                                if (editMode) R.string.onboarding_save else R.string.onboarding_generate,
                            ),
                        )
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
        FieldLabel(stringResource(R.string.field_age))
        SingleChoiceChips(
            options = AgeRange.entries.map { it to it.key },
            selected = profile.ageRange,
            onSelect = { v -> update { it.copy(ageRange = v) } },
        )
        FieldLabel(stringResource(R.string.field_gender))
        SingleChoiceChips(
            options = listOf(
                Gender.MALE to stringResource(R.string.option_male),
                Gender.FEMALE to stringResource(R.string.option_female),
                Gender.OTHER to stringResource(R.string.option_other),
            ),
            selected = profile.gender,
            onSelect = { v -> update { it.copy(gender = v) } },
        )
        FieldLabel(stringResource(R.string.field_occupation))
        SingleChoiceChips(
            options = listOf(
                Occupation.PROGRAMMER to stringResource(R.string.option_programmer),
                Occupation.STUDENT to stringResource(R.string.option_student),
                Occupation.OTHER to stringResource(R.string.option_other),
            ),
            selected = profile.occupation,
            onSelect = { v -> update { it.copy(occupation = v) } },
        )
        FieldLabel(stringResource(R.string.field_housing))
        SingleChoiceChips(
            options = listOf(
                Housing.RENT to stringResource(R.string.option_rent),
                Housing.OWN to stringResource(R.string.option_own),
                Housing.FAMILY to stringResource(R.string.option_live_family),
            ),
            selected = profile.housing,
            onSelect = { v -> update { it.copy(housing = v) } },
        )
        SwitchRow(stringResource(R.string.field_financial_stress), profile.financialStress) { v ->
            update { it.copy(financialStress = v) }
        }
    }
}

@Composable
private fun StepHabits(profile: Profile, update: ((Profile) -> Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel(stringResource(R.string.field_smoking))
        SingleChoiceChips(
            options = listOf(
                Smoking.YES to stringResource(R.string.option_smoking_yes),
                Smoking.QUIT to stringResource(R.string.option_smoking_quit),
                Smoking.NO to stringResource(R.string.option_smoking_no),
            ),
            selected = profile.smoking,
            onSelect = { v -> update { it.copy(smoking = v) } },
        )
        SwitchRow(stringResource(R.string.field_secondhand), profile.secondhandSmoke) { v ->
            update { it.copy(secondhandSmoke = v) }
        }
        FieldLabel(stringResource(R.string.field_alcohol))
        SingleChoiceChips(
            options = listOf(
                Alcohol.OFTEN to stringResource(R.string.option_alcohol_often),
                Alcohol.SOMETIMES to stringResource(R.string.option_sometimes),
                Alcohol.NO to stringResource(R.string.option_no_drink),
            ),
            selected = profile.alcohol,
            onSelect = { v -> update { it.copy(alcohol = v) } },
        )
        SwitchRow(stringResource(R.string.field_betel_nut), profile.betelNut) { v ->
            update { it.copy(betelNut = v) }
        }
        FieldLabel(stringResource(R.string.field_sugary))
        SingleChoiceChips(
            options = listOf(
                SugaryDrinks.DAILY to stringResource(R.string.option_drinks_daily),
                SugaryDrinks.SOMETIMES to stringResource(R.string.option_sometimes),
                SugaryDrinks.NO to stringResource(R.string.option_no_drink),
            ),
            selected = profile.sugaryDrinks,
            onSelect = { v -> update { it.copy(sugaryDrinks = v) } },
        )
        FieldLabel(stringResource(R.string.field_exercise))
        SingleChoiceChips(
            options = listOf(
                Exercise.NONE to stringResource(R.string.option_exercise_none),
                Exercise.LOW to stringResource(R.string.option_exercise_low),
                Exercise.OK to stringResource(R.string.option_exercise_ok),
            ),
            selected = profile.exercise,
            onSelect = { v -> update { it.copy(exercise = v) } },
        )
        SwitchRow(stringResource(R.string.field_sleep_short), profile.sleepShort) { v ->
            update { it.copy(sleepShort = v) }
        }
        FieldLabel(stringResource(R.string.field_chronic))
        MultiChoiceChips(
            options = listOf(
                Chronic.HYPERTENSION to stringResource(R.string.option_chronic_hypertension),
                Chronic.DIABETES to stringResource(R.string.option_chronic_diabetes),
                Chronic.KIDNEY to stringResource(R.string.option_chronic_kidney),
                Chronic.HEART to stringResource(R.string.option_chronic_heart),
                Chronic.OTHER to stringResource(R.string.option_other),
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
        FieldLabel(stringResource(R.string.field_children))
        SingleChoiceChips(
            options = listOf(
                Children.NONE to stringResource(R.string.option_children_none),
                Children.BABY to stringResource(R.string.option_children_baby),
                Children.SCHOOL to stringResource(R.string.option_children_school),
            ),
            selected = profile.children,
            onSelect = { v -> update { it.copy(children = v) } },
        )
        SwitchRow(stringResource(R.string.field_elderly), profile.hasElderly) { v ->
            update { it.copy(hasElderly = v) }
        }
        SwitchRow(stringResource(R.string.field_pregnant), profile.pregnant) { v ->
            update { it.copy(pregnant = v) }
        }
        SwitchRow(stringResource(R.string.field_abroad), profile.planningAbroad) { v ->
            update { it.copy(planningAbroad = v) }
        }
    }
}

@Composable
private fun StepGoals(profile: Profile, update: ((Profile) -> Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel(stringResource(R.string.field_goals))
        Text(
            stringResource(R.string.field_goals_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MultiChoiceChips(
            options = listOf(
                Goal.HEALTH to stringResource(R.string.goal_health),
                Goal.MONEY to stringResource(R.string.goal_money),
                Goal.TIME to stringResource(R.string.goal_time),
                Goal.CAREER to stringResource(R.string.goal_career),
                Goal.FAMILY to stringResource(R.string.goal_family),
                Goal.RELAX to stringResource(R.string.goal_relax),
            ),
            selected = profile.goals,
            onToggle = { v ->
                update { it.copy(goals = if (v in it.goals) it.goals - v else it.goals + v) }
            },
        )
    }
}
