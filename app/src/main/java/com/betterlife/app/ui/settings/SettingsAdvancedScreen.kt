// N9 设置分层:「高级」二级页 —— AI 供应商卡片(启用/禁用/测试/扫描模型)、AI 搜索配置、
// 主题色与动效档位。平庸用户永远不需要打开这里;返回键回一级设置页(AppNav 路由惯例)。
package com.betterlife.app.ui.settings

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betterlife.app.R
import com.betterlife.app.data.AiProvider
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.Spacing
import com.betterlife.app.ui.theme.ThemeMode
import com.betterlife.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAdvancedScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val currentThemeMode = ThemeMode.fromKey(settings.themeMode)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val savedMessage = stringResource(R.string.settings_saved)
    val deletedProviderMessage = stringResource(R.string.settings_provider_deleted)
    val newProviderName = stringResource(R.string.settings_provider_default_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_advanced_title)) },
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

            Spacer(Modifier.height(Spacing.space6))
        }
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

@Composable
private fun TestResultText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
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
