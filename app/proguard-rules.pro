# BetterLifeApp R8 / ProGuard 规则
#
# 说明：AGP 9 起 R8 默认全量模式（full mode），且
# android.r8.strictFullModeForKeepRules=true —— 也就是说 `-keep class A` 不再隐含
# 保留无参构造，需要显式写出来。下面的规则都按这个前提写。

# ---------------------------------------------------------------------------
# 堆栈可读性
# ---------------------------------------------------------------------------
# 刻意不保留 SourceFile：AGP 9 会用 r8-map-id-<hash> 替代它，配合 Android Studio
# 的 Logcat 自动反混淆比固定文件名更有用。行号保留，方便定位到源码行。
-keepattributes LineNumberTable

# ---------------------------------------------------------------------------
# WorkManager
# ---------------------------------------------------------------------------
# WorkManager 按类名反射实例化 Worker，构造签名必须原样保留。
# （androidx.work 自带 consumer 规则，这里显式写一遍，因为这条链路无法在本机运行时验证。）
-keep class com.betterlife.app.tasks.DailyReminderWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# @Serializable 生成的 serializer 与伴生对象会被 R8 当作"只被反射使用"而裁掉。
-keepattributes *Annotation*, InnerClasses

-keepclassmembers class com.betterlife.app.** {
    *** Companion;
}

-keepclasseswithmembers class com.betterlife.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.betterlife.app.**$$serializer { *; }
