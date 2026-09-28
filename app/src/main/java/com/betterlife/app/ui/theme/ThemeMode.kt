// 主题模式。健康类 App 的品牌识别依赖固定色,所以 Material You 是选项而非默认;
// 且选 Material You 时口径色不跟随壁纸(见 BetterLifeTheme 与 DESIGN_SYSTEM §3.1)。
// 枚举顺序即设置页主题选择器的展示顺序。
package com.betterlife.app.ui.theme

enum class ThemeMode(val key: String) {
    /** 品牌蓝紫(默认):固定配色,跟随系统明暗 */
    BRAND_BLUE_PURPLE("brand_blue_purple"),

    /** 品牌青绿:固定配色,跟随系统明暗 */
    BRAND_GREEN("brand_green"),

    /** 暖橙:固定配色,跟随系统明暗 */
    BRAND_ORANGE("brand_orange"),

    /** 青:固定配色,跟随系统明暗 */
    BRAND_TEAL("brand_teal"),

    /** 跟随系统壁纸(Material You,仅 Android 12+),跟随系统明暗 */
    MATERIAL_YOU("material_you"),

    /** 深色优先:固定配色(默认品牌蓝紫),始终深色 */
    DARK("dark"),
    ;

    companion object {
        /** 未知/缺失 key 回落到默认品牌(蓝紫);老用户已存的 "brand_green" 正常解析 */
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: BRAND_BLUE_PURPLE
    }
}
