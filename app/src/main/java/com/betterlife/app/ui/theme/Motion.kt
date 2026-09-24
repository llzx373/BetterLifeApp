// 全局动效方案。组件从 MaterialTheme.motionScheme 取 defaultSpatialSpec()/fastSpatialSpec()/effects spec，
// 不要手写 tween(300)。这样全局动效一致，且能一处切到 standard() 做对比。
package com.betterlife.app.ui.theme

import androidx.compose.material3.MotionScheme

internal val AppMotionScheme: MotionScheme = MotionScheme.expressive()
