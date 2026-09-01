package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.model.BpMeasurement
import com.example.data.repository.BpRepository
import com.example.data.repository.PersonProfile
import com.example.data.repository.UserSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupResult(
    val success: Boolean,
    val count: Int = 0,
    val message: String,
    val filePath: String = ""
)

object DatabaseBackupManager {

    private const val TAG = "DatabaseBackupManager"
    private const val BACKUP_VERSION = 2
    private const val BACKUP_DIR_NAME = "Blutdruck_Backups"
    const val BACKUP_FILE_NAME = "Blutdruck_Sicherung.db"

    /**
     * Resolves a human-readable friendly display name for SAF tree URIs (e.g. Google Drive, Downloads, Documents)
     * avoiding cryptic IDs or encoded strings ("kauderwelsch").
     */
    fun getFriendlyFolderDisplayName(context: Context, treeUri: Uri): String {
        try {
            val authority = treeUri.authority ?: ""
            val isGoogleDrive = authority.contains("com.google.android.apps.docs") || authority.contains("google")

            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

            var resolvedDisplayName: String? = null
            try {
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                resolvedDisplayName = name.trim()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying COLUMN_DISPLAY_NAME for URI: $treeUri", e)
            }

            // 1. Speziell für Google Drive
            if (isGoogleDrive) {
                val cleanName = resolvedDisplayName
                    ?.takeIf { it.isNotBlank() && !it.contains(":") && !it.matches(Regex("^[0-9a-zA-Z_-]{18,}$")) }

                return if (cleanName != null) {
                    "Google Drive / $cleanName"
                } else {
                    val decoded = Uri.decode(docId)
                    if (decoded.contains("/")) {
                        val sub = decoded.substringAfterLast("/").trim()
                        if (sub.isNotBlank() && !sub.matches(Regex("^[0-9a-zA-Z_-]{18,}$")) && sub != "root") {
                            "Google Drive / $sub"
                        } else {
                            "Google Drive"
                        }
                    } else if (decoded.contains("root") || decoded.isBlank()) {
                        "Google Drive (Hauptverzeichnis)"
                    } else {
                        "Google Drive"
                    }
                }
            }

            // 2. Lokaler Speicher / Downloads / Dokumente
            if (authority.contains("externalstorage") || authority.contains("providers.downloads")) {
                val decoded = Uri.decode(docId)
                val cleanPath = when {
                    decoded.startsWith("primary:") -> decoded.removePrefix("primary:")
                    decoded.startsWith("raw:") -> decoded.removePrefix("raw:").substringAfterLast("/")
                    else -> decoded.substringAfterLast(":")
                }.trim()

                if (resolvedDisplayName != null &&
                    !resolvedDisplayName!!.startsWith("primary:") &&
                    !resolvedDisplayName!!.contains(":")
                ) {
                    return if (cleanPath.contains("/")) "Interner Speicher / $cleanPath" else "Ordner: $resolvedDisplayName"
                }
                if (cleanPath.isNotBlank()) {
                    return when {
                        cleanPath.startsWith("Download", ignoreCase = true) -> "Downloads" + cleanPath.removePrefix("Download")
                        cleanPath.startsWith("Documents", ignoreCase = true) -> "Dokumente" + cleanPath.removePrefix("Documents")
                        else -> "Speicher / $cleanPath"
                    }
                }
            }

            if (!resolvedDisplayName.isNullOrBlank() && !resolvedDisplayName!!.matches(Regex("^[0-9a-zA-Z_-]{20,}$"))) {
                return "Ordner: $resolvedDisplayName"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving folder name for $treeUri", e)
        }

        // Fallback
        val rawSegment = Uri.decode(treeUri.lastPathSegment ?: "")
        val clean = if (rawSegment.contains(":")) rawSegment.substringAfterLast(":") else rawSegment
        return if (clean.isNotBlank() && !clean.matches(Regex("^[0-9a-zA-Z_-]{20,}$"))) {
            "Ordner: $clean"
        } else {
            "Ausgewählter Sicherungsordner"
        }
    }

    /**
     * Sanitizes stored display names to ensure existing cryptic strings are converted to clean labels.
     */
    fun sanitizeDisplayName(context: Context, uriStr: String, currentDisplay: String): String {
        if (uriStr.isNotBlank()) {
            try {
                val uri = Uri.parse(uriStr)
                return getFriendlyFolderDisplayName(context, uri)
            } catch (e: Exception) {
                // Keep current if parsing fails
            }
        }
        if (currentDisplay.contains("%") || currentDisplay.contains("content:") || currentDisplay.matches(Regex(".*[0-9a-fA-F]{15,}.*"))) {
            return "Benutzerdefinierter Sicherungsordner"
        }
        return currentDisplay.ifBlank { "App-Speicher (Dokumente / $BACKUP_DIR_NAME)" }
    }

    /**
     * Creates a comprehensive JSON-based database backup string containing:
     * 1. All measurements from all persons
     * 2. All person profiles and blood pressure norm limits
     * 3. All paired Bluetooth device assignments (MAC addresses, names, settings)
     */
    suspend fun createBackupJson(repository: BpRepository): String {
        val measurements = repository.getAllMeasurementsList()
        val settings = repository.settings.value

        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("app", "Microlife_Aponorm_BP_App")
        root.put("backupType", "FULL_DATABASE_BACKUP")
        root.put("backupScope", "MEASUREMENTS_PERSONS_DEVICES")
        root.put("createdAt", System.currentTimeMillis())
        root.put("createdAtFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(Date()))
        root.put("totalMeasurements", measurements.size)

        // 1. PERSONEN-PROFILE
        val personsArray = JSONArray()

        val p1Obj = JSONObject().apply {
            put("userIndex", settings.person1.userIndex)
            put("name", settings.person1.name)
            put("systoleNormMax", settings.person1.systoleNormMax)
            put("diastoleNormMax", settings.person1.diastoleNormMax)
            put("deviceAddress", settings.person1.deviceAddress)
            put("measurementsPerDay", settings.person1.measurementsPerDay)
            put("birthDate", settings.person1.birthDate)
        }
        personsArray.put(p1Obj)

        val p2Obj = JSONObject().apply {
            put("userIndex", settings.person2.userIndex)
            put("name", settings.person2.name)
            put("systoleNormMax", settings.person2.systoleNormMax)
            put("diastoleNormMax", settings.person2.diastoleNormMax)
            put("deviceAddress", settings.person2.deviceAddress)
            put("measurementsPerDay", settings.person2.measurementsPerDay)
            put("birthDate", settings.person2.birthDate)
        }
        personsArray.put(p2Obj)

        root.put("persons", personsArray)

        // 2. GERÄTE- & BLUETOOTH-KONFIGURATION
        val devicesArray = JSONArray()

        if (settings.person1.deviceAddress.isNotBlank()) {
            val dev1 = JSONObject().apply {
                put("assignedUserIndex", 1)
                put("assignedPersonName", settings.person1.name)
                put("deviceAddress", settings.person1.deviceAddress)
                put("deviceType", "aponorm / Microlife Bluetooth BP")
            }
            devicesArray.put(dev1)
        }

        if (settings.person2.deviceAddress.isNotBlank()) {
            val dev2 = JSONObject().apply {
                put("assignedUserIndex", 2)
                put("assignedPersonName", settings.person2.name)
                put("deviceAddress", settings.person2.deviceAddress)
                put("deviceType", "aponorm / Microlife Bluetooth BP")
            }
            devicesArray.put(dev2)
        }

        root.put("devices", devicesArray)

        // 3. APP-EINSTELLUNGEN
        val settingsObj = JSONObject().apply {
            put("selectedUserIndex", settings.selectedUserIndex)
            put("autoEraseAfterSync", settings.autoEraseAfterSync)
            put("autoBackupEnabled", settings.autoBackupEnabled)
            put("backupDirectoryUri", settings.backupDirectoryUri)
            put("backupDirectoryPathDisplay", settings.backupDirectoryPathDisplay)
            put("person1", p1Obj)
            put("person2", p2Obj)
        }
        root.put("settings", settingsObj)

        // 4. MESSWERTE (ALL MEASUREMENTS)
        val itemsArray = JSONArray()
        for (m in measurements) {
            val item = JSONObject().apply {
                put("id", m.id)
                put("timestamp", m.timestamp)
                put("timestampFormatted", m.formattedDateTime())
                put("systole", m.systole)
                put("diastole", m.diastole)
                put("pulse", m.pulse)
                put("afibDetected", m.afibDetected)
                put("userIndex", m.userIndex)
                put("personName", if (m.userIndex == 2) settings.person2.name else settings.person1.name)
                put("notes", m.notes)
            }
            itemsArray.put(item)
        }
        root.put("measurements", itemsArray)

        return root.toString(2)
    }

    /**
     * Exports the database backup directly to an OutputStream (e.g. from SAF CreateDocument Uri).
     */
    suspend fun exportBackupToUri(
        context: Context,
        destinationUri: Uri,
        repository: BpRepository
    ): BackupResult {
        return try {
            val jsonString = createBackupJson(repository)
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(destinationUri, "wt")
            if (outputStream != null) {
                outputStream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                val count = repository.getAllMeasurementsList().size
                val settings = repository.settings.value
                repository.updateLastBackupTimestamp(System.currentTimeMillis())
                BackupResult(
                    success = true,
                    count = count,
                    message = "Komplettsicherung erfolgreich: $count Messwerte, Profile (${settings.person1.name}, ${settings.person2.name}) & Bluetooth-Geräte gesichert."
                )
            } else {
                BackupResult(
                    success = false,
                    message = "Speicherort konnte nicht geöffnet werden."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting backup to URI", e)
            BackupResult(
                success = false,
                message = "Fehler bei der Sicherung: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Exports an automatic or standard backup to the designated backup folder or internal documents.
     * Always writes/updates into one single consistent file (Blutdruck_Sicherung.db).
     */
    suspend fun exportBackupToDirectory(
        context: Context,
        repository: BpRepository,
        customDirUriStr: String? = null
    ): BackupResult {
        return try {
            val jsonString = createBackupJson(repository)

            // 1. If custom SAF tree Uri is configured (e.g. Google Drive or chosen folder)
            if (!customDirUriStr.isNullOrBlank()) {
                try {
                    val treeUri = Uri.parse(customDirUriStr)
                    val docId = DocumentsContract.getTreeDocumentId(treeUri)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

                    var targetDocUri: Uri? = null

                    // Check if Blutdruck_Sicherung.db already exists in this folder to overwrite it
                    try {
                        context.contentResolver.query(
                            childrenUri,
                            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            while (cursor.moveToNext()) {
                                val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                                if (name.equals(BACKUP_FILE_NAME, ignoreCase = true) || name.equals("Blutdruck_Komplettsicherung.db", ignoreCase = true)) {
                                    val childDocId = cursor.getString(idIdx)
                                    targetDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed searching for existing backup file in tree", e)
                    }

                    // If file doesn't exist yet, create it once
                    if (targetDocUri == null) {
                        targetDocUri = DocumentsContract.createDocument(
                            context.contentResolver,
                            docUri,
                            "application/octet-stream",
                            BACKUP_FILE_NAME
                        )
                    }

                    if (targetDocUri != null) {
                        val os = context.contentResolver.openOutputStream(targetDocUri, "wt")
                        if (os != null) {
                            os.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                            val count = repository.getAllMeasurementsList().size
                            repository.updateLastBackupTimestamp(System.currentTimeMillis())
                            val folderDisplay = getFriendlyFolderDisplayName(context, treeUri)
                            return BackupResult(
                                success = true,
                                count = count,
                                message = "Sicherung in '$folderDisplay / $BACKUP_FILE_NAME' aktualisiert ($count Messwerte, Profile & Geräte).",
                                filePath = targetDocUri.toString()
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed writing to custom tree URI, falling back to local folder", e)
                }
            }

            // 2. Default Local Storage directory in Documents/Blutdruck_Backups
            val backupDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
                BACKUP_DIR_NAME
            )
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val targetFile = File(backupDir, BACKUP_FILE_NAME)
            FileOutputStream(targetFile).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }

            val count = repository.getAllMeasurementsList().size
            repository.updateLastBackupTimestamp(System.currentTimeMillis())

            BackupResult(
                success = true,
                count = count,
                message = "Sicherung in '$BACKUP_FILE_NAME' aktualisiert ($count Messwerte, Profile & Geräte).",
                filePath = targetFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error writing backup to local directory", e)
            BackupResult(
                success = false,
                message = "Fehler beim Erstellen der Sicherung: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Restores measurements, person profiles, and Bluetooth device configurations from a backup file.
     */
    suspend fun restoreBackupFromUri(
        context: Context,
        sourceUri: Uri,
        repository: BpRepository,
        mergeWithExisting: Boolean = true
    ): BackupResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return BackupResult(success = false, message = "Sicherungsdatei konnte nicht geöffnet werden.")

            val jsonString = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
            val root = JSONObject(jsonString)

            if (!root.has("measurements")) {
                return BackupResult(
                    success = false,
                    message = "Ungültiges Sicherungsformat: Keine Messdaten in Datei gefunden."
                )
            }

            val measurementsArray = root.getJSONArray("measurements")
            val parsedList = mutableListOf<BpMeasurement>()

            for (i in 0 until measurementsArray.length()) {
                val item = measurementsArray.getJSONObject(i)
                val m = BpMeasurement(
                    id = 0, // Auto-generated
                    timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                    systole = item.optInt("systole", 120),
                    diastole = item.optInt("diastole", 80),
                    pulse = item.optInt("pulse", 70),
                    afibDetected = item.optBoolean("afibDetected", false),
                    userIndex = item.optInt("userIndex", 1),
                    notes = item.optString("notes", "")
                )
                parsedList.add(m)
            }

            // Deduplication logic against existing DB entries
            val existingMeasurements = repository.getAllMeasurementsList()
            val existingKeySet = existingMeasurements.map { "${it.timestamp}_${it.userIndex}_${it.systole}_${it.diastole}" }.toSet()

            val toInsert = if (mergeWithExisting) {
                parsedList.filter { m ->
                    val key = "${m.timestamp}_${m.userIndex}_${m.systole}_${m.diastole}"
                    !existingKeySet.contains(key)
                }
            } else {
                repository.deleteAll()
                parsedList
            }

            if (toInsert.isNotEmpty()) {
                repository.insertMeasurements(toInsert)
            }

            // 2. Restore Person Profiles and Device MACs
            val curSettings = repository.settings.value
            var restoredP1 = curSettings.person1
            var restoredP2 = curSettings.person2

            // Check if structured persons array exists
            if (root.has("persons")) {
                val pArray = root.getJSONArray("persons")
                for (i in 0 until pArray.length()) {
                    val pObj = pArray.getJSONObject(i)
                    val uIdx = pObj.optInt("userIndex", i + 1)
                    if (uIdx == 1) {
                        restoredP1 = restoredP1.copy(
                            name = pObj.optString("name", restoredP1.name),
                            systoleNormMax = pObj.optInt("systoleNormMax", restoredP1.systoleNormMax),
                            diastoleNormMax = pObj.optInt("diastoleNormMax", restoredP1.diastoleNormMax),
                            deviceAddress = pObj.optString("deviceAddress", restoredP1.deviceAddress),
                            measurementsPerDay = pObj.optInt("measurementsPerDay", restoredP1.measurementsPerDay),
                            birthDate = pObj.optString("birthDate", restoredP1.birthDate)
                        )
                    } else if (uIdx == 2) {
                        restoredP2 = restoredP2.copy(
                            name = pObj.optString("name", restoredP2.name),
                            systoleNormMax = pObj.optInt("systoleNormMax", restoredP2.systoleNormMax),
                            diastoleNormMax = pObj.optInt("diastoleNormMax", restoredP2.diastoleNormMax),
                            deviceAddress = pObj.optString("deviceAddress", restoredP2.deviceAddress),
                            measurementsPerDay = pObj.optInt("measurementsPerDay", restoredP2.measurementsPerDay),
                            birthDate = pObj.optString("birthDate", restoredP2.birthDate)
                        )
                    }
                }
            } else if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                if (sObj.has("person1")) {
                    val p1Obj = sObj.getJSONObject("person1")
                    restoredP1 = restoredP1.copy(
                        name = p1Obj.optString("name", restoredP1.name),
                        systoleNormMax = p1Obj.optInt("systoleNormMax", restoredP1.systoleNormMax),
                        diastoleNormMax = p1Obj.optInt("diastoleNormMax", restoredP1.diastoleNormMax),
                        deviceAddress = p1Obj.optString("deviceAddress", restoredP1.deviceAddress),
                        measurementsPerDay = p1Obj.optInt("measurementsPerDay", restoredP1.measurementsPerDay),
                        birthDate = p1Obj.optString("birthDate", restoredP1.birthDate)
                    )
                }
                if (sObj.has("person2")) {
                    val p2Obj = sObj.getJSONObject("person2")
                    restoredP2 = restoredP2.copy(
                        name = p2Obj.optString("name", restoredP2.name),
                        systoleNormMax = p2Obj.optInt("systoleNormMax", restoredP2.systoleNormMax),
                        diastoleNormMax = p2Obj.optInt("diastoleNormMax", restoredP2.diastoleNormMax),
                        deviceAddress = p2Obj.optString("deviceAddress", restoredP2.deviceAddress),
                        measurementsPerDay = p2Obj.optInt("measurementsPerDay", restoredP2.measurementsPerDay),
                        birthDate = p2Obj.optString("birthDate", restoredP2.birthDate)
                    )
                }
            }

            // Also check explicit devices section if available
            if (root.has("devices")) {
                val devArray = root.getJSONArray("devices")
                for (i in 0 until devArray.length()) {
                    val dObj = devArray.getJSONObject(i)
                    val assignedIdx = dObj.optInt("assignedUserIndex", 1)
                    val address = dObj.optString("deviceAddress", "")
                    if (address.isNotBlank()) {
                        if (assignedIdx == 1) {
                            restoredP1 = restoredP1.copy(deviceAddress = address)
                        } else if (assignedIdx == 2) {
                            restoredP2 = restoredP2.copy(deviceAddress = address)
                        }
                    }
                }
            }

            val restoredSettings = curSettings.copy(
                person1 = restoredP1,
                person2 = restoredP2,
                autoEraseAfterSync = root.optJSONObject("settings")?.optBoolean("autoEraseAfterSync", curSettings.autoEraseAfterSync) ?: curSettings.autoEraseAfterSync,
                autoBackupEnabled = root.optJSONObject("settings")?.optBoolean("autoBackupEnabled", curSettings.autoBackupEnabled) ?: curSettings.autoBackupEnabled
            )

            repository.saveSettingsSync(restoredSettings)

            val totalRestored = parsedList.size
            val newAdded = toInsert.size
            val msg = if (mergeWithExisting) {
                "Wiederherstellung erfolgreich: $totalRestored Messwerte geladen ($newAdded neue hinzugefügt), Profile (${restoredP1.name}, ${restoredP2.name}) und Bluetooth-Geräte eingerichtet."
            } else {
                "Wiederherstellung erfolgreich: $totalRestored Messwerte, Profile (${restoredP1.name}, ${restoredP2.name}) und Bluetooth-Geräte vollständig wiederhergestellt."
            }

            BackupResult(
                success = true,
                count = totalRestored,
                message = msg
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup", e)
            BackupResult(
                success = false,
                message = "Wiederherstellung fehlgeschlagen: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Trigger auto-backup if enabled in user settings.
     */
    suspend fun performAutoBackupIfEnabled(
        context: Context,
        repository: BpRepository
    ): BackupResult {
        val settings = repository.settings.value
        if (settings.autoBackupEnabled) {
            return try {
                val res = exportBackupToDirectory(
                    context = context,
                    repository = repository,
                    customDirUriStr = settings.backupDirectoryUri.ifBlank { null }
                )
                Log.d(TAG, "Auto-backup completed: ${res.message}")
                res
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup error", e)
                BackupResult(success = false, message = "Fehler bei automatischer Sicherung: ${e.localizedMessage}")
            }
        }
        return BackupResult(success = false, message = "Automatische Sicherung ist deaktiviert.")
    }

    fun generateDefaultBackupFileName(): String {
        return BACKUP_FILE_NAME
    }
}
