package com.mistakemonsters.app.logic

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mistakemonsters.app.data.PracticeItem
import com.mistakemonsters.app.data.Profile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== 同类题练习卷 PDF 生成（端上 PdfDocument，A4 分页，中文字体用系统字）=====

object WorksheetPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val BOTTOM_SAFE = PAGE_H - 52f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    private fun paint(size: Float, bold: Boolean, color: Int = Color.rgb(34, 34, 34)): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            isFakeBoldText = bold
        }

    private fun layout(text: String, p: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1f)
            .build()

    private fun stars(n: Int): String {
        val k = n.coerceIn(1, 5)
        return "★".repeat(k) + "☆".repeat(5 - k)
    }

    class Doc {
        val pdf = PdfDocument()
        var pageNo = 1
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = MARGIN + 8f
        var title = ""

        fun start() {
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
        }

        fun newPage() {
            drawHeaderFooter()
            pdf.finishPage(page)
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = MARGIN + 4f
        }

        fun drawHeaderFooter() {
            if (pageNo > 1) {
                canvas.drawText(title, PAGE_W - MARGIN, 26f, paint(9f, false, Color.rgb(150, 150, 150)).apply { textAlign = Paint.Align.RIGHT })
            }
            canvas.drawText("由「错题小怪兽」生成 · 巩固错题，每天进步一点点", MARGIN, PAGE_H - 26f, paint(9f, false, Color.rgb(150, 150, 150)))
            canvas.drawText("$pageNo 页", PAGE_W - MARGIN, PAGE_H - 26f, paint(9f, false, Color.rgb(150, 150, 150)).apply { textAlign = Paint.Align.RIGHT })
        }

        fun finish() {
            drawHeaderFooter()
            pdf.finishPage(page)
        }
    }

    fun build(items: List<PracticeItem>, profile: Profile, title: String, withAnswers: Boolean): PdfDocument {
        val d = Doc()
        d.title = title.ifBlank { "同类题练习卷" }
        d.start()

        // ===== 标题块 =====
        d.canvas.drawText(d.title, MARGIN, d.y + 16f, paint(17f, true, Color.rgb(91, 63, 228)))
        d.y += 30f
        d.canvas.drawText(
            "${profile.name}（${profile.grade} 年级）  日期：______  用时：______  对了______题",
            MARGIN, d.y + 4f, paint(10f, false, Color.rgb(85, 85, 85)),
        )
        d.y += 22f
        d.canvas.drawText("这些题针对你最近的错题而出——先自己完成，再和家长核对答案页。", MARGIN, d.y + 4f, paint(9f, false, Color.rgb(122, 122, 122)))
        d.y += 26f

        // ===== 题目（每题题干+答题框不可跨页）=====
        items.forEachIndexed { idx, it ->
            val stemPaint = paint(11f, false)
            val bodyLayout = layout(it.stem, stemPaint, CONTENT_W - 62f)
            val boxH = if (it.stem.length > 40) 74f else 58f
            val blockH = 18f + bodyLayout.height + boxH + 14f
            if (d.y + blockH > BOTTOM_SAFE) d.newPage()

            d.canvas.drawText("${idx + 1}.", MARGIN, d.y + 12f, paint(11f, true))
            d.canvas.drawText(
                "${stars(it.difficulty)}" + (if (it.variant == "变式") " · 变式" else ""),
                MARGIN + 22f, d.y + 11f, paint(9f, false, Color.rgb(245, 166, 35)),
            )
            d.y += 18f
            d.canvas.save()
            d.canvas.translate(MARGIN + 62f, d.y)
            bodyLayout.draw(d.canvas)
            d.canvas.restore()
            d.y += bodyLayout.height + 6f
            val box = RectF(MARGIN + 62f, d.y, PAGE_W - MARGIN, d.y + boxH)
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Color.rgb(200, 200, 200)
            }
            d.canvas.drawRoundRect(box, 8f, 8f, boxPaint)
            d.y = box.bottom + 14f
        }
        if (d.y + 30f > BOTTOM_SAFE) d.newPage()
        d.canvas.drawText("做完后翻到最后一页核对答案。答错了没关系，把新的发现告诉爸爸妈妈或 AI 小老师。", MARGIN, d.y + 10f, paint(9f, false, Color.rgb(122, 122, 122)))

        // ===== 答案页 =====
        if (withAnswers) {
            d.newPage()
            d.canvas.drawText("参考答案与解析（给家长）", MARGIN, d.y + 16f, paint(17f, true, Color.rgb(91, 63, 228)))
            d.y += 36f
            items.forEachIndexed { idx, it ->
                val stemLayout = layout(it.stem, paint(9.5f, false, Color.rgb(85, 85, 85)), CONTENT_W - 20f)
                val solLayout = layout("解析：" + it.solution, paint(9.5f, false, Color.rgb(102, 102, 102)), CONTENT_W - 20f)
                val blockH = 14f + stemLayout.height + 22f + solLayout.height + 12f
                if (d.y + blockH > BOTTOM_SAFE) d.newPage()

                d.canvas.drawText("${idx + 1}.", MARGIN, d.y + 10f, paint(11f, true))
                d.canvas.save()
                d.canvas.translate(MARGIN + 20f, d.y)
                stemLayout.draw(d.canvas)
                d.canvas.restore()
                d.y += stemLayout.height + 6f
                d.canvas.drawText("答案：" + it.answer + (if (it.targetedCause.isNotBlank()) "　（针对错因：" + it.targetedCause + "）" else ""), MARGIN + 20f, d.y + 10f, paint(11f, true))
                d.y += 18f
                d.canvas.save()
                d.canvas.translate(MARGIN + 20f, d.y)
                solLayout.draw(d.canvas)
                d.canvas.restore()
                d.y += solLayout.height + 12f
            }
        }

        d.finish()
        return d.pdf
    }

    /** 保存到系统下载目录（API 29+ 走 MediaStore），返回给用户的提示文本 */
    fun save(context: Context, doc: PdfDocument, fileName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "已保存到 下载/$fileName"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val f = File(dir, fileName)
            FileOutputStream(f).use { doc.writeTo(it) }
            return "已保存到 ${f.absolutePath}"
        }
    }
}
