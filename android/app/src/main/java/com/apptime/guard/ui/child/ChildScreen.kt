package com.apptime.guard.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apptime.guard.core.model.ControlState
import com.apptime.guard.ui.AppViewModel
import com.apptime.guard.ui.components.formatCountdown
import com.apptime.guard.ui.components.formatMinutes
import com.apptime.guard.ui.components.stateColor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** 孩子端主界面：大时钟 + 今日剩余 + 状态 */
@Composable
fun ChildScreen(
    viewModel: AppViewModel,
    onLongPressClock: () -> Unit
) {
    val engine by viewModel.engineResult.collectAsState()
    val quota by viewModel.quota.collectAsState()

    // 每分钟刷新状态
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.evaluate()
            delay(30_000L)
        }
    }

    // 每秒更新时钟
    var timeText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            // 大时钟（长按进入家长模式）
            Column(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPressClock
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    timeText,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "长按时钟可进入家长模式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        item {
            // 今日剩余
            val remaining = engine.remainingMinutes
            val progress = if (remaining >= 0) {
                val total = 1440L.coerceAtLeast(quota?.usedMinutes ?: 0)
                1f - (remaining.toFloat() / total.coerceAtLeast(1f))
            } else 0.5f

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(72.dp),
                            color = stateColor(engine.state),
                            strokeWidth = 8.dp
                        )
                        Text(
                            if (remaining >= 0) "${remaining}" else "∞",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(Modifier.padding(start = 20.dp)) {
                        Text(
                            if (remaining >= 0) "今日剩余" else "今日不限",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (remaining >= 0) formatMinutes(remaining) else "可使用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            engine.activeRuleSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = stateColor(engine.state)
                        )
                    }
                }
            }
        }

        // 当前状态提示
        when (engine.state) {
            ControlState.AVAILABLE -> {}
            ControlState.REMINDING -> item {
                Text(
                    engine.activeRuleSummary,
                    color = stateColor(ControlState.REMINDING),
                    fontWeight = FontWeight.SemiBold
                )
            }
            ControlState.COOLING, ControlState.LOCKED -> item {
                Text(
                    "休息/锁定中 · ${formatCountdown(engine.waitSeconds)}",
                    color = stateColor(engine.state),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            // 使用统计概览
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("今日使用", fontWeight = FontWeight.SemiBold)
                    Text(
                        "已使用 ${formatMinutes(quota?.usedMinutes ?: 0)}" +
                            if ((quota?.bonusMinutes ?: 0) > 0) "（含奖励 ${quota?.bonusMinutes} 分钟）" else "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if ((quota?.restMinutes ?: 0) > 0) {
                        Text(
                            "休息 ${formatMinutes(quota?.restMinutes ?: 0)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
