package com.apptime.guard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apptime.guard.core.model.ControlState
import com.apptime.guard.util.Constants
import java.util.Calendar
import java.util.Locale

/** 分区卡片 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

/** 状态色 */
@Composable
fun stateColor(state: ControlState): androidx.compose.ui.graphics.Color =
    when (state) {
        ControlState.AVAILABLE -> androidx.compose.ui.graphics.Color(0xFF34A853)
        ControlState.REMINDING -> androidx.compose.ui.graphics.Color(0xFFFF9F43)
        ControlState.COOLING -> androidx.compose.ui.graphics.Color(0xFF4F6DF5)
        ControlState.LOCKED -> androidx.compose.ui.graphics.Color(0xFFE5484D)
    }

/** 分钟 → "1小时30分" */
fun formatMinutes(min: Long): String {
    if (min < 0) return "不限"
    val h = min / 60
    val m = min % 60
    return when {
        h == 0L -> "${m}分钟"
        m == 0L -> "${h}小时"
        else -> "${h}小时${m}分钟"
    }
}

/** 分钟（一天内 0..1439）→ "21:30" */
fun formatClock(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    return String.format(Locale.US, "%02d:%02d", h, m)
}

/** 今日星期（周一=0）中文 */
fun weekdayLabel(index: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[index.coerceIn(0, 6)]

fun todayWeekdayIndex(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

/** 秒 → 倒计时 "mm:ss" */
fun formatCountdown(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

/** 分类选择列表 */
fun categoryOptions(): List<Pair<String, String>> = Constants.CATEGORIES
