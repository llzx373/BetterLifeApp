package com.betterlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betterlife.app.data.AppSettings
import com.betterlife.app.ui.AppNav
import com.betterlife.app.ui.theme.BetterLifeTheme
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.ThemeMode
import com.betterlife.app.ui.theme.areSystemAnimationsDisabled
import com.betterlife.app.ui.theme.effectiveMotionLevel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用：它负责把主题里的 postSplashScreenTheme 应用上。
        // 放到 super 之后会先渲染一帧启动主题的背景，再切到 Compose，出现闪屏。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 主题模式与动效档位都是持久化设置，在这里订阅，切换即时生效（无需重启 Activity）
        val settingsStore = (application as BetterLifeApp).container.settingsStore
        setContent {
            val settings by settingsStore.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            // 系统「移除动画」优先于用户档位，且只需读一次
            val systemAnimationsOff = remember { areSystemAnimationsDisabled(this) }
            BetterLifeTheme(
                themeMode = ThemeMode.fromKey(settings.themeMode),
                motionLevel = effectiveMotionLevel(
                    MotionLevel.fromKey(settings.motionLevel),
                    systemAnimationsOff,
                ),
            ) {
                AppNav()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 进程跨夜存活时把「今天」推进到真实日期，任务查询随之切到新的一天
        (application as BetterLifeApp).container.taskManager.refreshToday()
    }
}
