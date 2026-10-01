package com.betterlife.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.betterlife.app.data.AppSettings
import com.betterlife.app.ui.AppNav
import com.betterlife.app.ui.theme.BetterLifeTheme
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.ThemeMode
import com.betterlife.app.ui.theme.areSystemAnimationsDisabled
import com.betterlife.app.ui.theme.effectiveMotionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** 通知深链目标（N2c 挽回通知直达待办页）：AppNav 消费一次后由回调置空 */
    private val navRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用：它负责把主题里的 postSplashScreenTheme 应用上。
        // 放到 super 之后会先渲染一帧启动主题的背景，再切到 Compose，出现闪屏。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        navRequest.value = intent?.getStringExtra(EXTRA_OPEN_ROUTE)

        // 主题模式与动效档位都是持久化设置，在这里订阅，切换即时生效（无需重启 Activity）
        val settingsStore = (application as BetterLifeApp).container.settingsStore
        setContent {
            val settings by settingsStore.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val navTarget by navRequest.collectAsStateWithLifecycle()
            // 系统「移除动画」优先于用户档位，且只需读一次
            val systemAnimationsOff = remember { areSystemAnimationsDisabled(this) }
            BetterLifeTheme(
                themeMode = ThemeMode.fromKey(settings.themeMode),
                motionLevel = effectiveMotionLevel(
                    MotionLevel.fromKey(settings.motionLevel),
                    systemAnimationsOff,
                ),
            ) {
                AppNav(
                    navTarget = navTarget,
                    onNavTargetConsumed = { navRequest.value = null },
                )
            }
        }
    }

    // App 在后台时 CLEAR_TOP 会把通知的 intent 送到这里
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navRequest.value = intent.getStringExtra(EXTRA_OPEN_ROUTE)
    }

    override fun onResume() {
        super.onResume()
        val container = (application as BetterLifeApp).container
        // 进程跨夜存活时把「今天」推进到真实日期，任务查询随之切到新的一天
        container.taskManager.refreshToday()
        // N2c：打开 App 即记当天为活跃日，挽回通知的 3 天计时随之自然重置
        lifecycleScope.launch(Dispatchers.IO) {
            container.settingsStore.setLastActiveDate(LocalDate.now().toString())
        }
    }

    companion object {
        /** 通知点击的深链目标 extra：值为 ROUTE_* 之一，由 AppNav 导航过去 */
        const val EXTRA_OPEN_ROUTE = "com.betterlife.app.extra.OPEN_ROUTE"
        const val ROUTE_TODO = "todo"
    }
}
