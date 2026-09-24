// 设置页:AI 服务商/API Key、每日提醒(含通知权限)、主题、档案入口、关于与免责
package com.betterlife.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.ThemeMode
import com.betterlife.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private data class PresetOption(
    val label: String,
    val baseUrl: String,
    val model: String,
    val isCustom: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val currentThemeMode = ThemeMode.fromKey(settings.themeMode)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val customLabel = stringResource(R.string.settings_custom)
    val savedMessage = stringResource(R.string.settings_saved)
    // 预设:核心层两个 + 自定义
    val presets = remember(vm.presets) {
        vm.presets.map { PresetOption(it.label, it.baseUrl, it.model) } +
            PresetOption("", "", "", isCustom = true)
    }
    val presetLabels = presets.map { if (it.isCustom) customLabel else it.label }

    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var userTouched by remember { mutableStateOf(false) }
    var emissions by remember { mutableIntStateOf(0) }
    // 跳过 ViewModel 的默认首发值,第二次发射才是 DataStore 里的真实配置
    LaunchedEffect(settings) {
        emissions++
        if (!userTouched && emissions >= 2) {
            baseUrl = settings.apiBaseUrl
            apiKey = settings.apiKey
            model = settings.apiModel
        }
    }

    var showTimePicker by remember { mutableStateOf(false) }

    val enableReminder: (Boolean) -> Unit = { enabled ->
        vm.setReminder(enabled, settings.reminderHour, settings.reminderMinute)
    }
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> enableReminder(granted) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            SectionTitle(stringResource(R.string.settings_section_ai))

            var expanded by remember { mutableStateOf(false) }
            val selectedIndex = presets.indexOfFirst { it.baseUrl == baseUrl && it.model == model }
            val selectedLabel = presetLabels.getOrElse(selectedIndex) { customLabel }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_provider)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    presets.forEachIndexed { index, preset ->
                        DropdownMenuItem(
                            text = { Text(presetLabels[index]) },
                            onClick = {
                                expanded = false
                                userTouched = true
                                if (!preset.isCustom) {
                                    baseUrl = preset.baseUrl
                                    model = preset.model
                                }
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { userTouched = true; apiKey = it },
                label = { Text(stringResource(R.string.settings_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { userTouched = true; baseUrl = it },
                label = { Text(stringResource(R.string.settings_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { userTouched = true; model = it },
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.saveApiConfig(baseUrl, apiKey, model)
                    scope.launch { snackbar.showSnackbar(savedMessage) }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.settings_save_ai)) }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_reminder))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_reminder_enable), modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.reminderEnabled,
                    onCheckedChange = { want ->
                        if (want && Build.VERSION.SDK_INT >= 33) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@Switch
                            }
                        }
                        enableReminder(want)
                    },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reminder_time)) },
                trailingContent = {
                    Text("%02d:%02d".format(settings.reminderHour, settings.reminderMinute))
                },
                modifier = Modifier.clickable { showTimePicker = true },
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_theme))
            ThemeMode.entries
                .filter { it != ThemeMode.MATERIAL_YOU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                .forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setThemeMode(mode) }
                            .padding(vertical = Spacing.space1),
                    ) {
                        RadioButton(
                            selected = currentThemeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                        )
                        Text(
                            text = themeModeLabel(mode),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = Spacing.space2),
                        )
                    }
                }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_profile))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_edit_profile)) },
                supportingContent = { Text(stringResource(R.string.settings_edit_profile_sub)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onEditProfile),
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_about))
            Text(
                stringResource(R.string.settings_about_source),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space6))
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = settings.reminderHour,
            initialMinute = settings.reminderMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    vm.setReminder(settings.reminderEnabled, timeState.hour, timeState.minute)
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.BRAND_GREEN -> R.string.theme_brand
        ThemeMode.MATERIAL_YOU -> R.string.theme_material_you
        ThemeMode.DARK -> R.string.theme_dark
    },
)
