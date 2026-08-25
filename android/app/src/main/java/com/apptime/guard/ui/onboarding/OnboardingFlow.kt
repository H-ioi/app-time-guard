package com.apptime.guard.ui.onboarding

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apptime.guard.AppTimeApp
import com.apptime.guard.core.engine.Templates
import com.apptime.guard.ui.parent.PinDialog
import com.apptime.guard.ui.components.formatMinutes
import kotlinx.coroutines.launch

/**
 * 3 分钟设置向导（需求 03 章 3.2）：
 * 1 角色与 PIN → 2 权限授予 → 3 华为专项 → 4 选模板 → 5 微调 → 6 完成
 */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = AppTimeApp.get(context)
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var pinDone by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf("primary") }
    var grantedAccessibility by remember { mutableStateOf(false) }
    var grantedUsage by remember { mutableStateOf(false) }
    var grantedNotification by remember { mutableStateOf(false) }
    var grantedAdmin by remember { mutableStateOf(false) }
    var allowBattery by remember { mutableStateOf(false) }

    val isHuawei = remember {
        val m = Build.MANUFACTURER.lowercase()
        m.contains("huawei") || m.contains("honor")
    }

    // 完成向导
    fun finish() {
        scope.launch {
            app.settings.setOnboarded(true)
            app.settings.setWelcomeDone()
            onFinished()
        }
    }

    fun grantAccessibility() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun grantUsage() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun grantNotification() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun grantAdmin() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(context, com.apptime.guard.service.GuardDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(comp)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "启用后 App 可锁定设备并防止被卸载")
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun grantBattery() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (ex: Exception) {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun grantHuaweiStartup() {
        try {
            context.startActivity(
                Intent("huawei.intent.action.APPLICATION_STARTUP_MANAGER")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ex: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    if (showPin) {
        PinDialog(
            title = "设置家长 PIN",
            verify = false,
            onDismiss = { showPin = false },
            onSuccess = {
                pinDone = true
                showPin = false
            }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "使用守护 · 设置向导",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "第 ${step + 1} 步 / 共 6 步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            when (step) {
                0 -> StepRole(pinDone) { showPin = true }
                1 -> StepPermission(
                    grantedAccessibility, grantedUsage, grantedNotification, grantedAdmin,
                    onAccessibility = grantAccessibility,
                    onUsage = grantUsage,
                    onNotification = grantNotification,
                    onAdmin = grantAdmin
                )
                2 -> StepHuawei(isHuawei, allowBattery, grantBattery, grantHuaweiStartup) {
                    allowBattery = it
                }
                3 -> StepTemplate(selectedTemplate) { selectedTemplate = it }
                4 -> StepReview(Templates.byId(selectedTemplate))
                5 -> StepDone()
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) { Text("上一步") }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        when (step) {
                            0 -> if (pinDone) step = 1
                            1 -> step = 2
                            2 -> step = 3
                            3 -> step = 4
                            4 -> {
                                scope.launch {
                                    app.settings.setBiometricOn(false)
                                    Templates.buildRules(Templates.byId(selectedTemplate))
                                        .forEach { app.database.ruleDao().upsert(it) }
                                    app.settings.setTemplateId(selectedTemplate)
                                    step = 5
                                }
                            }
                            5 -> finish()
                        }
                    }
                ) {
                    Text(if (step == 5) "开始使用" else "下一步")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepRole(pinDone: Boolean, onSetPin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("欢迎使用「使用守护」", style = MaterialTheme.typography.titleLarge)
            Text(
                "本 App 帮助家长管理孩子使用平板/手机的时长与频率，纯本地运行，无需联网。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("第一步：设置家长 PIN 码（4~6 位数字）", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onSetPin) {
                Text(if (pinDone) "✅ PIN 已设置（点击重新设置）" else "设置家长 PIN")
            }
        }
    }
}

@Composable
private fun StepPermission(
    accessibility: Boolean,
    usage: Boolean,
    notification: Boolean,
    admin: Boolean,
    onAccessibility: () -> Unit,
    onUsage: () -> Unit,
    onNotification: () -> Unit,
    onAdmin: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionRow("无障碍服务（核心拦截）", "阻止打开被限制的应用，时间到时锁定", accessibility, onAccessibility)
        PermissionRow("使用情况访问（统计）", "统计各应用使用时长，生成报告", usage, onUsage)
        PermissionRow("通知使用权（监测）", "感知通知开关状态，保障管控", notification, onNotification)
        PermissionRow("设备管理员（可选增强）", "锁定设备、防卸载（建议开启）", admin, onAdmin)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGrant)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = granted, onCheckedChange = { onGrant() })
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            TextButton(onClick = onGrant) { Text(if (granted) "已开启" else "去开启") }
        }
    }
}

@Composable
private fun StepHuawei(
    isHuawei: Boolean,
    allowBattery: Boolean,
    onBattery: () -> Unit,
    onStartup: () -> Unit,
    onAllowBattery: (Boolean) -> Unit
) {
    if (!isHuawei) {
        Text("未检测到华为设备，跳过此步。")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "检测到华为设备，请完成以下设置以保证管控不被系统清理（重要）",
            fontWeight = FontWeight.SemiBold
        )
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("应用启动管理", fontWeight = FontWeight.SemiBold)
                    Text("允许自启动、关联启动、后台活动", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onStartup) { Text("去设置") }
            }
        }
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("电池优化", fontWeight = FontWeight.SemiBold)
                    Text("关闭省电限制，避免服务被清理", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onBattery) { Text("去设置") }
            }
        }
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("应用分身", fontWeight = FontWeight.SemiBold)
                    Text("建议关闭，防止分身应用绕过限制", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    // 打开应用分身设置（华为私有 Activity 不稳定时回退应用管理）
                    try {
                        androidx.compose.ui.platform.LocalContext.current.startActivity(
                            Intent("huawei.intent.action.APP_CLONE_MANAGER")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (ex: Exception) {
                        androidx.compose.ui.platform.LocalContext.current.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${androidx.compose.ui.platform.LocalContext.current.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("去设置") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = allowBattery, onCheckedChange = onAllowBattery)
            Text("我已允许忽略电池优化")
        }
    }
}

@Composable
private fun StepTemplate(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("选择适合孩子年龄段的模板", fontWeight = FontWeight.SemiBold)
        Templates.list.forEach { t ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(t.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == t.id)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == t.id, onClick = { onSelect(t.id) })
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.SemiBold)
                        Text(t.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepReview(template: com.apptime.guard.core.model.TemplateConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("模板预览：${template.name}", fontWeight = FontWeight.SemiBold)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("· 上学日每日 ${formatMinutes(template.dailyQuotaWeekday.toLong())}")
                Text("· 周末每日 ${formatMinutes(template.dailyQuotaWeekend.toLong())}")
                Text("· 单次连续使用不超过 ${template.continuousMax} 分钟")
                Text("· 用 ${template.useX} 分钟休息 ${template.restY} 分钟")
                Text("· 就寝时间 ${template.bedtimeStart / 60}:%02d".format(template.bedtimeStart % 60))
                if (template.blockCategories.isNotEmpty()) {
                    Text("· 默认禁用分类：${template.blockCategories.joinToString("、")}")
                }
                Text("· 内置 5 分钟缓冲与到期提醒", color = MaterialTheme.colorScheme.secondary)
            }
        }
        Text(
            "保存后可在家长模式中随时微调。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun StepDone() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🎉", style = MaterialTheme.typography.displayMedium)
        Text("设置完成！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "设备已进入守护状态。\n长按孩子端页面顶部时钟图标可进入家长模式。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
