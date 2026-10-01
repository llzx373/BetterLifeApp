// 设置页:AI 供应商卡片(启用/禁用/测试/扫描模型)、番茄钟铃声、每日提醒、主题、档案入口、数据(备份/内容更新)、关于与免责
//
// 分组用表达性分段 ListItem 连成一组,不再靠 HorizontalDivider 划线;
// 输入框走 rememberSaveable,旋转设备不丢已经敲进去的内容。
package com.betterlife.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import com.betterlife.app.data.AiProvider
import com.betterlife.app.tasks.ContentSyncWorker
import com.betterlife.app.ui.common.MotionEntrance
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.ThemeMode
import com.betterlife.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val uriHandler = LocalUriHandler.current

    val savedMessage = stringResource(R.string.settings_saved)
    val deletedProviderMessage = stringResource(R.string.settings_provider_deleted)
    val newProviderName = stringResource(R.string.settings_provider_default_name)

    var showTimePicker by remember { mutableStateOf(false) }
    // 待确认的导入文件内容：读文件成功后才弹确认框,确认才真导入
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showClearChatConfirm by remember { mutableStateOf(false) }

    val enableReminder: (Boolean) -> Unit = { enabled ->
        vm.setReminder(enabled, settings.reminderHour, settings.reminderMinute)
    }
    val context = LocalContext.current
    val resources = LocalResources.current

    // 导出:系统文件选择器给目标 uri,拿到后由 VM 生成 JSON 并写流;结果走 dataAction → snackbar
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            vm.exportBackup { json ->
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("openOutputStream returned null")
            }
        }
    }

    // 导入:先读文件内容存起来,弹确认框;读不出来直接报失败,不进确认流程
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (json == null) {
                    snackbar.showSnackbar(resources.getString(R.string.settings_import_failed, ""))
                } else {
                    pendingImportJson = json
                }
            }
        }
    }

    // 导出/导入结果一次性消费:弹完 snackbar 就归位,避免重组后重复弹
    LaunchedEffect(state.dataAction) {
        when (val action = state.dataAction) {
            is SettingsViewModel.DataAction.Exported -> {
                snackbar.showSnackbar(resources.getString(R.string.settings_export_ok))
                vm.consumeDataAction()
            }

            is SettingsViewModel.DataAction.Imported -> {
                snackbar.showSnackbar(resources.getString(R.string.settings_import_ok, action.rows))
                vm.consumeDataAction()
            }

            is SettingsViewModel.DataAction.ExportFailed -> {
                snackbar.showSnackbar(
                    resources.getString(R.string.settings_export_failed) + " " + action.detail,
                )
                vm.consumeDataAction()
            }

            is SettingsViewModel.DataAction.ImportFailed -> {
                snackbar.showSnackbar(resources.getString(R.string.settings_import_failed, action.detail))
                vm.consumeDataAction()
            }

            else -> {}
        }
    }

    // 系统铃声选择器:返回 null 表示用户在 picker 里选了「无」,按恢复默认处理
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        vm.setTimerRingtone(uri?.toString().orEmpty())
    }

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

            settings.aiProviders.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    isActive = settings.activeProviderId == provider.id,
                    testState = state.testStates[provider.id] ?: SettingsViewModel.ConnectionTest.Idle,
                    scanState = state.scanStates[provider.id] ?: SettingsViewModel.ScanState.Idle,
                    onSave = { updated ->
                        vm.saveProvider(updated)
                        scope.launch { snackbar.showSnackbar(savedMessage) }
                    },
                    onDelete = {
                        vm.deleteProvider(provider.id)
                        scope.launch { snackbar.showSnackbar(deletedProviderMessage) }
                    },
                    onSetActive = { vm.setActiveProvider(provider.id) },
                    onSetEnabled = { vm.setProviderEnabled(provider.id, it) },
                    onTest = { vm.testProvider(provider.id) },
                    onScan = { baseUrl, apiKey -> vm.scanModels(provider.id, baseUrl, apiKey) },
                    onScanDismissed = { vm.clearScanState(provider.id) },
                )
            }

            OutlinedButton(
                onClick = { vm.addProvider(newProviderName) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_provider_add))
            }

            SectionTitle(stringResource(R.string.settings_section_ai_search))

            SearchConfigCard(
                apiKey = settings.searchApiKey,
                endpoint = settings.searchEndpoint,
                testState = state.searchTest,
                onSave = { key, endpoint ->
                    vm.saveSearchConfig(key, endpoint)
                    scope.launch { snackbar.showSnackbar(savedMessage) }
                },
                onTest = vm::testSearch,
            )

            SectionTitle(stringResource(R.string.settings_section_timer))

            val ringtoneTitle = remember(settings.timerRingtoneUri) {
                if (settings.timerRingtoneUri.isBlank()) null
                else runCatching {
                    RingtoneManager.getRingtone(context, Uri.parse(settings.timerRingtoneUri))
                        ?.getTitle(context)
                }.getOrNull()
            }
            SegmentedListItem(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            settings.timerRingtoneUri.takeIf { it.isNotBlank() }?.let(Uri::parse),
                        )
                    }
                    runCatching { ringtoneLauncher.launch(intent) }
                },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                trailingContent = {
                    Text(ringtoneTitle ?: stringResource(R.string.settings_timer_ringtone_default))
                },
            ) {
                Text(stringResource(R.string.settings_timer_ringtone))
            }
            SegmentedListItem(
                onClick = { vm.setTimerRingtone("") },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.settings_timer_ringtone_reset))
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
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 6),
            ) {
                Text(stringResource(R.string.settings_reminder_enable))
            }
            SegmentedListItem(
                onClick = {
                    showTimePicker = true
                },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 6),
                trailingContent = {
                    Text("%02d:%02d".format(settings.reminderHour, settings.reminderMinute))
                },
            ) {
                Text(stringResource(R.string.settings_reminder_time))
            }
            // N2b：HC 达标自动打卡后的报喜开关,默认开;只是通知偏好,不需要通知权限弹窗
            SegmentedListItem(
                checked = settings.hcPraiseEnabled,
                onCheckedChange = { vm.setHcPraiseEnabled(it) },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 6),
                supportingContent = { Text(stringResource(R.string.settings_hc_praise_sub)) },
            ) {
                Text(stringResource(R.string.settings_hc_praise))
            }
            // N2c：久未打开的挽回通知开关,默认开;7 天频控在决策纯函数里,开关只管发不发
            SegmentedListItem(
                checked = settings.reengageEnabled,
                onCheckedChange = { vm.setReengageEnabled(it) },
                shapes = ListItemDefaults.segmentedShapes(index = 3, count = 6),
                supportingContent = { Text(stringResource(R.string.settings_reengage_sub)) },
            ) {
                Text(stringResource(R.string.settings_reengage))
            }
            // N4：每周日晚的周报开关,默认开;0 打卡周不发的判断在决策纯函数里,开关只管发不发
            SegmentedListItem(
                checked = settings.weeklyReportEnabled,
                onCheckedChange = { vm.setWeeklyReportEnabled(it) },
                shapes = ListItemDefaults.segmentedShapes(index = 4, count = 6),
                supportingContent = { Text(stringResource(R.string.settings_weekly_report_sub)) },
            ) {
                Text(stringResource(R.string.settings_weekly_report))
            }
            // N5：每日一条内容推送开关,默认关(本阶段唯一默认关的推送);选条与 30 天去重在纯函数里,开关只管发不发
            SegmentedListItem(
                checked = settings.dailyContentEnabled,
                onCheckedChange = { vm.setDailyContentEnabled(it) },
                shapes = ListItemDefaults.segmentedShapes(index = 5, count = 6),
                supportingContent = { Text(stringResource(R.string.settings_daily_content_sub)) },
            ) {
                Text(stringResource(R.string.settings_daily_content))
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

            SectionTitle(stringResource(R.string.settings_section_senior))

            // N7：长辈模式一级开关;切换即时生效(主题/导航/今日页都订阅 settingsFlow)
            SegmentedListItem(
                checked = settings.seniorMode,
                onCheckedChange = { vm.setSeniorMode(it) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                supportingContent = { Text(stringResource(R.string.settings_senior_mode_sub)) },
            ) {
                Text(stringResource(R.string.settings_senior_mode))
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

            SectionTitle(stringResource(R.string.settings_section_data))

            // 备份导出/导入走系统文件选择器;「清空对话历史」有确认弹窗,误触可撤回决定
            SegmentedListItem(
                onClick = {
                    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    runCatching { exportLauncher.launch("betterlife-backup-$stamp.json") }
                },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
                supportingContent = { Text(stringResource(R.string.settings_export_data_sub)) },
            ) {
                Text(stringResource(R.string.settings_export_data))
            }
            SegmentedListItem(
                onClick = {
                    runCatching { importLauncher.launch(arrayOf("application/json")) }
                },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                supportingContent = { Text(stringResource(R.string.settings_import_data_sub)) },
            ) {
                Text(stringResource(R.string.settings_import_data))
            }
            // 内容更新:入队一次性同步 work 后立即反馈,结果不阻塞界面(失败由 WorkManager 退避重试)
            val contentSyncedLabel = remember(state.contentSyncedAt) {
                if (state.contentSyncedAt <= 0L) null
                else java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(state.contentSyncedAt),
                    java.time.ZoneId.systemDefault(),
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }
            val contentCheckStartedMessage = stringResource(R.string.settings_content_update_started)
            SegmentedListItem(
                onClick = {
                    ContentSyncWorker.enqueueOnce(context)
                    scope.launch { snackbar.showSnackbar(contentCheckStartedMessage) }
                },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_content_update_sub,
                            state.contentVersion.ifBlank { stringResource(R.string.settings_content_update_none) },
                            contentSyncedLabel ?: stringResource(R.string.settings_content_update_none),
                        )
                    )
                },
            ) {
                Text(stringResource(R.string.settings_content_update))
            }
            SegmentedListItem(
                onClick = { showClearChatConfirm = true },
                shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                supportingContent = { Text(stringResource(R.string.settings_clear_chat_sub)) },
            ) {
                Text(stringResource(R.string.settings_clear_chat))
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

    // 导入前必须确认:覆盖式导入不可撤销,文案把「不含 API 密钥」说在前面
    pendingImportJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportJson = null
                    vm.importBackup(json)
                }) { Text(stringResource(R.string.action_import)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_chat_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_chat_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearChatConfirm = false
                    vm.clearChatHistory()
                    scope.launch {
                        snackbar.showSnackbar(resources.getString(R.string.settings_clear_chat_done))
                    }
                }) {
                    Text(
                        text = stringResource(R.string.settings_clear_chat_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * 一张供应商卡:名称/Key/地址/模型先进本地表单态(按 id keyed,旋转不丢),
 * 点「保存」才落盘;测试与扫描都用这张卡自己的配置,互不影响。
 */
@Composable
private fun ProviderCard(
    provider: AiProvider,
    isActive: Boolean,
    testState: SettingsViewModel.ConnectionTest,
    scanState: SettingsViewModel.ScanState,
    onSave: (AiProvider) -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onScan: (baseUrl: String, apiKey: String) -> Unit,
    onScanDismissed: () -> Unit,
) {
    var name by rememberSaveable(provider.id) { mutableStateOf(provider.name) }
    var baseUrl by rememberSaveable(provider.id) { mutableStateOf(provider.baseUrl) }
    var apiKey by rememberSaveable(provider.id) { mutableStateOf(provider.apiKey) }
    var model by rememberSaveable(provider.id) { mutableStateOf(provider.model) }
    var keyVisible by rememberSaveable(provider.id) { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    // 扫描成功才弹选择窗;关掉弹窗时把状态归位,避免重组后反复弹
    LaunchedEffect(scanState) {
        if (scanState is SettingsViewModel.ScanState.Success) showModelPicker = true
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space3),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_provider_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.space2))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = provider.enabled, onCheckedChange = onSetEnabled)
                    Text(
                        text = stringResource(R.string.settings_provider_enable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isActive) {
                Text(
                    text = stringResource(R.string.settings_provider_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onSetActive) {
                    Text(stringResource(R.string.settings_provider_set_active))
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
                    onSave(
                        provider.copy(
                            name = name.trim(),
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                        )
                    )
                }) { Text(stringResource(R.string.action_save)) }

                Spacer(Modifier.width(Spacing.space2))

                OutlinedButton(
                    onClick = onTest,
                    enabled = testState !is SettingsViewModel.ConnectionTest.Running,
                ) {
                    if (testState is SettingsViewModel.ConnectionTest.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Spacing.space4),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(Spacing.space2))
                    }
                    Text(stringResource(R.string.settings_test_connection))
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OutlinedButton(
                onClick = { onScan(baseUrl.trim(), apiKey.trim()) },
                enabled = scanState !is SettingsViewModel.ScanState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (scanState is SettingsViewModel.ScanState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Spacing.space4),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(Spacing.space2))
                }
                Text(stringResource(R.string.settings_scan_models))
            }

            when (testState) {
                is SettingsViewModel.ConnectionTest.Success -> TestResultText(
                    text = stringResource(R.string.settings_test_ok),
                    color = MaterialTheme.colorScheme.primary,
                )

                is SettingsViewModel.ConnectionTest.Failed -> TestResultText(
                    text = testState.detail,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> {}
            }

            if (scanState is SettingsViewModel.ScanState.Failed) {
                TestResultText(
                    text = scanState.detail,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    val scannedModels = (scanState as? SettingsViewModel.ScanState.Success)?.models
    if (showModelPicker && scannedModels != null) {
        AlertDialog(
            onDismissRequest = {
                showModelPicker = false
                onScanDismissed()
            },
            title = { Text(stringResource(R.string.settings_scan_models_dialog_title)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(scannedModels) { id ->
                        Text(
                            text = id,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    model = id
                                    showModelPicker = false
                                    onScanDismissed()
                                }
                                .padding(vertical = Spacing.space2),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showModelPicker = false
                    onScanDismissed()
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private const val SOURCES_URL = "https://github.com/eternity4719/HowToLiveBetter"

/**
 * AI 搜索（博查 web-search）配置卡:key/端点先进本地表单态,点「保存」落盘;
 * 「测试搜索」真发一次搜索验证 key 与端点可用。
 */
@Composable
private fun SearchConfigCard(
    apiKey: String,
    endpoint: String,
    testState: SettingsViewModel.ConnectionTest,
    onSave: (String, String) -> Unit,
    onTest: () -> Unit,
) {
    var key by rememberSaveable { mutableStateOf(apiKey) }
    var ep by rememberSaveable { mutableStateOf(endpoint) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space3),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Text(
                text = stringResource(R.string.settings_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
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
                value = ep,
                onValueChange = { ep = it },
                label = { Text(stringResource(R.string.settings_search_endpoint)) },
                placeholder = { Text(stringResource(R.string.settings_search_endpoint_default)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSave(key, ep) }) { Text(stringResource(R.string.action_save)) }
                Spacer(Modifier.width(Spacing.space2))
                OutlinedButton(
                    onClick = onTest,
                    enabled = testState !is SettingsViewModel.ConnectionTest.Running,
                ) {
                    if (testState is SettingsViewModel.ConnectionTest.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Spacing.space4),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(Spacing.space2))
                    }
                    Text(stringResource(R.string.settings_search_test))
                }
            }
            when (testState) {
                is SettingsViewModel.ConnectionTest.Success -> TestResultText(
                    text = stringResource(R.string.settings_test_ok),
                    color = MaterialTheme.colorScheme.primary,
                )

                is SettingsViewModel.ConnectionTest.Failed -> TestResultText(
                    text = testState.detail,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> {}
            }
        }
    }
}

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
            MotionEntrance(visible = expanded, expand = true) {
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
        ThemeMode.BRAND_BLUE_PURPLE -> R.string.theme_brand_blue_purple
        ThemeMode.BRAND_GREEN -> R.string.theme_brand_green
        ThemeMode.BRAND_ORANGE -> R.string.theme_brand_orange
        ThemeMode.BRAND_TEAL -> R.string.theme_brand_teal
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
