package com.betterlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.betterlife.app.ui.AppNav
import com.betterlife.app.ui.theme.BetterLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用：它负责把主题里的 postSplashScreenTheme 应用上。
        // 放到 super 之后会先渲染一帧启动主题的背景，再切到 Compose，出现闪屏。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetterLifeTheme {
                AppNav()
            }
        }
    }
}
