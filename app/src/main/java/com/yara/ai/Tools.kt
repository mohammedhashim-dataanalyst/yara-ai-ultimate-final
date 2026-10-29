package com.yara.ai

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object CsvAnalyzer {
    private fun split(line: String, d: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false
        for (ch in line) {
            when {
                ch == '"' -> q = !q
                ch == d && !q -> { out.add(sb.toString().trim()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString().trim()); return out
    }

    fun summarize(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return "File looks empty."
        val d = listOf(',', ';', '\t', '|').maxByOrNull { c -> lines.first().count { it == c } } ?: ','
        val head = split(lines.first(), d)
        val rows = lines.drop(1).take(50000).map { split(it, d) }
        val sb = StringBuilder("Rows: ${rows.size}, Columns: ${head.size}\n")
        head.forEachIndexed { ci, name ->
            val vals = rows.mapNotNull { it.getOrNull(ci) }.filter { it.isNotBlank() }
            val nums = vals.mapNotNull { it.replace(",", "").toDoubleOrNull() }
            sb.append("\n• ").append(name).append(": ${vals.size} filled, ${rows.size - vals.size} missing")
            if (vals.isNotEmpty() && nums.size >= vals.size * 0.7) {
                val s = nums.sorted()
                sb.append(" | numeric min=%.2f max=%.2f mean=%.2f median=%.2f".format(Locale.US, s.first(), s.last(), s.average(), s[s.size / 2]))
            } else {
                val top = vals.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
                sb.append(" | ${vals.toSet().size} unique, top: " + top.joinToString { "${it.key} (${it.value})" })
            }
        }
        return sb.toString()
    }
}

object CvPdf {
    fun make(ctx: Context, text: String): Uri {
        val doc = PdfDocument()
        val w = 595; val h = 842; val m = 40; val cw = w - 2 * m
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF222222.toInt() }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = 0xFFB0245F.toInt() }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var y = m.toFloat()
        for (raw in text.lines()) {
            val isHead = raw.trim().startsWith("#") || (raw.trim().length in 3..40 && raw.trim() == raw.trim().uppercase() && raw.any { it.isLetter() })
            val line = raw.replace("**", "").replace(Regex("^#+\\s*"), "").replace(Regex("^\\s*[-*]\\s+"), "• ")
            val paint = if (isHead) head else body
            val sl = StaticLayout.Builder.obtain(line.ifBlank { " " }, 0, line.ifBlank { " " }.length, paint, cw)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (y + sl.height > h - m) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); y = m.toFloat()
            }
            page.canvas.save(); page.canvas.translate(m.toFloat(), y); sl.draw(page.canvas); page.canvas.restore()
            y += sl.height + if (isHead) 6 else 2
        }
        doc.finishPage(page)
        val dir = File(ctx.cacheDir, "cv"); dir.mkdirs()
        val f = File(dir, "Yara_CV.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }
}
