package com.medicineboxnotes.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class MedicineExtraction(
    val name: String? = null,
    val dosage: String? = null,
    val frequency: String? = null,
    val durationDays: Int? = null,
    val note: String? = null,
    val summary: String? = null,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AttachmentAnalysis(
    val summary: String? = null,
    val hospitalName: String? = null,
    val department: String? = null,
    val doctorName: String? = null,
    val chiefComplaint: String? = null,
    val diagnosis: String? = null,
    val doctorAdvice: String? = null,
    val medicines: List<MedicineExtraction> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable data class Citation(val id: String, val title: String, val kind: String)
@Serializable data class QueryAnswer(val answer: String, val grounded: Boolean, val citations: List<Citation>)

sealed interface AiStreamEvent {
    data class Token(val text: String) : AiStreamEvent
    data class Finished(val answer: QueryAnswer) : AiStreamEvent
}

interface MedicalAiClient {
    suspend fun extractMedicine(ocrText: String): MedicineExtraction
    suspend fun visionExtractMedicine(imagePaths: List<String>, hintOcrText: String): MedicineExtraction
    suspend fun analyzeAttachment(ocrText: String, attachmentType: String): AttachmentAnalysis
    fun answerStreaming(query: String, scope: String): Flow<AiStreamEvent>
    fun cancel()
}

object MedicalPrompting {
    val systemRules = """
        你是家庭医疗记录整理器。必须遵守：
        1. 只能依据用户提供的 OCR、本地记录和上下文。
        2. 禁止用医学常识或猜测补全缺失事实。
        3. 没有证据回答“没有”，无法确定回答“不知道”。
        4. 冲突、残缺或歧义必须说明需要核对原图。
        5. 只整理记录，不提供诊断、治疗或用药建议。
        6. 所有结论尽量引用来源。
        7. 只输出请求的 JSON，不附加 Markdown。
    """.trimIndent()
}
