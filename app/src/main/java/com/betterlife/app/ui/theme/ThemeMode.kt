// 主题模式三选一。健康类 App 的品牌识别依赖固定色，所以 Material You 是选项而非默认；
// 且选 Material You 时口径色不跟随壁纸（见 BetterLifeTheme 与 DESIGN_SYSTEM §3.1）。
package com.betterlife.app.ui.theme

enum class ThemeMode(val key: String) {
    /** 品牌绿（默认）：固定配色，跟随系统明暗 */
    BRAND_GREEN("brand_green"),

    /** 跟随系统壁纸（Material You，仅 Android 12+），跟随系统明暗 */
    MATERIAL_YOU("material_you"),

    /** 深色优先：固定配色，始终深色 */
    DARK("dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: BRAND_GREEN
    }
}
