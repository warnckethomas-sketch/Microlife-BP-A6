package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ble.BleSyncStatus
import com.example.ble.MicrolifeBleManager
import com.example.data.backup.BackupResult
import com.example.data.backup.DatabaseBackupManager
import com.example.data.model.BpMeasurement
import com.example.data.repository.BpRepository
import com.example.data.repository.UserSettings
import com.example.pdf.PdfExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BpViewModel(
    private val repository: BpRepository,
    private val bleManager: MicrolifeBleManager,
    private val context: Context? = null
) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settings

    val measurements: StateFlow<List<BpMeasurement>> = settings
        .flatMapLatest { s ->
            repository.getMeasurementsForUser(s.selectedUserIndex)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val syncStatus: StateFlow<BleSyncStatus> = bleManager.syncStatus
    val diagnosticLogs: StateFlow<List<String>> = bleManager.diagnosticLogs

    init {
        // Collect downloaded measurements from BLE manager and save to Room DB tagged with active user
        viewModelScope.launch {
            bleManager.downloadedMeasurements.collect { downloadedList ->
                if (downloadedList.isNotEmpty()) {
                    val activeUserIdx = settings.value.selectedUserIndex
                    val taggedMeasurements = downloadedList.map { it.copy(userIndex = activeUserIdx) }
                    val newlyInserted = repository.insertMeasurements(taggedMeasurements)
                    bleManager.updateSuccessInsertedCount(newlyInserted)
                    if (newlyInserted > 0) {
                        bleManager.logDiagnose("💾 $newlyInserted neue Messwerte in Datenbank gespeichert.")
                    } else {
                        bleManager.logDiagnose("ℹ️ Keine neuen Messwerte (bereits vorhanden).")
                    }

                    // Automatische Datensicherung direkt nach dem Datentransfer anstoßen
                    context?.let { ctx ->
                        if (settings.value.autoBackupEnabled) {
                            bleManager.logDiagnose("📦 Starte automatische Datensicherung nach Datentransfer...")
                            val backupResult = DatabaseBackupManager.performAutoBackupIfEnabled(ctx, repository)
                            if (backupResult.success) {
                                bleManager.logDiagnose("✓ Automatische Sicherung erfolgreich: ${backupResult.message}")
                            } else {
                                bleManager.logDiagnose("ℹ️ Automatische Sicherung: ${backupResult.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectUser(userIndex: Int) {
        val current = settings.value
        if (current.selectedUserIndex != userIndex) {
            repository.saveSettings(current.copy(selectedUserIndex = userIndex))
        }
    }

    fun startBleScan() {
        val activeDevice = settings.value.activePerson.deviceAddress
        if (activeDevice.isNotBlank()) {
            bleManager.connectToDevice(
                address = activeDevice,
                autoErase = settings.value.autoEraseAfterSync,
                is12HourFormat = settings.value.use12HourTimeFormat
            )
        } else {
            bleManager.startScan()
        }
    }

    fun startBleScanForDevices() {
        bleManager.startScan()
    }

    fun connectToDevice(address: String) {
        if (address.isNotBlank()) {
            val current = settings.value
            val updated = if (current.selectedUserIndex == 2) {
                current.copy(person2 = current.person2.copy(deviceAddress = address))
            } else {
                current.copy(person1 = current.person1.copy(deviceAddress = address))
            }
            repository.saveSettings(updated)
        }
        bleManager.connectToDevice(
            address = address,
            autoErase = settings.value.autoEraseAfterSync,
            is12HourFormat = settings.value.use12HourTimeFormat
        )
    }

    fun finishSyncAndClearDeviceMemory(context: Context? = null) {
        viewModelScope.launch {
            bleManager.completeBatchAndEraseMemory()
            val ctx = context ?: this@BpViewModel.context
            if (ctx != null) {
                DatabaseBackupManager.performAutoBackupIfEnabled(ctx, repository)
            }
        }
    }

    fun resetSyncStatus() {
        bleManager.resetStatus()
    }

    // --- BLE GATT DIAGNOSE FUNKTIONEN ---

    fun clearDiagnosticLogs() {
        bleManager.clearDiagnosticLogs()
    }

    fun sendManualTimeSync(targetAddress: String? = null) {
        val address = targetAddress ?: settings.value.activePerson.deviceAddress
        bleManager.sendManualTimeSync(
            targetAddress = address.ifBlank { null },
            is12HourFormat = settings.value.use12HourTimeFormat
        )
    }

    fun sendManualReadDeviceTime(targetAddress: String? = null) {
        val address = targetAddress ?: settings.value.activePerson.deviceAddress
        bleManager.sendManualReadDeviceTime(
            targetAddress = address.ifBlank { null }
        )
    }

    fun sendManualEraseMemory(targetAddress: String? = null) {
        val address = targetAddress ?: settings.value.activePerson.deviceAddress
        bleManager.sendManualEraseMemory(
            targetAddress = address.ifBlank { null }
        )
    }

    fun getFormattedDiagnosticLog(): String {
        return bleManager.getFormattedDiagnosticLog()
    }

    fun saveDiagnosticLogToFile(context: Context, onComplete: ((String) -> Unit)? = null) {
        val result = bleManager.saveDiagnosticLogsToFile()
        Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
        onComplete?.invoke(result.second)
    }

    fun writeDiagnosticLogToUri(context: Context, uri: Uri, onComplete: ((String) -> Unit)? = null) {
        val result = bleManager.writeDiagnosticLogToUri(uri)
        Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
        onComplete?.invoke(result.second)
    }

    fun shareDiagnosticLog(context: Context) {
        try {
            val logText = bleManager.getFormattedDiagnosticLog()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, logText)
                putExtra(Intent.EXTRA_SUBJECT, "Aponorm BP3Gu1-6B BLE Diagnose")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Diagnoseprotokoll teilen via")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler beim Teilen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateSettings(newSettings: UserSettings) {
        repository.saveSettings(newSettings)
    }

    fun updateChartScaleMax(max: Int) {
        val current = settings.value
        repository.saveSettings(current.copy(chartScaleMax = max.coerceIn(120, 300)))
    }

    fun setBackupDirectory(uri: Uri, displayPath: String) {
        val current = settings.value
        repository.saveSettings(
            current.copy(
                backupDirectoryUri = uri.toString(),
                backupDirectoryPathDisplay = displayPath
            )
        )
    }

    fun backupDatabaseNow(context: Context, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = DatabaseBackupManager.exportBackupToDirectory(
                context = context,
                repository = repository,
                customDirUriStr = settings.value.backupDirectoryUri.ifBlank { null }
            )
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            onComplete?.invoke(result.message)
        }
    }

    suspend fun backupDatabaseDirect(context: Context): BackupResult {
        return DatabaseBackupManager.exportBackupToDirectory(
            context = context,
            repository = repository,
            customDirUriStr = settings.value.backupDirectoryUri.ifBlank { null }
        )
    }

    fun backupDatabaseToUri(context: Context, destinationUri: Uri, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = DatabaseBackupManager.exportBackupToUri(
                context = context,
                destinationUri = destinationUri,
                repository = repository
            )
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            onComplete?.invoke(result.message)
        }
    }

    fun restoreDatabaseFromUri(context: Context, sourceUri: Uri, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = DatabaseBackupManager.restoreBackupFromUri(
                context = context,
                sourceUri = sourceUri,
                repository = repository,
                mergeWithExisting = true
            )
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            onComplete?.invoke(result.message)
        }
    }

    fun deleteMeasurement(id: Int) {
        viewModelScope.launch {
            repository.deleteMeasurement(id)
        }
    }

    fun deleteAllMeasurements() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun printPdf(
        context: Context,
        targetMeasurements: List<BpMeasurement>? = null,
        monthTitle: String? = null
    ) {
        val listToPrint = targetMeasurements ?: measurements.value
        PdfExporter.printPdf(
            context = context,
            measurements = listToPrint,
            settings = settings.value,
            monthTitle = monthTitle
        )
    }

    fun exportPdfToUri(
        context: Context,
        destinationUri: Uri,
        targetMeasurements: List<BpMeasurement>? = null,
        monthTitle: String? = null
    ) {
        val listToExport = targetMeasurements ?: measurements.value
        PdfExporter.exportToUri(
            context = context,
            destinationUri = destinationUri,
            measurements = listToExport,
            settings = settings.value,
            monthTitle = monthTitle
        )
    }
}

class BpViewModelFactory(
    private val repository: BpRepository,
    private val bleManager: MicrolifeBleManager,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BpViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BpViewModel(repository, bleManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
