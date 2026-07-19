package com.medicineboxnotes.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicineboxnotes.ai.AiStreamEvent
import com.medicineboxnotes.ai.MedicalAiClient
import com.medicineboxnotes.database.MedicineRepository
import com.medicineboxnotes.model.*
import com.medicineboxnotes.android.platform.ReminderScheduler
import com.medicineboxnotes.android.platform.BackupService
import android.net.Uri
import com.medicineboxnotes.ai.MlKitOcrService
import com.medicineboxnotes.android.platform.ImageStore
import com.medicineboxnotes.model.AttachmentType
import com.medicineboxnotes.model.MedicineScanType
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class QueryProgressStage { IDLE, THINKING, SEARCHING, ORGANIZING, STREAMING, COMPLETE }
data class QueryUiState(
    val running: Boolean = false,
    val stage: QueryProgressStage = QueryProgressStage.IDLE,
    val streamed: String = "",
    val answer: String = "",
    val error: String? = null,
)
enum class MediaProcessStage { SAVING, RECOGNIZING, SAVED, ERROR }
data class MediaProcessState(val stage: MediaProcessStage, val recognizedCharacters: Int = 0, val photoSaved: Boolean = false, val error: String? = null)
enum class MedicineAiMode { OCR, VISION }
enum class MedicineAiStage { RUNNING, COMPLETE, ERROR }
data class MedicineAiProcessState(
    val stage: MedicineAiStage,
    val mode: MedicineAiMode,
    val extraction: com.medicineboxnotes.ai.MedicineExtraction? = null,
    val sourceText: String = "",
    val error: String? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MedicineBoxApplication
    private val repository: MedicineRepository = app.repository
    private val ai: MedicalAiClient get() = app.aiClient
    val members = repository.members.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val records = repository.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val medicines = repository.medicines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val today = repository.today(LocalDate.now()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val modelState = app.modelDownloads.state
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage = _backupMessage.asStateFlow()
    private val _mediaStates = MutableStateFlow<Map<String, MediaProcessState>>(emptyMap())
    val mediaStates = _mediaStates.asStateFlow()
    private val _medicineAiStates = MutableStateFlow<Map<String, MedicineAiProcessState>>(emptyMap())
    val medicineAiStates = _medicineAiStates.asStateFlow()

    private val _query = MutableStateFlow(QueryUiState())
    val query: StateFlow<QueryUiState> = _query.asStateFlow()
    private var queryJob: Job? = null

    private val prefs = application.getSharedPreferences("medicine_box_settings", 0)
    private val _riskAcknowledged = MutableStateFlow(prefs.getBoolean("ai_risk_ack", false))
    val riskAcknowledged = _riskAcknowledged.asStateFlow()

    fun acknowledgeRisk(value: Boolean) {
        prefs.edit().putBoolean("ai_risk_ack", value).apply(); _riskAcknowledged.value = value
    }

    fun saveMember(member: FamilyMember) = viewModelScope.launch { repository.saveMember(member) }
    fun deleteMember(member: FamilyMember) = viewModelScope.launch { repository.deleteMember(member) }

    fun saveRecord(record: MedicalRecord, prescriptions: List<Prescription> = emptyList()) = viewModelScope.launch {
        repository.saveRecord(record, prescriptions)
        ReminderScheduler(getApplication()).update(record)
    }
    fun deleteRecord(record: MedicalRecord) = viewModelScope.launch {
        repository.deleteRecord(record).forEach { path -> runCatching { getApplication<Application>().filesDir.resolve(path).delete() } }
        ReminderScheduler(getApplication()).cancel(record.id)
    }
    suspend fun recordBundle(id: String) = repository.recordBundle(id)
    fun observeRecordBundle(id: String) = repository.observeRecordBundle(id)
    fun observeMedicineScans(id: String) = repository.observeScans(id)

    fun saveMedicine(medicine: MedicineItem) = viewModelScope.launch { repository.saveMedicine(medicine) }
    fun deleteMedicine(medicine: MedicineItem) = viewModelScope.launch { repository.deleteMedicine(medicine) }
    fun setTaken(item: TodayMedication, taken: Boolean) = viewModelScope.launch { repository.setTaken(item, LocalDate.now(), taken) }
    fun syncPrescription(record: MedicalRecord, member: FamilyMember?, prescription: Prescription) = viewModelScope.launch {
        repository.syncPrescription(record, member, prescription, MedicineItem(
            name = prescription.medicineName, frequency = prescription.frequency, durationDays = prescription.durationDays,
        ).estimatedStock())
    }

    fun addRecordAttachment(recordId: String, uri: Uri, type: AttachmentType = AttachmentType.OTHER) = viewModelScope.launch {
        updateMediaState(recordId, MediaProcessState(MediaProcessStage.SAVING))
        var photoSaved = false
        runCatching {
            val stored = withContext(Dispatchers.IO) { ImageStore(getApplication()).import(uri) }
            val attachment = RecordAttachment(recordId = recordId, imagePath = stored.imagePath, thumbnailPath = stored.thumbnailPath, type = type)
            repository.saveAttachment(attachment)
            photoSaved = true
            updateMediaState(recordId, MediaProcessState(MediaProcessStage.RECOGNIZING, photoSaved = true))
            val savedUri = Uri.fromFile(getApplication<Application>().filesDir.resolve(stored.imagePath))
            val ocr = MlKitOcrService(getApplication()).recognize(savedUri)
            val analysis = if (_riskAcknowledged.value) runCatching { ai.analyzeAttachment(ocr.text, type.name) }.getOrNull() else null
            repository.saveAttachment(attachment.copy(
                ocrText = ocr.text, aiSummary = analysis?.summary.orEmpty(),
                aiStructuredJson = analysis?.toString().orEmpty(), aiSearchableText = listOf(ocr.text, analysis?.summary).joinToString(" "),
            ))
            updateMediaState(recordId, MediaProcessState(MediaProcessStage.SAVED, ocr.text.length, photoSaved = true))
        }.onFailure { updateMediaState(recordId, MediaProcessState(MediaProcessStage.ERROR, photoSaved = photoSaved, error = it.message)) }
        cleanupCapture(uri)
    }

    fun addMedicineScan(medicine: MedicineItem, uri: Uri, type: MedicineScanType = MedicineScanType.OTHER) = viewModelScope.launch {
        if (repository.medicine(medicine.id) == null) repository.saveMedicine(medicine)
        updateMediaState(medicine.id, MediaProcessState(MediaProcessStage.SAVING))
        var photoSaved = false
        runCatching {
            val stored = withContext(Dispatchers.IO) { ImageStore(getApplication()).import(uri) }
            val scan = MedicineScanAsset(medicineId = medicine.id, imagePath = stored.imagePath, thumbnailPath = stored.thumbnailPath, type = type)
            repository.saveScan(scan)
            photoSaved = true
            updateMediaState(medicine.id, MediaProcessState(MediaProcessStage.RECOGNIZING, photoSaved = true))
            val savedUri = Uri.fromFile(getApplication<Application>().filesDir.resolve(stored.imagePath))
            val ocr = MlKitOcrService(getApplication()).recognize(savedUri)
            repository.saveScan(scan.copy(ocrText = ocr.text))
            val current = repository.medicine(medicine.id) ?: medicine
            repository.saveMedicine(current.copy(aggregatedOcrText = listOf(current.aggregatedOcrText, ocr.text).filter(String::isNotBlank).joinToString("\n")))
            updateMediaState(medicine.id, MediaProcessState(MediaProcessStage.SAVED, ocr.text.length, photoSaved = true))
        }.onFailure { updateMediaState(medicine.id, MediaProcessState(MediaProcessStage.ERROR, photoSaved = photoSaved, error = it.message)) }
        cleanupCapture(uri)
    }

    fun deleteMedicineScan(scan: MedicineScanAsset) = viewModelScope.launch {
        val paths = repository.deleteScan(scan)
        withContext(Dispatchers.IO) {
            paths.forEach { path -> runCatching { getApplication<Application>().filesDir.resolve(path).delete() } }
        }
        _medicineAiStates.update { it - scan.medicineId }
    }

    fun organizeMedicine(medicine: MedicineItem, useVision: Boolean = false) = viewModelScope.launch {
        if (!_riskAcknowledged.value) { _backupMessage.value = "请先在查询页确认 AI 使用风险"; return@launch }
        val mode = if (useVision) MedicineAiMode.VISION else MedicineAiMode.OCR
        _medicineAiStates.update { it + (medicine.id to MedicineAiProcessState(MedicineAiStage.RUNNING, mode)) }
        runCatching {
            val current = repository.medicine(medicine.id) ?: medicine
            val scans = repository.scans(medicine.id)
            val ocrText = scans.map { it.ocrText.trim() }.filter(String::isNotBlank).joinToString("\n")
                .ifBlank { current.aggregatedOcrText }
            val result = if (useVision) {
                val paths = scans.map { getApplication<Application>().filesDir.resolve(it.imagePath).absolutePath }
                ai.visionExtractMedicine(paths, ocrText)
            } else ai.extractMedicine(ocrText)
            repository.saveMedicine(current.copy(
                name = result.name ?: current.name,
                dosage = result.dosage ?: current.dosage,
                frequency = result.frequency ?: current.frequency,
                durationDays = result.durationDays ?: current.durationDays,
                note = result.note ?: current.note,
                aiSummary = result.summary ?: current.aiSummary,
                aggregatedOcrText = ocrText,
            ))
            _medicineAiStates.update {
                it + (medicine.id to MedicineAiProcessState(MedicineAiStage.COMPLETE, mode, result, ocrText))
            }
        }.onFailure { error ->
            _medicineAiStates.update {
                it + (medicine.id to MedicineAiProcessState(MedicineAiStage.ERROR, mode, error = error.message ?: "Unknown error"))
            }
        }
    }

    fun ask(question: String) {
        if (question.isBlank() || !_riskAcknowledged.value) return
        queryJob?.cancel(); ai.cancel()
        val scope = buildString {
            records.value.take(8).forEach { appendLine("[Record ${it.id}] ${it.hospitalName} ${it.department} ${it.diagnosis} ${it.doctorAdvice}") }
            medicines.value.take(8).forEach { appendLine("[Medicine ${it.id}] ${it.name} ${it.dosage} ${it.frequency} 库存${it.stock} ${it.aiSummary}") }
        }
        queryJob = viewModelScope.launch {
            _query.value = QueryUiState(running = true, stage = QueryProgressStage.THINKING)
            runCatching {
                coroutineScope {
                    val startedAt = System.currentTimeMillis()
                    val progressJob = launch {
                        delay(450)
                        if (isActive) _query.update { it.copy(stage = QueryProgressStage.SEARCHING) }
                        delay(750)
                        if (isActive) _query.update { it.copy(stage = QueryProgressStage.ORGANIZING) }
                    }
                    var finalAnswer: String? = null
                    ai.answerStreaming(question, scope).collect { event ->
                        // Gemma produces a JSON envelope while reasoning. Never expose those raw chunks.
                        if (event is AiStreamEvent.Finished) finalAnswer = event.answer.answer
                    }
                    val minimumTransitionMs = 1_300L
                    delay((minimumTransitionMs - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0L))
                    progressJob.cancelAndJoin()
                    val answer = finalAnswer.orEmpty()
                    _query.value = QueryUiState(running = true, stage = QueryProgressStage.STREAMING)
                    answer.chunked(2).forEach { chunk ->
                        _query.update { it.copy(streamed = it.streamed + chunk) }
                        delay(16)
                    }
                    _query.value = QueryUiState(stage = QueryProgressStage.COMPLETE, streamed = answer, answer = answer)
                }
            }.onFailure { _query.value = QueryUiState(error = it.message ?: "查询失败") }
        }
    }

    fun cancelQuery() { queryJob?.cancel(); ai.cancel(); _query.update { it.copy(running = false) } }
    fun clearQuery() { queryJob?.cancel(); ai.cancel(); _query.value = QueryUiState() }
    fun startModelDownload() = app.modelDownloads.start()
    fun pauseModelDownload() = app.modelDownloads.pause()
    fun deleteModel() = app.modelDownloads.delete()
    fun exportBackup(uri: Uri, password: String) = viewModelScope.launch {
        _backupMessage.value = "正在导出…"
        _backupMessage.value = runCatching { BackupService(getApplication(), app.database).export(uri, password.toCharArray()); "备份已导出" }.getOrElse { "导出失败：${it.message}" }
    }
    fun importBackup(uri: Uri, password: String) = viewModelScope.launch {
        _backupMessage.value = "正在导入…"
        _backupMessage.value = runCatching { BackupService(getApplication(), app.database).import(uri, password.toCharArray()); "备份已导入" }.getOrElse { "导入失败：${it.message}" }
    }
    fun clearBackupMessage() { _backupMessage.value = null }

    private fun updateMediaState(key: String, state: MediaProcessState) {
        _mediaStates.update { it + (key to state) }
    }

    private fun cleanupCapture(uri: Uri) {
        if (uri.scheme != "file") return
        val file = uri.path?.let { java.io.File(it) } ?: return
        if (file.parentFile == getApplication<Application>().cacheDir && file.name.startsWith("capture-")) file.delete()
    }
}
