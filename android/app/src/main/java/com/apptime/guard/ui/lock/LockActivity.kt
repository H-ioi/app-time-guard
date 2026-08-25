package com.apptime.guard.ui.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apptime.guard.core.model.ControlState
import com.apptime.guard.core.model.EngineResult
import com.apptime.guard.ui.components.formatCountdown
import com.apptime.guard.ui.theme.AppTimeTheme
import kotlinx.coroutines.delay

/**
 * 全屏锁定界面：冷却/锁定/就寝/拦截提示时展示。
 * - 拦截返回键；用户尝试离开（Home/最近任务）时自动拉回；
 * - 倒计时实时刷新；家长模式验证通过后由 GuardService 发送关闭广播。
 */
class LockActivity : ComponentActivity() {

    private var result by mutableStateOf<EngineResult>(
        EngineResult(state = ControlState.LOCKED, activeRuleSummary = "")
    )

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.apptime.guard.action.CLOSE_LOCK") {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏 + 防截图（严格模式由服务端决定，这里默认开启防截图）
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        result = EngineResult(
            state = runCatching {
                ControlState.valueOf(intent.getStringExtra("state") ?: "LOCKED")
            }.getOrDefault(ControlState.LOCKED),
            activeRuleSummary = intent.getStringExtra("summary") ?: "",
            remainingMinutes = intent.getLongExtra("remaining", -1),
            waitSeconds = intent.getLongExtra("wait", 0)
        )

        registerReceiver(closeReceiver, IntentFilter("com.apptime.guard.action.CLOSE_LOCK"))

        setContent {
            AppTimeTheme {
                LockContent(result)
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeReceiver) }
        super.onDestroy()
    }

    // 拦截返回键
    override fun onBackPressed() {
        // 不响应返回：必须由家长模式解锁
    }

    // 用户尝试离开（Home 等）→ 拉回前台
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val intent = Intent(this, LockActivity::class.java).apply {
            putExtra("state", result.state.name)
            putExtra("summary", result.activeRuleSummary)
            putExtra("wait", result.waitSeconds)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    companion object {
        fun start(context: Context, result: EngineResult) {
            val intent = Intent(context, LockActivity::class.java).apply {
                putExtra("state", result.state.name)
                putExtra("summary", result.activeRuleSummary)
                putExtra("remaining", result.remainingMinutes)
                putExtra("wait", result.waitSeconds)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun LockContent(result: EngineResult) {
    var countdown by remember { mutableLongStateOf(result.waitSeconds) }
    var showClock by remember { mutableStateOf(true) }

    LaunchedEffect(result.waitSeconds) {
        countdown = result.waitSeconds
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    val bg = when (result.state) {
        ControlState.LOCKED -> MaterialTheme.colorScheme.primary
        ControlState.COOLING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "⏸",
                fontSize = 48.sp
            )
            Text(
                result.activeRuleSummary.ifEmpty { "请休息一下" },
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            if (countdown > 0) {
                Text(
                    formatCountdown(countdown),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (result.remainingMinutes >= 0 && result.state == ControlState.LOCKED) {
                Text(
                    "今日剩余 ${result.remainingMinutes} 分钟",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                )
            }
            Text(
                "需要继续使用？请家长在家长模式中处理",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
