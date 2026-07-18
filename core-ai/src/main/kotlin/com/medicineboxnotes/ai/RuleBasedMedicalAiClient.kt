package com.medicineboxnotes.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RuleBasedMedicalAiClient : MedicalAiClient {
    private val dosage = Regex("(?:每次|剂量|用量)[:：\\s]*([0-9.]+\\s*(?:mg|g|ml|片|粒|袋|支))", RegexOption.IGNORE_CASE)
    private val frequency = Regex("((?:每日|一天|每晚|每晨)[一二两三四五六七八九0-9]+次|每[0-9]+小时[一二两三四0-9]*次)")
    private val duration = Regex("(?:疗程|连用|服用)[:：\\s]*([0-9一二三四五六七八九十]+)\\s*天")
    private val metadata = Regex("^(药盒正面|药盒反面|药盒侧面|说明书|其他)$")

    override suspend fun extractMedicine(ocrText: String): MedicineExtraction {
        val lines = ocrText.lines().map { it.trim() }.filter { it.length > 1 && !metadata.matches(it) }
        val name = lines.firstOrNull { line ->
            listOf("片", "胶囊", "颗粒", "口服液", "注射液", "丸", "药").any(line::contains) &&
                !line.contains("用法") && !line.contains("说明书")
        }
        return sanitize(MedicineExtraction(
            name = name,
            dosage = dosage.find(ocrText)?.groupValues?.getOrNull(1),
            frequency = frequency.find(ocrText)?.value,
            durationDays = duration.find(ocrText)?.groupValues?.getOrNull(1)?.toChineseNumber(),
            summary = name?.let { "从 OCR 文本整理：$it" },
            warnings = if (name == null) listOf("无法确认是药品信息，请核对原图") else emptyList(),
        ))
    }

    override suspend fun visionExtractMedicine(imagePaths: List<String>, hintOcrText: String) = extractMedicine(hintOcrText)

    override suspend fun analyzeAttachment(ocrText: String, attachmentType: String): AttachmentAnalysis {
        val meds = ocrText.lines().filter { it.contains("片") || it.contains("胶囊") }.take(8).map {
            MedicineExtraction(name = it.trim(), warnings = listOf("规则识别结果，需要核对原图"))
        }
        return AttachmentAnalysis(
            summary = ocrText.take(160).ifBlank { "没有可整理的 OCR 文本" },
            medicines = meds,
            warnings = listOf("当前使用规则模式，结构化结果可能不完整"),
        )
    }

    override fun answerStreaming(query: String, scope: String): Flow<AiStreamEvent> = flow {
        val tokens = tokenize(query)
        val snippets = scope.lines().filter { line -> tokens.any { it.length >= 2 && line.contains(it, true) } }.take(5)
        val result = if (snippets.isEmpty()) QueryAnswer("不知道，本地记录中没有找到可引用的信息。", true, emptyList())
        else QueryAnswer(
            "在本地记录中找到：\n${snippets.joinToString("\n") { it.replace(Regex("\\[(?:Medicine|Record) [^]]+]\\s*"), "") }}",
            true, listOf(Citation("local", "本地匹配记录", "record")),
        )
        emit(AiStreamEvent.Token(result.answer)); emit(AiStreamEvent.Finished(result))
    }

    override fun cancel() = Unit

    private fun tokenize(text: String) = buildSet {
        addAll(text.split(Regex("[\\s，。？！、：]+")))
        text.windowed(2).filter { it.none(Char::isWhitespace) }.forEach(::add)
    }

    private fun sanitize(value: MedicineExtraction): MedicineExtraction {
        fun String?.clean() = this?.trim()?.takeUnless { it.isBlank() || it in setOf("没有", "不知道", "null") }
        val cleaned = value.copy(name = value.name.clean(), dosage = value.dosage.clean(), frequency = value.frequency.clean(), note = value.note.clean(), summary = value.summary.clean())
        return if (cleaned.name == null && cleaned.warnings.isEmpty()) cleaned.copy(warnings = listOf("无法确认是药品信息")) else cleaned
    }

    private fun String.toChineseNumber(): Int? = toIntOrNull() ?: mapOf(
        "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4, "五" to 5,
        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
    )[this]
}
