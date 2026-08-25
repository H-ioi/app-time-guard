package com.apptime.guard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apptime.guard.AppTimeApp
import com.apptime.guard.core.model.AuditLog
import com.apptime.guard.core.model.ControlState
import com.apptime.guard.core.model.EngineResult
import com.apptime.guard.core.model.QuotaState
import com.apptime.guard.core.model.Rule
import com.apptime.guard.util.UsageStatsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 集中 ViewModel：供家长端/孩子端使用 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val core = AppTimeApp.get(app)

    private val _engineResult = MutableStateFlow(
        EngineResult(state = ControlState.AVAILABLE)
    )
    val engineResult: StateFlow<EngineResult> = _engineResult

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules

    private val _quota = MutableStateFlow<QuotaState?>(null)
    val quota: StateFlow<QuotaState?> = _quota

    private val _audit = MutableStateFlow<List<AuditLog>>(emptyList())
    val audit: StateFlow<List<AuditLog>> = _audit

    private val _isParent = MutableStateFlow(false)
    val isParent: StateFlow<Boolean> = _isParent

    private val _childApp = MutableStateFlow<String?>(null)
    val childApp: StateFlow<String?> = _childApp

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _rules.value = core.database.ruleDao().getAll()
            _quota.value = core.quotaManager.getState()
            _engineResult.value = core.ruleEngine.evaluate(UsageStatsHelper.getTopPackage(getApplication()))
            _childApp.value = UsageStatsHelper.getTopPackage(getApplication())
        }
    }

    fun evaluate() {
        viewModelScope.launch {
            _engineResult.value = core.ruleEngine.evaluate(
                UsageStatsHelper.getTopPackage(getApplication())
            )
            _quota.value = core.quotaManager.getState()
        }
    }

    fun loadAudit() {
        viewModelScope.launch {
            _audit.value = core.database.auditDao().getRecent(200)
        }
    }

    // ---- 家长操作 ----

    fun enterParentMode() {
        _isParent.value = true
    }

    fun exitParentMode() {
        _isParent.value = false
        refresh()
    }

    fun saveRule(rule: Rule) {
        viewModelScope.launch {
            core.database.ruleDao().upsert(rule)
            core.database.auditDao().insert(
                AuditLog(type = "RULE", detail = "保存规则：${rule.name}")
            )
            refresh()
        }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            core.database.ruleDao().delete(rule)
            core.database.auditDao().insert(
                AuditLog(type = "RULE", detail = "删除规则：${rule.name}")
            )
            refresh()
        }
    }

    fun applyTemplate(templateId: String) {
        viewModelScope.launch {
            val template = com.apptime.guard.core.engine.Templates.byId(templateId)
            // 清空旧规则（应用模板前先移除全部规则）
            core.database.ruleDao().getAll().forEach { core.database.ruleDao().delete(it) }
            com.apptime.guard.core.engine.Templates.buildRules(template)
                .forEach { core.database.ruleDao().upsert(it) }
            core.settings.setTemplateId(templateId)
            core.database.auditDao().insert(
                AuditLog(type = "TEMPLATE", detail = "应用模板：${template.name}")
            )
            refresh()
        }
    }

    fun lockNow(minutes: Int) {
        viewModelScope.launch {
            core.quotaManager.lockUntil(minutes)
            core.database.auditDao().insert(
                AuditLog(type = "PARENT", detail = "家长锁定 $minutes 分钟")
            )
            refresh()
        }
    }

    fun unlockNow() {
        viewModelScope.launch {
            core.quotaManager.unlockNow()
            core.database.auditDao().insert(AuditLog(type = "PARENT", detail = "家长解锁"))
            refresh()
        }
    }

    fun pause(minutes: Int) {
        viewModelScope.launch {
            core.quotaManager.pauseUntil(minutes)
            core.database.auditDao().insert(
                AuditLog(type = "PARENT", detail = "暂停管控 $minutes 分钟")
            )
            refresh()
        }
    }

    fun resume() {
        viewModelScope.launch {
            core.quotaManager.resumeNow()
            refresh()
        }
    }

    fun addBonus(minutes: Int) {
        viewModelScope.launch {
            core.quotaManager.addBonus(minutes.toLong())
            core.database.auditDao().insert(
                AuditLog(type = "PARENT", detail = "发放奖励时长 $minutes 分钟")
            )
            refresh()
        }
    }

    fun relaxApp(pkg: String, minutes: Int) {
        viewModelScope.launch {
            core.database.relaxationDao().upsert(
                com.apptime.guard.core.model.TempRelaxation(
                    packageName = pkg,
                    endElapsed = core.timeTrust.elapsed() + minutes * 60_000L
                )
            )
            core.database.auditDao().insert(
                AuditLog(type = "PARENT", detail = "临时放宽 $pkg $minutes 分钟")
            )
            refresh()
        }
    }

    fun categoryOf(pkg: String) = viewModelScope.launch {
        core.ruleEngine.categoryOf(pkg)
    }

    fun saveCategoryOverride(pkg: String, category: String) {
        viewModelScope.launch {
            core.database.categoryDao().upsert(
                com.apptime.guard.core.model.CategoryOverride(pkg, category)
            )
            refresh()
        }
    }
}
