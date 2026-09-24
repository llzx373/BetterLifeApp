// 设置页:AI 服务商/API Key、每日提醒(含通知权限)、档案入口、关于与免责
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.viewmodel.SettingsViewModel
import com.betterlife.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private data class PresetOption(val label: String, val baseUrl: String, val model: String)

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

    // 预设:核心层两个 + 自定义
    val presets = remember(vm.presets) {
        vm.presets.map { PresetOption(it.label, it.baseUrl, it.model) } +
            PresetOption("自定义", "", "")
    }

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
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("AI 问答")

            var expanded by remember { mutableStateOf(false) }
            val selectedLabel = presets.firstOrNull {
                it.baseUrl == baseUrl && it.model == model
            }?.label ?: "自定义"
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("服务商") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                expanded = false
                                userTouched = true
                                if (preset.label != "自定义") {
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
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { userTouched = true; baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { userTouched = true; model = it },
                label = { Text("模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.saveApiConfig(baseUrl, apiKey, model)
                    scope.launch { snackbar.showSnackbar("已保存") }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("保存 AI 设置") }

            HorizontalDivider()
            SectionTitle("每日提醒")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("开启提醒", modifier = Modifier.weight(1f))
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
                headlineContent = { Text("提醒时间") },
                trailingContent = {
                    Text("%02d:%02d".format(settings.reminderHour, settings.reminderMinute))
                },
                modifier = Modifier.clickable { showTimePicker = true },
            )

            HorizontalDivider()
            SectionTitle("主题")
            ThemeMode.entries
                .filter { it != ThemeMode.MATERIAL_YOU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                .forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setThemeMode(mode) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = currentThemeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                        )
                        Text(
                            text = themeModeLabel(mode),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

            HorizontalDivider()
            SectionTitle("档案")
            ListItem(
                headlineContent = { Text("修改我的档案") },
                supportingContent = { Text("重新走一遍引导,已填内容会保留") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onEditProfile),
            )

            HorizontalDivider()
            SectionTitle("关于")
            Text(
                "内容出处:《高性价比人生指南》\ngithub.com/eternity4719/HowToLiveBetter",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "内容版权归原项目所有,请遵循其许可证使用。本应用仅做整理与呈现,不构成医疗、法律或投资意见;涉及健康与安全的决定,请咨询专业人士。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
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
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.BRAND_GREEN -> "品牌绿(默认)"
    ThemeMode.MATERIAL_YOU -> "跟随系统壁纸"
    ThemeMode.DARK -> "深色优先"
}
