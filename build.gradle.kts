// AGP 9 起内置 Kotlin 支持，不再应用 org.jetbrains.kotlin.android 插件。
// Kotlin 版本由 libs.versions.toml 的 kotlin 决定：AGP 自带一个较低的 KGP，
// 而 compose / serialization 编译器插件的版本必须与 Kotlin 编译器一致，
// 所以这两个插件按 catalog 里的版本声明，Gradle 取高版本后即完成对齐。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
