package com.mistakemonsters.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakemonsters.app.update.UpdateManager
import com.mistakemonsters.app.update.Updater

// ===== 在线更新对话框（观察 UpdateManager 状态）=====

@Composable
fun UpdateDialog(manager: UpdateManager) {
    val st = manager.status
    when (st) {
        is UpdateManager.Status.Available -> {
            AlertDialog(
                onDismissRequest = { manager.dismiss() },
                confirmButton = {
                    TextButton(onClick = { manager.startDownload(st.info) }) {
                        Text("⬇️ 下载更新", color = C.PrimaryDeep, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { manager.dismiss() }) { Text("下次再说", color = C.InkSoft) }
                },
                title = { Text("发现新版本 ${st.info.version} 🎉", fontWeight = FontWeight.Bold) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "当前版本 ${Updater.currentVersion()} → 新版本 ${st.info.version}（${formatSize(st.info.size)}）",
                            fontSize = 12.sp, color = C.InkSoft,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(st.info.notes, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                },
            )
        }
        is UpdateManager.Status.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                dismissButton = {},
                title = { Text("正在下载更新…", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Box(
                            Modifier.fillMaxWidth().height(10.dp).background(Color(0xFFF0EDE8), RoundedCornerShape(999.dp)),
                        ) {
                            Box(
                                Modifier.fillMaxWidth((st.pct.coerceAtLeast(2)) / 100f).height(10.dp)
                                    .background(C.Primary, RoundedCornerShape(999.dp)),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${st.pct}%", fontSize = 13.sp, color = C.InkSoft, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
        is UpdateManager.Status.Ready -> {
            // 下载完成：自动拉起安装器（未授权"安装未知应用"时给提示）
            LaunchedEffect(st.apk.absolutePath) {
                manager.install(st.apk)
            }
            AlertDialog(
                onDismissRequest = { manager.dismiss() },
                confirmButton = {
                    TextButton(onClick = { manager.install(st.apk) }) {
                        Text("再次尝试安装", color = C.PrimaryDeep, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { manager.dismiss() }) { Text("稍后", color = C.InkSoft) }
                },
                title = { Text("下载完成 ✅", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        manager.installHint?.let {
                            Text(it, color = C.Accent, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text("将安装 ${st.info.version}，请在系统弹窗中点「安装」。", fontSize = 13.sp)
                    }
                },
            )
        }
        else -> {}
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes > 1024 * 1024) "${bytes / 1024 / 1024} MB" else "${bytes / 1024} KB"
