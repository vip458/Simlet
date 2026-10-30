# Simlet — 反射入口与按名字调用的库成员必须保留
-keep class com.qianxi.simlet.core.SimletOverrideJob { *; }
-keep class com.qianxi.simlet.core.JobLauncher { *; }

# 通过 getDeclaredMethod("newProcess") 借用 Shizuku 的 shell 进程能力
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuBinderWrapper { *; }

# 目标机型可能带 Oplus 扩展接口，按类名反射查找，不能被改名影响
-keep class com.android.internal.telephony.** { *; }
