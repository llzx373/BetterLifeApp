// Baseline Profile 生成器模块。**只跑在真机/模拟器上**,CI 不跑它。
//
// 产出会写回 app/src/main/baselineProfiles/baseline-prof.txt(见根目录 docs 说明),
// 由 AGP 在打 release 包时合并进 APK,再由 profileinstaller 装上。
plugins {
    // 不带版本号:com.android.test 与 AGP 同一个构件,已由 :app 带上 classpath,
    // 再写版本会报 "already on the classpath with an unknown version"
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.betterlife.app.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // macrobenchmark 与 BaselineProfileRule 的最低要求
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 这是 com.android.test 模块:它测试的是 :app,自己不产出可分发产物
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
