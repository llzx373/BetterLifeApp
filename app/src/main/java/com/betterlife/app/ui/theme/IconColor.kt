// 桌面图标配色。Android 没有"运行时改 android:icon"的 API，换色靠启停 manifest 里
// 一一对应的 activity-alias（每个 alias 挂一个 mipmap 变体）。
// 枚举顺序即设置页「图标颜色」选择器的展示顺序；key 存进 DataStore（SettingsStore）。
package com.betterlife.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class IconColor(
    val key: String,
    /** manifest 里对应 activity-alias 的相对类名 */
    val aliasName: String,
    /** 设置页色板预览用的渐变两端色（与 ic_launcher_background* 一致） */
    val previewLight: Long,
    val previewDeep: Long,
) {
    /** 蓝紫（默认）：与品牌默认主题同色 */
    BLUE_PURPLE("blue_purple", ".launcher.BluePurpleAlias", 0xFF4F4DCB, 0xFF1C0D9D),

    /** 青绿：旧版默认图标的绿 */
    GREEN("green", ".launcher.GreenAlias", 0xFF43A047, 0xFF1B5E20),

    /** 暖橙 */
    ORANGE("orange", ".launcher.OrangeAlias", 0xFFAC3400, 0xFF5D1800),

    /** 青 */
    TEAL("teal", ".launcher.TealAlias", 0xFF006A63, 0xFF003733),
    ;

    companion object {
        /** 未知/缺失 key 回落到默认蓝紫 */
        fun fromKey(key: String?): IconColor =
            entries.firstOrNull { it.key == key } ?: BLUE_PURPLE
    }
}

object LauncherIconSwitcher {

    /**
     * 启用选中的 alias、停用其余 alias。DONT_KILL_APP：不切进程，桌面图标稍后由 launcher 刷新。
     * alias 的启停状态由系统持久化，重启/升级后保持，无需每次冷启动重放。
     */
    fun apply(context: Context, color: IconColor) {
        val pm = context.packageManager
        val pkg = context.packageName
        IconColor.entries.forEach { c ->
            val state = if (c == color) {
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
}
