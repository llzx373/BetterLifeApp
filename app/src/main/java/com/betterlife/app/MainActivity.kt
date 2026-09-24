package com.betterlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betterlife.app.data.AppSettings
import com.betterlife.app.ui.AppNav
import com.betterlife.app.ui.theme.BetterLifeTheme
import com.betterlife.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用：它负责把主题里的 postSplashScreenTheme 应用上。
        // 放到 super 之后会先渲染一帧启动主题的背景，再切到 Compose，出现闪屏。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 主题模式是持久化设置，在这里订阅，切换即时生效（无需重启 Activity）
        val settingsStore = (application as BetterLifeApp).container.settingsStore
        setContent {
            val settings by settingsStore.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            BetterLifeTheme(themeMode = ThemeMode.fromKey(settings.themeMode)) {
                AppNav()
            }
        }
    }
}
