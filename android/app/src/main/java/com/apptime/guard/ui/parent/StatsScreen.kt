package com.apptime.guard.ui.parent

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.apptime.guard.core.model.AuditLog
import com.apptime.guard.core.model.QuotaState
import com.apptime.guard.ui.AppViewModel
import com.apptime.guard.ui.components.SectionCard
import com.apptime.guard.ui.components.formatMinutes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 统计报告：日报（App 排行）+ 周报 + 审计日志 */
@Composable
fun StatsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var todayState by remember { mutableStateOf<QuotaState?>(null) }
    var weekStates by remember { mutableStateOf<List<Pair<String, QuotaState?>>>(emptyList()) }
    var audits by remember { mutableStateOf<List<AuditLog>>(emptyList()) }

    LaunchedEffect(Unit) {
        val app = AppTimeApp.get(context)
        withContext(Dispatchers.IO) {
            todayState = app.quotaManager.getState()
            weekStates = buildWeek(app)
            audits = app.database.auditDao().getRecent(100)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("统计报告", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            SectionCard("今日使用排行") {
                val perApp = parsePerApp(todayState?.perAppUsed.orEmpty())
                if (perApp.isEmpty()) {
                    Text("今日暂无使用数据")
                } else {
                    perApp.sortedByDescending { it.second }.forEach { (pkg, min) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pkg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(formatMinutes(min), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            SectionCard("近 7 天") {
                weekStates.forEach { (date, state) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date, Modifier.weight(1f))
                        Text(
                            formatMinutes(state?.usedMinutes ?: 0),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            SectionCard("审计日志（最近 100 条）") {
                audits.forEach { log ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(log.ts)) +
                                " · " + log.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(log.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private suspend fun buildWeek(app: AppTimeApp): List<Pair<String, QuotaState?>> {
    val dao = app.database.quotaDao()
    val all = dao.getAll().associateBy { it.date }
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return (6 downTo 0).map { off ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -off) }
        val key = fmt.format(cal.time)
        key to all[key]
    }
}

private fun parsePerApp(raw: String): List<Pair<String, Long>> =
    raw.split(",").filter { it.isNotEmpty() }.mapNotNull {
        val parts = it.split(":")
        if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0) else null
    }
