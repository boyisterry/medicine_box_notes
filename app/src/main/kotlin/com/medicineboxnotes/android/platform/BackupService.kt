package com.medicineboxnotes.android.platform

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.medicineboxnotes.database.*
import com.medicineboxnotes.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = 1,
    val exportedAtEpochMillis: Long = System.currentTimeMillis(),
    val members: List<FamilyMember>,
    val records: List<MedicalRecord>,
    val prescriptions: List<Prescription>,
    val attachments: List<RecordAttachment>,
    val medicines: List<MedicineItem>,
    val logs: List<MedicationLog>,
    val scans: List<MedicineScanAsset>,
    val fileSha256: Map<String, String>,
)

class BackupService(private val context: Context, private val db: MedicineBoxDatabase) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val random = SecureRandom()

    suspend fun export(uri: Uri, password: CharArray) {
        require(password.size >= 8) { "备份密码至少 8 位" }
        val attachments = db.attachments().getAll().map { it.model() }
        val scans = db.medicineScans().getAll().map { it.model() }
        val paths = (attachments.flatMap { listOfNotNull(it.imagePath, it.thumbnailPath) } + scans.flatMap { listOfNotNull(it.imagePath, it.thumbnailPath) }).distinct()
        val hashes = paths.mapNotNull { path -> context.filesDir.resolve(path).takeIf(File::isFile)?.let { path to it.sha256() } }.toMap()
        val snapshot = BackupSnapshot(
            members = db.familyMembers().getAll().map { it.model() }, records = db.medicalRecords().getAll().map { it.model() },
            prescriptions = db.prescriptions().getAll().map { it.model() }, attachments = attachments,
            medicines = db.medicines().getAll().map { it.model() }, logs = db.medicationLogs().getAll().map { it.model() },
            scans = scans, fileSha256 = hashes,
        )
        context.contentResolver.openOutputStream(uri, "w")!!.buffered().use { raw ->
            val salt = ByteArray(16).also(random::nextBytes); val iv = ByteArray(12).also(random::nextBytes)
            raw.write(MAGIC); raw.write(salt); raw.write(iv)
            CipherOutputStream(raw, cipher(Cipher.ENCRYPT_MODE, password, salt, iv)).use { encrypted ->
                ZipOutputStream(encrypted).use { zip ->
                    zip.putNextEntry(ZipEntry("snapshot.json")); zip.write(json.encodeToString(snapshot).toByteArray()); zip.closeEntry()
                    hashes.keys.forEach { path ->
                        zip.putNextEntry(ZipEntry("files/$path")); context.filesDir.resolve(path).inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        }
        password.fill('\u0000')
    }

    suspend fun import(uri: Uri, password: CharArray) {
        require(password.size >= 8) { "备份密码至少 8 位" }
        val stage = context.cacheDir.resolve("restore-${System.nanoTime()}").apply { mkdirs() }
        var snapshot: BackupSnapshot? = null
        try {
            context.contentResolver.openInputStream(uri)!!.buffered().use { raw ->
                val dataInput = DataInputStream(raw)
                val header = ByteArray(4).also(dataInput::readFully)
                require(header.contentEquals(MAGIC)) { "不是 MedicineBoxNotes 加密备份" }
                val salt = ByteArray(16).also(dataInput::readFully); val iv = ByteArray(12).also(dataInput::readFully)
                ZipInputStream(CipherInputStream(raw, cipher(Cipher.DECRYPT_MODE, password, salt, iv))).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when {
                            entry.name == "snapshot.json" -> snapshot = json.decodeFromString(zip.readBytes().decodeToString())
                            entry.name.startsWith("files/") -> {
                                val relative = entry.name.removePrefix("files/")
                                require(!relative.contains("..") && !relative.startsWith('/')) { "备份包含非法路径" }
                                val output = stage.resolve(relative); output.parentFile?.mkdirs(); output.outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val data = requireNotNull(snapshot) { "备份缺少数据清单" }
            require(data.schemaVersion == 1) { "不支持的备份版本 ${data.schemaVersion}" }
            data.fileSha256.forEach { (path, expected) -> require(stage.resolve(path).sha256() == expected) { "附件校验失败：$path" } }
            db.withTransaction {
                db.familyMembers().upsertAll(data.members.map { it.entity() }); db.medicalRecords().upsertAll(data.records.map { it.entity() })
                db.prescriptions().upsertAll(data.prescriptions.map { it.entity() }); db.attachments().upsertAll(data.attachments.map { it.entity() })
                db.medicines().upsertAll(data.medicines.map { it.entity() }); db.medicationLogs().upsertAll(data.logs.map { it.entity() })
                db.medicineScans().upsertAll(data.scans.map { it.entity() })
            }
            data.fileSha256.keys.forEach { path -> stage.resolve(path).copyTo(context.filesDir.resolve(path).also { it.parentFile?.mkdirs() }, overwrite = true) }
        } finally { password.fill('\u0000'); stage.deleteRecursively() }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, 180_000, 256))
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, key, GCMParameterSpec(128, iv)); updateAAD(MAGIC) }
    }

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object { private val MAGIC = byteArrayOf('M'.code.toByte(), 'B'.code.toByte(), 'N'.code.toByte(), 1) }
}
