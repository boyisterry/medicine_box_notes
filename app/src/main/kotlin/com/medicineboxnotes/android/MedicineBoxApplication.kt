package com.medicineboxnotes.android

import android.app.Application
import com.medicineboxnotes.ai.MedicalAiClient
import com.medicineboxnotes.ai.RuleBasedMedicalAiClient
import com.medicineboxnotes.ai.GemmaMedicalAiClient
import com.medicineboxnotes.database.MedicineBoxDatabase
import com.medicineboxnotes.database.MedicineRepository
import com.medicineboxnotes.android.platform.ReminderScheduler
import com.medicineboxnotes.android.platform.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedicineBoxApplication : Application() {
    lateinit var database: MedicineBoxDatabase; private set
    lateinit var repository: MedicineRepository; private set
    lateinit var aiClient: MedicalAiClient; private set
    lateinit var modelDownloads: ModelDownloadManager; private set

    override fun onCreate() {
        super.onCreate()
        LocaleController.applyToApplication(this)
        database = MedicineBoxDatabase.get(this)
        repository = MedicineRepository(database)
        aiClient = RuleBasedMedicalAiClient()
        modelDownloads = ModelDownloadManager(this) { file -> activateGemma(file) }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.seedDefaults()
            val migrationPrefs = getSharedPreferences("medicine_box_migrations", MODE_PRIVATE)
            if (!migrationPrefs.getBoolean("legacy_default_members_english_v1", false)) {
                repository.migrateLegacyDefaultMembersToEnglish()
                migrationPrefs.edit().putBoolean("legacy_default_members_english_v1", true).apply()
            }
            ReminderScheduler(this@MedicineBoxApplication).rescheduleAll(database)
        }
    }

    private fun activateGemma(file: java.io.File) {
        val previous = aiClient
        aiClient = GemmaMedicalAiClient(file, cacheDir.resolve("litertlm").apply { mkdirs() })
        if (previous is AutoCloseable) runCatching { previous.close() }
    }
}
