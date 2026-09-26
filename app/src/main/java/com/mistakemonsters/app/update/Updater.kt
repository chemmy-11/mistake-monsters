package com.mistakemonsters.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mistakemonsters.app.BuildConfig
import com.mistakemonsters.app.llm.LlmException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// ===== 在线更新：GitHub Release 检查 / APK 下载（带镜像回退）/ 安装引导 =====

object Updater {
    const val REPO = "chemmy-11/mistake-monsters"

    data class UpdateInfo(
        val tagName: String,
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val size: Long,
    )

    fun currentVersion(): String = BuildConfig.VERSION_NAME

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 拉取最新 Release 信息（GitHub API 直连；本机网络实测 api.github.com 可达） */
    suspend fun checkLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "mistake-monsters-app")
            .build()
        val resp = try {
            client.newCall(req).execute()
        } catch (e: Exception) {
            throw LlmException("无法连接 GitHub：${e.message ?: e}")
        }
        resp.use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                if (r.code == 404) throw LlmException("还没有发布任何版本")
                throw LlmException("GitHub 返回 ${r.code}")
            }
            val o = JSONObject(text)
            val tag = o.optString("tag_name").removePrefix("v")
            val assets = o.optJSONArray("assets") ?: JSONObject.NULL.let { org.json.JSONArray() }
            var apkUrl = ""
            var apkName = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    apkName = a.optString("name")
                    size = a.optLong("size")
                    break
                }
            }
            if (apkUrl.isBlank()) throw LlmException("最新版本未附带 APK 安装包")
            UpdateInfo(
                tagName = o.optString("tag_name"),
                version = tag,
                notes = o.optString("body").take(1200),
                apkUrl = apkUrl,
                apkName = apkName,
                size = size,
            )
        }
    }

    /** 语义化版本比较：remote 是否比 current 新 */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.').map { it.trim().toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * APK 下载地址：github.com 直连经常不可用，按顺序尝试镜像前缀。
     * 镜像只代理文件下载（releases/download），检查接口走 api.github.com。
     */
    fun downloadUrls(direct: String): List<String> = listOf(
        direct,
        "https://ghfast.top/$direct",
        "https://gh-proxy.com/$direct",
    )

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            // 清理旧安装包
            dir.listFiles()?.forEach { it.delete() }
            val outFile = File(dir, "update-${info.version}.apk")

            var lastErr: Exception? = null
            for (url in downloadUrls(info.apkUrl)) {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", "mistake-monsters-app").build()
                    client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw LlmException("下载地址返回 ${r.code}")
                        val body = r.body ?: throw LlmException("下载响应为空")
                        val total = body.contentLength()
                        var done = 0L
                        var lastPct = -1
                        body.byteStream().use { input ->
                            outFile.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    output.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            onProgress(pct)
                                        }
                                    }
                                }
                            }
                        }
                        if (outFile.length() < 1024 * 100) throw LlmException("下载的文件不完整")
                        return@withContext outFile
                    }
                } catch (e: Exception) {
                    lastErr = e
                    outFile.delete()
                }
            }
            throw lastErr ?: LlmException("下载失败")
        }

    /** 引导安装：无"安装未知应用"授权时先跳系统设置，授权后回来再点一次 */
    fun install(context: Context, apk: File): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return "请在系统设置中允许「安装未知应用」，然后回来再点一次安装"
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ""
    }
}

/** 更新流程的可观察状态（Compose 直接读取） */
class UpdateManager(private val context: Context) {
    sealed class Status {
        data object Idle : Status()
        data object Checking : Status()
        data object UpToDate : Status()
        data class Available(val info: Updater.UpdateInfo, val auto: Boolean) : Status()
        data class Downloading(val info: Updater.UpdateInfo, val pct: Int) : Status()
        data class Ready(val info: Updater.UpdateInfo, val apk: File) : Status()
        data class Failed(val message: String) : Status()
    }

    var status: Status by mutableStateOf(Status.Idle)
        private set
    var installHint: String? by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 启动时静默检查（失败不打扰） */
    fun autoCheck() {
        if (status !is Status.Idle && status !is Status.Failed) return
        scope.launch {
            try {
                val info = Updater.checkLatest()
                if (Updater.isNewer(info.version, Updater.currentVersion())) {
                    status = Status.Available(info, auto = true)
                }
                // 相同版本：静默，不打扰
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    /** 手动检查（设置页按钮，结果可见） */
    fun manualCheck() {
        status = Status.Checking
        scope.launch {
            try {
                val info = Updater.checkLatest()
                status = if (Updater.isNewer(info.version, Updater.currentVersion())) {
                    Status.Available(info, auto = false)
                } else {
                    Status.UpToDate
                }
            } catch (e: Exception) {
                status = Status.Failed(e.message ?: "检查失败")
            }
        }
    }

    fun dismiss() {
        status = Status.Idle
    }

    fun startDownload(info: Updater.UpdateInfo) {
        status = Status.Downloading(info, 0)
        scope.launch {
            try {
                val apk = Updater.download(context, info) { pct ->
                    status = Status.Downloading(info, pct)
                }
                status = Status.Ready(info, apk)
            } catch (e: Exception) {
                status = Status.Failed(e.message ?: "下载失败")
            }
        }
    }

    fun install(apk: File) {
        installHint = Updater.install(context, apk)
    }
}
