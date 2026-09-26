// 设置页:AI 服务商/API Key(含连接自检)、每日提醒、主题、档案入口、关于与免责
//
// 分组用表达性分段 ListItem 连成一组,不再靠 HorizontalDivider 划线;
// 输入框走 rememberSaveable,旋转设备不丢已经敲进去的内容。
package com.betterlife.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.theme.MotionLevel
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
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val currentThemeMode = ThemeMode.fromKey(settings.themeMode)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current

    val customLabel = stringResource(R.string.settings_custom)
    val savedMessage = stringResource(R.string.settings_saved)
    val presets = remember(vm.presets) {
        vm.presets.map { PresetOption(it.label, it.baseUrl, it.model) } +
            PresetOption("", "", "", isCustom = true)
    }
    val presetLabels = presets.map { if (it.isCustom) customLabel else it.label }

    var baseUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    // 只在真实配置读出来之后填一次表单,之后完全交给用户
    LaunchedEffect(state.loaded) {
        if (state.loaded && !initialized) {
            baseUrl = settings.apiBaseUrl
            apiKey = settings.apiKey
            model = settings.apiModel
            initialized = true
        }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    val enableReminder: (Boolean) -> Unit = { enabled ->
        vm.setReminder(enabled, settings.reminderHour, settings.reminderMinute)
    }
    val context = LocalContext.current

    // 拒绝通知权限时开关会被打回去,不说原因就像「开关坏了」;给个跳转系统设置的出口
    val deniedMessage = stringResource(R.string.settings_reminder_denied)
    val openSettingsAction = stringResource(R.string.action_open_settings)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        enableReminder(granted)
        if (!granted) {
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = deniedMessage,
                    actionLabel = openSettingsAction,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openNotificationSettings(context)
                }
            }
        }
    }

    // 权限可能在本页之外被收回(系统设置里关掉);回到本页时重新核对一次
    var notificationsEnabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

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
        if (!state.loaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

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
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.settings_api_key)) },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (keyVisible) R.string.settings_api_key_hide else R.string.settings_api_key_show,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.settings_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = {
                    vm.saveApiConfig(baseUrl, apiKey, model)
                    scope.launch { snackbar.showSnackbar(savedMessage) }
                }) { Text(stringResource(R.string.settings_save_ai)) }

                Spacer(Modifier.width(Spacing.space2))

                OutlinedButton(
                    onClick = { vm.testConnection() },
                    enabled = state.connectionTest !is SettingsViewModel.ConnectionTest.Running,
                ) {
                    if (state.connectionTest is SettingsViewModel.ConnectionTest.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Spacing.space4),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(Spacing.space2))
                    }
                    Text(stringResource(R.string.settings_test_connection))
                }
            }

            when (val test = state.connectionTest) {
                is SettingsViewModel.ConnectionTest.Success -> TestResultText(
                    text = stringResource(R.string.settings_test_ok),
                    color = MaterialTheme.colorScheme.primary,
                )

                is SettingsViewModel.ConnectionTest.Failed -> TestResultText(
                    text = test.detail,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> {}
            }

            SectionTitle(stringResource(R.string.settings_section_reminder))

            SegmentedListItem(
                checked = settings.reminderEnabled,
                onCheckedChange = { want ->
                    if (want && Build.VERSION.SDK_INT >= 33) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@SegmentedListItem
                        }
                    }
                    enableReminder(want)
                },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.settings_reminder_enable))
            }
            SegmentedListItem(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showTimePicker = true
                },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                trailingContent = {
                    Text("%02d:%02d".format(settings.reminderHour, settings.reminderMinute))
                },
            ) {
                Text(stringResource(R.string.settings_reminder_time))
            }

            // 开关还开着、权限却没了:静默失效比关开关更糟,给一行明说 + 出口
            if (reminderWarningNeeded(settings.reminderEnabled, notificationsEnabled)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Spacing.space4),
                    )
                    Spacer(Modifier.width(Spacing.space2))
                    Text(
                        text = stringResource(R.string.settings_reminder_revoked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { openNotificationSettings(context) }) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_section_theme))

            val themeModes = remember {
                ThemeMode.entries.filter {
                    it != ThemeMode.MATERIAL_YOU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                }
            }
            themeModes.forEachIndexed { index, mode ->
                SegmentedListItem(
                    selected = currentThemeMode == mode,
                    onClick = { vm.setThemeMode(mode) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = themeModes.size),
                ) {
                    Text(themeModeLabel(mode))
                }
            }

            SectionTitle(stringResource(R.string.settings_section_motion))

            val currentMotionLevel = MotionLevel.fromKey(settings.motionLevel)
            val motionLevels = MotionLevel.entries
            motionLevels.forEachIndexed { index, level ->
                SegmentedListItem(
                    selected = currentMotionLevel == level,
                    onClick = { vm.setMotionLevel(level) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = motionLevels.size),
                ) {
                    Text(motionLevelLabel(level))
                }
            }

            SectionTitle(stringResource(R.string.settings_section_profile))

            SegmentedListItem(
                onClick = onEditProfile,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                supportingContent = { Text(stringResource(R.string.settings_edit_profile_sub)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
            ) {
                Text(stringResource(R.string.settings_edit_profile))
            }

            SectionTitle(stringResource(R.string.settings_section_about))
            Text(
                stringResource(R.string.settings_about_source),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_about_source_url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    runCatching { uriHandler.openUri(SOURCES_URL) }
                },
            )
            DisclaimerSection()
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

private const val SOURCES_URL = "https://github.com/eternity4719/HowToLiveBetter"

/** 提醒开着但通知权限不在时才需要警告行。提成纯函数,判定规则可以脱离 Android 环境测试 */
internal fun reminderWarningNeeded(reminderEnabled: Boolean, notificationsEnabled: Boolean): Boolean =
    reminderEnabled && !notificationsEnabled

/** 跳系统通知设置;EXTRA_APP_PACKAGE 让 ROM 直接定位到本应用 */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun TestResultText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** 免责声明默认收起:长文默认展开是「关于」页读不下去的根源 */
@Composable
private fun DisclaimerSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.space3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_disclaimer_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MotionEntrance(visible = expanded) {
                Text(
                    text = stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.BRAND_GREEN -> R.string.theme_brand
        ThemeMode.MATERIAL_YOU -> R.string.theme_material_you
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun motionLevelLabel(level: MotionLevel): String = stringResource(
    when (level) {
        MotionLevel.STANDARD -> R.string.motion_standard
        MotionLevel.REDUCED -> R.string.motion_reduced
        MotionLevel.OFF -> R.string.motion_off
    },
)
