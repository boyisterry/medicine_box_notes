package com.medicineboxnotes.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relationship: String,
    val emoji: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "medical_records",
    foreignKeys = [ForeignKey(
        entity = FamilyMemberEntity::class,
        parentColumns = ["id"], childColumns = ["memberId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("memberId"), Index("visitDateEpochDay")],
)
data class MedicalRecordEntity(
    @PrimaryKey val id: String,
    val memberId: String?,
    val visitDateEpochDay: Long,
    val hospitalName: String,
    val department: String,
    val doctorName: String,
    val chiefComplaint: String,
    val diagnosis: String,
    val doctorAdvice: String,
    val followUpNote: String,
    val followUpDateEpochDay: Long?,
)

@Entity(
    tableName = "prescriptions",
    foreignKeys = [ForeignKey(
        entity = MedicalRecordEntity::class,
        parentColumns = ["id"], childColumns = ["recordId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recordId")],
)
data class PrescriptionEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val medicineName: String,
    val dosage: String,
    val frequency: String,
    val durationDays: Int,
    val note: String,
)

@Entity(
    tableName = "record_attachments",
    foreignKeys = [ForeignKey(
        entity = MedicalRecordEntity::class,
        parentColumns = ["id"], childColumns = ["recordId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recordId")],
)
data class RecordAttachmentEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val imagePath: String,
    val thumbnailPath: String?,
    val ocrText: String,
    val aiSummary: String,
    val aiStructuredJson: String,
    val aiSearchableText: String,
    val aiProviderRaw: String,
    val type: String,
)

@Entity(indices = [Index("sourcePrescriptionId"), Index("sourceMemberId")], tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String,
    val frequency: String,
    val durationDays: Int,
    val note: String,
    val aiSummary: String,
    val aggregatedOcrText: String,
    val stock: Int,
    val sourceRecordId: String?,
    val sourcePrescriptionId: String?,
    val sourceHospital: String,
    val sourceVisitDateEpochDay: Long?,
    val sourceMemberId: String?,
    val sourceMemberName: String,
    val scheduledTimesJson: String,
    val planStartEpochDay: Long?,
    val planEndEpochDay: Long?,
    val isActive: Boolean,
)

@Entity(tableName = "medication_logs", indices = [Index("medicineId"), Index("scheduledDateEpochDay")])
data class MedicationLogEntity(
    @PrimaryKey val id: String,
    val medicineId: String,
    val memberId: String?,
    val memberName: String,
    val scheduledTime: String,
    val scheduledDateEpochDay: Long,
    val takenAtEpochMillis: Long,
)

@Entity(
    tableName = "medicine_scans",
    foreignKeys = [ForeignKey(
        entity = MedicineEntity::class,
        parentColumns = ["id"], childColumns = ["medicineId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("medicineId")],
)
data class MedicineScanEntity(
    @PrimaryKey val id: String,
    val medicineId: String,
    val imagePath: String,
    val thumbnailPath: String?,
    val ocrText: String,
    val type: String,
)
