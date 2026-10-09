// 桌面图标变体（拼色底 + % 前景）。Android 没有"运行时改 android:icon"的 API，
// 换图标靠启停 manifest 里一一对应的 activity-alias（每个 alias 挂一个 mipmap 变体）。
// 枚举顺序即设置页「桌面图标」选择器的展示顺序；key 存进 DataStore（SettingsStore）。
package com.betterlife.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class LauncherIcon(
    val key: String,
    /** manifest 里对应 activity-alias 的相对类名 */
    val aliasName: String,
    /** manifest 里 android:enabled 的初始值（判断「当前是否已启用」时兜底用） */
    val enabledByDefault: Boolean,
    /** 设置页色板预览：健康域（左上）与财富域（右下）的浅色端，与 ic_launcher_bg_* 一致 */
    val previewHealth: Long,
    val previewWealth: Long,
) {
    /** 酒红 × 金（默认）：红金 = 喜庆与财富 */
    WINE_GOLD("wine_gold", ".launcher.WineGoldAlias", true, 0xFFC2185B, 0xFFC77C02),

    /** 蓝紫 × 金：与品牌默认主题同色 */
    BLUE_GOLD("blue_gold", ".launcher.BlueGoldAlias", false, 0xFF4F4DCB, 0xFFC77C02),

    /** 青绿 × 金 */
    GREEN_GOLD("green_gold", ".launcher.GreenGoldAlias", false, 0xFF43A047, 0xFFC77C02),

    /** 青 × 金 */
    TEAL_GOLD("teal_gold", ".launcher.TealGoldAlias", false, 0xFF006A63, 0xFFC77C02),

    /** 石墨 × 金：低调商务 */
    SLATE_GOLD("slate_gold", ".launcher.SlateGoldAlias", false, 0xFF455A64, 0xFFC77C02),

    /** 玫瑰 × 青：唯一财富域非金的变体 */
    ROSE_TEAL("rose_teal", ".launcher.RoseTealAlias", false, 0xFFE75480, 0xFF006A63),
    ;

    companion object {
        /** 旧版单色图标的 key → 最接近的新变体（升级迁移用，单色图标已随本版移除） */
        private val LEGACY_KEY_MAP = mapOf(
            "blue_purple" to BLUE_GOLD,
            "green" to GREEN_GOLD,
            "orange" to WINE_GOLD,
            "teal" to TEAL_GOLD,
        )

        /** 旧 key 走迁移映射，未知/缺失 key 回落到默认酒红金 */
        fun fromKey(key: String?): LauncherIcon =
            entries.firstOrNull { it.key == key } ?: LEGACY_KEY_MAP[key] ?: WINE_GOLD
    }
}

object LauncherIconSwitcher {

    /**
     * 启用选中的 alias、停用其余 alias。DONT_KILL_APP：不切进程，桌面图标稍后由 launcher 刷新。
     * alias 的启停状态由系统持久化，重启/升级后保持，无需每次冷启动重放。
     *
     * 注意：只能在前台没有本 App 界面时调用（MainActivity.onStop 时机）。在前台切：
     * 当前 task 的启动 alias 被停用，launcher 会立刻把这个 task 关掉回到桌面，
     * 用户看到的就是「一点切换图标 App 就闪退」。
     */
    fun apply(context: Context, icon: LauncherIcon) {
        val pm = context.packageManager
        val pkg = context.packageName
        LauncherIcon.entries.forEach { c ->
            val state = if (c == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + c.aliasName),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** 目标 alias 已启用时不动（避免无谓的 package-changed 广播），否则交给 [apply] */
    fun applyIfNeeded(context: Context, icon: LauncherIcon) {
        val pm = context.packageManager
        val pkg = context.packageName
        val state = pm.getComponentEnabledSetting(ComponentName(pkg, pkg + icon.aliasName))
        val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon.enabledByDefault)
        if (!enabled) apply(context, icon)
    }
}
