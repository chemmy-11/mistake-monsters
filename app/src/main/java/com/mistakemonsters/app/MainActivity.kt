package com.mistakemonsters.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.mistakemonsters.app.ui.C
import com.mistakemonsters.app.ui.CaptureScreen
import com.mistakemonsters.app.ui.HomeScreen
import com.mistakemonsters.app.ui.MistakeMonstersTheme
import com.mistakemonsters.app.ui.PracticeScreen
import com.mistakemonsters.app.ui.QuestionDetailScreen
import com.mistakemonsters.app.ui.QuestionsScreen
import com.mistakemonsters.app.ui.ReportScreen
import com.mistakemonsters.app.ui.SettingsScreen
import com.mistakemonsters.app.ui.UpdateDialog

// ===== 单 Activity + 手写导航 =====

enum class Tab(val label: String, val emoji: String) {
    HOME("首页", "🏠"),
    CAPTURE("收录", "📸"),
    LIST("错题本", "📒"),
    PRACTICE("练习卷", "📝"),
    REPORT("画像", "🧠"),
    SETTINGS("设置", "⚙️"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MistakeMonstersTheme {
                Root()
            }
        }
    }
}

@Composable
fun Root() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var practiceIds by remember { mutableStateOf<List<Long>?>(null) }
    val app = LocalContext.current.applicationContext as App

    // 启动时静默检查更新
    LaunchedEffect(Unit) { app.updater.autoCheck() }

    fun gotoPractice(ids: List<Long>) {
        practiceIds = ids
        tab = Tab.PRACTICE
    }

    // 详情页覆盖在 tab 之上，返回键先退详情
    BackHandler(enabled = detailId != null) { detailId = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && detailId == null,
                        onClick = { detailId = null; tab = t },
                        icon = { Text(t.emoji, fontSize = 20.sp) },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.PrimaryDeep,
                            selectedTextColor = C.PrimaryDeep,
                            indicatorColor = C.PrimarySoft,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(pad),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (detailId != null) {
                    QuestionDetailScreen(
                        app = app,
                        questionId = detailId!!,
                        onClose = { detailId = null },
                        onPractice = { gotoPractice(listOf(detailId!!)) },
                    )
                } else {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            app = app,
                            onCapture = { tab = Tab.CAPTURE },
                            onOpenQuestion = { detailId = it },
                            onOpenReport = { tab = Tab.REPORT },
                            onOpenPractice = { tab = Tab.PRACTICE },
                            onOpenSettings = { tab = Tab.SETTINGS },
                            onOpenQuestions = { tab = Tab.LIST },
                        )
                        Tab.CAPTURE -> CaptureScreen(app = app, onDone = { tab = Tab.LIST })
                        Tab.LIST -> QuestionsScreen(
                            app = app,
                            onOpenQuestion = { detailId = it },
                            onPractice = { gotoPractice(it) },
                            onCapture = { tab = Tab.CAPTURE },
                        )
                        Tab.PRACTICE -> PracticeScreen(app = app, presetQuestionIds = practiceIds)
                        Tab.REPORT -> ReportScreen(app = app, onPracticeForCause = { causeId ->
                            app.lastCauseId = causeId
                            practiceIds = null
                            tab = Tab.PRACTICE
                        })
                        Tab.SETTINGS -> SettingsScreen(app = app)
                    }
                }
            }
            // 在线更新对话框
            UpdateDialog(app.updater)
        }
    }
}
