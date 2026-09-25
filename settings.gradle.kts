pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BetterLifeApp"
include(":app")
// Baseline Profile 生成器：独立的 test-only 模块，跑在真机/模拟器上
include(":baselineprofile")
