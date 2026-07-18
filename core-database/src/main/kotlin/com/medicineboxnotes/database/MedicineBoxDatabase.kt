package com.medicineboxnotes.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FamilyMemberEntity::class, MedicalRecordEntity::class, PrescriptionEntity::class,
        RecordAttachmentEntity::class, MedicineEntity::class, MedicationLogEntity::class,
        MedicineScanEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MedicineBoxDatabase : RoomDatabase() {
    abstract fun familyMembers(): FamilyMemberDao
    abstract fun medicalRecords(): MedicalRecordDao
    abstract fun prescriptions(): PrescriptionDao
    abstract fun attachments(): AttachmentDao
    abstract fun medicines(): MedicineDao
    abstract fun medicationLogs(): MedicationLogDao
    abstract fun medicineScans(): MedicineScanDao

    companion object {
        @Volatile private var instance: MedicineBoxDatabase? = null

        fun get(context: Context): MedicineBoxDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MedicineBoxDatabase::class.java,
                "medicine-box-notes.db",
            ).build().also { instance = it }
        }
    }
}
