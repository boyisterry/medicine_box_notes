package com.medicineboxnotes.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

class GemmaMedicalAiClient(
    private val modelFile: File,
    private val cacheDir: File,
    private val fallback: MedicalAiClient = RuleBasedMedicalAiClient(),
) : MedicalAiClient, AutoCloseable {
    private val mutex = Mutex()
    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gemma-inference").apply { isDaemon = true }
    }
    private val inferenceDispatcher = inferenceExecutor.asCoroutineDispatcher()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var engine: Engine? = null
    @Volatile private var cancelled = false

    private suspend fun engine(): Engine = engine ?: mutex.withLock {
        engine ?: initialize(Backend.GPU()).getOrElse { initialize(Backend.CPU()).getOrThrow() }.also { engine = it }
    }

    private suspend fun initialize(backend: Backend): Result<Engine> = try {
        Result.success(Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = backend, cacheDir = cacheDir.absolutePath)).also { it.initialize() })
    } catch (error: Throwable) { Result.failure(error) }

    override suspend fun extractMedicine(ocrText: String): MedicineExtraction = runCatching {
        generateJson<MedicineExtraction>(
            """从以下 OCR 提取药品信息。严格只输出一个 JSON 对象，键名和类型必须为：
                |{"name":null,"dosage":null,"frequency":null,"durationDays":null,"note":null,"summary":null,"warnings":[]}
                |无法从 OCR 确认的字段保持 null，禁止猜测。OCR：
                |$ocrText
            """.trimMargin(),
        )
    }.getOrElse { fallback.extractMedicine(ocrText) }

    override suspend fun visionExtractMedicine(imagePaths: List<String>, hintOcrText: String): MedicineExtraction =
        extractMedicine(hintOcrText) // Gemma 4 image content support is isolated for a later model capability probe.

    override suspend fun analyzeAttachment(ocrText: String, attachmentType: String): AttachmentAnalysis = runCatching {
        generateJson<AttachmentAnalysis>(
            """整理类型为 $attachmentType 的附件，只依据 OCR。严格只输出一个 JSON 对象，键名和类型必须为：
                |{"summary":null,"hospitalName":null,"department":null,"doctorName":null,"chiefComplaint":null,"diagnosis":null,"doctorAdvice":null,"medicines":[],"warnings":[]}
                |无法确认的字段保持 null，禁止猜测。OCR：
                |$ocrText
            """.trimMargin(),
        )
    }.getOrElse { fallback.analyzeAttachment(ocrText, attachmentType) }

    override fun answerStreaming(query: String, scope: String): Flow<AiStreamEvent> = flow {
        cancelled = false
        try {
            val activeEngine = engine()
            mutex.withLock {
                val conversation = activeEngine.createConversation(ConversationConfig(systemInstruction = Contents.of(MedicalPrompting.systemRules)))
                val raw = StringBuilder()
                conversation.use {
                    it.sendMessageAsync(
                        """问题：$query
                            |本地上下文：
                            |$scope
                            |严格只输出一个 JSON 对象，键名和类型必须为：
                            |{"answer":"回答文本","grounded":true,"citations":[{"id":"上下文中的 UUID","title":"来源标题","kind":"Medicine 或 Record"}]}
                            |每个事实必须有对应引用；没有证据时 answer 只能是“没有”或“不知道”，citations 必须为空数组。
                        """.trimMargin(),
                    )
                        .collect { chunk ->
                            if (cancelled) throw CancellationException("cancelled")
                            val text = chunk.textContent()
                            raw.append(text)
                        }
                }
                emit(AiStreamEvent.Finished(sanitizeAnswer(decode<QueryAnswer>(raw.toString()))))
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Throwable) { fallback.answerStreaming(query, scope).collect { emit(it) } }
    }.flowOn(inferenceDispatcher)

    private suspend inline fun <reified T> generateJson(prompt: String): T = withContext(inferenceDispatcher) {
        val activeEngine = engine()
        mutex.withLock {
            activeEngine.createConversation(ConversationConfig(systemInstruction = Contents.of(MedicalPrompting.systemRules))).use {
                decode(it.sendMessage(prompt).textContent())
            }
        }
    }

    private fun Message.textContent(): String = contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString(separator = "") { it.text }

    private inline fun <reified T> decode(raw: String): T {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "模型未返回 JSON" }
        return json.decodeFromString(raw.substring(start, end + 1))
    }

    private fun sanitizeAnswer(value: QueryAnswer): QueryAnswer {
        val answer = value.answer.replace(Regex("\\[(?:Medicine|Record) [^]]+]"), "").trim()
        if (value.citations.isEmpty() && !(value.grounded && (answer.startsWith("没有") || answer.startsWith("不知道")))) {
            throw IllegalStateException("无引用的不安全回答")
        }
        return value.copy(answer = answer)
    }

    override fun cancel() { cancelled = true }
    override fun close() {
        inferenceExecutor.execute {
            engine?.close()
            engine = null
            inferenceDispatcher.close()
        }
    }
}
