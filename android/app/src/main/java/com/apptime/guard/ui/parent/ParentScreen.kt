package com.apptime.guard.ui.parent

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apptime.guard.ui.AppViewModel
import com.apptime.guard.ui.components.SectionCard
import com.apptime.guard.ui.components.formatCountdown
import com.apptime.guard.ui.components.formatMinutes
import com.apptime.guard.ui.components.stateColor
import kotlinx.coroutines.delay

/** 家长端容器：底部导航（首页/规则/应用/统计/设置） */
@Composable
fun ParentScreen(onExit: () -> Unit) {
    val vm: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var tab by remember { mutableIntStateOf(0) }

    // 家长模式进入时刷新
    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(60_000L)
            vm.evaluate()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("首页", "规则", "应用", "统计", "设置").forEachIndexed { i, name ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                when (i) {
                                    0 -> Icons.Default.Home
                                    1 -> Icons.Default.Lock
                                    2 -> Icons.Default.PlayArrow
                                    3 -> Icons.Default.Star
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = name
                            )
                        },
                        label = { Text(name) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ParentHomeTab(vm)
                1 -> RulesScreen(vm)
                2 -> AppsScreen(vm)
                3 -> StatsScreen(vm)
                4 -> SettingsScreen(vm, onExit)
            }
        }
    }
}

/** 家长首页：今日状态 + 快速干预 */
@Composable
fun ParentHomeTab(vm: AppViewModel) {
    val engine by vm.engineResult.collectAsState()
    val quota by vm.quota.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("家长模式", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            SectionCard("今日管控状态") {
                val remaining = engine.remainingMinutes
                val progress = if (remaining >= 0) {
                    1f - (remaining.toFloat() / (quota?.usedMinutes?.plus(remaining) ?: 1).coerceAtLeast(1f))
                } else 0.5f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(64.dp),
                        color = stateColor(engine.state)
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(
                            if (remaining >= 0) "今日剩余 ${formatMinutes(remaining)}"
                            else "今日不限",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("已用 ${formatMinutes(quota?.usedMinutes ?: 0)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            engine.activeRuleSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = stateColor(engine.state)
                        )
                    }
                }
            }
        }

        item {
            SectionCard("快速干预") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.lockNow(30) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("锁定30分")
                    }
                    OutlinedButton(onClick = { vm.unlockNow() }, modifier = Modifier.weight(1f)) {
                        Text("解锁")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.pause(10) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("暂停10分")
                    }
                    OutlinedButton(onClick = { vm.addBonus(15) }, modifier = Modifier.weight(1f)) {
                        Text("+奖励15分")
                    }
                }
                Text(
                    "提示：锁定会立即生效并全屏拦截；暂停期间不统计时长。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        item {
            SectionCard("孩子设备使用概览") {
                Text("· 已用 ${formatMinutes(quota?.usedMinutes ?: 0)}（含奖励 ${quota?.bonusMinutes ?: 0} 分钟）")
                Text("· 休息 ${formatMinutes(quota?.restMinutes ?: 0)}")
                Text("· 今日解锁 ${quota?.unlockCount ?: 0} 次")
                if (engine.anomaly != null) {
                    Text("⚠️ ${engine.anomaly}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
