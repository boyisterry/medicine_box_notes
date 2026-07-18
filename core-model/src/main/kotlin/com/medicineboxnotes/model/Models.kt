package com.medicineboxnotes.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Serializable enum class AttachmentType { MEDICAL_RECORD, PRESCRIPTION, EXAMINATION, OTHER }
@Serializable enum class MedicineScanType { FRONT, BACK, SIDE, INSTRUCTION, OTHER }

@Serializable
data class FamilyMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationship: String,
    val emoji: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class Prescription(
    val id: String = UUID.randomUUID().toString(),
    val recordId: String,
    val medicineName: String,
    val dosage: String = "",
    val frequency: String = "",
    val durationDays: Int = 0,
    val note: String = "",
)

@Serializable
data class RecordAttachment(
    val id: String = UUID.randomUUID().toString(),
    val recordId: String,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val ocrText: String = "",
    val aiSummary: String = "",
    val aiStructuredJson: String = "",
    val aiSearchableText: String = "",
    val aiProviderRaw: String = "",
    val type: AttachmentType = AttachmentType.OTHER,
)

@Serializable
data class MedicalRecord(
    val id: String = UUID.randomUUID().toString(),
    val memberId: String?,
    val visitDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val hospitalName: String = "",
    val department: String = "",
    val doctorName: String = "",
    val chiefComplaint: String = "",
    val diagnosis: String = "",
    val doctorAdvice: String = "",
    val followUpNote: String = "",
    val followUpDateEpochDay: Long? = null,
)

@Serializable
data class MedicineItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String = "",
    val frequency: String = "",
    val durationDays: Int = 0,
    val note: String = "",
    val aiSummary: String = "",
    val aggregatedOcrText: String = "",
    val stock: Int = 0,
    val sourceRecordId: String? = null,
    val sourcePrescriptionId: String? = null,
    val sourceHospital: String = "",
    val sourceVisitDateEpochDay: Long? = null,
    val sourceMemberId: String? = null,
    val sourceMemberName: String = "",
    val scheduledTimes: List<String> = emptyList(),
    val planStartEpochDay: Long? = null,
    val planEndEpochDay: Long? = null,
    val isActive: Boolean = false,
)

@Serializable
data class MedicationLog(
    val id: String = UUID.randomUUID().toString(),
    val medicineId: String,
    val memberId: String? = null,
    val memberName: String = "",
    val scheduledTime: String,
    val scheduledDateEpochDay: Long,
    val takenAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class MedicineScanAsset(
    val id: String = UUID.randomUUID().toString(),
    val medicineId: String,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val ocrText: String = "",
    val type: MedicineScanType = MedicineScanType.OTHER,
)

data class MedicalRecordBundle(
    val record: MedicalRecord,
    val member: FamilyMember?,
    val prescriptions: List<Prescription>,
    val attachments: List<RecordAttachment>,
)

data class TodayMedication(
    val medicine: MedicineItem,
    val scheduledTime: LocalTime,
    val taken: Boolean,
)

fun MedicineItem.estimatedStock(): Int {
    val doses = when {
        frequency.contains("三") || frequency.contains("3") -> 3
        frequency.contains("两") || frequency.contains("二") || frequency.contains("2") -> 2
        frequency.contains("四") || frequency.contains("4") -> 4
        frequency.isBlank() -> 0
        else -> 1
    }
    return (doses * durationDays).coerceAtLeast(0)
}

fun MedicineItem.isScheduledOn(day: LocalDate): Boolean {
    if (!isActive || scheduledTimes.isEmpty()) return false
    val epoch = day.toEpochDay()
    return (planStartEpochDay == null || epoch >= planStartEpochDay) &&
        (planEndEpochDay == null || epoch <= planEndEpochDay)
}
