package com.mistakemonsters.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakemonsters.app.App
import com.mistakemonsters.app.data.DraftQuestion
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== 收录错题向导：拍照 → 识别确认 → AI 归因入库 =====

@Composable
fun CaptureScreen(app: App, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf("upload") } // upload | crop | recognizing | review | analyzing | done
    var error by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropRect by remember { mutableStateOf<FloatArray?>(null) } // 待裁剪框（图像像素坐标 [l, t, r, b]）
    var imagePath by remember { mutableStateOf<String?>(null) }
    var drafts by remember { mutableStateOf<List<DraftQuestion>>(emptyList()) }
    var answers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var progress by remember { mutableStateOf(0 to 0) }
    var createdCount by remember { mutableStateOf(0) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // 拍照：FileProvider uri
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val uri = pendingPhotoUri
            if (uri != null) scope.launch {
                val bmp = uriToBitmap(context, uri)
                if (bmp == null) { error = "图片读取失败，换一张试试"; step = "upload" }
                else { bitmap = bmp; cropRect = defaultCrop(bmp); step = "crop" }
            }
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = uriToBitmap(context, uri)
                if (bmp == null) { error = "图片读取失败，换一张试试"; step = "upload" }
                else { bitmap = bmp; cropRect = defaultCrop(bmp); step = "crop" }
            }
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "capture_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.mistakemonsters.app.fileprovider", f)
        pendingPhotoUri = uri
        takePicture.launch(uri)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 步骤条
        Text(
            when (step) {
                "upload" -> "第 1 步 · 拍下你的错题"
                "crop" -> "第 2 步 · 裁剪照片"
                "recognizing" -> "第 2 步 · 裁剪照片"
                "review", "analyzing" -> "第 3 步 · 确认题目与我的答案"
                else -> "第 4 步 · 收录完成"
            },
            fontSize = 13.sp, color = C.InkSoft, fontWeight = FontWeight.Bold,
        )
        error?.let { ErrorBox(it) { error = null } }

        when (step) {
            "upload", "recognizing" -> {
                MsCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("📸", fontSize = 44.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("拍下你的错题", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "把写错了的作业或试卷拍下来（连同你写的答案），AI 只负责收录和归因，不会直接替你解题哦 😉",
                            color = C.InkSoft, fontSize = 13.sp, lineHeight = 19.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MsButton("📷 拍照", { launchCamera() })
                            GhostButton("🖼 从相册选", {
                                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                        Spacer(Modifier.height(10.dp))
                        bitmap?.let {
                            Image(
                                it.asImageBitmap(), null,
                                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        if (step == "recognizing") {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("AI 正在识别题目…", color = C.InkSoft, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("支持 JPG / PNG，照片保存在本机", color = C.InkSoft, fontSize = 11.sp)
                    }
                }
            }

            "crop" -> {
                val bmp = bitmap
                val rect = cropRect
                if (bmp != null && rect != null) {
                    MsCard {
                        Column(Modifier.fillMaxWidth()) {
                            Text("✂️ 裁剪照片", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "拖动方框框住错题（连同你写的答案），去掉多余背景，识别更准",
                                color = C.InkSoft, fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            CropOverlay(bitmap = bmp, rect = rect, onRectChange = { cropRect = it })
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "裁剪区域：${(rect[2] - rect[0]).toInt()} × ${(rect[3] - rect[1]).toInt()} px    原图：${bmp.width} × ${bmp.height} px",
                                color = C.InkSoft, fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GhostButton("↺ 重新选择", { step = "upload"; bitmap = null; cropRect = null })
                                GhostButton("使用原图", {
                                    scope.launch {
                                        handleImage(
                                            app,
                                            { b -> bitmap = b },
                                            { p -> imagePath = p },
                                            bmp,
                                            { _, e -> error = e; cropRect = defaultCrop(bmp); step = "crop" },
                                            { s -> step = s },
                                            { d ->
                                                drafts = d
                                                answers = d.mapIndexed { i, dd -> i to dd.myAnswer }.toMap()
                                                step = "review"
                                            },
                                        )
                                    }
                                })
                            }
                            Spacer(Modifier.height(10.dp))
                            MsButton("✓ 确认裁剪并识别", {
                                val cropped = cropBitmap(bmp, rect)
                                scope.launch {
                                    handleImage(
                                        app,
                                        { b -> bitmap = b },
                                        { p -> imagePath = p },
                                        cropped,
                                        { _, e -> error = e; cropRect = defaultCrop(cropped); step = "crop" },
                                        { s -> step = s },
                                        { d ->
                                            drafts = d
                                            answers = d.mapIndexed { i, dd -> i to dd.myAnswer }.toMap()
                                            step = "review"
                                        },
                                    )
                                }
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            "review" -> {
                Text("AI 识别到 ${drafts.size} 道题，请确认", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("勾选要收录的错题，并填写/修改「我当时写的答案」——这是 AI 判断错因的关键。", color = C.InkSoft, fontSize = 13.sp)
                drafts.forEachIndexed { i, d ->
                    MsCard {
                        Row {
                            Checkbox(checked = d.selected, onCheckedChange = { drafts = drafts.mapIndexed { j, x -> if (j == i) x.copy(selected = it) else x } })
                            Column(Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = d.questionText,
                                    onValueChange = { t -> drafts = drafts.mapIndexed { j, x -> if (j == i) x.copy(questionText = t) else x } },
                                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                                    colors = fieldColors(),
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MsChip(d.category, C.PrimarySoft, C.PrimaryDeep)
                                    if (d.subtype.isNotBlank()) MsChip(d.subtype, C.SkySoft, C.Sky)
                                    d.knowledgeTags.forEach { MsChip(it, C.MintSoft, C.Mint) }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = answers[i] ?: "",
                                    onValueChange = { t -> answers = answers + (i to t) },
                                    label = { Text("我的答案（很重要）") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = fieldColors(),
                                )
                                if (d.note.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("📷 ${d.note}", color = C.InkSoft, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("← 重拍", {
                        step = "upload"; drafts = emptyList(); bitmap = null; cropRect = null
                    })
                    MsButton("🧠 AI 分析错因并收录（${drafts.count { it.selected }} 道）", {
                        val chosen = drafts.withIndex().filter { it.value.selected }
                        if (chosen.isEmpty()) return@MsButton
                        scope.launch {
                            step = "analyzing"
                            progress = 0 to chosen.size
                            createdCount = 0
                            for ((i, d) in chosen) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        app.pipeline.analyzeAndCreate(
                                            questionText = d.questionText,
                                            myAnswer = answers[i] ?: "",
                                            category = d.category,
                                            subtype = d.subtype,
                                            knowledgeTags = d.knowledgeTags,
                                            note = d.note.ifBlank { null },
                                            imagePath = imagePath,
                                        )
                                    }
                                    createdCount++
                                } catch (e: Exception) {
                                    error = e.message ?: "分析失败"
                                }
                                progress = (progress.first + 1) to progress.second
                            }
                            if (createdCount > 0) step = "done" else step = "review"
                        }
                    }, enabled = drafts.any { it.selected })
                }
                Spacer(Modifier.height(80.dp))
            }

            "analyzing" -> {
                MsCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = C.Primary)
                        Spacer(Modifier.height(10.dp))
                        Text("AI 正在分析错因、生成辅导提示…（${progress.first}/${progress.second}）", color = C.InkSoft, fontSize = 13.sp)
                    }
                }
            }

            "done" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
                    Text("🎉", fontSize = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("收录成功！小怪兽 +$createdCount 👾", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("去错题本查看 AI 的错因分析和引导提示吧", color = C.InkSoft, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("📸 再收录一道", {
                            step = "upload"; bitmap = null; drafts = emptyList(); createdCount = 0; error = null
                        })
                        MsButton("📒 去错题本", { onDone() })
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Primary,
    unfocusedBorderColor = Color(0xFFF0EDE8),
)

// ---------- 图片处理与识别管线 ----------

private suspend fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }

private suspend fun handleImage(
    app: App,
    setBitmap: (Bitmap?) -> Unit,
    setImagePath: (String?) -> Unit,
    raw: Bitmap?,
    onError: (String, String) -> Unit,
    setStep: (String) -> Unit,
    onDrafts: (List<DraftQuestion>) -> Unit,
) {
    if (raw == null) {
        onError("upload", "图片读取失败，换一张试试")
        return
    }
    setStep("recognizing")
    try {
        val dataUrl = withContext(Dispatchers.IO) { compressToDataUrl(raw) }
        setBitmap(raw)
        val savedPath = withContext(Dispatchers.IO) {
            val dir = File(app.filesDir, "uploads").apply { mkdirs() }
            val f = File(dir, "img_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { raw.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            f.name
        }
        setImagePath(savedPath)
        val drafts = withContext(Dispatchers.IO) { app.pipeline.recognizeDraft(dataUrl) }
        if (drafts.isEmpty()) {
            onError("upload", "AI 没有识别出题目，试试把题拍得更清楚、光线更亮一些")
        } else {
            onDrafts(drafts)
        }
    } catch (e: Exception) {
        onError("upload", e.message ?: "识别失败")
    }
}

private fun compressToDataUrl(src: Bitmap): String {
    val max = 1600
    val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
    val bmp = if (scale < 1f) {
        Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    } else src
    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
    val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}

// ---------- 裁剪步骤：图像像素坐标裁剪框，拖动移动 / 四角缩放 ----------

private const val MIN_CROP_PX = 60f // 最小裁剪边长（图像像素）

private fun defaultCrop(bmp: Bitmap): FloatArray {
    val mx = bmp.width * 0.05f
    val my = bmp.height * 0.05f
    return floatArrayOf(mx, my, bmp.width - mx, bmp.height - my)
}

private fun cropBitmap(bmp: Bitmap, r: FloatArray): Bitmap {
    val l = r[0].toInt().coerceIn(0, bmp.width - 2)
    val t = r[1].toInt().coerceIn(0, bmp.height - 2)
    val rr = r[2].toInt().coerceIn(l + 1, bmp.width)
    val bb = r[3].toInt().coerceIn(t + 1, bmp.height)
    return Bitmap.createBitmap(bmp, l, t, rr - l, bb - t)
}

@Composable
private fun CropOverlay(bitmap: Bitmap, rect: FloatArray, onRectChange: (FloatArray) -> Unit) {
    val density = LocalDensity.current
    val rectState = androidx.compose.runtime.rememberUpdatedState(rect)
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 图像在容器内 ContentScale.Fit 的显示参数（居中留边）
    val scale = if (viewSize.width > 0 && viewSize.height > 0)
        min(viewSize.width.toFloat() / bitmap.width, viewSize.height.toFloat() / bitmap.height) else 1f
    val dispW = bitmap.width * scale
    val dispH = bitmap.height * scale
    val offX = (viewSize.width - dispW) / 2f
    val offY = (viewSize.height - dispH) / 2f
    val boxW = with(density) { ((rect[2] - rect[0]) * scale).toDp() }
    val boxH = with(density) { ((rect[3] - rect[1]) * scale).toDp() }
    val handle = 28.dp
    val handleOffset = (-14).dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF33303B))
            .onSizeChanged { viewSize = it },
    ) {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

        if (viewSize.width > 0 && viewSize.height > 0) {
            // 裁剪框外暗角
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val dim = Color(0x9933303B)
                        val l = offX + rectState.value[0] * scale
                        val t = offY + rectState.value[1] * scale
                        val rr = offX + rectState.value[2] * scale
                        val bb = offY + rectState.value[3] * scale
                        drawRect(dim, Offset(0f, 0f), Size(size.width, t.coerceAtLeast(0f)))
                        drawRect(dim, Offset(0f, bb), Size(size.width, (size.height - bb).coerceAtLeast(0f)))
                        drawRect(dim, Offset(0f, t), Size(l.coerceAtLeast(0f), bb - t))
                        drawRect(dim, Offset(rr, t), Size((size.width - rr).coerceAtLeast(0f), bb - t))
                    },
            )
            // 裁剪框：整体拖动
            Box(
                Modifier
                    .offset { IntOffset((offX + rectState.value[0] * scale).toInt(), (offY + rectState.value[1] * scale).toInt()) }
                    .size(boxW, boxH)
                    .border(2.dp, C.Primary)
                    .pointerInput(bitmap, scale) {
                        detectDragGestures { _, drag ->
                            val r = rectState.value
                            val w = r[2] - r[0]
                            val h = r[3] - r[1]
                            val nl = (r[0] + drag.x / scale).coerceIn(0f, bitmap.width - w)
                            val nt = (r[1] + drag.y / scale).coerceIn(0f, bitmap.height - h)
                            onRectChange(floatArrayOf(nl, nt, nl + w, nt + h))
                        }
                    },
            ) {
                // 三分构图线
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val line = Color.White.copy(alpha = 0.4f)
                            drawLine(line, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1f)
                            drawLine(line, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1f)
                            drawLine(line, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), 1f)
                            drawLine(line, Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), 1f)
                        },
                )
                // 四角缩放手柄（子节点优先获得手势）
                listOf("nw", "ne", "sw", "se").forEach { corner ->
                    val align = when (corner) {
                        "nw" -> Alignment.TopStart
                        "ne" -> Alignment.TopEnd
                        "sw" -> Alignment.BottomStart
                        else -> Alignment.BottomEnd
                    }
                    Box(
                        Modifier
                            .align(align)
                            .offset(handleOffset, handleOffset)
                            .size(handle)
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .border(3.dp, C.Primary, RoundedCornerShape(4.dp))
                            .pointerInput(bitmap, scale, corner) {
                                detectDragGestures { _, drag ->
                                    val r = rectState.value
                                    val dx = drag.x / scale
                                    val dy = drag.y / scale
                                    val out = when (corner) {
                                        "nw" -> floatArrayOf(
                                            (r[0] + dx).coerceIn(0f, r[2] - MIN_CROP_PX),
                                            (r[1] + dy).coerceIn(0f, r[3] - MIN_CROP_PX),
                                            r[2], r[3],
                                        )
                                        "ne" -> floatArrayOf(
                                            r[0], r[1],
                                            (r[2] + dx).coerceIn(r[0] + MIN_CROP_PX, bitmap.width.toFloat()),
                                            r[3],
                                        )
                                        "sw" -> floatArrayOf(
                                            (r[0] + dx).coerceIn(0f, r[2] - MIN_CROP_PX),
                                            r[1], r[2],
                                            (r[3] + dy).coerceIn(r[1] + MIN_CROP_PX, bitmap.height.toFloat()),
                                        )
                                        else -> floatArrayOf(
                                            r[0], r[1],
                                            (r[2] + dx).coerceIn(r[0] + MIN_CROP_PX, bitmap.width.toFloat()),
                                            (r[3] + dy).coerceIn(r[1] + MIN_CROP_PX, bitmap.height.toFloat()),
                                        )
                                    }
                                    onRectChange(out)
                                }
                            },
                    )
                }
            }
        }
    }
}
