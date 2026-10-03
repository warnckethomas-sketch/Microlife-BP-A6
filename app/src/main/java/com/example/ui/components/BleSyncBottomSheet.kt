package com.example.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.BlePermissionHelper
import com.example.ble.BleSyncStatus
import com.example.ble.DiscoveredBleDevice
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanMutedText
import com.example.ui.theme.CleanNormText
import com.example.ui.theme.CleanOnPrimary
import com.example.ui.theme.CleanOnSurface
import com.example.ui.theme.CleanOutline
import com.example.ui.theme.CleanPrimary
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.CleanSurfaceVariant

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight

@Composable
fun BleSyncBottomSheet(
    syncStatus: BleSyncStatus,
    onStartScan: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(BlePermissionHelper.hasPermissions(context)) }
    var isBtEnabled by remember { mutableStateOf(BlePermissionHelper.isBluetoothEnabled(context)) }
    var isLocEnabled by remember { mutableStateOf(BlePermissionHelper.isLocationEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermissions = perms.values.all { it }
        isBtEnabled = BlePermissionHelper.isBluetoothEnabled(context)
        isLocEnabled = BlePermissionHelper.isLocationEnabled(context)
        if (hasPermissions) {
            onStartScan()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = CleanSurface,
            border = BorderStroke(1.dp, CleanOutline.copy(alpha = 0.6f)),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Title Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CleanSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = "BLE",
                                tint = CleanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Gerät verbinden",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CleanOnSurface
                            )
                            Text(
                                text = "aponorm® & Microlife® Bluetooth",
                                fontSize = 12.sp,
                                color = CleanMutedText
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen", tint = CleanMutedText)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

            // Diagnostic System Checks Banner (shown if permission / BT / GPS missing)
            if (!hasPermissions || !isBtEnabled || !isLocEnabled) {
                Surface(
                    color = Color(0xFFFFF8E1),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voraussetzungen für die Bluetooth-Suche:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = Color(0xFF5D4037)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!hasPermissions) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• Bluetooth-Berechtigungen", fontSize = 12.sp, color = Color(0xFF424242))
                                Button(
                                    onClick = { permissionLauncher.launch(BlePermissionHelper.getRequiredPermissions()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Erteilen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (!isBtEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• Bluetooth ist ausgeschaltet", fontSize = 12.sp, color = Color(0xFF424242))
                                OutlinedButton(
                                    onClick = { BlePermissionHelper.openBluetoothSettings(context) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Einschalten", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (!isLocEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• Standort (GPS) für BLE-Suche", fontSize = 12.sp, color = Color(0xFF424242))
                                OutlinedButton(
                                    onClick = { BlePermissionHelper.openLocationSettings(context) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Aktivieren", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            when (syncStatus) {
                is BleSyncStatus.Idle -> {
                    // Instruction Guide Card
                    Surface(
                        color = CleanSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "So verbinden Sie Ihr Messgerät:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = CleanOnSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Bluetooth-Schalter am Messgerät auf ON schieben (oder 'M'-/'Bluetooth'-Taste 3 Sek. gedrückt halten, bis das Bluetooth-Symbol blinkt).\n" +
                                        "2. Halten Sie das Smartphone nah an das Messgerät (< 1 m).\n" +
                                        "3. Tippen Sie auf 'Jetzt scannen'.",
                                fontSize = 12.sp,
                                color = CleanMutedText,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!BlePermissionHelper.hasPermissions(context)) {
                                permissionLauncher.launch(BlePermissionHelper.getRequiredPermissions())
                            } else {
                                onStartScan()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_start_ble_scan"),
                        colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary, contentColor = CleanOnPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Jetzt nach Geräten scannen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                is BleSyncStatus.Scanning -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = CleanPrimary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Suche nach Bluetooth-Geräten...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CleanOnSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bitte stellen Sie sicher, dass das Messgerät eingeschaltet ist und 'bt' / 'CN' blinkt.",
                            fontSize = 12.sp,
                            color = CleanMutedText,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is BleSyncStatus.DiscoveredDevices -> {
                    Text(
                        text = "Gefundene Bluetooth-Geräte (${syncStatus.devices.size}):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CleanOnSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (syncStatus.devices.isEmpty()) {
                        Surface(
                            color = CleanSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Kein Bluetooth-Gerät gefunden",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Ist das Messgerät eingeschaltet (Display leuchtet)?\n" +
                                            "• Blinkt das Bluetooth-Symbol am Gerät?\n" +
                                            "• Smartphone näher an das Gerät heranhalten (< 1 m)\n" +
                                            "• Bluetooth & Standort am Smartphone kurz aus- und wieder einschalten.",
                                    fontSize = 11.5.sp,
                                    color = CleanMutedText,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Erneut scannen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { BlePermissionHelper.openAppSettings(context) },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, CleanOutline),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = CleanPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("App-Rechte", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CleanPrimary)
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.height(240.dp)) {
                            items(syncStatus.devices) { device ->
                                DiscoveredDeviceItem(
                                    device = device,
                                    onSelect = { onConnectDevice(device.address) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onStartScan) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = CleanPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aktualisieren", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CleanPrimary)
                            }
                        }
                    }
                }

                is BleSyncStatus.Connecting -> {
                    SyncStepStateCard(
                        title = "Verbindung aufbauen",
                        subtitle = "Verbinde mit ${syncStatus.deviceName} (Gerät zeigt 'CN')...",
                        icon = Icons.Default.Bluetooth,
                        isLoading = true
                    )
                }

                is BleSyncStatus.TimeSyncing -> {
                    SyncStepStateCard(
                        title = "1. Uhrzeit-Synchronisation",
                        subtitle = "Aktuelle Smartphone-Uhrzeit wird an das Messgerät übertragen...",
                        icon = Icons.Default.Schedule,
                        isLoading = true
                    )
                }

                is BleSyncStatus.Downloading -> {
                    Column {
                        SyncStepStateCard(
                            title = "2. Messdaten-Download",
                            subtitle = "Messwerte werden ausgelesen (${syncStatus.current} empfangen)...",
                            icon = Icons.Default.Bluetooth,
                            isLoading = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = CleanPrimary,
                            trackColor = CleanSurfaceVariant
                        )
                    }
                }

                is BleSyncStatus.ErasingMemory -> {
                    SyncStepStateCard(
                        title = "3. Speicher-Löschung",
                        subtitle = "Daten lokal gespeichert. Sende Löschquittierung...",
                        icon = Icons.Default.DeleteSweep,
                        isLoading = true
                    )
                }

                is BleSyncStatus.Success -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = CleanNormText,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Synchronisation erfolgreich!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanNormText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                syncStatus.newlyInserted > 0 ->
                                    "${syncStatus.newlyInserted} neue Messwerte übertragen"
                                syncStatus.newlyInserted == 0 ->
                                    "Keine neuen Messwerte übertragen"
                                else ->
                                    "${syncStatus.count} neue Messwerte übertragen"
                            },
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = CleanMutedText
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary, contentColor = CleanOnPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Fertig", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is BleSyncStatus.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Hinweis zum Verbindungsaufbau",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = syncStatus.message,
                                    fontSize = 12.sp,
                                    color = Color(0xFF37474F),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Erneut scannen", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { BlePermissionHelper.openAppSettings(context) },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, CleanOutline),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Berechtigungen", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CleanPrimary)
                            }
                        }
                    }
                }
            }

            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun DiscoveredDeviceItem(
    device: DiscoveredBleDevice,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isMicrolife) CleanSurfaceVariant else CleanBackground
        ),
        border = BorderStroke(1.dp, if (device.isMicrolife) CleanPrimary else CleanOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (device.isMicrolife) CleanPrimary else CleanMutedText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = CleanOnSurface
                    )
                    if (device.isMicrolife) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = CleanPrimary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Kompatibel",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "MAC: ${device.address}  •  Signal: ${device.rssi} dBm",
                    fontSize = 11.sp,
                    color = CleanMutedText
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Verbinden", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SyncStepStateCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CleanSurfaceVariant),
        border = BorderStroke(1.dp, CleanOutline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = CleanPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = CleanPrimary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CleanOnSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = CleanMutedText
                )
            }
        }
    }
}
