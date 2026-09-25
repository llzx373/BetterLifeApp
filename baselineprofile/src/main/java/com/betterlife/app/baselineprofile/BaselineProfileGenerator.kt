package com.betterlife.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 App 自己的 baseline profile。
 *
 * 跑法(需要连着真机或模拟器):
 * ```
 * ./gradlew :app:generateBaselineProfile
 * ```
 *
 * 覆盖的是**启动路径**:冷启动 → 首屏组合 → Room 初始化 → entries.json 解析。
 * 这三件事都在首帧之后的主线程上,是冷启动时间的主要来源。
 *
 * 注意:Compose 等库自带的 profile 已经由 AGP 合并进包,但那只覆盖库自己的代码;
 * 这里生成的是**本 App** 的热点。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupProfile() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            // 等首屏稳定:推荐要读 601 条条目,这一下正是要进 profile 的部分
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.betterlife.app"
    }
}
