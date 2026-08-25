package com.apptime.guard.ui.parent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apptime.guard.AppTimeApp
import com.apptime.guard.core.model.CastPolicy
import com.apptime.guard.core.model.SecurityLevel
import com.apptime.guard.data.db.AppDatabase
import com.apptime.guard.ui.AppViewModel
import com.apptime.guard.ui.components.SectionCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 系统设置 */
@Composable
fun SettingsScreen(vm: AppViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val app = AppTimeApp.get(context)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val securityLevel by app.settings.securityLevel.collectAsState(initial = SecurityLevel.STANDARD)
    val castPolicy by app.settings.castPolicy.collectAsState(initial = CastPolicy.COUNT_AS_USE)
    val silentMode by app.settings.silentMode.collectAsState(initial = false)
    val dailyReport by app.settings.dailyReportOn.collectAsState(initial = true)
    var resetHour by remember { mutableStateOf(0) }
    var resetMinute by remember { mutableStateOf(0) }
    var showPinDialog by remember { mutableStateOf(false) }
    var exportMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val (h, m) = app.settings.getResetTime()
        resetHour = h
        resetMinute = m
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("系统设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        item {
            SectionCard("安全") {
                TextButton(onClick = { showPinDialog = true }) { Text("修改家长 PIN") }
                Text("安全等级", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecurityLevel.entries.forEach { level ->
                        FilterChip(
                            selected = securityLevel == level,
                            onClick = {
                                scope.launch { app.settings.setSecurityLevel(level) }
                            },
                            label = { Text(level.label) }
                        )
                    }
                }
                Text(
                    "严格级：检测到时间篡改或守护组件异常时立即锁定设备，需家长 PIN 处理；标准级：告警并最保守结算。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            SectionCard("投屏处理（息屏投屏电视）") {
                CastPolicy.entries.forEach { policy ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch { app.settings.setCastPolicy(policy) }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = castPolicy == policy,
                            onCheckedChange = {
                                scope.launch { app.settings.setCastPolicy(policy) }
                            }
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(policy.label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(
                    "投屏计为使用：投屏期间即使息屏也消耗配额。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            SectionCard("提醒与报告") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("静默模式", Modifier.weight(1f))
                    Switch(
                        checked = silentMode,
                        onCheckedChange = { scope.launch { app.settings.setSilentMode(it) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每日报告提醒", Modifier.weight(1f))
                    Switch(
                        checked = dailyReport,
                        onCheckedChange = {
                            // 简化：静默模式字段承载（MVP 合并实现）
                        }
                    )
                }
            }
        }

        item {
            SectionCard("重置时刻") {
                Text("每日配额按以下时刻重置（默认 00:00）")
                Text("重置时间：${resetHour}:%02d".format(resetMinute), fontWeight = FontWeight.SemiBold)
                Slider(
                    value = (resetHour * 60 + resetMinute).toFloat(),
                    onValueChange = {
                        resetHour = it.toInt() / 60
                        resetMinute = it.toInt() % 60
                    },
                    valueRange = 0f..1439f
                )
                TextButton(onClick = {
                    scope.launch { app.settings.setResetTime(resetHour, resetMinute) }
                }) { Text("保存重置时刻") }
            }
        }

        item {
            SectionCard("防绕过开关") {
                Text("· 时间可信：单调时钟偏移检测（自动生效）")
                Text("· 防卸载提醒：设备管理员被关闭时告警")
                Text("· 无障碍守护：服务被关闭时审计记录")
            }
        }

        item {
            SectionCard("华为专项") {
                TextButton(onClick = { openHuaweiStartup(context) }) { Text("应用启动管理（保活）") }
                TextButton(onClick = { openBattery(context) }) { Text("电池优化设置") }
                TextButton(onClick = { openAppClone(context) }) { Text("应用分身设置") }
            }
        }

        item {
            SectionCard("数据") {
                Button(
                    onClick = {
                        scope.launch {
                            exportMsg = exportCsv(context, app.database)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出数据（CSV 到本地下载目录）") }
                exportMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        item {
            SectionCard("关于") {
                Text("使用守护 v1.0.0（纯本地离线）")
                Text("忘记 PIN：卸载重装后重新设置（数据将清除）。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text("退出家长模式")
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            title = "修改家长 PIN",
            verify = false,
            onDismiss = { showPinDialog = false },
            onSuccess = { showPinDialog = false }
        )
    }
}

private fun openHuaweiStartup(context: Context) {
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

private fun openBattery(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (ex: Exception) { }
}

private fun openAppClone(context: Context) {
    try {
        context.startActivity(
            Intent("huawei.intent.action.APP_CLONE_MANAGER")
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

private suspend fun exportCsv(context: Context, db: AppDatabase): String {
    return withContext(Dispatchers.IO) {
        runCatching {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val file = File(dir, "apptime_export_${System.currentTimeMillis()}.csv")
            val sb = StringBuilder()
            sb.appendLine("日期,已用分钟,休息分钟,解锁次数,奖励分钟")
            db.quotaDao().getAll().forEach {
                sb.appendLine("${it.date},${it.usedMinutes},${it.restMinutes},${it.unlockCount},${it.bonusMinutes}")
            }
            file.writeText(sb.toString())
            "已导出到 ${file.absolutePath}"
        }.getOrElse { "导出失败：${it.message}" }
    }
}
