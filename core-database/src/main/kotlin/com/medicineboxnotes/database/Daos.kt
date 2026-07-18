package com.medicineboxnotes.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members ORDER BY createdAtEpochMillis") fun observeAll(): Flow<List<FamilyMemberEntity>>
    @Query("SELECT * FROM family_members ORDER BY createdAtEpochMillis") suspend fun getAll(): List<FamilyMemberEntity>
    @Query("SELECT * FROM family_members WHERE id=:id") suspend fun get(id: String): FamilyMemberEntity?
    @Upsert suspend fun upsert(value: FamilyMemberEntity)
    @Upsert suspend fun upsertAll(values: List<FamilyMemberEntity>)
    @Delete suspend fun delete(value: FamilyMemberEntity)
}

@Dao
interface MedicalRecordDao {
    @Query("SELECT * FROM medical_records ORDER BY visitDateEpochDay DESC") fun observeAll(): Flow<List<MedicalRecordEntity>>
    @Query("SELECT * FROM medical_records WHERE id=:id") fun observe(id: String): Flow<MedicalRecordEntity?>
    @Query("SELECT * FROM medical_records ORDER BY visitDateEpochDay DESC") suspend fun getAll(): List<MedicalRecordEntity>
    @Query("SELECT * FROM medical_records WHERE id=:id") suspend fun get(id: String): MedicalRecordEntity?
    @Upsert suspend fun upsert(value: MedicalRecordEntity)
    @Upsert suspend fun upsertAll(values: List<MedicalRecordEntity>)
    @Delete suspend fun delete(value: MedicalRecordEntity)
}

@Dao
interface PrescriptionDao {
    @Query("SELECT * FROM prescriptions WHERE recordId=:recordId") fun observeForRecord(recordId: String): Flow<List<PrescriptionEntity>>
    @Query("SELECT * FROM prescriptions WHERE recordId=:recordId") suspend fun getForRecord(recordId: String): List<PrescriptionEntity>
    @Query("SELECT * FROM prescriptions") suspend fun getAll(): List<PrescriptionEntity>
    @Upsert suspend fun upsertAll(values: List<PrescriptionEntity>)
    @Query("DELETE FROM prescriptions WHERE recordId=:recordId AND id NOT IN (:keepIds)") suspend fun deleteMissing(recordId: String, keepIds: List<String>)
    @Query("DELETE FROM prescriptions WHERE recordId=:recordId") suspend fun deleteForRecord(recordId: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM record_attachments WHERE recordId=:recordId") fun observeForRecord(recordId: String): Flow<List<RecordAttachmentEntity>>
    @Query("SELECT * FROM record_attachments WHERE recordId=:recordId") suspend fun getForRecord(recordId: String): List<RecordAttachmentEntity>
    @Query("SELECT * FROM record_attachments") suspend fun getAll(): List<RecordAttachmentEntity>
    @Upsert suspend fun upsertAll(values: List<RecordAttachmentEntity>)
    @Delete suspend fun delete(value: RecordAttachmentEntity)
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY name") fun observeAll(): Flow<List<MedicineEntity>>
    @Query("SELECT * FROM medicines ORDER BY name") suspend fun getAll(): List<MedicineEntity>
    @Query("SELECT * FROM medicines WHERE id=:id") suspend fun get(id: String): MedicineEntity?
    @Query("SELECT * FROM medicines WHERE sourcePrescriptionId=:id LIMIT 1") suspend fun byPrescription(id: String): MedicineEntity?
    @Upsert suspend fun upsert(value: MedicineEntity)
    @Upsert suspend fun upsertAll(values: List<MedicineEntity>)
    @Delete suspend fun delete(value: MedicineEntity)
    @Query("UPDATE medicines SET sourcePrescriptionId=NULL, sourceRecordId=NULL WHERE sourceRecordId=:recordId") suspend fun detachRecord(recordId: String)
    @Query("UPDATE medicines SET sourceMemberId=NULL, sourceMemberName='' WHERE sourceMemberId=:memberId") suspend fun detachMember(memberId: String)
    @Query("UPDATE medicines SET sourceMemberName=:name WHERE sourceMemberId=:memberId") suspend fun renameMember(memberId: String, name: String)
}

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs WHERE scheduledDateEpochDay=:day") fun observeDay(day: Long): Flow<List<MedicationLogEntity>>
    @Query("SELECT * FROM medication_logs") suspend fun getAll(): List<MedicationLogEntity>
    @Upsert suspend fun upsert(value: MedicationLogEntity)
    @Upsert suspend fun upsertAll(values: List<MedicationLogEntity>)
    @Query("DELETE FROM medication_logs WHERE medicineId=:medicineId") suspend fun deleteForMedicine(medicineId: String)
    @Query("DELETE FROM medication_logs WHERE medicineId=:medicineId AND scheduledDateEpochDay=:day AND scheduledTime=:time") suspend fun deleteCheck(medicineId: String, day: Long, time: String)
    @Query("UPDATE medication_logs SET memberName=:name WHERE memberId=:memberId") suspend fun renameMember(memberId: String, name: String)
}

@Dao
interface MedicineScanDao {
    @Query("SELECT * FROM medicine_scans WHERE medicineId=:medicineId ORDER BY rowid DESC") fun observeForMedicine(medicineId: String): Flow<List<MedicineScanEntity>>
    @Query("SELECT * FROM medicine_scans") suspend fun getAll(): List<MedicineScanEntity>
    @Upsert suspend fun upsertAll(values: List<MedicineScanEntity>)
    @Delete suspend fun delete(value: MedicineScanEntity)
}
