# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# 序列化模型
-keep class com.apptime.guard.core.model.** { *; }

# 无障碍服务配置
-keep class com.apptime.guard.service.GuardAccessibilityService { *; }
-keep class com.apptime.guard.service.GuardNotificationListener { *; }
-keep class com.apptime.guard.service.GuardDeviceAdminReceiver { *; }
