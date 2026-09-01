package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.data.model.BpMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.TimeZone
import java.util.UUID

sealed class BleSyncStatus {
    object Idle : BleSyncStatus()
    object Scanning : BleSyncStatus()
    data class DiscoveredDevices(val devices: List<DiscoveredBleDevice>) : BleSyncStatus()
    data class Connecting(val deviceName: String) : BleSyncStatus()
    object TimeSyncing : BleSyncStatus()
    data class Downloading(val current: Int, val total: Int) : BleSyncStatus()
    object ErasingMemory : BleSyncStatus()
    data class Success(val count: Int, val newlyInserted: Int = -1) : BleSyncStatus()
    data class Error(val message: String) : BleSyncStatus()
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isMicrolife: Boolean
)

class MicrolifeBleManager(private val context: Context) {

    companion object {
        private const val TAG = "MicrolifeBleManager"

        // Propriitäre UUIDs der Microlife BP3GU1-68 Serie (aponorm® Basis Plus BT / Basis Control PLUS BT / Connected Health+)
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val TX_CHAR_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb") // Vom Gerät lesen (Notifications/Indications)
        val RX_CHAR_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb") // Zum Gerät schreiben
        val WRTCMD_CHAR_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb") // Aponorm Schreib-Kanal

        // Client Characteristic Configuration Descriptor (CCCD)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Alternative Microlife / Aponorm UART UUIDs
        val APONORM_FFE0_SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val APONORM_FFE1_CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

        // Standard Blood Pressure GATT Service UUIDs
        val BLOOD_PRESSURE_SERVICE_UUID: UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
        val BP_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")
        val DATE_TIME_CHAR_UUID: UUID = UUID.fromString("00002a08-0000-1000-8000-00805f9b34fb")

        // Standard Current Time Service (0x1805) & Characteristics
        val CURRENT_TIME_SERVICE_UUID: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        val CURRENT_TIME_CHAR_UUID: UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")

        // Standard Device Information Service (0x180A) & Hardware Info
        val DEVICE_INFO_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val SYSTEM_ID_CHAR_UUID: UUID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_CHAR_UUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val SERIAL_NUMBER_CHAR_UUID: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REV_CHAR_UUID: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val HARDWARE_REV_CHAR_UUID: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
        val SOFTWARE_REV_CHAR_UUID: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_CHAR_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")

        // FFF3 RTC / Setup Service UUIDs
        val SERVICE_FFF3_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        val FFF4_CHAR_UUID: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
        val FFF5_CHAR_UUID: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val _syncStatus = MutableStateFlow<BleSyncStatus>(BleSyncStatus.Idle)
    val syncStatus: StateFlow<BleSyncStatus> = _syncStatus.asStateFlow()

    private val _downloadedMeasurements = MutableSharedFlow<List<BpMeasurement>>()
    val downloadedMeasurements: SharedFlow<List<BpMeasurement>> = _downloadedMeasurements.asSharedFlow()

    // --- GATT DIAGNOSE PROTOKOLL SYSTEM ---
    private val _diagnosticLogs = MutableStateFlow<List<String>>(
        listOf("Diagnose-Bereitschaft hergestellt. Warten auf Verbindung...")
    )
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    fun logDiagnose(message: String) {
        val timeStamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formatted = "[$timeStamp] $message"
        Log.i("BLE_DIAGNOSE", message)
        val current = _diagnosticLogs.value.toMutableList()
        current.add(formatted)
        _diagnosticLogs.value = current
    }

    fun clearDiagnosticLogs() {
        _diagnosticLogs.value = emptyList()
        logDiagnose("Diagnose-Protokoll zurückgesetzt.")
    }

    private val discoveredDevicesMap = mutableMapOf<String, DiscoveredBleDevice>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    // --- SAMMEL-BUFFER FÜR GESTÜCKELTE BLE-PAKETE ---
    private val dataBuffer = ByteArrayOutputStream()
    private var expectedTotalSize = 0
    private val receivedBatch = mutableListOf<BpMeasurement>()
    private var isDataDownloadCompleted = false
    private var isErasingOrFinishing = false

    private val commandQueue: Queue<Runnable> = LinkedList()
    private var isCommandPending = false

    // --- BEFEHLS-WARTESCHLANGEN-STEUERUNG (1200ms Puffer vor jedem Schritt) ---

    private var zeitschritt = 1
    private var pendingBondDeviceAddress: String? = null
    private var isReceiverRegistered = false

    // ANDROID 16 FIX: Automatisches Koppeln im Hintergrund
    private val bondingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == intent?.action) {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (bondState == BluetoothDevice.BOND_BONDED && device != null) {
                    Log.d("Android16_Fix", "🎉 Automatisch im Hintergrund gekoppelt! Starte jetzt GATT...")
                    logDiagnose("🎉 Automatisch im Hintergrund gekoppelt! Starte jetzt GATT...")
                    // Jetzt erst ist der Kanal verschlüsselt und offen!
                    connectGattInternal(device)
                }
            }
        }
    }

    private fun registerBondingReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(bondingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(bondingReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Registrieren des Bonding-Receivers", e)
            }
        }
    }

    private fun unregisterBondingReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bondingReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun queueOperation(runnable: Runnable) {
        commandQueue.add(runnable)
        processNextCommand()
    }

    private fun processNextCommand() {
        if (isCommandPending || commandQueue.isEmpty()) return

        isCommandPending = true
        val operation = commandQueue.poll()

        // ZWANGSPAUSE: Wir geben dem trägen 2021er Chip vor JEDEM Schritt 1,2 Sekunden Zeit!
        handler.postDelayed({
            operation?.run()
        }, 1200)
    }

    private fun commandCompleted() {
        isCommandPending = false
        processNextCommand()
    }

    private enum class BleState {
        IDLE, CONNECTED, NOTIFICATIONS_ENABLED, UNLOCKED, USER_SELECTED, TIME_SYNCED, DOWNLOADING
    }

    @Volatile
    private var currentState = BleState.IDLE

    @Volatile
    private var realGattConnected = false

    /**
     * Erstellt ein Microlife/Aponorm Protokoll-Paket mit exakter Summen-Prüfsumme.
     * Header ist stets 0x4D ('M').
     * Prüfsumme = (0x4D + cmdByte + sum(payloadBytes)) & 0xFF
     */
    private fun buildMicrolifeCommand(cmdByte: Byte, payload: ByteArray): ByteArray {
        val packet = ByteArray(2 + payload.size + 1)
        packet[0] = 0x4D.toByte() // 'M'
        packet[1] = cmdByte
        System.arraycopy(payload, 0, packet, 2, payload.size)
        var sum = (packet[0].toInt() and 0xFF) + (packet[1].toInt() and 0xFF)
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        packet[packet.size - 1] = (sum and 0xFF).toByte()
        return packet
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        discoveredDevicesMap.clear()
        logDiagnose("Starte BLE Umgebungssuche (Scan)...")

        // 0. Vorprüfungen: Berechtigungen & Bluetooth-Status
        if (!BlePermissionHelper.hasPermissions(context)) {
            logDiagnose("❌ FEHLER: Bluetooth/Standort-Berechtigungen fehlen!")
            _syncStatus.value = BleSyncStatus.Error("Bluetooth- und Standortberechtigungen sind nicht erteilt. Bitte tippen Sie auf 'Berechtigungen erteilen'.")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            logDiagnose("❌ FEHLER: Bluetooth am Smartphone ist deaktiviert!")
            _syncStatus.value = BleSyncStatus.Error("Bluetooth ist auf dem Smartphone ausgeschaltet. Bitte Bluetooth in den Android-Einstellungen aktivieren.")
            return
        }

        if (!BlePermissionHelper.isLocationEnabled(context)) {
            Log.w(TAG, "Location is turned off on device, BLE scan might return fewer results")
        }

        // 1. Bereits im Smartphone gekoppelte (Bonded) Bluetooth-Geräte sofort einlesen
        try {
            bluetoothAdapter.bondedDevices?.forEach { device ->
                val rawName = device.name ?: ""
                val address = device.address
                val isCompatible = isDeviceNameOrServiceCompatible(rawName, emptyList())
                val name = when {
                    rawName.isNotBlank() -> rawName
                    isCompatible -> "Gekoppeltes aponorm® / Microlife Gerät"
                    else -> "Gekoppeltes Bluetooth-Gerät ($address)"
                }
                discoveredDevicesMap[address] = DiscoveredBleDevice(
                    name = name,
                    address = address,
                    rssi = -45,
                    isMicrolife = isCompatible
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking bonded devices", e)
        }

        val initialList = discoveredDevicesMap.values.toList().sortedByDescending { it.isMicrolife }
        if (initialList.isNotEmpty()) {
            _syncStatus.value = BleSyncStatus.DiscoveredDevices(initialList)
        } else {
            _syncStatus.value = BleSyncStatus.Scanning
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            _syncStatus.value = BleSyncStatus.Error("Bluetooth LE Scanner nicht verfügbar. Bitte Bluetooth kurz aus- und wieder einschalten.")
            return
        }

        val scanSettingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scanSettingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            scanSettingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            scanSettingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }

        val scanSettings = scanSettingsBuilder.build()

        try {
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)

            // Optional: Auch Classic Discovery für Dual-Mode Geräte starten
            try {
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
                bluetoothAdapter.startDiscovery()
            } catch (e: Exception) {
                Log.d(TAG, "Classic discovery start skipped", e)
            }

            // 20 Sekunden Scanzeit für zuverlässiges Erfassen von Geräten mit längeren Werbe-Intervallen
            handler.postDelayed({
                stopScan()
                val finalList = discoveredDevicesMap.values.toList().sortedByDescending { it.isMicrolife }
                _syncStatus.value = BleSyncStatus.DiscoveredDevices(finalList)
            }, 20000)
        } catch (e: Exception) {
            Log.e(TAG, "Scan error", e)
            _syncStatus.value = BleSyncStatus.Error("Fehler beim Starten des Bluetooth-Scans: ${e.localizedMessage}")
        }
    }

    private fun isDeviceNameOrServiceCompatible(name: String, serviceUuids: List<ParcelUuid>?): Boolean {
        val lower = name.lowercase().trim()
        val nameMatch = lower.contains("aponorm") ||
                lower.contains("microlife") ||
                lower.contains("bp") ||
                lower.contains("basis") ||
                lower.contains("control") ||
                lower.contains("watchbp") ||
                lower.contains("a6") ||
                lower.contains("a2") ||
                lower.contains("a3") ||
                lower.contains("b2") ||
                lower.contains("b3") ||
                lower.contains("b6") ||
                lower.contains("3gu") ||
                lower.contains("3gy") ||
                lower.contains("3af") ||
                lower.contains("3ms") ||
                lower.contains("3me") ||
                lower.contains("3mz") ||
                lower.contains("3ag") ||
                lower.contains("3kv") ||
                lower.contains("blood") ||
                lower.contains("pressure") ||
                lower.contains("blutdruck") ||
                lower.contains("health") ||
                lower.contains("medical") ||
                lower.contains("cardio") ||
                lower.contains("omron") ||
                lower.contains("beurer") ||
                lower.contains("sanitas") ||
                lower.contains("medel") ||
                lower.contains("braun") ||
                lower.contains("boso") ||
                lower.contains("bt")

        if (nameMatch) return true

        serviceUuids?.forEach { pUuid ->
            val uuidStr = pUuid.uuid.toString().lowercase()
            if (uuidStr.contains("fff0") || uuidStr.contains("ffe0") || uuidStr.contains("1810") || uuidStr.contains("180a") || uuidStr.contains("1800")) {
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Stop scan error", e)
        }
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val rawName = device.name ?: scanRecord?.deviceName ?: ""
            val address = device.address
            val rssi = result.rssi
            val serviceUuids = scanRecord?.serviceUuids

            val isCompatible = isDeviceNameOrServiceCompatible(rawName, serviceUuids)

            val displayName = when {
                rawName.isNotBlank() -> rawName
                isCompatible -> "aponorm® / Microlife Messgerät"
                else -> "Bluetooth-Gerät ($address)"
            }

            discoveredDevicesMap[address] = DiscoveredBleDevice(
                name = displayName,
                address = address,
                rssi = rssi,
                isMicrolife = isCompatible
            )

            val updatedList = discoveredDevicesMap.values.toList().sortedByDescending { it.isMicrolife }
            _syncStatus.value = BleSyncStatus.DiscoveredDevices(updatedList)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with code $errorCode")
            _syncStatus.value = BleSyncStatus.Error("BLE-Scan fehlgeschlagen (Code $errorCode)")
        }
    }

    private var is12HourTimeFormat: Boolean = false
    private var isOnlyTimeSyncMode: Boolean = false
    private var isOnlyReadTimeMode: Boolean = false
    private var measurementRequested: Boolean = false
    private var timeSyncAckSent: Boolean = false

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        address: String,
        is12HourFormat: Boolean = false,
        onlyTimeSync: Boolean = false,
        onlyReadTime: Boolean = false
    ) {
        this.is12HourTimeFormat = is12HourFormat
        this.isOnlyTimeSyncMode = onlyTimeSync
        this.isOnlyReadTimeMode = onlyReadTime
        this.isDataDownloadCompleted = false
        this.isErasingOrFinishing = false
        this.measurementRequested = false
        this.timeSyncAckSent = false
        stopScan()
        receivedBatch.clear()
        dataBuffer.reset()
        expectedTotalSize = 0
        realGattConnected = false
        currentState = BleState.IDLE

        val modeStr = when {
            onlyTimeSync -> " (Reine Uhrzeit-Einstellung)"
            onlyReadTime -> " (Reine Uhrzeit-Auslesung)"
            else -> " (Messdaten-Download / Ringspeicher)"
        }
        logDiagnose("Verbindungsaufbau initiiert zu MAC-Adresse: $address$modeStr")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            logDiagnose("❌ FEHLER: Bluetooth am Smartphone ist nicht aktiviert!")
            _syncStatus.value = BleSyncStatus.Error("Bluetooth ist auf dem Gerät nicht aktiviert.")
            return
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }

        if (device == null) {
            logDiagnose("❌ FEHLER: Ungültige Bluetooth-Adresse $address")
            _syncStatus.value = BleSyncStatus.Error("Ungültige Bluetooth-Adresse: $address")
            return
        }

        val devName = device.name ?: "aponorm® / Microlife"
        logDiagnose("Verbinde mit GATT Server ($devName, Transport LE)...")
        _syncStatus.value = BleSyncStatus.Connecting(devName)

        val bondStateStr = when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> "GEKOPPELT (BONDED)"
            BluetoothDevice.BOND_BONDING -> "KOPPLUNG LÄUFT (BONDING)"
            else -> "NICHT GEKOPPELT (BOND_NONE - Standard BLE)"
        }
        logDiagnose("✓ Geräte-Status: $bondStateStr. Starte LE GATT...")
        connectGattInternal(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectGattInternal(device: BluetoothDevice) {
        val timeoutRunnable = Runnable {
            if (_syncStatus.value is BleSyncStatus.Connecting && !realGattConnected) {
                Log.w(TAG, "GATT connection timeout for ${device.address}")
                disconnect()
                _syncStatus.value = BleSyncStatus.Error("Verbindung zum Aponorm-Gerät fehlgeschlagen (Timeout). Bitte schalten Sie das Gerät ein.")
            }
        }
        handler.postDelayed(timeoutRunnable, 10000)

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Zwingt Android, AUSSCHLIESSLICH den LE-Transportweg zu nutzen.
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "GATT connect error", e)
            handler.removeCallbacks(timeoutRunnable)
            _syncStatus.value = BleSyncStatus.Error("GATT Verbindungsfehler: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        connectToDevice(device.address)
    }

    @SuppressLint("MissingPermission")
    fun connectToDeviceForDiagnose(device: BluetoothDevice) {
        connectToDevice(device.address)
    }

    // Zentrale Hilfsfunktionen zum Senden von BLE-Paketen
    private fun findWriteCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val rxService = gatt.getService(SERVICE_UUID)
            ?: gatt.getService(APONORM_FFE0_SERVICE_UUID)

        var char = rxService?.getCharacteristic(RX_CHAR_UUID)
            ?: rxService?.getCharacteristic(TX_CHAR_UUID)
            ?: rxService?.getCharacteristic(APONORM_FFE1_CHAR_UUID)

        if (char != null) return char

        for (service in gatt.services) {
            for (c in service.characteristics) {
                val props = c.properties
                if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                ) {
                    Log.d(TAG, "Dynamisch schreibbare Charakteristik gefunden: ${c.uuid} in Service ${service.uuid} (props=$props)")
                    return c
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun sendPacket(gatt: BluetoothGatt, bytes: ByteArray, writeNoResponse: Boolean = true) {
        val rxChar = findWriteCharacteristic(gatt)
        if (rxChar != null) {
            val writeType = if (writeNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            rxChar.writeType = writeType

            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(rxChar, bytes, writeType) == BluetoothGatt.GATT_SUCCESS
            } else {
                rxChar.value = bytes
                gatt.writeCharacteristic(rxChar)
            }

            val typeLabel = if (writeNoResponse) "WRITE_NO_RESPONSE" else "WRITE_TYPE_DEFAULT"
            logDiagnose("TX ($typeLabel) -> ${bytes.joinToString(" ") { "%02X".format(it) }} (an ${rxChar.uuid}, ok=$success)")
            Log.d(TAG, "sendPacket ($typeLabel, len=${bytes.size}, success=$success): ${bytes.joinToString(" ") { "%02X".format(it) }}")

            if (writeNoResponse) {
                handler.postDelayed({
                    commandCompleted()
                }, 300)
            }
        } else {
            logDiagnose("❌ FEHLER: Keine schreibbare Characteristic gefunden!")
            Log.e(TAG, "sendPacket: Keine RX Characteristic gefunden!")
            commandCompleted()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                realGattConnected = true
                currentState = BleState.CONNECTED
                handler.removeCallbacksAndMessages(null)
                commandQueue.clear()
                isCommandPending = false

                val devName = gatt.device.name ?: "aponorm® / Microlife"
                val devAddr = gatt.device.address
                val bondStateStr = when (gatt.device.bondState) {
                    BluetoothDevice.BOND_BONDED -> "GEKOPPELT (BONDED)"
                    BluetoothDevice.BOND_BONDING -> "KOPPLUNG LÄUFT (BONDING)"
                    else -> "NICHT GEKOPPELT (NONE)"
                }

                Log.d(TAG, "Samsung A55 physisch verbunden. Erzwinge MTU-Request auf Hauptthread...")
                logDiagnose("◆ GERÄT PHYSISCH VERBUNDEN: $devName ($devAddr)")
                logDiagnose("   ├-- Sicherheits-Status: $bondStateStr")
                logDiagnose("   └-- Samsung A55 Fix: Erzwinge MTU-Request (40 Bytes) auf Hauptthread (MainLooper)...")

                _syncStatus.value = BleSyncStatus.Connecting(devName)

                // Samsung Knox benötigt hier zwingend den MainLooper, sonst schlägt der Request fehl!
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentState == BleState.CONNECTED && realGattConnected) {
                        try {
                            val success = gatt.requestMtu(40) // 40 Bytes reichen völlig für das Zeitpaket
                            Log.d(TAG, "MTU-Anforderung abgesetzt: $success")
                            logDiagnose("▶ MTU-Anforderung (40 Bytes) auf MainLooper abgesetzt: $success")
                            if (!success) {
                                logDiagnose("   └-- requestMtu() lieferte false, starte Service-Suche direkt...")
                                gatt.discoverServices()
                            }
                        } catch (e: Exception) {
                            logDiagnose("❌ Fehler bei requestMtu: ${e.message}")
                            gatt.discoverServices()
                        }
                    }
                }, 600) // 600ms Pause nach Verbindungsaufbau

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logDiagnose("ℹ️ Verbindung beendet (Status: $status).")
                commandQueue.clear()
                isCommandPending = false
                realGattConnected = false
                currentState = BleState.IDLE
                bluetoothGatt = null

                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing GATT on disconnect", e)
                }

                if (_syncStatus.value !is BleSyncStatus.Success &&
                    _syncStatus.value !is BleSyncStatus.Error
                ) {
                    if (receivedBatch.isNotEmpty()) {
                        scope.launch {
                            completeBatchAndFinish()
                        }
                    } else {
                        _syncStatus.value = BleSyncStatus.Error("Keine Messdaten vom Microlife / aponorm® Gerät empfangen.")
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "Samsung MTU-Status erhalten -> Aktuelle MTU: $mtu Bytes (Status: $status)")
            logDiagnose("✓ Samsung MTU-Status erhalten -> Aktuelle MTU: $mtu Bytes (Status: $status). Suche jetzt Dienste...")
            commandCompleted()
            queueOperation {
                logDiagnose("▶ Starte GATT Service-Suche...")
                try {
                    val disc = gatt.discoverServices()
                    if (!disc) {
                        logDiagnose("   └-- discoverServices() ergab false")
                        commandCompleted()
                    }
                } catch (e: Exception) {
                    logDiagnose("   └-- Service-Discovery Fehler: ${e.message}")
                    commandCompleted()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            commandCompleted()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logDiagnose("❌ Fehler bei Service-Suche. Status: $status")
                _syncStatus.value = BleSyncStatus.Error("Services konnten nicht entdeckt werden (Status $status).")
                return
            }

            logDiagnose("\n=================================")
            logDiagnose("   TECHNISCHE GERÄTE-ANALYSE     ")
            logDiagnose("   Modell: Aponorm BP3Gu1-6B     ")
            logDiagnose("   Adresse: ${gatt.device.address} (${gatt.device.name ?: "Unbekannt"})")
            logDiagnose("=================================\n")

            // Jedes Detail der Hardware auslesen & live im Diagnosefenster ausgeben
            for (service in gatt.services) {
                val serviceName = when (service.uuid) {
                    SERVICE_UUID -> "Microlife Primary UART (0xFFF0)"
                    APONORM_FFE0_SERVICE_UUID -> "Aponorm Alt UART (0xFFE0)"
                    BLOOD_PRESSURE_SERVICE_UUID -> "Standard Blood Pressure (0x1810)"
                    DEVICE_INFO_SERVICE_UUID -> "Standard Device Information (0x180A)"
                    CURRENT_TIME_SERVICE_UUID -> "Standard Current Time (0x1805)"
                    SERVICE_FFF3_UUID -> "Microlife Config/RTC (0xFFF3)"
                    else -> "GATT Service"
                }
                logDiagnose("▶ $serviceName [${service.uuid}]")

                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val propList = mutableListOf<String>()

                    if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) propList.add("READ")
                    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) propList.add("WRITE")
                    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) propList.add("WRITE_NO_RESPONSE")
                    if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) propList.add("NOTIFY")
                    if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) propList.add("INDICATE")

                    val charName = when (characteristic.uuid) {
                        TX_CHAR_UUID -> "TX Stream / Notify (0xFFF1)"
                        RX_CHAR_UUID -> "RX Command / Write (0xFFF2)"
                        MODEL_NUMBER_CHAR_UUID -> "Model Number String (0x2A24)"
                        SERIAL_NUMBER_CHAR_UUID -> "Serial Number String (0x2A25)"
                        FIRMWARE_REV_CHAR_UUID -> "Firmware Revision (0x2A26)"
                        HARDWARE_REV_CHAR_UUID -> "Hardware Revision (0x2A27)"
                        MANUFACTURER_NAME_CHAR_UUID -> "Manufacturer Name (0x2A29)"
                        DATE_TIME_CHAR_UUID -> "SIG Date Time (0x2A08)"
                        CURRENT_TIME_CHAR_UUID -> "SIG Current Time (0x2A2B)"
                        else -> "Characteristic"
                    }

                    logDiagnose("   |-- $charName [${characteristic.uuid}]")
                    logDiagnose("   |   └-- Flags: ${propList.joinToString(", ")}")

                    for (desc in characteristic.descriptors) {
                        logDiagnose("   |       └-- Deskriptor: ${desc.uuid}")
                    }
                }
                logDiagnose("   -------------------------------------------------")
            }
            logDiagnose("\n=== GATT-ANALYSE VOLLSTÄNDIG ===")
            logDiagnose("Fahre mit der automatisierten Protokollkette fort...\n")

            // Normaler Modus (Messungen auslesen oder Uhrzeit einstellen): Schritt 3 in die Warteschlange
            queueOperation {
                logDiagnose("▶ SCHRITT 3: Aktiviere Benachrichtigungen (CCCD Descriptor)...")
                enableNotifications(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableNotifications(gatt: BluetoothGatt) {
            // Priorisiere explizit die Microlife / Aponorm TX Characteristic FFF1
            val fff0Service = gatt.getService(SERVICE_UUID)
            val fff1Char = fff0Service?.getCharacteristic(TX_CHAR_UUID)

            if (fff1Char != null) {
                logDiagnose("▶ Aktiviere CCCD (0x2902) für primäre Microlife TX-Charakteristik: $TX_CHAR_UUID...")
                gatt.setCharacteristicNotification(fff1Char, true)
                val cccd = fff1Char.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    val descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    cccd.value = descriptorValue
                    val written = writeGattDescriptor(gatt, cccd, descriptorValue)
                    Log.d(TAG, "CCCD für FFF1 geschrieben: $written")
                    return
                }
            }

            // Fallback für alternative Services/Characteristics
            val targetServiceUuids = listOf(APONORM_FFE0_SERVICE_UUID, BLOOD_PRESSURE_SERVICE_UUID)
            var writtenAny = false
            for (serviceUuid in targetServiceUuids) {
                val service = gatt.getService(serviceUuid) ?: continue

                for (char in service.characteristics) {
                    val props = char.properties
                    val supportsNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    val supportsIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                    if (supportsNotify || supportsIndicate) {
                        gatt.setCharacteristicNotification(char, true)
                        val cccd = char.getDescriptor(CCCD_UUID)
                        if (cccd != null) {
                            val descriptorValue = if (supportsIndicate) {
                                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            } else {
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            }
                            cccd.value = descriptorValue
                            val written = writeGattDescriptor(gatt, cccd, descriptorValue)
                            Log.d(TAG, "CCCD für ${char.uuid} geschrieben: $written")
                            writtenAny = true
                            break
                        }
                    }
                }
                if (writtenAny) break
            }
            if (!writtenAny) {
                Log.w(TAG, "Keine passende Notify/Indicate Characteristic für CCCD gefunden!")
                commandCompleted()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            commandCompleted()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logDiagnose("⚠️ onDescriptorWrite fehlgeschlagen: status=$status")
                return
            }
            logDiagnose("✓ Deskriptor (CCCD) erfolgreich aktiviert.")
            
            // Fall 1: Reine Uhrzeit-Auslesung (Analyse-Modus)
            if (isOnlyReadTimeMode) {
                isOnlyReadTimeMode = false
                logDiagnose("▶ Analysiere Geräte-Uhrzeit & interner RTC-Status...")
                sendReadDeviceTime(gatt)
                return
            }

            // Fall 2: Reiner Uhrzeit-Synchronisationsmodus (über Button "Uhr synchronisieren")
            if (isOnlyTimeSyncMode) {
                logDiagnose("▶ Reiner Uhrzeit-Modus: Starte Uhrzeitsynchronisation (Opcode 0x03, Basisjahr 2022)...")
                sendTimeSynchronization(gatt)
                return
            }

            // Fall 3: Normaler Messdaten-Download (Reiner Datenstrom ohne Uhrzeitbefehl)
            logDiagnose("▶ Stream-Kanal bereit. Fordere Messdaten direkt vom Aponorm Gerät an (reiner Datenstrom)...")
            _syncStatus.value = BleSyncStatus.Downloading(0, 1)
            sendPacket(gatt, CMD_GET_MEASUREMENTS, writeNoResponse = true)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val bytes = characteristic.value ?: ByteArray(0)
            val hexString = bytes.joinToString(" ") { "%02X".format(it) }
            logDiagnose("📖 READ von ${characteristic.uuid}: $hexString (${bytes.size} Bytes, Status=$status)")
            if (characteristic.uuid == DATE_TIME_CHAR_UUID && bytes.size >= 7) {
                val year = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
                val month = bytes[2].toInt() and 0xFF
                val day = bytes[3].toInt() and 0xFF
                val hour = bytes[4].toInt() and 0xFF
                val minute = bytes[5].toInt() and 0xFF
                val second = bytes[6].toInt() and 0xFF
                logDiagnose("🔍 Bluetooth SIG Standard Date/Time: %02d.%02d.%04d %02d:%02d:%02d".format(day, month, year, hour, minute, second))
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val charUuid = characteristic.uuid
            val charUuidStr = charUuid.toString().lowercase()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logDiagnose("✓ Befehl quittiert auf $charUuid (Status: 0)")
                if (charUuidStr.contains("fff2") || charUuidStr.contains("fff5")) {
                    if (isOnlyTimeSyncMode) {
                        if (!timeSyncAckSent) {
                            timeSyncAckSent = true
                            logDiagnose("✓ Uhrzeit-Befehl erfolgreich an das Aponorm Gerät übertragen.")
                            logDiagnose("▶ Warte auf Abschluss der Geräte-Verarbeitung (Display-Uhrzeit / Piepton)...")
                            _syncStatus.value = BleSyncStatus.Success(0)
                            // Nicht sofort hart trennen, damit das Gerät nicht mit 'FL' abbricht, sondern die RTC speichert!
                            handler.postDelayed({
                                if (bluetoothGatt != null && realGattConnected) {
                                    logDiagnose("ℹ️ Beende Zeit-Einstellungssitzung regulär.")
                                    disconnect()
                                }
                            }, 8000)
                        }
                    }
                }
            } else {
                logDiagnose("⚠️ onCharacteristicWrite: status=$status (char=$charUuid)")
            }
            commandCompleted()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val packet = characteristic.value ?: ByteArray(0)
            handleIncomingPacket(gatt, characteristic, packet)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPacket(gatt, characteristic, value)
        }

        @SuppressLint("MissingPermission")
        private fun handleIncomingPacket(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            packet: ByteArray
        ) {
            if (packet.isEmpty()) return

            val charUuidStr = characteristic.uuid.toString().lowercase()
            if (charUuidStr == "0000fff1-0000-1000-8000-00805f9b34fb" ||
                charUuidStr == "0000ffe1-0000-1000-8000-00805f9b34fb" ||
                charUuidStr == "0000fff2-0000-1000-8000-00805f9b34fb"
            ) {
                val hexString = packet.joinToString(" ") { "%02X".format(it) }
                logDiagnose("RX <- $hexString (${packet.size} Bytes von ${characteristic.uuid})")

                // Protokoll-Analyse für empfangene Microlife/Aponorm Pakete ('M' = 0x4D)
                if (packet.isNotEmpty() && packet[0] == 0x4D.toByte()) {
                    analyzeAndLogIncomingPacketStructure(packet)
                }

                // Wenn Download bereits abgeschlossen ist oder Speicher gelöscht wird, keine weiteren Messungen parsen
                if (isDataDownloadCompleted || isErasingOrFinishing) {
                    return
                }

                // Handshake 0x81 (129) Bestätigung (Zeit-Sync Quittung)
                if (packet.size == 1 && (packet[0].toInt() and 0xFF) == 129) {
                    logDiagnose("✓ Handshake-Antwort 0x81 (129) empfangen! Uhrzeit erfolgreich synchronisiert. Fordere Messdaten an...")
                    dataBuffer.reset()
                    expectedTotalSize = 0
                    _syncStatus.value = BleSyncStatus.Downloading(0, 1)
                    sendPacket(gatt, CMD_GET_MEASUREMENTS, writeNoResponse = true)
                    return
                }

                // 1. Paket in den Sammel-Buffer schreiben
                try {
                    dataBuffer.write(packet)
                } catch (e: Exception) {
                    Log.e("Aponorm", "Buffer-Fehler", e)
                    logDiagnose("❌ Buffer-Fehler: ${e.message}")
                }

                val currentBuffer = dataBuffer.toByteArray()
                Log.d("Aponorm", "Paket erhalten. Buffer-Größe: ${currentBuffer.size} Bytes")

                // 2. Header auswerten, um die erwartete Gesamtgröße zu erfahren ("M1" = 0x4D 0x31 oder "M:" = 0x4D 0x3A oder "M" 0xFF)
                if (currentBuffer.size >= 4 && expectedTotalSize == 0) {
                    if (currentBuffer[0] == 0x4D.toByte() && (currentBuffer[1] == 0x31.toByte() || currentBuffer[1] == 0x3A.toByte() || currentBuffer[1] == 0xFF.toByte())) {
                        val payloadLength = ((currentBuffer[2].toInt() and 0xFF) * 256) + (currentBuffer[3].toInt() and 0xFF)
                        if (payloadLength in 1..4096) {
                            expectedTotalSize = payloadLength + 4 // Payload + 4 Bytes Header
                            Log.d("Aponorm", "Gerät kündigt Gesamtgröße an: $expectedTotalSize Bytes")
                            logDiagnose("Gerät kündigt Gesamtgröße an: $expectedTotalSize Bytes")
                        }
                    }
                }

                scheduleStreamFinish()

                // 3. Wenn angekündigte Größe erreicht ist, parsen
                if (expectedTotalSize > 0 && currentBuffer.size >= expectedTotalSize) {
                    if (expectedTotalSize == 5 && (currentBuffer[4].toInt() and 0xFF) == 129) {
                        logDiagnose("✓ Handshake-Antwort 0x81 (129) empfangen! Uhrzeit erfolgreich synchronisiert. Fordere Messdaten an...")
                        dataBuffer.reset()
                        expectedTotalSize = 0
                        _syncStatus.value = BleSyncStatus.Downloading(0, 1)
                        sendPacket(gatt, CMD_GET_MEASUREMENTS, writeNoResponse = true)
                        return
                    }

                    Log.d("Aponorm", "Download vollständig ($expectedTotalSize Bytes). Starte Analyse...")
                    logDiagnose("✓ Download vollständig (${currentBuffer.size} Bytes). Starte Analyse...")
                    parseAponormDataStream(currentBuffer, currentBuffer.size)

                    dataBuffer.reset()
                    expectedTotalSize = 0
                }
            } else if (characteristic.uuid == BP_MEASUREMENT_CHAR_UUID) {
                // Standard Bluetooth SIG GATT 0x2A35 Fallback
                val gattMeasurement = parseGattBpMeasurement(packet)
                if (gattMeasurement != null) {
                    if (receivedBatch.none { it.timestamp == gattMeasurement.timestamp && it.systole == gattMeasurement.systole }) {
                        receivedBatch.add(gattMeasurement)
                        logDiagnose("📊 GATT: sys ${gattMeasurement.systole} mmHg, dia ${gattMeasurement.diastole} mmHg, puls ${gattMeasurement.pulse} /min")
                        _syncStatus.value = BleSyncStatus.Downloading(receivedBatch.size, receivedBatch.size + 1)
                        scheduleStreamFinish()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeGattDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                descriptor.value = value
                gatt.writeDescriptor(descriptor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing descriptor", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeGattChar(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        return try {
            // BP3Gu1-6B / aponorm Chips erfordern zwingend WRITE_TYPE_NO_RESPONSE
            val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            char.writeType = writeType

            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, bytes, writeType) == BluetoothGatt.GATT_SUCCESS
            } else {
                char.value = bytes
                gatt.writeCharacteristic(char)
            }
            Log.d(TAG, "writeGattChar auf ${char.uuid} (writeType=WRITE_TYPE_NO_RESPONSE, len=${bytes.size}): $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error writing characteristic ${char.uuid}", e)
            false
        }
    }

    /**
     * SFLOAT Decoder für Bluetooth SIG IEEE 11073 (16-Bit Float)
     */
    private fun decodeSFloat(byteLow: Byte, byteHigh: Byte): Float {
        val b0 = byteLow.toInt() and 0xFF
        val b1 = byteHigh.toInt() and 0xFF
        val mantissa = b0 or ((b1 and 0x0F) shl 8)
        val signedMantissa = if ((mantissa and 0x0800) != 0) {
            mantissa or -0x1000
        } else {
            mantissa
        }
        val rawExponent = (b1 shr 4) and 0x0F
        val signedExponent = if ((rawExponent and 0x08) != 0) {
            rawExponent or -0x10
        } else {
            rawExponent
        }
        return signedMantissa * Math.pow(10.0, signedExponent.toDouble()).toFloat()
    }

    /**
     * Standard Bluetooth SIG Blood Pressure Measurement (0x2A35) Frame Decoder
     */
    private fun parseGattBpMeasurement(bytes: ByteArray): BpMeasurement? {
        if (bytes.size < 7) return null
        val flags = bytes[0].toInt() and 0xFF
        val isKpa = (flags and 0x01) != 0
        val hasTimestamp = (flags and 0x02) != 0
        val hasPulse = (flags and 0x04) != 0
        val hasStatus = (flags and 0x10) != 0

        var sys = decodeSFloat(bytes[1], bytes[2])
        var dia = decodeSFloat(bytes[3], bytes[4])
        if (isKpa) {
            sys *= 7.50062f
            dia *= 7.50062f
        }

        var offset = 7
        var timestamp = System.currentTimeMillis()

        if (hasTimestamp && bytes.size >= offset + 7) {
            val year = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            val month = bytes[offset + 2].toInt() and 0xFF
            val day = bytes[offset + 3].toInt() and 0xFF
            val hour = bytes[offset + 4].toInt() and 0xFF
            val minute = bytes[offset + 5].toInt() and 0xFF
            val second = bytes[offset + 6].toInt() and 0xFF

            if (year in 2000..2099 && month in 1..12 && day in 1..31) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, second)
                }
                timestamp = cal.timeInMillis
            }
            offset += 7
        }

        var pulse = 70
        if (hasPulse && bytes.size >= offset + 2) {
            pulse = decodeSFloat(bytes[offset], bytes[offset + 1]).toInt()
            offset += 2
        }

        var afib = false
        if (hasStatus && bytes.size >= offset + 2) {
            val statusFlags = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            afib = (statusFlags and 0x0001 != 0) || (statusFlags and 0x0020 != 0)
        }

        val sysInt = sys.toInt()
        val diaInt = dia.toInt()
        if (sysInt !in 40..260 || diaInt !in 30..180) return null

        return BpMeasurement(
            timestamp = timestamp,
            systole = sysInt,
            diastole = diaInt,
            pulse = if (pulse in 30..220) pulse else 70,
            afibDetected = afib,
            notes = "aponorm® / Microlife Messung"
        )
    }

    /**
     * Echte Messwerte aus dem Aponorm / Microlife BLE-Datenstrom dekodieren
     */
    private fun parseBloodPressureData(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return

        try {
            // Versuch 1: Aponorm 10-Byte Blockdatenstrom ab Byte 38
            if (bytes.size >= 48) {
                var foundAny = false
                var i = 38
                while (i + 8 <= bytes.size) {
                    val sys = bytes[i].toInt() and 0xFF
                    val dia = bytes[i + 1].toInt() and 0xFF
                    val pulse = bytes[i + 2].toInt() and 0xFF

                    val yByte = bytes[i + 3].toInt() and 0xFF
                    val mByte = bytes[i + 4].toInt() and 0xFF
                    val dByte = bytes[i + 5].toInt() and 0xFF
                    val hourByte = bytes[i + 6].toInt() and 0xFF
                    val minByte = bytes[i + 7].toInt() and 0xFF

                    if (sys in 40..260 && dia in 30..180 && mByte in 1..12 && dByte in 1..31 && hourByte in 0..23 && minByte in 0..59) {
                        val year = if (yByte < 100) yByte + 2000 else yByte
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, mByte - 1)
                            set(Calendar.DAY_OF_MONTH, dByte)
                            set(Calendar.HOUR_OF_DAY, hourByte)
                            set(Calendar.MINUTE, minByte)
                            set(Calendar.SECOND, 0)
                        }

                        val measurement = BpMeasurement(
                            timestamp = cal.timeInMillis,
                            systole = sys,
                            diastole = dia,
                            pulse = if (pulse in 30..220) pulse else 70,
                            afibDetected = false,
                            notes = "aponorm® / Microlife Messung"
                        )

                        if (receivedBatch.none { it.timestamp == measurement.timestamp && it.systole == measurement.systole }) {
                            receivedBatch.add(measurement)
                            val isPm = (hourByte and 0x80) != 0
                            val logLine = "%04d-%02d-%02d %02d:%02d  SYS: %d DIA: %d PULS: %d".format(
                                year, mByte, dByte, hourByte, minByte, sys, dia, pulse
                            )
                            logDiagnose("📊 $logLine")
                            logDiagnose("   └-- Roh-Zeitstempel: [YY=0x%02X (%d), MM=0x%02X (%d), DD=0x%02X (%d), HH=0x%02X (%d), MIN=0x%02X (%d) | PM-Bit: %b]".format(
                                yByte, yByte, mByte, mByte, dByte, dByte, hourByte, hourByte, minByte, minByte, isPm
                            ))
                            Log.i(TAG, "Messung ab Offset $i: $logLine")
                            foundAny = true
                        }
                    }
                    i += 10
                }

                if (foundAny) {
                    _syncStatus.value = BleSyncStatus.Downloading(receivedBatch.size, receivedBatch.size + 1)
                    scheduleStreamFinish()
                    return
                }
            }

            // Versuch 2: Standard GATT 0x2A35 Frame
            val gattMeasurement = parseGattBpMeasurement(bytes)
            if (gattMeasurement != null) {
                if (receivedBatch.none { it.timestamp == gattMeasurement.timestamp && it.systole == gattMeasurement.systole }) {
                    receivedBatch.add(gattMeasurement)
                    logDiagnose("📊 GATT: sys ${gattMeasurement.systole} mmHg, dia ${gattMeasurement.diastole} mmHg, pulse ${gattMeasurement.pulse} /min")
                    Log.i(TAG, "Echte GATT Messung dekodiert: SYS=${gattMeasurement.systole}, DIA=${gattMeasurement.diastole}, Puls=${gattMeasurement.pulse}, AFIB=${gattMeasurement.afibDetected}")
                    _syncStatus.value = BleSyncStatus.Downloading(receivedBatch.size, receivedBatch.size + 1)
                    scheduleStreamFinish()
                }
                return
            }

            // Versuch 2: Proprietäres Microlife/Aponorm UART Paket (M: = 0x4D, 0x3A)
            if (bytes.size >= 5 && bytes[0] == 0x4D.toByte() && bytes[1] == 0x3A.toByte()) {
                val payloadLen = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
                val payloadStart = 4
                val availablePayload = minOf(payloadLen, bytes.size - 4 - 1) // ohne Checksum

                // Prüfen, ob Payload direkt mit Messung bei Index 4 startet oder bei Index 5 (nach Opcode 0xA3)
                val startOffsets = mutableListOf<Int>()
                if (bytes.size >= payloadStart + 8) {
                    val sysAt4 = bytes[payloadStart].toInt() and 0xFF
                    val diaAt4 = bytes[payloadStart + 1].toInt() and 0xFF
                    if (sysAt4 in 40..260 && diaAt4 in 30..180) {
                        startOffsets.add(payloadStart)
                    } else if (bytes.size >= payloadStart + 9) {
                        val sysAt5 = bytes[payloadStart + 1].toInt() and 0xFF
                        val diaAt5 = bytes[payloadStart + 2].toInt() and 0xFF
                        if (sysAt5 in 40..260 && diaAt5 in 30..180) {
                            startOffsets.add(payloadStart + 1)
                        }
                    }
                }

                for (baseOffset in startOffsets) {
                    var currentOffset = baseOffset
                    while (currentOffset + 8 <= bytes.size) {
                        val sysRaw = bytes[currentOffset].toInt() and 0xFF
                        val diaRaw = bytes[currentOffset + 1].toInt() and 0xFF
                        val pulseRaw = bytes[currentOffset + 2].toInt() and 0xFF

                        if (sysRaw !in 40..260 || diaRaw !in 30..180) {
                            break
                        }

                        val yByte = bytes[currentOffset + 3].toInt() and 0xFF
                        val mByte = bytes[currentOffset + 4].toInt() and 0xFF
                        val dByte = bytes[currentOffset + 5].toInt() and 0xFF
                        val hByte = bytes[currentOffset + 6].toInt() and 0xFF
                        val minByte = bytes[currentOffset + 7].toInt() and 0xFF

                        var year = Calendar.getInstance().get(Calendar.YEAR)
                        var month = Calendar.getInstance().get(Calendar.MONTH) + 1
                        var day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                        var hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        var minute = Calendar.getInstance().get(Calendar.MINUTE)

                        if (yByte in 1..99) year = 2000 + yByte
                        else if (yByte in 2000..2099) year = yByte
                        if (mByte in 1..12) month = mByte
                        if (dByte in 1..31) day = dByte
                        if (hByte in 0..23) hour = hByte
                        if (minByte in 0..59) minute = minByte

                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month - 1)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                        }

                        val measurement = BpMeasurement(
                            timestamp = cal.timeInMillis,
                            systole = sysRaw,
                            diastole = diaRaw,
                            pulse = if (pulseRaw in 30..220) pulseRaw else 70,
                            afibDetected = false,
                            notes = "aponorm® / Microlife (2021 BP3Gu1-6B)"
                        )

                        if (receivedBatch.none { it.timestamp == measurement.timestamp && it.systole == measurement.systole }) {
                            receivedBatch.add(measurement)
                            val isPm = (hByte and 0x80) != 0
                            val logLine = "%04d-%02d-%02d %02d:%02d  sys %d mmHg, dia %d mmHg, pulse %d /min".format(
                                year, month, day, hour, minute, measurement.systole, measurement.diastole, measurement.pulse
                            )
                            logDiagnose("📊 $logLine")
                            logDiagnose("   └-- Roh-Zeitstempel: [YY=0x%02X (%d), MM=0x%02X (%d), DD=0x%02X (%d), HH=0x%02X (%d), MIN=0x%02X (%d) | PM-Bit: %b]".format(
                                yByte, yByte, mByte, mByte, dByte, dByte, hByte, hByte, minByte, minByte, isPm
                            ))
                            Log.i(TAG, "Echte 2021er Microlife Messung dekodiert: $logLine")
                            _syncStatus.value = BleSyncStatus.Downloading(receivedBatch.size, receivedBatch.size + 1)
                            scheduleStreamFinish()
                        }

                        currentOffset += 8
                    }
                }
                if (receivedBatch.isNotEmpty()) return
            }

            // Fallback für sonstige UART-Paketstrukturen
            val offset = when {
                bytes.size >= 8 && bytes[0] == 0x4D.toByte() && (bytes[1] == 0x05.toByte() || bytes[1] == 0xFF.toByte()) -> 2
                bytes.size >= 8 && bytes[0] == 0x4D.toByte() -> 1
                else -> 0
            }

            if (bytes.size - offset < 3) return

            val sysRaw = bytes[offset].toInt() and 0xFF
            val diaRaw = bytes[offset + 1].toInt() and 0xFF
            val pulseRaw = bytes[offset + 2].toInt() and 0xFF

            if (sysRaw !in 40..260 || diaRaw !in 30..180) {
                Log.d(TAG, "Ignoriere Nicht-Messwert-Paket: SYS=$sysRaw DIA=$diaRaw")
                return
            }

            val dt = if (bytes.size - offset >= 8) {
                val yByte = bytes[offset + 3].toInt() and 0xFF
                val mByte = bytes[offset + 4].toInt() and 0xFF
                val dByte = bytes[offset + 5].toInt() and 0xFF
                val hByte = bytes[offset + 6].toInt() and 0xFF
                val minByte = bytes[offset + 7].toInt() and 0xFF
                decodeMicrolifeDateTime(yByte, mByte, dByte, hByte, minByte)
            } else {
                val now = Calendar.getInstance()
                DecodedDateTime(
                    year = now.get(Calendar.YEAR),
                    month = now.get(Calendar.MONTH) + 1,
                    day = now.get(Calendar.DAY_OF_MONTH),
                    hour24 = now.get(Calendar.HOUR_OF_DAY),
                    minute = now.get(Calendar.MINUTE),
                    isPm = now.get(Calendar.HOUR_OF_DAY) >= 12,
                    hour12 = now.get(Calendar.HOUR).let { if (it == 0) 12 else it },
                    timestamp = now.timeInMillis
                )
            }

            val timestamp = dt.timestamp

            val flags = if (bytes.size - offset >= 9) bytes[offset + 8].toInt() and 0xFF else 0
            val afib = (flags and 0x01 != 0) || (flags and 0x04 != 0) || (flags and 0x08 != 0)

            val measurement = BpMeasurement(
                timestamp = timestamp,
                systole = sysRaw,
                diastole = diaRaw,
                pulse = if (pulseRaw in 30..220) pulseRaw else 70,
                afibDetected = afib,
                notes = "aponorm® / Microlife Messung"
            )

            if (receivedBatch.none { it.timestamp == measurement.timestamp && it.systole == measurement.systole }) {
                receivedBatch.add(measurement)
                Log.i(TAG, "Echte Microlife Messung dekodiert (${dt.formatLogString()}): SYS=${measurement.systole}, DIA=${measurement.diastole}, Puls=${measurement.pulse}, AFIB=${measurement.afibDetected}")
                logDiagnose("📊 Einzeleintrag -> ${dt.formatLogString()} | SYS: ${measurement.systole} mmHg | DIA: ${measurement.diastole} mmHg")
                _syncStatus.value = BleSyncStatus.Downloading(receivedBatch.size, receivedBatch.size + 1)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing BP data stream", e)
        }
    }

    private val receivedBuffer = ByteArray(4096)
    private var bufferLength = 0
    private var streamEndRunnable: Runnable? = null

    // Hilfsmethode zur BCD-Konvertierung falls benötigt
    private fun decimalToBcd(valInt: Int): Byte {
        return (((valInt / 10) shl 4) or (valInt % 10)).toByte()
    }

    /**
     * Kodiert das Stunden-Byte für das Microlife / Aponorm Protokoll:
     * - Im 24h-Modus: 0x00 (0 Uhr) bis 0x17 (23 Uhr) als normaler Hex-Wert.
     * - Im 12h-Modus: 0x01 bis 0x0C (1 bis 12 Uhr).
     *   - AM (00:00 - 11:59): Hex-Wert 1 bis 12 (0x01..0x0C), PM-Bit 0x80 NICHT gesetzt.
     *     (00:00 Uhr = 12 AM -> 0x0C; 09:35 Uhr = 9 AM -> 0x09)
     *   - PM (12:00 - 23:59): Hex-Wert 1 bis 12 (0x01..0x0C) + 0x80 (Bit 7 gesetzt).
     *     (12:00 Uhr = 12 PM -> 0x0C + 0x80 = 0x8C; 21:35 Uhr = 9 PM -> 0x09 + 0x80 = 0x89)
     */
    fun encodeHourByte(hourOfDay: Int, is12HourMode: Boolean): Byte {
        return if (is12HourMode) {
            val hour12 = when {
                hourOfDay == 0 -> 12
                hourOfDay > 12 -> hourOfDay - 12
                else -> hourOfDay
            }
            val isPm = hourOfDay >= 12
            val finalVal = if (isPm) (hour12 or 0x80) else hour12
            (finalVal and 0xFF).toByte()
        } else {
            (hourOfDay and 0xFF).toByte()
        }
    }

    /**
     * Erstellt das Array für die aktuelle Smartphone-Uhrzeit
     * für das Aponorm BP3Gu1-6B / Microlife Gerät:
     * Reihenfolge: Jahr (26), Monat (1..12), Tag (1..31), Stunde, Minute (0..59)
     */
    fun buildTimeCommand(
        headerByte: Byte = 0x31.toByte(),
        opcode: Byte = 0x08.toByte(),
        useBcd: Boolean = false,
        is12HourMode: Boolean = false
    ): ByteArray {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) - 2000 // 26 für 2026
        val month = calendar.get(Calendar.MONTH) + 1  // 1-12
        val day = calendar.get(Calendar.DAY_OF_MONTH) // 1-31
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY) // 0-23
        val minute = calendar.get(Calendar.MINUTE)    // 0-59

        val yB = if (useBcd) decimalToBcd(year) else year.toByte()
        val mB = if (useBcd) decimalToBcd(month) else month.toByte()
        val dB = if (useBcd) decimalToBcd(day) else day.toByte()
        val hB = if (useBcd) decimalToBcd(encodeHourByte(hourOfDay, is12HourMode).toInt() and 0xFF) else encodeHourByte(hourOfDay, is12HourMode)
        val minB = if (useBcd) decimalToBcd(minute) else minute.toByte()

        // 11 Bytes: [0x4D, Header, 0x00, 0x06, Opcode, Jahr, Monat, Tag, Stunde, Minute, Checksum]
        val cmd = byteArrayOf(
            0x4D.toByte(),        // 'M'
            headerByte,           // 0x31 ('1' für M1 Modell) oder 0xFF
            0x00.toByte(),        // 0
            0x06.toByte(),        // Länge 6 (Opcode + 5 Bytes für Jahr, Monat, Tag, Stunde, Minute)
            opcode,               // 0x08 (SET_TIME) oder 0x0D
            yB,                   // Jahr (z.B. 26 -> 0x1A)
            mB,                   // Monat (z.B. 8 -> 0x08)
            dB,                   // Tag (z.B. 16 -> 0x10)
            hB,                   // Stunde (24h: 0x00..0x17, 12h: 0x01..0x0C + 0x80 bei PM)
            minB,                 // Minute (z.B. 45 -> 0x2D)
            0.toByte()            // Checksumme
        )

        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += (cmd[i].toInt() and 0xFF)
        }
        cmd[cmd.size - 1] = (sum and 0xFF).toByte()
        return cmd
    }

    /**
     * Erstellt das exakte 12-Byte Aponorm / Microlife Lokalzeit-Kommando (Opcode 0x03):
     * [0x4D, 0x31, 0x00, 0x07, 0x03, Jahr, Monat, Tag, Stunde_Lokal, Minute, Sekunde, Checksum]
     *
     * Beispiel aus Wireshark: 4d 31 00 07 03 1a 08 13 11 0a 00 d8 (19.08.2026 17:10:00 Uhr)
     * Checksumme = Summe aller vorherigen Bytes modulo 256.
     */
    fun buildAponormLocalTimeCommand(cal: Calendar = Calendar.getInstance()): ByteArray {
        val year = (cal.get(Calendar.YEAR) - 2000).toByte()
        val month = (cal.get(Calendar.MONTH) + 1).toByte()
        val day = cal.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = cal.get(Calendar.HOUR_OF_DAY).toByte()
        val minute = cal.get(Calendar.MINUTE).toByte()
        val second = cal.get(Calendar.SECOND).toByte()

        val cmd = byteArrayOf(
            0x4D.toByte(), // 'M'
            0x31.toByte(), // '1'
            0x00.toByte(),
            0x07.toByte(), // Länge 7
            0x03.toByte(), // Opcode 0x03: Setze RTC Lokalzeit
            year,
            month,
            day,
            hour,
            minute,
            second,
            0x00.toByte()  // Checksumme
        )

        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += (cmd[i].toInt() and 0xFF)
        }
        cmd[cmd.size - 1] = (sum and 0xFF).toByte()
        return cmd
    }

    /**
     * Erstellt das 12-Byte Aponorm / Microlife Zeit-Kommando mit Header 0xFF (Opcode 0x00):
     * [0x4D, 0xFF, 0x00, 0x08, 0x00, Jahr, Monat, Tag, Stunde, Minute, Sekunde, Checksum]
     * Verwendet die lokale Smartphone-Uhrzeit (24-Stunden-Format), damit das Gerät die exakte lokale Stunde anzeigt.
     */
    fun buildAponormHeaderFFTimeCommand(cal: Calendar = Calendar.getInstance()): ByteArray {
        val year = (cal.get(Calendar.YEAR) - 2000).toByte()
        val month = (cal.get(Calendar.MONTH) + 1).toByte()
        val day = cal.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = cal.get(Calendar.HOUR_OF_DAY).toByte()
        val minute = cal.get(Calendar.MINUTE).toByte()
        val second = cal.get(Calendar.SECOND).toByte()

        val cmd = byteArrayOf(
            0x4D.toByte(), // 'M'
            0xFF.toByte(), // Header 0xFF
            0x00.toByte(),
            0x08.toByte(), // Länge 8
            0x00.toByte(), // Opcode 0x00: Setze RTC-Zeit
            year,
            month,
            day,
            hour,
            minute,
            second,
            0x00.toByte()  // Checksumme
        )

        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += (cmd[i].toInt() and 0xFF)
        }
        cmd[cmd.size - 1] = (sum and 0xFF).toByte()
        return cmd
    }

    /**
     * Erstellt den klassischen 11-Byte Microlife Zeitbefehl (Opcode 0x08 oder 0x0D)
     */
    fun buildTimeCommand9Byte(
        headerByte: Byte = 0xFF.toByte(),
        opcode: Byte = 0xFE.toByte(),
        is12HourMode: Boolean = false
    ): ByteArray {
        val calendar = Calendar.getInstance()
        val year = (calendar.get(Calendar.YEAR) - 2000).toByte()
        val month = (calendar.get(Calendar.MONTH) + 1).toByte()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val hourByte = encodeHourByte(hourOfDay, is12HourMode)
        val minute = calendar.get(Calendar.MINUTE).toByte()
        val second = 0x00.toByte() // Microlife Blutdruckmessgeräte nutzen keine Sekunden (fest 0x00)

        val cmd = byteArrayOf(
            0x4D.toByte(),
            headerByte,
            0x00.toByte(),
            0x09.toByte(),
            year,
            month,
            day,
            hourByte,
            minute,
            second,
            0x00.toByte(),
            opcode,
            0.toByte()
        )

        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += (cmd[i].toInt() and 0xFF)
        }
        cmd[cmd.size - 1] = (sum and 0xFF).toByte()
        return cmd
    }

    /**
     * Sendet die vollständige Microlife / Aponorm Zeit-Synchronisations-Sequenz:
     * 1. Aponorm 12-Byte Lokalzeit-Paket (Opcode 0x03): [0x4D, 0x31, 0x00, 0x07, 0x03, YY, MM, DD, HH, MI, SS, CS]
     * 2. Aponorm 11-Byte M1/A6 Zeit-Paket (Opcode 0x08): [0x4D, 0x31, 0x00, 0x06, 0x08, YY, MM, DD, HH, MI, CS]
     * 3. Aponorm 12-Byte Header 0xFF Zeit-Paket (Opcode 0x00): [0x4D, 0xFF, 0x00, 0x08, 0x00, YY, MM, DD, HH, MI, SS, CS]
     * 4. Microlife 13-Byte Zeit-Paket (Header 0xFF, Opcode 0xFE): [0x4D, 0xFF, 0x00, 0x09, YY, MM, DD, HH, MI, SS, 0x00, 0xFE, CS]
     */
    @SuppressLint("MissingPermission")
    fun sendTimeSynchronization(gatt: BluetoothGatt? = bluetoothGatt) {
        val activeGatt = gatt ?: bluetoothGatt
        if (activeGatt == null) {
            logDiagnose("⚠️ Zeit-Synchronisation nicht möglich: Keine aktive GATT-Verbindung.")
            return
        }

        _syncStatus.value = BleSyncStatus.TimeSyncing
        val writeChar = activeGatt.getService(SERVICE_UUID)?.getCharacteristic(RX_CHAR_UUID)
            ?: findWriteCharacteristic(activeGatt)

        if (writeChar == null) {
            logDiagnose("❌ Schreibkanal FFF2 nicht gefunden!")
            return
        }

        val localCal = Calendar.getInstance()

        val localStr = String.format("%02d.%02d.%04d %02d:%02d:%02d",
            localCal.get(Calendar.DAY_OF_MONTH),
            localCal.get(Calendar.MONTH) + 1,
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.HOUR_OF_DAY),
            localCal.get(Calendar.MINUTE),
            localCal.get(Calendar.SECOND)
        )
        logDiagnose("▶ Sende Smartphone-Echtzeit an RTC: $localStr...")

        // 1. Aponorm Lokalzeit-Befehl (Opcode 0x03, 12 Bytes)
        val localCmd = buildAponormLocalTimeCommand(localCal)
        val localHex = localCmd.joinToString(" ") { "%02X".format(it) }
        logDiagnose("   ├-- 1/4 Sende Aponorm RTC-Paket (Opcode 0x03, 12 Bytes): $localHex")
        sendPacket(activeGatt, localCmd, writeNoResponse = true)

        // 2. Aponorm / Microlife 11-Byte Zeit-Befehl (Opcode 0x08) nach 180ms
        handler.postDelayed({
            if (bluetoothGatt != null && realGattConnected) {
                val timeCmd11 = buildTimeCommand(headerByte = 0x31.toByte(), opcode = 0x08.toByte(), is12HourMode = false)
                val hexStr11 = timeCmd11.joinToString(" ") { "%02X".format(it) }
                logDiagnose("   ├-- 2/4 Sende M1/A6 Zeit-Format (11 Bytes, Opcode 0x08): $hexStr11")
                sendPacket(activeGatt, timeCmd11, writeNoResponse = true)
            }
        }, 180)

        // 3. Aponorm Header 0xFF Zeit-Befehl (Opcode 0x00) nach 360ms
        handler.postDelayed({
            if (bluetoothGatt != null && realGattConnected) {
                val ffCmd = buildAponormHeaderFFTimeCommand(localCal)
                val ffHex = ffCmd.joinToString(" ") { "%02X".format(it) }
                logDiagnose("   ├-- 3/4 Sende Header 0xFF Zeit-Befehl (Opcode 0x00): $ffHex")
                sendPacket(activeGatt, ffCmd, writeNoResponse = true)
            }
        }, 360)

        // 4. Microlife 13-Byte Zeit-Befehl (Opcode 0xFE) nach 540ms
        handler.postDelayed({
            if (bluetoothGatt != null && realGattConnected) {
                val timeCmd13 = buildTimeCommand9Byte(headerByte = 0xFF.toByte(), opcode = 0xFE.toByte(), is12HourMode = false)
                val hexStr13 = timeCmd13.joinToString(" ") { "%02X".format(it) }
                logDiagnose("   └-- 4/4 Sende 9-Payload Zeit-Format (13 Bytes, Opcode 0xFE): $hexStr13")
                sendPacket(activeGatt, timeCmd13, writeNoResponse = true)
            }
        }, 540)
    }

    /**
     * Erstellt den Microlife RTC / Geräte-Uhrzeit Abfragebefehl (Opcode 0xFB im 9-Byte Format)
     * [0x4D, Header, 0x00, 0x09, 0, 0, 0, 0, 0, 0, 0, 0xFB, Checksum]
     */
    fun buildReadTimeCommand(headerByte: Byte = 0xFF.toByte()): ByteArray {
        val cmd = byteArrayOf(
            0x4D.toByte(),
            headerByte,
            0x00.toByte(),
            0x09.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xFB.toByte(), // 0xFB = GET_TIME / READ_RTC
            0.toByte()
        )
        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += (cmd[i].toInt() and 0xFF)
        }
        cmd[cmd.size - 1] = (sum and 0xFF).toByte()
        return cmd
    }

    /**
     * Erstellt den Microlife Geräte-Info / Versions-Abfragebefehl (Opcode 0xFA im 9-Byte Format)
     */
    fun buildReadVersionCommand(headerByte: Byte = 0xFF.toByte()): ByteArray {
        val cmd = byteArrayOf(
            0x4D.toByte(),
            headerByte,
            0x00.toByte(),
            0x09.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xFA.toByte(), // 0xFA = GET_VERSION
            0.toByte()
        )
        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += (cmd[i].toInt() and 0xFF)
        }
        cmd[cmd.size - 1] = (sum and 0xFF).toByte()
        return cmd
    }

    /**
     * Liest die Geräte-Uhrzeit und interne RTC-Register über BLE aus.
     */
    @SuppressLint("MissingPermission")
    fun sendReadDeviceTime(gatt: BluetoothGatt? = bluetoothGatt) {
        val activeGatt = gatt ?: bluetoothGatt
        if (activeGatt == null) {
            logDiagnose("⚠️ Keine aktive GATT-Verbindung zum Lesen der Geräte-Uhrzeit.")
            return
        }

        logDiagnose("🔍 Sende Abfrage für interne Geräte-Uhrzeit (Opcode 0xFB / GET_RTC)...")

        // 1. Microlife 9-Byte GET_RTC Befehl (Header 0xFF, Opcode 0xFB)
        val readCmdFF = buildReadTimeCommand(headerByte = 0xFF.toByte())
        sendPacket(activeGatt, readCmdFF, writeNoResponse = true)

        // 2. M1-Modell 9-Byte GET_RTC Befehl (Header 0x31, Opcode 0xFB) nach 200ms
        handler.postDelayed({
            if (bluetoothGatt != null && realGattConnected) {
                val readCmd31 = buildReadTimeCommand(headerByte = 0x31.toByte())
                sendPacket(activeGatt, readCmd31, writeNoResponse = true)
            }
        }, 200)

        // 3. Standard Bluetooth SIG Date/Time (0x2A08) direkt auslesen
        handler.postDelayed({
            if (bluetoothGatt != null && realGattConnected) {
                try {
                    val dateTimeChar = activeGatt.getService(BLOOD_PRESSURE_SERVICE_UUID)?.getCharacteristic(DATE_TIME_CHAR_UUID)
                    if (dateTimeChar != null) {
                        activeGatt.readCharacteristic(dateTimeChar)
                        logDiagnose("🔍 Lese Standard Bluetooth SIG 0x2A08 Date/Time Charakteristik...")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Standard 0x2A08 Date/Time read skipped: ${e.message}")
                }
            }
        }, 400)
    }

    /**
     * Startet den gezielten Diagnose-Vorgang "Geräte-Uhrzeit auslesen".
     * Verbindet sich mit dem Blutdruckmessgerät, fragt die interne RTC-Uhrzeit ab
     * und gibt die exakten Byte-Strukturen und Werte im Diagnoseprotokoll aus.
     */
    @SuppressLint("MissingPermission")
    fun sendManualReadDeviceTime(targetAddress: String? = null) {
        this.isOnlyReadTimeMode = true
        if (bluetoothGatt != null && realGattConnected) {
            logDiagnose("▶ Lese Geräte-Uhrzeit von aktiver GATT-Verbindung aus...")
            sendReadDeviceTime(bluetoothGatt)
        } else if (!targetAddress.isNullOrBlank()) {
            logDiagnose("▶ Baue Verbindung zu $targetAddress auf, um Geräte-Uhrzeit auszulesen...")
            connectToDevice(targetAddress, is12HourFormat = this.is12HourTimeFormat, onlyReadTime = true)
        } else {
            logDiagnose("⚠️ Keine Verbindung und keine Geräte-Adresse. Bitte zuerst 'Auslesen starten' tippen oder Gerät in Einstellungen wählen.")
        }
    }

    /**
     * Detaillierte Protokollanalyse für empfangene RX-Pakete vom Messgerät.
     */
    private fun analyzeAndLogIncomingPacketStructure(packet: ByteArray) {
        try {
            val hex = packet.joinToString(" ") { "%02X".format(it) }
            val headerChar = packet[0].toInt().toChar()
            val modelId = packet[1].toInt() and 0xFF
            val len = if (packet.size >= 4) ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF) else 0

            // Ist es ein 20-Byte Header-Paket des Gesamtdatenstroms? (z.B. 4D 31 02 1D ...)
            if (packet.size >= 4 && (packet[1] == 0x31.toByte() || packet[1] == 0x3A.toByte() || packet[1] == 0xFF.toByte()) && len > 20) {
                logDiagnose("🔬 GERÄTE-DATENSTROM-KOPF EMPFANGEN ($hex):")
                logDiagnose("   ├-- Modell-Kennung: 'M%c' (0x%02X 0x%02X)".format(modelId.toChar(), packet[0], modelId))
                logDiagnose("   ├-- Angekündigte Datengröße: $len Bytes (enthält alle gespeicherten Messwerte & Zeitstempel)")
                logDiagnose("   └-- Empfange jetzt die einzelnen Messdatenblöcke...")
                return
            }

            val opcode = if (packet.size >= 5) packet[4].toInt() and 0xFF else -1
            logDiagnose("🔬 ANALYSE DES EMPFANGENEN GERÄTE-PAKETS ($hex):")
            logDiagnose("   ├-- Header: 0x%02X ('%c'), Modell/ID: 0x%02X, Länge: %d".format(packet[0], headerChar, modelId, len))

            if (opcode != -1) {
                val opcodeDesc = when (opcode) {
                    0xFB -> "0xFB (Uhrzeit / RTC Antwort)"
                    0xFA -> "0xFA (Geräte-Info / Versions-Antwort)"
                    0xFD -> "0xFD (Messdaten-Kopf)"
                    0x08 -> "0x08 (M1 Zeit-Paket)"
                    0x81 -> "0x81 (ACK / Quittung)"
                    else -> "0x%02X".format(opcode)
                }
                logDiagnose("   ├-- Opcode / Typ: $opcodeDesc")
            }

            // Aufschlüsselung von Zeit-Bytes (oft ab Index 4 oder 5)
            val dateOffset = when {
                packet.size >= 11 && (opcode == 0x08 || opcode == 0xFB || opcode == 0xFE) -> 5
                packet.size >= 10 -> 4
                else -> -1
            }

            if (dateOffset != -1 && packet.size >= dateOffset + 5) {
                val yB = packet[dateOffset].toInt() and 0xFF
                val mB = packet[dateOffset + 1].toInt() and 0xFF
                val dB = packet[dateOffset + 2].toInt() and 0xFF
                val hB = packet[dateOffset + 3].toInt() and 0xFF
                val minB = packet[dateOffset + 4].toInt() and 0xFF
                val secB = if (packet.size >= dateOffset + 6) packet[dateOffset + 5].toInt() and 0xFF else 0

                val isPm = (hB and 0x80) != 0
                val rawHourVal = hB and 0x7F
                val fullYear = if (yB < 100) 2000 + yB else yB

                logDiagnose("   ├-- Zeit-Bytes: [YY=0x%02X (%d), MM=0x%02X (%d), DD=0x%02X (%d), HH=0x%02X (%d), MIN=0x%02X (%d), SEC=0x%02X]".format(
                    yB, yB, mB, mB, dB, dB, hB, hB, minB, minB, secB
                ))
                logDiagnose("   └-- Interpretation: Datum %04d-%02d-%02d %02d:%02d (PM-Bit 0x80: %b, Basis-Stunde: %d)".format(
                    fullYear, mB, dB, if (isPm && rawHourVal < 12) rawHourVal + 12 else rawHourVal, minB, isPm, rawHourVal
                ))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Packet analyze error: ${e.message}")
        }
    }

    /**
     * Manuelle Uhrzeit-Synchronisation für Diagnose und Live-Test.
     * Falls noch keine GATT-Verbindung besteht, wird automatisch eine Verbindung
     * zum Zielgerät aufgebaut, die Uhrzeit gesetzt und danach sauber geschlossen.
     */
    @SuppressLint("MissingPermission")
    fun sendManualTimeSync(targetAddress: String? = null, is12HourFormat: Boolean = this.is12HourTimeFormat) {
        this.is12HourTimeFormat = is12HourFormat
        this.isOnlyTimeSyncMode = true
        this.timeSyncAckSent = false
        this.measurementRequested = false
        if (bluetoothGatt != null && realGattConnected) {
            logDiagnose("▶ Sende sofortige Uhrzeit-Synchronisation an aktive GATT-Verbindung...")
            sendTimeSynchronization(bluetoothGatt)
        } else if (!targetAddress.isNullOrBlank()) {
            logDiagnose("▶ Baue Bluetooth-Verbindung zu $targetAddress für Uhrzeit-Einstellung auf...")
            connectToDevice(targetAddress, is12HourFormat = is12HourFormat, onlyTimeSync = true)
        } else {
            logDiagnose("⚠️ Keine aktive GATT-Verbindung und kein Gerät konfiguriert. Bitte zuerst 'Auslesen starten' tippen oder ein Gerät in den Einstellungen wählen.")
        }
    }

    // Feste Befehls-Arrays laut Aponorm / Microlife Protokoll
    private val CMD_GET_MEASUREMENTS = byteArrayOf(
        77.toByte(), 0xFF.toByte(), 0.toByte(), 9.toByte(),
        0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(),
        0xFD.toByte(), 82.toByte()
    )

    private fun resetBuffer() {
        bufferLength = 0
    }

    private fun scheduleStreamFinish() {
        streamEndRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            scope.launch {
                if (!isDataDownloadCompleted && !isErasingOrFinishing) {
                    val currentBuffer = dataBuffer.toByteArray()
                    if (currentBuffer.size >= 7) {
                        logDiagnose("✓ Datenstrom abgeschlossen (${currentBuffer.size} Bytes). Starte Dekodierung...")
                        parseAponormDataStream(currentBuffer, currentBuffer.size)
                    } else if (receivedBatch.isNotEmpty()) {
                        logDiagnose("✓ Alle Messwerte empfangen (${receivedBatch.size} Einträge). Schließe Synchronisation ab...")
                        completeBatchAndFinish()
                    } else {
                        logDiagnose("ℹ️ Keine Messdaten im Datenstrom empfangen.")
                        completeBatchAndFinish()
                    }
                }
            }
        }
        streamEndRunnable = r
        handler.postDelayed(r, 2500)
    }

    @SuppressLint("MissingPermission")
    suspend fun completeBatchAndEraseMemory() {
        completeBatchAndFinish()
    }

    @SuppressLint("MissingPermission")
    suspend fun completeBatchAndFinish() {
        if (_syncStatus.value is BleSyncStatus.Success || _syncStatus.value is BleSyncStatus.Error) {
            return
        }
        isDataDownloadCompleted = true

        val count = receivedBatch.size
        if (count > 0) {
            logDiagnose("ℹ️ Gerätespeicher bleibt erhalten (Hardware-Ringspeicher überschreibt älteste Werte automatisch).")
            delay(200)

            // Nach Datum sortieren (neueste zuerst für UI)
            receivedBatch.sortByDescending { it.timestamp }

            _downloadedMeasurements.emit(receivedBatch.toList())
            _syncStatus.value = BleSyncStatus.Success(count)
            logDiagnose("🎉 SYNCHRONISATION ERFOLGREICH: $count echte Messungen übertragen.")

            // Sende Uhrzeit-Synchronisation an das Gerät als Abschluss-Handshake
            bluetoothGatt?.let { gatt ->
                logDiagnose("▶ SCHRITT 8: Sende Uhrzeit-Synchronisation an Gerätespeicher...")
                try {
                    val localCal = Calendar.getInstance()
                    val localStr = String.format("%02d.%02d.%04d %02d:%02d:%02d",
                        localCal.get(Calendar.DAY_OF_MONTH),
                        localCal.get(Calendar.MONTH) + 1,
                        localCal.get(Calendar.YEAR),
                        localCal.get(Calendar.HOUR_OF_DAY),
                        localCal.get(Calendar.MINUTE),
                        localCal.get(Calendar.SECOND)
                    )

                    // 1. Exakter Aponorm Lokalzeit-Befehl (Opcode 0x03, 12 Bytes)
                    val localCmd = buildAponormLocalTimeCommand(localCal)
                    val localHex = localCmd.joinToString(" ") { "%02X".format(it) }
                    logDiagnose("   ├-- Sende Aponorm RTC-Uhrzeit: $localStr (Opcode 0x03) -> $localHex")
                    sendPacket(gatt, localCmd, writeNoResponse = true)

                    delay(200)

                    // 2. Aponorm 11-Byte Zeit-Befehl (Opcode 0x08)
                    val timeCmd11 = buildTimeCommand(headerByte = 0x31.toByte(), opcode = 0x08.toByte(), is12HourMode = false)
                    val hexStr11 = timeCmd11.joinToString(" ") { "%02X".format(it) }
                    logDiagnose("   ├-- Sende M1/A6 Zeit-Paket (11 Bytes): $hexStr11")
                    sendPacket(gatt, timeCmd11, writeNoResponse = true)

                    delay(200)

                    // 3. Aponorm Header 0xFF Zeit-Befehl (Opcode 0x00)
                    val ffCmd = buildAponormHeaderFFTimeCommand(localCal)
                    val ffHex = ffCmd.joinToString(" ") { "%02X".format(it) }
                    logDiagnose("   └-- Sende Header 0xFF Zeit-Paket: $ffHex")
                    sendPacket(gatt, ffCmd, writeNoResponse = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Senden des Abschluss-Zeitpakets", e)
                }

                // Dem Gerät Zeit geben, um die RTC im EEPROM zu fixieren
                logDiagnose("⏳ Warte auf interne RTC-Speicherung im Aponorm Gerät...")
                delay(2000)
            }
        } else {
            // Keine neuen Messungen (Gerätespeicher war bereits leer oder wurde gelöscht)
            _downloadedMeasurements.emit(emptyList())
            _syncStatus.value = BleSyncStatus.Success(0)
            logDiagnose("ℹ️ Gerätespeicher ist leer (0 Messungen gefunden).")
        }

        delay(500)
        disconnect()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            commandQueue.clear()
            isCommandPending = false
            handler.removeCallbacksAndMessages(null)
            val gatt = bluetoothGatt
            bluetoothGatt = null
            realGattConnected = false
            currentState = BleState.IDLE
            
            // Erst sanft trennen (LL_TERMINATE_IND über BLE senden)
            gatt?.disconnect()
            
            // gatt.close() verzögert nach 1200ms ausführen, damit der Bluetooth-Controller die Trennung ordentlich quittiert
            handler.postDelayed({
                try {
                    gatt?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing GATT", e)
                }
            }, 1200)
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
    }

    private data class DecodedDateTime(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour24: Int,
        val minute: Int,
        val isPm: Boolean,
        val hour12: Int,
        val timestamp: Long
    ) {
        fun formatLogString(): String {
            val amPm = if (isPm) "PM" else "AM"
            return String.format("%04d/%02d/%02d %02d:%02d (%02d:%02d %s)", year, month, day, hour24, minute, hour12, minute, amPm)
        }
    }

    /**
     * Dekodiert Datums- und Uhrzeitbytes des Aponorm / Microlife BP3Gu1-6B.
     * Reihenfolge der Hardware-Bytes: Jahr -> Monat -> Tag -> Stunde -> Minute
     * Berücksichtigt Bit-Maskierung sowie AM/PM Flags (Bit 7 / 0x80 oder Bit 6 / 0x40).
     */
    private fun decodeMicrolifeDateTime(
        rawYearByte: Int,
        rawMonthByte: Int,
        rawDayByte: Int,
        rawHourByte: Int,
        rawMinByte: Int
    ): DecodedDateTime {
        // 1. Jahr: 6 Bits Maske (0..63) + 2000
        val yVal = rawYearByte and 0x3F
        var year = if (yVal in 1..99) 2000 + yVal else 2026
        if (year !in 2020..2027) {
            year = 2026
        }

        // 2. Monat: 4 Bits Maske (1..12)
        var month = rawMonthByte and 0x0F
        if (month !in 1..12) month = 1

        // 3. Tag: 5 Bits Maske (1..31)
        var day = rawDayByte and 0x1F
        if (day !in 1..31) day = 1

        // 4. Stunde & AM/PM Erkennung (Original-App Format)
        val isPmFlag = (rawHourByte and 0x80) != 0 || (rawHourByte and 0x40) != 0
        val hourRawValue = rawHourByte and 0x1F

        val (hour24, isPm, hour12) = when {
            // 12-Stunden-Format mit gesetztem PM-Flag
            isPmFlag && hourRawValue in 1..11 -> {
                Triple(hourRawValue + 12, true, hourRawValue)
            }
            isPmFlag && hourRawValue == 12 -> {
                Triple(12, true, 12) // 12 PM = 12:00 Mittag
            }
            // 12-Stunden-Format AM mit Flag
            !isPmFlag && (rawHourByte and 0xC0) != 0 && hourRawValue == 12 -> {
                Triple(0, false, 12) // 12 AM = 00:00 Mitternacht
            }
            !isPmFlag && (rawHourByte and 0xC0) != 0 && hourRawValue in 1..11 -> {
                Triple(hourRawValue, false, hourRawValue)
            }
            // Normales 24h-Format (0..23)
            hourRawValue in 0..23 -> {
                val pm = hourRawValue >= 12
                val h12 = when {
                    hourRawValue == 0 -> 12
                    hourRawValue > 12 -> hourRawValue - 12
                    else -> hourRawValue
                }
                Triple(hourRawValue, pm, h12)
            }
            else -> {
                val h = hourRawValue % 24
                Triple(h, h >= 12, if (h == 0) 12 else if (h > 12) h - 12 else h)
            }
        }

        // 5. Minute: 6 Bits Maske (0..59)
        var min = rawMinByte and 0x3F
        if (min !in 0..59) min = 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return DecodedDateTime(
            year = year,
            month = month,
            day = day,
            hour24 = hour24,
            minute = min,
            isPm = isPm,
            hour12 = hour12,
            timestamp = cal.timeInMillis
        )
    }

    private data class Raw7ByteRecord(
        val offset: Int,
        val sys: Int,
        val dia: Int,
        val pulse: Int,
        val rawDayByte: Int,
        val day: Int,
        val rawHourByte: Int,
        val hour24: Int,
        val min: Int,
        val rawYearByte: Int,
        val year: Int
    )

    private fun parseAponormDataStream(rawData: ByteArray, totalLength: Int) {
        if (totalLength < 7) {
            Log.w("Aponorm", "Der Speicher des Geräts ist leer.")
            logDiagnose("ℹ️ Der Speicher des Geräts ist leer (Länge: $totalLength Bytes).")
            scope.launch {
                completeBatchAndFinish()
            }
            return
        }

        logDiagnose("📊 Scanne $totalLength Gerätedaten-Bytes nach allen echten 7-Byte Aponorm-Messsätzen...")

        // 1. Alle plausiblen 7-Byte Rohdaten extrahieren
        val rawRecords = mutableListOf<Raw7ByteRecord>()
        var i = 0
        while (i <= totalLength - 7) {
            val sysRaw   = rawData[i].toInt() and 0xFF
            val diaRaw   = rawData[i + 1].toInt() and 0xFF
            val pulseRaw = rawData[i + 2].toInt() and 0xFF
            val rawDay   = rawData[i + 3].toInt() and 0xFF
            val b4       = rawData[i + 4].toInt() and 0xFF
            val minRaw   = rawData[i + 5].toInt() and 0xFF
            val yearRaw  = rawData[i + 6].toInt() and 0xFF

            if (sysRaw in 50..260 && diaRaw in 30..180 && pulseRaw in 30..240 &&
                sysRaw >= diaRaw + 15 && minRaw in 0..59) {
                val yearVal = yearRaw and 0x3F
                val year = if (yearVal in 1..99) 2000 + yearVal else 2026
                if (year in 2020..2030) {
                    val day = rawDay and 0x1F
                    if (day in 1..31) {
                        val hour24 = (b4 and 0x3F).let { if (it in 0..23) it else (b4 and 0x1F) }
                        if (hour24 in 0..23) {
                            rawRecords.add(
                                Raw7ByteRecord(
                                    offset = i,
                                    sys = sysRaw,
                                    dia = diaRaw,
                                    pulse = pulseRaw,
                                    rawDayByte = rawDay,
                                    day = day,
                                    rawHourByte = b4,
                                    hour24 = hour24,
                                    min = minRaw,
                                    rawYearByte = yearRaw,
                                    year = year
                                )
                            )
                            i += 7
                            continue
                        }
                    }
                }
            }
            i += 1
        }

        if (rawRecords.isEmpty()) {
            logDiagnose("ℹ️ Keine gespeicherten Messwerte im Gerätespeicher gefunden (Speicher ist leer).")
            scope.launch {
                completeBatchAndFinish()
            }
            return
        }

        // 2. Ringspeicher-Umbruch ermitteln (Head-Index der neuesten Messung)
        val now = System.currentTimeMillis()
        val calNow = Calendar.getInstance()
        val currentMonth = calNow.get(Calendar.MONTH) + 1
        val currentDay = calNow.get(Calendar.DAY_OF_MONTH)
        val currentHour = calNow.get(Calendar.HOUR_OF_DAY)
        val currentMin = calNow.get(Calendar.MINUTE)

        var headIndex = rawRecords.size - 1
        for (k in 0 until rawRecords.size - 1) {
            val curr = rawRecords[k]
            val next = rawRecords[k + 1]

            val isMonthRollover = (curr.day in 28..31 && next.day in 1..4)
            val isTimeMovingForward = (next.day > curr.day) || (next.day == curr.day && (next.hour24 * 60 + next.min) >= (curr.hour24 * 60 + curr.min))

            // Falls die nächste Messung bei Interpretation als heute/Zukunft in der Zukunft liegen würde:
            val nextInFutureIfCurrIsToday = (curr.day == currentDay && next.day == currentDay && (next.hour24 * 60 + next.min) > (currentHour * 60 + currentMin + 15))
            val nextDayInFuture = (curr.day <= currentDay && next.day > currentDay && k < rawRecords.size - 5)

            if (!isMonthRollover && !isTimeMovingForward) {
                headIndex = k
                break
            } else if (nextInFutureIfCurrIsToday || nextDayInFuture) {
                headIndex = k
                break
            }
        }

        // 3. Chronologische Reihenfolge herstellen (Älteste -> Neueste)
        val chronological: List<Raw7ByteRecord> = if (headIndex < rawRecords.size - 1) {
            rawRecords.subList(headIndex + 1, rawRecords.size) + rawRecords.subList(0, headIndex + 1)
        } else {
            rawRecords
        }

        // 4. Monate und Jahre rückwärts ausgehend von der neuesten Messung zuweisen
        val last = chronological.last()
        var curYear = last.year
        var curMonth = currentMonth

        val anchorCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, curYear)
            set(Calendar.MONTH, curMonth - 1)
            set(Calendar.DAY_OF_MONTH, last.day)
            set(Calendar.HOUR_OF_DAY, last.hour24)
            set(Calendar.MINUTE, last.min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Falls der neueste Eintrag bei Annahme des aktuellen Monats in der Zukunft liegt:
        if (anchorCal.timeInMillis > now + 15 * 60 * 1000L) {
            curMonth -= 1
            if (curMonth == 0) {
                curMonth = 12
                curYear -= 1
            }
        }

        val decodedTemp = mutableListOf<Pair<BpMeasurement, DecodedDateTime>>()
        for (idx in chronological.indices.reversed()) {
            val r = chronological[idx]
            if (idx < chronological.size - 1) {
                val nextR = chronological[idx + 1]
                val isMonthBoundary = (r.day > nextR.day) || (r.day == nextR.day && (r.hour24 * 60 + r.min) > (nextR.hour24 * 60 + nextR.min))
                if (isMonthBoundary) {
                    curMonth -= 1
                    if (curMonth == 0) {
                        curMonth = 12
                        curYear -= 1
                    }
                }
            }

            var measCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, curYear)
                set(Calendar.MONTH, curMonth - 1)
                set(Calendar.DAY_OF_MONTH, r.day)
                set(Calendar.HOUR_OF_DAY, r.hour24)
                set(Calendar.MINUTE, r.min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Sicherheitsprüfung: Ein Messwert kann nie in der Zukunft liegen
            if (measCal.timeInMillis > now + 15 * 60 * 1000L) {
                curMonth -= 1
                if (curMonth == 0) {
                    curMonth = 12
                    curYear -= 1
                }
                measCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, curYear)
                    set(Calendar.MONTH, curMonth - 1)
                    set(Calendar.DAY_OF_MONTH, r.day)
                    set(Calendar.HOUR_OF_DAY, r.hour24)
                    set(Calendar.MINUTE, r.min)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }

            val isPm = r.hour24 >= 12
            val hour12 = when {
                r.hour24 == 0 -> 12
                r.hour24 > 12 -> r.hour24 - 12
                else -> r.hour24
            }

            val dt = DecodedDateTime(
                year = curYear,
                month = curMonth,
                day = r.day,
                hour24 = r.hour24,
                minute = r.min,
                isPm = isPm,
                hour12 = hour12,
                timestamp = measCal.timeInMillis
            )

            val measurement = BpMeasurement(
                timestamp = dt.timestamp,
                systole = r.sys,
                diastole = r.dia,
                pulse = r.pulse,
                afibDetected = false,
                notes = "aponorm® / Microlife Messung"
            )

            decodedTemp.add(Pair(measurement, dt))
        }

        val decodedChronological = decodedTemp.reversed()

        var messungsIndex = 0
        var foundCount = 0

        for ((measurement, dt) in decodedChronological) {
            messungsIndex++
            val ausgabe = String.format(
                "MESSUNG Nr. %d -> %s | SYS: %d mmHg | DIA: %d mmHg | PULS: %d /min",
                messungsIndex, dt.formatLogString(), measurement.systole, measurement.diastole, measurement.pulse
            )
            Log.i("Aponorm_Echtzeit", ausgabe)
            logDiagnose("📊 $ausgabe")

            if (receivedBatch.none { it.timestamp == measurement.timestamp && it.systole == measurement.systole }) {
                receivedBatch.add(measurement)
                foundCount++
            }
        }

        if (foundCount == 0) {
            logDiagnose("ℹ️ Keine neuen Messwerte im Gerätespeicher gefunden.")
        } else {
            logDiagnose("✓ $foundCount echte Messwerte erfolgreich entschlüsselt und importiert.")
        }

        scope.launch {
            completeBatchAndFinish()
        }
    }

    fun resetStatus() {
        _syncStatus.value = BleSyncStatus.Idle
    }

    fun updateSuccessInsertedCount(insertedCount: Int) {
        val current = _syncStatus.value
        if (current is BleSyncStatus.Success) {
            _syncStatus.value = current.copy(newlyInserted = insertedCount)
        }
    }

    // --- GATT DIAGNOSE SPEICHERN & EXPORTIEREN ---

    fun getFormattedDiagnosticLog(): String {
        val logs = _diagnosticLogs.value
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("==================================================")
            appendLine("   APONORM / MICROLIFE BLE GATT DIAGNOSE-LOG      ")
            appendLine("   Erstellt am: $dateStr")
            appendLine("   Gerätemodell: Aponorm BP3Gu1-6B / SN 2000717   ")
            appendLine("==================================================")
            appendLine()
            if (logs.isEmpty()) {
                appendLine("(Keine Log-Einträge vorhanden)")
            } else {
                logs.forEach { appendLine(it) }
            }
        }
    }

    fun saveDiagnosticLogsToFile(): Pair<Boolean, String> {
        val logs = _diagnosticLogs.value
        if (logs.isEmpty()) {
            return Pair(false, "Keine Diagnosedaten vorhanden!")
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Aponorm_Diagnose_$timeStamp.txt"
        val content = getFormattedDiagnosticLog()

        return try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }
            val file = File(documentsDir, fileName)
            FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            logDiagnose("Diagnoseprotokoll gespeichert: Documents/$fileName")
            Pair(true, "Gespeichert in: Documents/$fileName")
        } catch (e: Exception) {
            try {
                val appDocDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                val file = File(appDocDir, fileName)
                FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
                logDiagnose("Diagnoseprotokoll gespeichert: ${file.name}")
                Pair(true, "Gespeichert in App-Ordner: ${file.name}")
            } catch (e2: Exception) {
                logDiagnose("Fehler beim Speichern: ${e2.message}")
                Pair(false, "Fehler beim Speichern: ${e2.message}")
            }
        }
    }

    fun writeDiagnosticLogToUri(uri: Uri): Pair<Boolean, String> {
        val content = getFormattedDiagnosticLog()
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            logDiagnose("Diagnoseprotokoll erfolgreich in Datei gespeichert.")
            Pair(true, "Diagnoseprotokoll erfolgreich gespeichert.")
        } catch (e: Exception) {
            logDiagnose("Fehler beim Schreiben der Datei: ${e.message}")
            Pair(false, "Fehler beim Schreiben der Datei: ${e.message}")
        }
    }
}
