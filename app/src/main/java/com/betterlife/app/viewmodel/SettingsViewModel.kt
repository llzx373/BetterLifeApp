package com.betterlife.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ai.ChatMessage
import com.betterlife.app.ai.LlmClient
import com.betterlife.app.ai.WebSearcher
import com.betterlife.app.data.AiProvider
import com.betterlife.app.data.AppSettings
import com.betterlife.app.data.SettingsStore
import com.betterlife.app.data.backup.BackupManager
import com.betterlife.app.data.content.ContentSyncRepository
import com.betterlife.app.data.db.ChatMessageDao
import com.betterlife.app.tasks.ReminderScheduler
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
    private val llmClient: LlmClient,
    private val webSearcher: WebSearcher,
    private val backupManager: BackupManager,
    private val chatMessageDao: ChatMessageDao,
    private val contentSyncRepository: ContentSyncRepository,
) : ViewModel() {

    /** 某张卡的连接自检结果，交给界面翻成文案 */
    sealed interface ConnectionTest {
        data object Idle : ConnectionTest
        data object Running : ConnectionTest
        data object Success : ConnectionTest
        data class Failed(val detail: String) : ConnectionTest
    }

    /** 某张卡的模型扫描结果；Success 的列表由界面弹窗给用户挑 */
    sealed interface ScanState {
        data object Idle : ScanState
        data object Loading : ScanState
        data class Success(val models: List<String>) : ScanState
        data class Failed(val detail: String) : ScanState
    }

    /** 数据区一次性动作（导出/导入）的进度与结果，界面翻成 snackbar 后调用 [consumeDataAction] 归位 */
    sealed interface DataAction {
        data object Idle : DataAction
        data object Running : DataAction
        data object Exported : DataAction
        data class Imported(val rows: Int) : DataAction
        data class ExportFailed(val detail: String) : DataAction
        data class ImportFailed(val detail: String) : DataAction
    }

    /**
     * [loaded] 明确区分「DataStore 还没读出来」和「读出来就是空」——
     * 界面据此显示加载态，而不是先渲染空表单再被填上。
     * testStates / scanStates 按供应商 id 分开记，每张卡各看各的。
     */
    data class UiState(
        val settings: AppSettings = AppSettings(),
        val loaded: Boolean = false,
        val testStates: Map<String, ConnectionTest> = emptyMap(),
        val scanStates: Map<String, ScanState> = emptyMap(),
        /** 「测试搜索」按钮的结果 */
        val searchTest: ConnectionTest = ConnectionTest.Idle,
        /** 数据区导出/导入动作的状态 */
        val dataAction: DataAction = DataAction.Idle,
        /** 内容库当前版本（content_meta；空 = 还没读到） */
        val contentVersion: String = "",
        /** 上次内容同步时间（epoch millis；0 = 还没读到） */
        val contentSyncedAt: Long = 0L,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.settingsFlow.collect { s ->
                _uiState.value = _uiState.value.copy(settings = s, loaded = true)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val meta = contentSyncRepository.currentMeta() ?: return@launch
            _uiState.value = _uiState.value.copy(
                contentVersion = meta.contentVersion,
                contentSyncedAt = meta.updatedAt,
            )
        }
    }

    /** 追加一张空白卡，名称由界面给默认值 */
    fun addProvider(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.upsertProvider(
                AiProvider(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    baseUrl = "",
                    apiKey = "",
                    model = "",
                    enabled = false,
                )
            )
        }
    }

    fun saveProvider(provider: AiProvider) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.upsertProvider(provider)
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.deleteProvider(id)
        }
        _uiState.value = _uiState.value.copy(
            testStates = _uiState.value.testStates - id,
            scanStates = _uiState.value.scanStates - id,
        )
    }

    fun setProviderEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setProviderEnabled(id, enabled)
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setActiveProvider(id)
        }
    }

    /** 用这张卡已保存的配置真发一次请求，让用户知道卡到底能不能用 */
    fun testProvider(id: String) {
        val provider = _uiState.value.settings.aiProviders.firstOrNull { it.id == id } ?: return
        if (_uiState.value.testStates[id] is ConnectionTest.Running) return
        _uiState.value = _uiState.value.copy(
            testStates = _uiState.value.testStates + (id to ConnectionTest.Running),
        )
        viewModelScope.launch {
            val result = llmClient.chat(
                messages = listOf(ChatMessage(ChatMessage.ROLE_USER, PING_PROMPT)),
                model = provider.model,
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
            )
            _uiState.value = _uiState.value.copy(
                testStates = _uiState.value.testStates + (id to result.fold(
                    onSuccess = { ConnectionTest.Success },
                    onFailure = { ConnectionTest.Failed(it.message.orEmpty()) },
                )),
            )
        }
    }

    /** 扫模型用卡片当前表单值（界面显式传入），不要求先保存 */
    fun scanModels(id: String, baseUrl: String, apiKey: String) {
        if (_uiState.value.scanStates[id] is ScanState.Loading) return
        _uiState.value = _uiState.value.copy(
            scanStates = _uiState.value.scanStates + (id to ScanState.Loading),
        )
        viewModelScope.launch {
            val result = llmClient.listModels(baseUrl, apiKey)
            _uiState.value = _uiState.value.copy(
                scanStates = _uiState.value.scanStates + (id to result.fold(
                    onSuccess = { ScanState.Success(it) },
                    onFailure = { ScanState.Failed(it.message.orEmpty()) },
                )),
            )
        }
    }

    /** 选完模型或关掉弹窗后归位，免得重组时弹窗又冒出来 */
    fun clearScanState(id: String) {
        _uiState.value = _uiState.value.copy(
            scanStates = _uiState.value.scanStates + (id to ScanState.Idle),
        )
    }

    fun setTimerRingtone(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setTimerRingtone(uri)
        }
    }

    fun saveSearchConfig(apiKey: String, endpoint: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setSearchConfig(apiKey, endpoint)
        }
    }

    /** 真发一次搜索，让用户知道 key/端点配得对不对 */
    fun testSearch() {
        if (_uiState.value.searchTest is ConnectionTest.Running) return
        _uiState.value = _uiState.value.copy(searchTest = ConnectionTest.Running)
        viewModelScope.launch(Dispatchers.IO) {
            val result = webSearcher.search(SEARCH_TEST_QUERY, count = 3)
            _uiState.value = _uiState.value.copy(
                searchTest = result.fold(
                    onSuccess = { ConnectionTest.Success },
                    onFailure = { ConnectionTest.Failed(it.message.orEmpty()) },
                ),
            )
        }
    }

    /**
     * 导出全部本地数据为 JSON 并交给 [write] 落盘（写文件需要 ContentResolver，由界面提供）。
     * 导出与写入任一步失败都记为 Failed，界面弹失败文案。
     */
    fun exportBackup(write: suspend (String) -> Unit) {
        if (_uiState.value.dataAction is DataAction.Running) return
        _uiState.value = _uiState.value.copy(dataAction = DataAction.Running)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { write(backupManager.exportJson()) }
            _uiState.value = _uiState.value.copy(
                dataAction = result.fold(
                    onSuccess = { DataAction.Exported },
                    onFailure = { DataAction.ExportFailed(it.message.orEmpty()) },
                ),
            )
        }
    }

    /** 导入备份 JSON：成功返回导入条数；解析/校验失败数据不变，原因交给界面展示 */
    fun importBackup(json: String) {
        if (_uiState.value.dataAction is DataAction.Running) return
        _uiState.value = _uiState.value.copy(dataAction = DataAction.Running)
        viewModelScope.launch(Dispatchers.IO) {
            val result = backupManager.importJson(json)
            _uiState.value = _uiState.value.copy(
                dataAction = result.fold(
                    onSuccess = { DataAction.Imported(it) },
                    onFailure = { DataAction.ImportFailed(it.message.orEmpty()) },
                ),
            )
        }
    }

    /** 界面消费完结果（snackbar 已弹）后归位，避免重组时重复弹 */
    fun consumeDataAction() {
        _uiState.value = _uiState.value.copy(dataAction = DataAction.Idle)
    }

    /** 清空本机全部 AI 对话记录 */
    fun clearChatHistory() {
        viewModelScope.launch(Dispatchers.IO) { chatMessageDao.clear() }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setReminder(enabled, hour, minute)
            if (enabled) reminderScheduler.enqueue(hour, minute)
            else reminderScheduler.cancel()
        }
    }

    /** N2b：「自动打卡报喜」开关 */
    fun setHcPraiseEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setHcPraiseEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setThemeMode(mode.key)
        }
    }

    fun setMotionLevel(level: MotionLevel) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setMotionLevel(level.key)
        }
    }

    companion object {
        private const val PING_PROMPT = "ping"
        private const val SEARCH_TEST_QUERY = "今日新闻"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                val c = app.container
                SettingsViewModel(
                    c.settingsStore, c.reminderScheduler, c.llmClient, c.webSearcher,
                    c.backupManager, c.chatMessageDao, c.contentSyncRepository,
                )
            }
        }
    }
}
