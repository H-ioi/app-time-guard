package com.apptime.guard.ui.parent

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.apptime.guard.core.model.AppInfo
import com.apptime.guard.util.Constants
import com.apptime.guard.ui.AppViewModel
import com.apptime.guard.ui.components.SectionCard
import com.apptime.guard.ui.components.categoryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用管理：白名单/黑名单/分类批量/按应用限时。
 * 简化实现：以分类列表 + 单应用行操作为主。
 */
@Composable
fun AppsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val app = AppTimeApp.get(context)

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            loadInstalledApps(context)
        }
    }

    val filtered = apps.filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("应用管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "配置白名单、黑名单、分类批量禁用与单应用限时。",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("搜索应用") }, modifier = Modifier.fillMaxWidth()
            )
        }
        items(filtered, key = { it.packageName }) { info ->
            AppRow(info, vm)
        }
    }
}

@Composable
private fun AppRow(info: AppInfo, vm: AppViewModel) {
    val context = LocalContext.current
    val app = AppTimeApp.get(context)

    var catOverride by remember { mutableStateOf<String?>(null) }
    var catMenu by remember { mutableStateOf(false) }
    var limitText by remember { mutableStateOf("") }

    LaunchedEffect(info.packageName) {
        catOverride = withContext(Dispatchers.IO) {
            app.database.categoryDao().getAll()
                .firstOrNull { it.packageName == info.packageName }?.category
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(info.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        info.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                // 分类选择
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { catMenu = true }) {
                        Text("分类:${Constants.categoryLabel(catOverride ?: info.category)}")
                    }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        categoryOptions().forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.saveCategoryOverride(info.packageName, key)
                                    catOverride = key
                                    catMenu = false
                                }
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = limitText, onValueChange = { limitText = it },
                    label = { Text("临时放宽(分钟)") },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    limitText.toIntOrNull()?.let { min ->
                        if (min > 0) vm.relaxApp(info.packageName, min)
                    }
                }) { Text("放宽") }
            }
            Text(
                "说明：临时放宽到期自动恢复；单应用长期限时请在规则中配置 perAppLimits。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

private suspend fun loadInstalledApps(context: android.content.Context): List<AppInfo> {
    val pm = context.packageManager
    val installed = pm.getInstalledApplications(0)
    return installed
        .filter { it.enabled }
        .mapNotNull { ai ->
            if (ai.packageName == context.packageName) return@mapNotNull null
            if (ai.packageName.startsWith("com.android.")) return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(ai).toString() }
                .getOrDefault(ai.packageName)
            AppInfo(
                packageName = ai.packageName,
                label = label,
                category = Constants.guessCategory(ai.packageName),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
        .sortedBy { it.label.lowercase() }
}
