package com.apptime.guard.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.apptime.guard.util.Constants
import com.apptime.guard.util.PinManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PIN 输入对话框（验证或首次设置）。
 * 4~6 位数字，错误 5 次锁定 30 秒。
 */
@Composable
fun PinDialog(
    title: String,
    verify: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val app = AppTimeApp.get(context)
    val pinManager = remember { PinManager(app.settings) }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockUntil by remember { mutableStateOf(0L) }
    var attempts by remember { mutableStateOf(0) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 锁定倒计时
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockUntil) {
        while (lockUntil > now) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    fun onDigit(d: Char) {
        if (System.currentTimeMillis() < lockUntil) return
        if (pin.length >= 6) return
        pin += d
        if (pin.length == 4) {
            scope.launch {
                if (verify) {
                    val ok = pinManager.verify(pin)
                    if (ok) {
                        onSuccess()
                    } else {
                        attempts++
                        if (attempts >= Constants.PIN_MAX_ATTEMPTS) {
                            lockUntil = System.currentTimeMillis() + Constants.PIN_LOCK_MS
                            error = "错误次数过多，请 ${
                                Constants.PIN_LOCK_MS / 1000
                            } 秒后重试"
                            attempts = 0
                        } else {
                            error = "PIN 错误，还剩 ${Constants.PIN_MAX_ATTEMPTS - attempts} 次"
                        }
                        pin = ""
                    }
                } else {
                    pinManager.setPin(pin)
                    onSuccess()
                }
            }
        }
    }

    fun onBackspace() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // PIN 圆点显示
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    repeat(4) { i ->
                        Surface(
                            modifier = Modifier.size(16.dp),
                            shape = CircleShape,
                            color = if (i < pin.length)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {}
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (System.currentTimeMillis() < lockUntil) {
                    Text(
                        "锁定中 ${(lockUntil - now) / 1000} 秒",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = { onBackspace() }) { Text("删除") }
            }
        }
    )
}
