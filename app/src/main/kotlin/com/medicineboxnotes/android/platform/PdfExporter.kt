package com.medicineboxnotes.android.platform

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.medicineboxnotes.model.MedicalRecordBundle
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PdfExporter(private val context: Context) {
    fun export(bundle: MedicalRecordBundle): android.net.Uri {
        val document = PdfDocument(); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(42, 37, 32); textSize = 14f }
        var pageNumber = 0; var page: PdfDocument.Page? = null; var y = 0f
        fun nextPage() {
            page?.let(document::finishPage); pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()); y = 54f
        }
        fun line(text: String, title: Boolean = false) {
            if (page == null || y > 790) nextPage()
            paint.typeface = if (title) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; paint.textSize = if (title) 19f else 13f
            text.chunked(if (title) 28 else 44).forEach { chunk -> if (y > 790) nextPage(); page!!.canvas.drawText(chunk, 45f, y, paint); y += if (title) 28f else 21f }
        }
        line("家庭病历 · ${bundle.member?.name.orEmpty()}", true)
        line("就诊日期：${LocalDate.ofEpochDay(bundle.record.visitDateEpochDay)}")
        line("医院：${bundle.record.hospitalName}  科室：${bundle.record.department}")
        line("医生：${bundle.record.doctorName}"); y += 10
        line("诊断信息", true); line(bundle.record.diagnosis.ifBlank { "未填写" })
        line("医嘱", true); line(bundle.record.doctorAdvice.ifBlank { "未填写" })
        line("处方", true); bundle.prescriptions.forEach { line("${it.medicineName}  ${it.dosage}  ${it.frequency}  ${it.durationDays} 天") }
        page?.let(document::finishPage)
        val dir = context.cacheDir.resolve("exports").apply { mkdirs() }
        val file = File(dir, "病历-${bundle.member?.name ?: "未归属"}-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.pdf")
        file.outputStream().use(document::writeTo); document.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
