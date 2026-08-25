package com.apptime.guard

import android.app.Application
import android.content.Context
import com.apptime.guard.core.engine.CastDetector
import com.apptime.guard.core.engine.QuotaManager
import com.apptime.guard.core.engine.RuleEngine
import com.apptime.guard.core.engine.TimeTrust
import com.apptime.guard.data.db.AppDatabase
import com.apptime.guard.data.prefs.SettingsRepository
import com.apptime.guard.service.GuardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppTimeApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var timeTrust: TimeTrust
        private set
    lateinit var quotaManager: QuotaManager
        private set
    lateinit var ruleEngine: RuleEngine
        private set
    lateinit var castDetector: CastDetector
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        database = AppDatabase.get(this)
        timeTrust = TimeTrust(settings)
        quotaManager = QuotaManager(this, database, settings, timeTrust)
        ruleEngine = RuleEngine(this, database, settings, quotaManager, timeTrust)
        castDetector = CastDetector(this, settings)

        // 开机自检：启动管控服务并初始化时间基准
        appScope.launch {
            timeTrust.init()
            GuardService.start(this@AppTimeApp)
        }
    }

    companion object {
        lateinit var instance: AppTimeApp
            private set

        fun get(context: Context): AppTimeApp = context.applicationContext as AppTimeApp
    }
}
