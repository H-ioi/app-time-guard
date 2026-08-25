package com.apptime.guard.util

/** 全局常量 */
object Constants {
    const val CHANNEL_GUARD = "guard_channel"
    const val CHANNEL_REMIND = "remind_channel"
    const val CHANNEL_ALERT = "alert_channel"
    const val NOTIF_ID_GUARD = 1001
    const val NOTIF_ID_REMIND = 1002
    const val NOTIF_ID_ALERT = 1003
    const val NOTIF_ID_REQUEST = 1004

    const val ACTION_UPDATE = "com.apptime.guard.action.UPDATE"
    const val ACTION_REQUEST_PARENT = "com.apptime.guard.action.REQUEST_PARENT"
    const val ACTION_LOCK_NOW = "com.apptime.guard.action.LOCK_NOW"
    const val ACTION_PAUSE = "com.apptime.guard.action.PAUSE"
    const val ACTION_UNPAUSE = "com.apptime.guard.action.UNPAUSE"

    const val EXTRA_PACKAGE = "extra_package"
    const val EXTRA_RELAX_MIN = "extra_relax_min"
    const val EXTRA_PAUSE_MIN = "extra_pause_min"

    /** 时间篡改判定阈值（毫秒） */
    const val TAMPER_THRESHOLD_MS = 5 * 60 * 1000L

    /** PIN 连续错误锁定 */
    const val PIN_MAX_ATTEMPTS = 5
    const val PIN_LOCK_MS = 30_000L

    /** 分类定义 */
    val CATEGORIES = listOf(
        "game" to "游戏",
        "video" to "视频",
        "social" to "社交",
        "shopping" to "购物",
        "store" to "应用商店",
        "browser" to "浏览器",
        "reading" to "阅读",
        "learning" to "学习",
        "other" to "其他"
    )

    fun categoryLabel(c: String): String = CATEGORIES.firstOrNull { it.first == c }?.second ?: c

    /** 常用分类包名前缀映射（简化版，正式版可引入完整规则库） */
    fun guessCategory(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            p.contains("game") || p.contains("play.google") -> "game"
            p.contains("video") || p.contains("tv") || p.contains("bilibili") ||
                    p.contains("iqiyi") || p.contains("youku") || p.contains("douyin") ||
                    p.contains("kuaishou") || p.contains("mgtv") -> "video"
            p.contains("weixin") || p.contains("wechat") || p.contains("qq") ||
                    p.contains("sina") || p.contains("moments") -> "social"
            p.contains("taobao") || p.contains("jd") || p.contains("pinduoduo") ||
                    p.contains("suning") || p.contains("amazon") -> "shopping"
            p.contains("appmarket") || p.contains("market") || p.contains("store") -> "store"
            p.contains("browser") || p.contains("explorer") || p.contains("chrome") ||
                    p.contains("uc") || p.contains("firefox") || p.contains("quark") -> "browser"
            p.contains("reader") || p.contains("book") || p.contains("novel") ||
                    p.contains("dushu") -> "reading"
            p.contains("lesson") || p.contains("class") || p.contains("learn") ||
                    p.contains("edu") || p.contains("homework") || p.contains("dict") ||
                    p.contains("study") || p.contains("xuexi") -> "learning"
            else -> "other"
        }
    }
}
