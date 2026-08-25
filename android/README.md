# 使用守护 · Android 版

面向 Android 8.0+（API 26+）手机/平板；兼容华为 HarmonyOS 2/3/4.x（运行 APK）。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Room（规则/配额/审计本地存储）+ DataStore（设置）
- AccessibilityService（应用拦截）+ DevicePolicyManager（防卸载/锁屏）
- UsageStats（统计）+ DisplayManager（投屏检测）+ SystemClock 单调时钟（防改时间）
- 纯本地离线：**不申请任何网络权限**

## 构建

```bash
# 使用 Android Studio 打开 android/ 目录，或命令行（Windows 用 gradlew.bat）：
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17、Android SDK 34。项目已内置 Gradle Wrapper（Gradle 8.9，兼容 AGP 8.5.2），首次构建自动下载 Gradle 发行版。

## 模块结构

```
app/src/main/java/com/apptime/guard/
├── core/model/      # 数据模型（规则/配额/审计/枚举）
├── core/engine/     # 规则引擎、状态机、配额、时间可信、投屏检测、模板
├── data/db/         # Room 数据库与 DAO
├── data/prefs/      # DataStore 设置仓库
├── service/         # 前台服务、无障碍、设备管理、通知监听、开机/安装广播
├── ui/              # Compose UI（向导/家长端/孩子端/锁定界面/统计）
└── util/            # PIN 加密、常量、UsageStats 辅助
```

## 关键能力对照（需求 05 章 5.1~5.4）

| 能力 | 实现 |
| --- | --- |
| 规则引擎/状态机 | `core/engine/RuleEngine.kt`（最严格优先结算） |
| 用停交替/冷却 | `QuotaManager.startRest()` + 引擎 6.1/6.2 |
| 时间篡改防护 | `core/engine/TimeTrust.kt`（单调时钟偏移检测） |
| 投屏息屏管控 | `core/engine/CastDetector.kt` + 家长策略 |
| 应用拦截 | `service/GuardAccessibilityService.kt` |
| 防卸载 | `service/GuardDeviceAdminReceiver.kt`（设备管理员） |
| 提醒/缓冲/锁定 | `service/GuardService.kt` + `ui/lock/LockActivity.kt` |
