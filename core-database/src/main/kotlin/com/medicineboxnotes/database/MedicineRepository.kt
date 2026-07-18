package com.medicineboxnotes.database

import androidx.room.withTransaction
import com.medicineboxnotes.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class MedicineRepository(private val db: MedicineBoxDatabase) {
    val members: Flow<List<FamilyMember>> = db.familyMembers().observeAll().map { rows -> rows.map { it.model() } }
    val records: Flow<List<MedicalRecord>> = db.medicalRecords().observeAll().map { rows -> rows.map { it.model() } }
    val medicines: Flow<List<MedicineItem>> = db.medicines().observeAll().map { rows -> rows.map { it.model() } }

    suspend fun seedDefaults() {
        if (db.familyMembers().getAll().isNotEmpty()) return
        db.familyMembers().upsertAll(listOf(
            FamilyMember(name = "Me", relationship = "Self", emoji = "M").entity(),
            FamilyMember(name = "Dad", relationship = "Father", emoji = "D").entity(),
            FamilyMember(name = "Mom", relationship = "Mother", emoji = "M").entity(),
        ))
    }

    suspend fun migrateLegacyDefaultMembersToEnglish() = db.withTransaction {
        val replacements = mapOf(
            ("我" to "本人") to ("Me" to "Self"),
            ("爸爸" to "父亲") to ("Dad" to "Father"),
            ("妈妈" to "母亲") to ("Mom" to "Mother"),
        )
        db.familyMembers().getAll().forEach { row ->
            val replacement = replacements[row.name to row.relationship] ?: return@forEach
            val updated = row.copy(name = replacement.first, relationship = replacement.second, emoji = replacement.first.take(1))
            db.familyMembers().upsert(updated)
            db.medicines().renameMember(row.id, replacement.first)
            db.medicationLogs().renameMember(row.id, replacement.first)
        }
    }

    suspend fun member(id: String) = db.familyMembers().get(id)?.model()
    suspend fun saveMember(value: FamilyMember) = db.withTransaction {
        db.familyMembers().upsert(value.entity())
        db.medicines().renameMember(value.id, value.name)
        db.medicationLogs().renameMember(value.id, value.name)
    }
    suspend fun deleteMember(value: FamilyMember) = db.withTransaction {
        db.medicines().detachMember(value.id)
        db.familyMembers().delete(value.entity())
    }

    suspend fun recordBundle(id: String): MedicalRecordBundle? {
        val record = db.medicalRecords().get(id)?.model() ?: return null
        return MedicalRecordBundle(
            record, record.memberId?.let { member(it) },
            db.prescriptions().getForRecord(id).map { it.model() },
            db.attachments().getForRecord(id).map { it.model() },
        )
    }

    fun observeRecordBundle(id: String): Flow<MedicalRecordBundle?> = combine(
        db.medicalRecords().observe(id), members,
        db.prescriptions().observeForRecord(id), db.attachments().observeForRecord(id),
    ) { recordRow, memberRows, prescriptionRows, attachmentRows ->
        recordRow?.model()?.let { record ->
            MedicalRecordBundle(
                record = record,
                member = memberRows.firstOrNull { it.id == record.memberId },
                prescriptions = prescriptionRows.map { it.model() },
                attachments = attachmentRows.map { it.model() },
            )
        }
    }

    suspend fun saveRecord(record: MedicalRecord, prescriptions: List<Prescription>) = db.withTransaction {
        db.medicalRecords().upsert(record.entity())
        db.prescriptions().upsertAll(prescriptions.map { it.copy(recordId = record.id).entity() })
        if (prescriptions.isEmpty()) db.prescriptions().deleteForRecord(record.id)
        else db.prescriptions().deleteMissing(record.id, prescriptions.map { it.id })
    }

    suspend fun deleteRecord(record: MedicalRecord): List<String> = db.withTransaction {
        val paths = db.attachments().getForRecord(record.id).flatMap { listOfNotNull(it.imagePath, it.thumbnailPath) }
        db.medicines().detachRecord(record.id)
        db.medicalRecords().delete(record.entity())
        paths
    }

    suspend fun saveMedicine(value: MedicineItem) = db.medicines().upsert(value.entity())
    suspend fun medicine(id: String) = db.medicines().get(id)?.model()
    suspend fun deleteMedicine(value: MedicineItem) = db.withTransaction {
        db.medicationLogs().deleteForMedicine(value.id)
        db.medicines().delete(value.entity())
    }

    suspend fun saveAttachment(value: RecordAttachment) = db.attachments().upsertAll(listOf(value.entity()))
    suspend fun saveScan(value: MedicineScanAsset) = db.medicineScans().upsertAll(listOf(value.entity()))
    fun observeScans(medicineId: String): Flow<List<MedicineScanAsset>> =
        db.medicineScans().observeForMedicine(medicineId).map { rows -> rows.map { it.model() } }

    suspend fun syncPrescription(record: MedicalRecord, member: FamilyMember?, prescription: Prescription, addStock: Int = 0) = db.withTransaction {
        val current = db.medicines().byPrescription(prescription.id)?.model()
        val updated = current?.copy(
            name = prescription.medicineName, dosage = prescription.dosage,
            frequency = prescription.frequency, durationDays = prescription.durationDays,
            note = prescription.note, stock = current.stock + addStock,
            sourceRecordId = record.id, sourceHospital = record.hospitalName,
            sourceVisitDateEpochDay = record.visitDateEpochDay,
            sourceMemberId = member?.id, sourceMemberName = member?.name.orEmpty(),
        ) ?: MedicineItem(
            name = prescription.medicineName, dosage = prescription.dosage,
            frequency = prescription.frequency, durationDays = prescription.durationDays,
            note = prescription.note, stock = addStock,
            sourceRecordId = record.id, sourcePrescriptionId = prescription.id,
            sourceHospital = record.hospitalName, sourceVisitDateEpochDay = record.visitDateEpochDay,
            sourceMemberId = member?.id, sourceMemberName = member?.name.orEmpty(),
        )
        db.medicines().upsert(updated.entity())
    }

    fun today(day: LocalDate): Flow<List<TodayMedication>> = combine(
        medicines, db.medicationLogs().observeDay(day.toEpochDay()),
    ) { meds, logs ->
        meds.filter { it.isScheduledOn(day) }.flatMap { medicine ->
            medicine.scheduledTimes.mapNotNull { raw ->
                runCatching { LocalTime.parse(raw) }.getOrNull()?.let { time ->
                    TodayMedication(medicine, time, logs.any { it.medicineId == medicine.id && it.scheduledTime == raw })
                }
            }
        }.sortedWith(compareBy<TodayMedication> { it.taken }.thenBy { it.scheduledTime })
    }

    suspend fun setTaken(item: TodayMedication, day: LocalDate, taken: Boolean) {
        val raw = item.scheduledTime.toString()
        if (taken) db.medicationLogs().upsert(MedicationLog(
            id = UUID.nameUUIDFromBytes("${item.medicine.id}:${day}:$raw".toByteArray()).toString(),
            medicineId = item.medicine.id, memberId = item.medicine.sourceMemberId,
            memberName = item.medicine.sourceMemberName, scheduledTime = raw,
            scheduledDateEpochDay = day.toEpochDay(),
        ).entity()) else db.medicationLogs().deleteCheck(item.medicine.id, day.toEpochDay(), raw)
    }
}
