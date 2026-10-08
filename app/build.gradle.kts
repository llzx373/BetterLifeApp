import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.baselineprofile)
}

// 正式签名的读取优先级：环境变量 > 根目录 .env > keystore.properties（三者均不入库）。
// 全部缺失时回退到 debug 签名，保证 assembleRelease 在任何机器上都能产出可安装的包。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// .env 按 KEY=VALUE 逐行解析：不用 java.util.Properties，避免反斜杠被当作转义符
// （Windows 路径里的 \U 会被误解析成 Unicode 转义）。值可带成对的单/双引号。
val dotenv: Map<String, String> = run {
    val file = rootProject.file(".env")
    if (!file.exists()) {
        emptyMap()
    } else {
        file.readLines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@mapNotNull null
            }
            val eq = trimmed.indexOf('=')
            if (eq <= 0) {
                return@mapNotNull null
            }
            val key = trimmed.substring(0, eq).trim()
            var value = trimmed.substring(eq + 1).trim()
            if (value.length >= 2 && value.first() == value.last() &&
                (value.first() == '"' || value.first() == '\'')
            ) {
                value = value.substring(1, value.length - 1)
            }
            key to value
        }.toMap()
    }
}

fun signingProperty(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: dotenv[envName]?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProperty("BETTERLIFE_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingProperty("BETTERLIFE_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingProperty("BETTERLIFE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingProperty("BETTERLIFE_KEY_PASSWORD", "keyPassword")
val hasReleaseKeystore = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { it != null }

android {
    namespace = "com.betterlife.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.betterlife.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // 版本约定：versionName 三段式 —— 大重构迭代第一位、大特性更新迭代第二位、
        // 正式版本迭代第三位；versionCode 即版本号后面的「-n」开发迭代号
        // （0.1.0-1 ↔ versionCode 1），开发中只递增 versionCode。
        versionCode = 4
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "未配置正式签名（环境变量、.env 与 keystore.properties 均缺失）：release 构建回退到 debug 签名。" +
                        "正式发布前请配置签名（见 docs/BUILD.md 的「发布构建与签名」一节）。"
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // 启用 screenshotTest 源集（配合 gradle.properties 的同名开关）
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    sourceSets {
        // Room schema JSON 打进 androidTest assets，MigrationTestHelper 据此校验迁移
        named("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    packaging {
        resources {
            // 精确排除，不要再用 "META-INF/*" 通配 —— 那会连带干掉 META-INF/services
            // 与 META-INF/versions/**，引入 ServiceLoader 型库时会静默失效。
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

// Room schema 导出到 app/schemas/，既入库做版本档案，也供 androidTest 的 MigrationTestHelper 使用
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// APK 产物文件名用英文：BetterLife-<versionName>-<versionCode>.apk
// AGP 9 移除了 applicationVariants DSL，无法直接配置输出文件名；assembleRelease
// 在脚本评估时还未注册，用 matching+configureEach 惰性挂钩，构建完成后改名。
val renameReleaseApk = tasks.register("renameReleaseApk") {
    // 配置期取值（String/Int 可序列化），避免 doLast 闭包捕获 android 扩展导致配置缓存失败
    val outDir = layout.buildDirectory.dir("outputs/apk/release")
    val versionName = android.defaultConfig.versionName
    val versionCode = android.defaultConfig.versionCode
    doLast {
        val built = outDir.get().asFile.resolve("app-release.apk")
        val target = outDir.get().asFile.resolve("BetterLife-$versionName-$versionCode.apk")
        if (built.exists()) built.renameTo(target)
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(renameReleaseApk)
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    // 条目库的大屏 list-detail
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    // 今日步数：Health Connect 优先，不可用降级到本机计步传感器
    implementation(libs.androidx.health.connect)
    // 把打包进来的 baseline profile 真正装上（API < 33 尤其需要）
    implementation(libs.androidx.profileinstaller)
    // B5 桌面小部件：今日任务一键打卡
    implementation(libs.androidx.glance.appwidget)

    // baseline profile 生成器（只有跑 generateBaselineProfile 时才会用到）
    baselineProfile(project(":baselineprofile"))

    // 第三方
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 测试
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // 设备测试：Room 迁移校验（MigrationTestHelper 读 schemas/ 里的 JSON）
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    // 截图测试（host 侧渲染 Compose 预览，不需要设备）
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}

// 多个单测按相对路径直接读 src/main/assets(entries.json、images/manifest.json、图片文件),
// 声明为测试输入:assets 一变测试就重跑,否则 Gradle 会用 FROM-CACHE 的旧结果放行
tasks.withType<Test> {
    inputs.dir("src/main/assets").withPropertyName("mainAssets").withPathSensitivity(PathSensitivity.RELATIVE)
}
