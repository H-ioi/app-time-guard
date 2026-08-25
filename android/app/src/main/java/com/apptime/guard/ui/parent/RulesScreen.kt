package com.apptime.guard.ui.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apptime.guard.core.model.Rule
import com.apptime.guard.core.model.RuleMode
import com.apptime.guard.core.model.ScopeType
import com.apptime.guard.core.engine.Templates
import com.apptime.guard.ui.AppViewModel
import com.apptime.guard.ui.components.SectionCard
import com.apptime.guard.ui.components.formatClock
import com.apptime.guard.ui.components.formatMinutes
import com.apptime.guard.ui.components.weekdayLabel

/** 规则列表 + 模板入口 + 新建 */
@Composable
fun RulesScreen(vm: AppViewModel) {
    val rules by vm.rules.collectAsState()
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }

    if (editing != null || creating) {
        RuleEditScreen(
            rule = editing,
            onSave = {
                vm.saveRule(it)
                editing = null
                creating = false
            },
            onDelete = {
                editing?.let { r -> vm.deleteRule(r) }
                editing = null
            },
            onCancel = {
                editing = null
                creating = false
            }
        )
        return
    }

    if (showTemplates) {
        TemplateDialog(vm) { showTemplates = false }
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建规则")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("规则管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            item {
                Button(onClick = { showTemplates = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("一键套用模板")
                }
            }
            item {
                Text(
                    "多规则同时生效时按最严格优先结算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            items(rules, key = { it.id }) { rule ->
                RuleCard(rule, onClick = { editing = rule })
            }
            if (rules.isEmpty()) {
                item {
                    Text("暂无规则，点击右下角 + 新建，或一键套用模板。")
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(rule.mode.label)
                        append(" · ")
                        append(formatClock(rule.startMin))
                        append("-")
                        append(formatClock(rule.endMin))
                        if (rule.dailyQuotaMin != null) {
                            append(" · 每日 ")
                            append(formatMinutes(rule.dailyQuotaMin.toLong()))
                        }
                        if (rule.useXMin != null && rule.restYMin != null) {
                            append(" · 用${rule.useXMin}停${rule.restYMin}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    weekdayLabel(0) + "~" + weekdayLabel(6),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
    }
}

/** 规则编辑页 */
@Composable
private fun RuleEditScreen(
    rule: Rule?,
    onSave: (Rule) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(rule?.name ?: "新规则") }
    var mode by remember { mutableStateOf(rule?.mode ?: RuleMode.NORMAL) }
    var scopeType by remember { mutableStateOf(rule?.scopeType ?: ScopeType.ALL) }
    var startMin by remember { mutableStateOf(rule?.startMin ?: 0) }
    var endMin by remember { mutableStateOf(rule?.endMin ?: 1439) }
    var dailyQuota by remember { mutableStateOf(rule?.dailyQuotaMin?.toString() ?: "") }
    var continuousMax by remember { mutableStateOf(rule?.continuousMaxMin?.toString() ?: "") }
    var useX by remember { mutableStateOf(rule?.useXMin?.toString() ?: "") }
    var restY by remember { mutableStateOf(rule?.restYMin?.toString() ?: "") }
    var buffer by remember { mutableStateOf(rule?.bufferMinutes ?: 5) }
    var enabled by remember { mutableStateOf(rule?.enabled ?: true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("编辑规则", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("规则名称") }, modifier = Modifier.fillMaxWidth()
        )

        SectionCard("模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label) }
                    )
                }
            }
        }

        SectionCard("应用范围") {
            ScopeType.entries.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { scopeType = s },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = scopeType == s, onCheckedChange = { scopeType = s })
                    Text(s.label)
                }
            }
            Text(
                "提示：白名单/黑名单/分类的具体应用请在「应用」页配置。",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard("时间窗") {
            Text("开始 ${formatClock(startMin)}  结束 ${formatClock(endMin)}")
            Text("开始时刻（拖拽调整）：", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = startMin.toFloat(), onValueChange = { startMin = it.toInt() },
                valueRange = 0f..1439f
            )
            Text("结束时刻：", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = endMin.toFloat(), onValueChange = { endMin = it.toInt() },
                valueRange = 0f..1439f
            )
            if (endMin < startMin) {
                Text("跨天规则（次日 ${formatClock(endMin)} 结束）", color = MaterialTheme.colorScheme.secondary)
            }
        }

        SectionCard("时长限制") {
            OutlinedTextField(
                value = dailyQuota, onValueChange = { dailyQuota = it },
                label = { Text("每日总时长（分钟，留空不限）") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = continuousMax, onValueChange = { continuousMax = it },
                label = { Text("单次连续上限（分钟，留空不限）") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = useX, onValueChange = { useX = it },
                    label = { Text("用（分钟）") }, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = restY, onValueChange = { restY = it },
                    label = { Text("停（分钟）") }, modifier = Modifier.weight(1f)
                )
            }
            Text("缓冲 ${buffer} 分钟", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = buffer.toFloat(), onValueChange = { buffer = it.toInt() },
                valueRange = 0f..15f, steps = 14
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = { enabled = it })
            Text(if (enabled) "规则启用中" else "规则已停用", modifier = Modifier.padding(start = 8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onSave(
                        (rule ?: Rule()).copy(
                            name = name,
                            mode = mode,
                            scopeType = scopeType,
                            startMin = startMin,
                            endMin = endMin,
                            dailyQuotaMin = dailyQuota.toIntOrNull(),
                            continuousMaxMin = continuousMax.toIntOrNull(),
                            useXMin = useX.toIntOrNull(),
                            restYMin = restY.toIntOrNull(),
                            bufferMinutes = buffer,
                            enabled = enabled
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
            if (rule != null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("删除") }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
        }
    }
}

/** 模板选择对话框 */
@Composable
private fun TemplateDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("一键套用模板", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("应用模板将替换当前全部规则。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Templates.list.forEach { t ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    vm.applyTemplate(t.id)
                    onDismiss()
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(t.name, fontWeight = FontWeight.SemiBold)
                    Text(t.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
    }
}
