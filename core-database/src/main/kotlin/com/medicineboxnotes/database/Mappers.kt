package com.medicineboxnotes.database

import com.medicineboxnotes.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun FamilyMemberEntity.model() = FamilyMember(id, name, relationship, emoji, createdAtEpochMillis)
fun FamilyMember.entity() = FamilyMemberEntity(id, name, relationship, emoji, createdAtEpochMillis)
fun MedicalRecordEntity.model() = MedicalRecord(id, memberId, visitDateEpochDay, hospitalName, department, doctorName, chiefComplaint, diagnosis, doctorAdvice, followUpNote, followUpDateEpochDay)
fun MedicalRecord.entity() = MedicalRecordEntity(id, memberId, visitDateEpochDay, hospitalName, department, doctorName, chiefComplaint, diagnosis, doctorAdvice, followUpNote, followUpDateEpochDay)
fun PrescriptionEntity.model() = Prescription(id, recordId, medicineName, dosage, frequency, durationDays, note)
fun Prescription.entity() = PrescriptionEntity(id, recordId, medicineName, dosage, frequency, durationDays, note)
fun RecordAttachmentEntity.model() = RecordAttachment(id, recordId, imagePath, thumbnailPath, ocrText, aiSummary, aiStructuredJson, aiSearchableText, aiProviderRaw, AttachmentType.valueOf(type))
fun RecordAttachment.entity() = RecordAttachmentEntity(id, recordId, imagePath, thumbnailPath, ocrText, aiSummary, aiStructuredJson, aiSearchableText, aiProviderRaw, type.name)
fun MedicineEntity.model() = MedicineItem(id, name, dosage, frequency, durationDays, note, aiSummary, aggregatedOcrText, stock, sourceRecordId, sourcePrescriptionId, sourceHospital, sourceVisitDateEpochDay, sourceMemberId, sourceMemberName, json.decodeFromString(scheduledTimesJson), planStartEpochDay, planEndEpochDay, isActive)
fun MedicineItem.entity() = MedicineEntity(id, name, dosage, frequency, durationDays, note, aiSummary, aggregatedOcrText, stock, sourceRecordId, sourcePrescriptionId, sourceHospital, sourceVisitDateEpochDay, sourceMemberId, sourceMemberName, json.encodeToString(scheduledTimes), planStartEpochDay, planEndEpochDay, isActive)
fun MedicationLogEntity.model() = MedicationLog(id, medicineId, memberId, memberName, scheduledTime, scheduledDateEpochDay, takenAtEpochMillis)
fun MedicationLog.entity() = MedicationLogEntity(id, medicineId, memberId, memberName, scheduledTime, scheduledDateEpochDay, takenAtEpochMillis)
fun MedicineScanEntity.model() = MedicineScanAsset(id, medicineId, imagePath, thumbnailPath, ocrText, MedicineScanType.valueOf(type))
fun MedicineScanAsset.entity() = MedicineScanEntity(id, medicineId, imagePath, thumbnailPath, ocrText, type.name)
