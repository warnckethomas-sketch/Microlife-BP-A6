package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.BleSyncStatus
import com.example.ble.DiscoveredBleDevice
import com.example.data.backup.DatabaseBackupManager
import com.example.data.repository.UserSettings
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanNormContainer
import com.example.ui.theme.CleanNormText
import com.example.ui.theme.CleanOnSurface
import com.example.ui.theme.CleanOnSurfaceVariant
import com.example.ui.theme.CleanOutline
import com.example.ui.theme.CleanPrimary
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.CleanSurfaceVariant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsAccordionSection {
    NONE, PERSON_1, PERSON_2, OPTIONS, BACKUP, DATA
}

@Composable
fun SettingsDialog(
    currentSettings: UserSettings,
    syncStatus: BleSyncStatus = BleSyncStatus.Idle,
    onStartBleScan: () -> Unit = {},
    onSaveSettings: (UserSettings) -> Unit,
    onBackupDatabaseNow: () -> Unit = {},
    onBackupDatabaseToUri: (Uri) -> Unit = {},
    onRestoreDatabaseFromUri: (Uri) -> Unit = {},
    onSelectBackupFolder: (Uri, String) -> Unit = { _, _ -> },
    onClearAllData: () -> Unit,
    onOpenDiagnose: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Person 1 State
    var p1Name by remember { mutableStateOf(currentSettings.person1.name) }
    var p1SysText by remember { mutableStateOf(currentSettings.person1.systoleNormMax.toString()) }
    var p1DiaText by remember { mutableStateOf(currentSettings.person1.diastoleNormMax.toString()) }
    var p1MeasurementsPerDayText by remember { mutableStateOf(currentSettings.person1.measurementsPerDay.toString()) }
    var p1Device by remember { mutableStateOf(currentSettings.person1.deviceAddress) }

    // Person 2 State
    var p2Name by remember { mutableStateOf(currentSettings.person2.name) }
    var p2SysText by remember { mutableStateOf(currentSettings.person2.systoleNormMax.toString()) }
    var p2DiaText by remember { mutableStateOf(currentSettings.person2.diastoleNormMax.toString()) }
    var p2MeasurementsPerDayText by remember { mutableStateOf(currentSettings.person2.measurementsPerDay.toString()) }
    var p2Device by remember { mutableStateOf(currentSettings.person2.deviceAddress) }

    // Options State
    var autoErase by remember { mutableStateOf(currentSettings.autoEraseAfterSync) }
    var use12Hour by remember { mutableStateOf(currentSettings.use12HourTimeFormat) }
    var autoBackupEnabled by remember { mutableStateOf(currentSettings.autoBackupEnabled) }
    var backupDirUri by remember { mutableStateOf(currentSettings.backupDirectoryUri) }
    var backupPathDisplay by remember {
        mutableStateOf(
            DatabaseBackupManager.sanitizeDisplayName(
                context,
                currentSettings.backupDirectoryUri,
                currentSettings.backupDirectoryPathDisplay
            )
        )
    }
    var chartScaleMaxText by remember { mutableStateOf(currentSettings.chartScaleMax.toString()) }

    LaunchedEffect(currentSettings) {
        p1Name = currentSettings.person1.name
        p1SysText = currentSettings.person1.systoleNormMax.toString()
        p1DiaText = currentSettings.person1.diastoleNormMax.toString()
        p1MeasurementsPerDayText = currentSettings.person1.measurementsPerDay.toString()
        p1Device = currentSettings.person1.deviceAddress

        p2Name = currentSettings.person2.name
        p2SysText = currentSettings.person2.systoleNormMax.toString()
        p2DiaText = currentSettings.person2.diastoleNormMax.toString()
        p2MeasurementsPerDayText = currentSettings.person2.measurementsPerDay.toString()
        p2Device = currentSettings.person2.deviceAddress

        autoErase = currentSettings.autoEraseAfterSync
        use12Hour = currentSettings.use12HourTimeFormat
        autoBackupEnabled = currentSettings.autoBackupEnabled
        backupDirUri = currentSettings.backupDirectoryUri
        backupPathDisplay = DatabaseBackupManager.sanitizeDisplayName(
            context,
            currentSettings.backupDirectoryUri,
            currentSettings.backupDirectoryPathDisplay
        )
        chartScaleMaxText = currentSettings.chartScaleMax.toString()
    }

    // Collapsible accordion section state: all sections closed by default, only one open at a time
    var openSection by remember { mutableStateOf(SettingsAccordionSection.NONE) }

    val isPerson1Expanded = openSection == SettingsAccordionSection.PERSON_1
    val isPerson2Expanded = openSection == SettingsAccordionSection.PERSON_2
    val isOptionsExpanded = openSection == SettingsAccordionSection.OPTIONS
    val isBackupExpanded = openSection == SettingsAccordionSection.BACKUP
    val isDataExpanded = openSection == SettingsAccordionSection.DATA

    // Activity Result Launchers for Database Backup & Restore
    val openBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onRestoreDatabaseFromUri(uri)
        }
    }

    val openFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Ignore if not persistable
            }
            val display = DatabaseBackupManager.getFriendlyFolderDisplayName(context, uri)
            backupDirUri = uri.toString()
            backupPathDisplay = display
            onSelectBackupFolder(uri, display)
        }
    }

    // Active BLE Scan target: 1 for Person 1, 2 for Person 2, null if scan dialog is closed
    var scanTargetPersonIndex by remember { mutableStateOf<Int?>(null) }

    // High contrast dark text styling for 100% legibility in setup area
    val darkTextColor = Color(0xFF101418)
    val darkLabelColor = Color(0xFF2C3034)
    val darkMutedColor = Color(0xFF50555A)

    val darkTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = darkTextColor,
        unfocusedTextColor = darkTextColor,
        focusedLabelColor = CleanPrimary,
        unfocusedLabelColor = darkLabelColor,
        focusedBorderColor = CleanPrimary,
        unfocusedBorderColor = Color(0xFF7B8086),
        focusedPlaceholderColor = darkMutedColor,
        unfocusedPlaceholderColor = darkMutedColor,
        focusedSuffixColor = darkTextColor,
        unfocusedSuffixColor = darkLabelColor,
        focusedLeadingIconColor = CleanPrimary,
        unfocusedLeadingIconColor = darkLabelColor
    )

    val boldDarkInputTextStyle = TextStyle(
        color = darkTextColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CleanSurface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CleanSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CleanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Setup: Personen & BT-Geräte",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = darkTextColor
                    )
                    Text(
                        text = "MAC-Zuordnung baugleicher Microlife A6",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = darkMutedColor
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Info Box for Identical Bluetooth Devices
                Surface(
                    color = CleanSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CleanOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CleanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Bei zwei baugleichen Microlife A6 BT-Geräten ordnen Sie hier jedem Profil die spezifische MAC-Adresse mit der Scan-Funktion zu.",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = darkTextColor,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ================= ACCORDION 1: PERSON 1 =================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CleanBackground),
                    border = BorderStroke(1.5.dp, if (isPerson1Expanded) CleanPrimary else CleanOutline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Collapsible Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openSection = if (openSection == SettingsAccordionSection.PERSON_1) {
                                        SettingsAccordionSection.NONE
                                    } else {
                                        SettingsAccordionSection.PERSON_1
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(CleanPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (p1Name.isNotBlank()) "PERSON 1: $p1Name" else "PERSON 1 (Benutzer A)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = darkTextColor
                                    )
                                    Text(
                                        text = if (p1Device.isNotBlank()) "MAC: $p1Device" else "Keine MAC zugeordnet",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (p1Device.isNotBlank()) CleanPrimary else darkMutedColor
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = CleanSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Profil 1",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CleanPrimary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isPerson1Expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isPerson1Expanded) "Einklappen" else "Ausklappen",
                                    tint = darkTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isPerson1Expanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            ) {
                                HorizontalDivider(color = CleanOutline.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 10.dp))

                                OutlinedTextField(
                                    value = p1Name,
                                    onValueChange = { p1Name = it },
                                    label = { Text("Name Person 1", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CleanPrimary) },
                                    singleLine = true,
                                    textStyle = boldDarkInputTextStyle,
                                    colors = darkTextFieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_p1_name")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = p1SysText,
                                        onValueChange = { p1SysText = it },
                                        label = { Text("Max. Systole", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                        suffix = { Text("mmHg", color = darkTextColor, fontWeight = FontWeight.Bold) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = boldDarkInputTextStyle,
                                        colors = darkTextFieldColors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_p1_sys_norm")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = p1DiaText,
                                        onValueChange = { p1DiaText = it },
                                        label = { Text("Max. Diastole", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                        suffix = { Text("mmHg", color = darkTextColor, fontWeight = FontWeight.Bold) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = boldDarkInputTextStyle,
                                        colors = darkTextFieldColors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_p1_dia_norm")
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Messungen pro Tag Eingabe + Schnellwahl für Person 1
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = p1MeasurementsPerDayText,
                                        onValueChange = { p1MeasurementsPerDayText = it },
                                        label = { Text("Messungen pro Tag", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                        suffix = { Text("x / Tag", color = darkTextColor, fontWeight = FontWeight.Bold) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = boldDarkInputTextStyle,
                                        colors = darkTextFieldColors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_p1_measurements_per_day")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(2, 3, 4).forEach { count ->
                                            val isSelected = p1MeasurementsPerDayText.trim() == count.toString()
                                            Surface(
                                                onClick = { p1MeasurementsPerDayText = count.toString() },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) CleanPrimary else CleanSurfaceVariant,
                                                border = BorderStroke(1.dp, if (isSelected) CleanPrimary else CleanOutline)
                                            ) {
                                                Text(
                                                    text = "${count}x",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else darkTextColor,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Speicher-Überwachung: Warnt bei 80 ausstehenden Messungen vor Datenverlust.",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = darkMutedColor,
                                    modifier = Modifier.padding(top = 3.dp, start = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Device MAC Address Field + Scan Button
                                OutlinedTextField(
                                    value = p1Device,
                                    onValueChange = { p1Device = it },
                                    label = { Text("Zugeordnetes BT-Gerät (MAC)", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("z.B. AA:BB:CC:DD:EE:FF", color = darkMutedColor) },
                                    leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = CleanPrimary) },
                                    singleLine = true,
                                    textStyle = boldDarkInputTextStyle,
                                    colors = darkTextFieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_p1_device")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // BLE Scanner Button for Person 1 (Short & easily readable)
                                Button(
                                    onClick = {
                                        scanTargetPersonIndex = 1
                                        onStartBleScan()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("btn_scan_mac_p1"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CleanPrimary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BluetoothSearching,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "BLE-Scan (Person 1)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Badge if MAC is set
                                if (p1Device.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = CleanNormContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = CleanNormText,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "MAC $p1Device zugeordnet",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CleanNormText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ================= ACCORDION 2: PERSON 2 =================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CleanBackground),
                    border = BorderStroke(1.5.dp, if (isPerson2Expanded) CleanOnSurfaceVariant else CleanOutline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Collapsible Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openSection = if (openSection == SettingsAccordionSection.PERSON_2) {
                                        SettingsAccordionSection.NONE
                                    } else {
                                        SettingsAccordionSection.PERSON_2
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(CleanOnSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (p2Name.isNotBlank()) "PERSON 2: $p2Name" else "PERSON 2 (Benutzer B)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = darkTextColor
                                    )
                                    Text(
                                        text = if (p2Device.isNotBlank()) "MAC: $p2Device" else "Keine MAC zugeordnet",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (p2Device.isNotBlank()) CleanOnSurfaceVariant else darkMutedColor
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = CleanSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Profil 2",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CleanOnSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isPerson2Expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isPerson2Expanded) "Einklappen" else "Ausklappen",
                                    tint = darkTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isPerson2Expanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            ) {
                                HorizontalDivider(color = CleanOutline.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 10.dp))

                                OutlinedTextField(
                                    value = p2Name,
                                    onValueChange = { p2Name = it },
                                    label = { Text("Name Person 2", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CleanOnSurfaceVariant) },
                                    singleLine = true,
                                    textStyle = boldDarkInputTextStyle,
                                    colors = darkTextFieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_p2_name")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = p2SysText,
                                        onValueChange = { p2SysText = it },
                                        label = { Text("Max. Systole", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                        suffix = { Text("mmHg", color = darkTextColor, fontWeight = FontWeight.Bold) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = boldDarkInputTextStyle,
                                        colors = darkTextFieldColors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_p2_sys_norm")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = p2DiaText,
                                        onValueChange = { p2DiaText = it },
                                        label = { Text("Max. Diastole", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                        suffix = { Text("mmHg", color = darkTextColor, fontWeight = FontWeight.Bold) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = boldDarkInputTextStyle,
                                        colors = darkTextFieldColors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_p2_dia_norm")
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Messungen pro Tag Eingabe + Schnellwahl für Person 2
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = p2MeasurementsPerDayText,
                                        onValueChange = { p2MeasurementsPerDayText = it },
                                        label = { Text("Messungen pro Tag", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                        suffix = { Text("x / Tag", color = darkTextColor, fontWeight = FontWeight.Bold) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = boldDarkInputTextStyle,
                                        colors = darkTextFieldColors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_p2_measurements_per_day")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(2, 3, 4).forEach { count ->
                                            val isSelected = p2MeasurementsPerDayText.trim() == count.toString()
                                            Surface(
                                                onClick = { p2MeasurementsPerDayText = count.toString() },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) CleanOnSurfaceVariant else CleanSurfaceVariant,
                                                border = BorderStroke(1.dp, if (isSelected) CleanOnSurfaceVariant else CleanOutline)
                                            ) {
                                                Text(
                                                    text = "${count}x",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else darkTextColor,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Speicher-Überwachung: Warnt bei 80 ausstehenden Messungen vor Datenverlust.",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = darkMutedColor,
                                    modifier = Modifier.padding(top = 3.dp, start = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Device MAC Address Field + Scan Button
                                OutlinedTextField(
                                    value = p2Device,
                                    onValueChange = { p2Device = it },
                                    label = { Text("Zugeordnetes BT-Gerät (MAC)", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("z.B. AA:BB:CC:DD:EE:FF", color = darkMutedColor) },
                                    leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = CleanOnSurfaceVariant) },
                                    singleLine = true,
                                    textStyle = boldDarkInputTextStyle,
                                    colors = darkTextFieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_p2_device")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // BLE Scanner Button for Person 2 (Short & easily readable)
                                Button(
                                    onClick = {
                                        scanTargetPersonIndex = 2
                                        onStartBleScan()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("btn_scan_mac_p2"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CleanOnSurfaceVariant,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BluetoothSearching,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "BLE-Scan (Person 2)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Badge if MAC is set
                                if (p2Device.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = CleanNormContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = CleanNormText,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "MAC $p2Device zugeordnet",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CleanNormText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ================= ACCORDION 3: OPTIONEN & SKALA =================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CleanBackground),
                    border = BorderStroke(1.5.dp, if (isOptionsExpanded) CleanPrimary else CleanOutline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Collapsible Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openSection = if (openSection == SettingsAccordionSection.OPTIONS) {
                                        SettingsAccordionSection.NONE
                                    } else {
                                        SettingsAccordionSection.OPTIONS
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(CleanSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = CleanPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "OPTIONEN & DIAGRAMM",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = darkTextColor
                                    )
                                    Text(
                                        text = "Speicher löschen, Skala ($chartScaleMaxText mmHg)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = darkMutedColor
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isOptionsExpanded) "Einklappen" else "Ausklappen",
                                tint = darkTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isOptionsExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            ) {
                                HorizontalDivider(color = CleanOutline.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Automatisches Speicher-Löschen",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = darkTextColor
                                        )
                                        Text(
                                            text = "Sendet nach Daten-Download MCLR-Löschbefehl an das Gerät.",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = darkMutedColor
                                        )
                                    }
                                    Switch(
                                        checked = autoErase,
                                        onCheckedChange = { autoErase = it },
                                        modifier = Modifier.testTag("switch_auto_erase")
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Diagramm-Skala (Maximalwert):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = darkTextColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(180, 200, 210, 240).forEach { presetVal ->
                                        val isSel = chartScaleMaxText == presetVal.toString()
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSel) CleanPrimary else CleanSurfaceVariant,
                                            border = BorderStroke(1.dp, if (isSel) CleanPrimary else CleanOutline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { chartScaleMaxText = presetVal.toString() }
                                        ) {
                                            Text(
                                                text = "$presetVal",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.White else darkTextColor,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = chartScaleMaxText,
                                    onValueChange = { chartScaleMaxText = it },
                                    label = { Text("Max. Skalenwert manuell", color = darkLabelColor, fontWeight = FontWeight.Bold) },
                                    suffix = { Text("mmHg", color = darkTextColor, fontWeight = FontWeight.Bold) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = boldDarkInputTextStyle,
                                    colors = darkTextFieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_chart_scale_max")
                                )

                                if (onOpenDiagnose != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0F172A),
                                        border = BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenDiagnose() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Terminal,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "GATT & Bluetooth Diagnose",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    color = Color(0xFFF1F5F9)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF0284C7)
                                            ) {
                                                Text(
                                                    text = "ÖFFNEN",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ================= ACCORDION 4: DATENSICHERUNG =================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CleanBackground),
                    border = BorderStroke(1.5.dp, if (isBackupExpanded) CleanPrimary else CleanOutline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Collapsible Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openSection = if (openSection == SettingsAccordionSection.BACKUP) {
                                        SettingsAccordionSection.NONE
                                    } else {
                                        SettingsAccordionSection.BACKUP
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(CleanSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = CleanPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "DATENSICHERUNG & BACKUP",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = darkTextColor
                                    )
                                    Text(
                                        text = if (autoBackupEnabled) "Automatisches Backup aktiv" else "Manuelles Backup",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (autoBackupEnabled) CleanPrimary else darkMutedColor
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isBackupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isBackupExpanded) "Einklappen" else "Ausklappen",
                                tint = darkTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isBackupExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            ) {
                                HorizontalDivider(color = CleanOutline.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 10.dp))

                                // Scope Info Card
                                Surface(
                                    color = CleanSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CleanOutline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Storage,
                                                contentDescription = null,
                                                tint = CleanPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Umfang der Datenbanksicherung:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = darkTextColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Alle Messwerte beider Personen (Systole, Diastole, Puls, AFIB)\n• Alle Profile & Grenzwerte\n• Alle Bluetooth-Gerätezuordnungen & MACs",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = darkTextColor,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Auto-Backup Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Automatische Datenbanksicherung",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = darkTextColor
                                        )
                                        Text(
                                            text = "Sichert nach jedem Sync in ${DatabaseBackupManager.BACKUP_FILE_NAME}.",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = darkMutedColor
                                        )
                                    }
                                    Switch(
                                        checked = autoBackupEnabled,
                                        onCheckedChange = { autoBackupEnabled = it },
                                        modifier = Modifier.testTag("switch_auto_backup")
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Storage Location Card
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CleanSurfaceVariant),
                                    border = BorderStroke(1.dp, CleanOutline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = CleanPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Speicherort für Sicherungen:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = darkTextColor
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = backupPathDisplay,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanPrimary,
                                            maxLines = 2
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "Zieldatei: ${DatabaseBackupManager.BACKUP_FILE_NAME}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = darkTextColor
                                        )

                                        if (currentSettings.lastBackupTimestamp > 0) {
                                            val lastDateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
                                                .format(Date(currentSettings.lastBackupTimestamp))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Letzte Sicherung: $lastDateStr",
                                                fontSize = 10.5.sp,
                                                color = darkMutedColor
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { openFolderTreeLauncher.launch(null) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("btn_change_backup_location"),
                                                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                                                border = BorderStroke(1.dp, CleanPrimary)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FolderOpen,
                                                    contentDescription = null,
                                                    tint = CleanPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Ordner wählen",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CleanPrimary
                                                )
                                            }

                                            if (backupDirUri.isNotBlank()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        backupDirUri = ""
                                                        backupPathDisplay = "App-Speicher (Dokumente / Blutdruck_Backups)"
                                                    },
                                                    modifier = Modifier.testTag("btn_reset_backup_location"),
                                                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                                                ) {
                                                    Text("Standard", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Backup & Restore Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onBackupDatabaseNow()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_backup_database_now"),
                                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backup,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Alles sichern",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            openBackupDocLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                        },
                                        border = BorderStroke(1.dp, CleanPrimary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_restore_database"),
                                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = null,
                                            tint = CleanPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Wiederherstellen",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ================= ACCORDION 5: DATENVERWALTUNG =================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CleanBackground),
                    border = BorderStroke(1.5.dp, if (isDataExpanded) Color(0xFFD32F2F) else CleanOutline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Collapsible Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openSection = if (openSection == SettingsAccordionSection.DATA) {
                                        SettingsAccordionSection.NONE
                                    } else {
                                        SettingsAccordionSection.DATA
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFEBEE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "DATENVERWALTUNG",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = darkTextColor
                                    )
                                    Text(
                                        text = "Messwerte zurücksetzen / löschen",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = darkMutedColor
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isDataExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isDataExpanded) "Einklappen" else "Ausklappen",
                                tint = darkTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isDataExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            ) {
                                HorizontalDivider(color = CleanOutline.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 10.dp))

                                Button(
                                    onClick = {
                                        onClearAllData()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_clear_all_data")
                                ) {
                                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Alle Messwerte löschen", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p1Sys = p1SysText.toIntOrNull() ?: 135
                    val p1Dia = p1DiaText.toIntOrNull() ?: 85
                    val p1Daily = p1MeasurementsPerDayText.toIntOrNull()?.coerceIn(1, 20) ?: 2

                    val p2Sys = p2SysText.toIntOrNull() ?: 140
                    val p2Dia = p2DiaText.toIntOrNull() ?: 90
                    val p2Daily = p2MeasurementsPerDayText.toIntOrNull()?.coerceIn(1, 20) ?: 2

                    val customScaleMax = chartScaleMaxText.toIntOrNull()?.coerceIn(120, 300) ?: 210

                    onSaveSettings(
                        currentSettings.copy(
                            person1 = currentSettings.person1.copy(
                                name = p1Name.trim().ifBlank { "Person 1" },
                                systoleNormMax = p1Sys,
                                diastoleNormMax = p1Dia,
                                deviceAddress = p1Device.trim(),
                                measurementsPerDay = p1Daily
                            ),
                            person2 = currentSettings.person2.copy(
                                name = p2Name.trim().ifBlank { "Person 2" },
                                systoleNormMax = p2Sys,
                                diastoleNormMax = p2Dia,
                                deviceAddress = p2Device.trim(),
                                measurementsPerDay = p2Daily
                            ),
                            autoEraseAfterSync = autoErase,
                            use12HourTimeFormat = use12Hour,
                            autoBackupEnabled = autoBackupEnabled,
                            backupDirectoryUri = backupDirUri,
                            backupDirectoryPathDisplay = backupPathDisplay,
                            chartScaleMax = customScaleMax
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary, contentColor = Color.White),
                modifier = Modifier.testTag("btn_save_settings")
            ) {
                Text("Speichern", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", fontWeight = FontWeight.Bold, color = darkTextColor)
            }
        }
    )

    // ================= SCAN MAC ADDRESS DIALOG =================
    if (scanTargetPersonIndex != null) {
        val targetUserIndex = scanTargetPersonIndex!!
        val targetPersonName = if (targetUserIndex == 1) p1Name else p2Name

        LaunchedEffect(scanTargetPersonIndex) {
            onStartBleScan()
        }

        AlertDialog(
            onDismissRequest = { scanTargetPersonIndex = null },
            containerColor = CleanSurface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CleanSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = CleanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "BLE-Scan & MAC auslesen",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = darkTextColor
                        )
                        Text(
                            text = "Ziel: $targetPersonName (Profil $targetUserIndex)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanPrimary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Scanne nach Bluetooth-Blutdruckmessgeräten in Ihrer Nähe, um die MAC-Adresse automatisch auszulesen:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = darkTextColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Scan Status Indicator
                    Surface(
                        color = CleanSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (syncStatus is BleSyncStatus.Scanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = CleanPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Scan aktiv (15 Sek.)... Suche Geräte...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CleanPrimary
                                )
                            } else if (syncStatus is BleSyncStatus.Error) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = (syncStatus as BleSyncStatus.Error).message,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = CleanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Scan abgeschlossen / bereit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = darkTextColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Discovered Real BLE Devices from Scanner
                    if (syncStatus is BleSyncStatus.DiscoveredDevices) {
                        if (syncStatus.devices.isNotEmpty()) {
                            Text(
                                text = "GEFUNDENE BLUETOOTH GERÄTE (${syncStatus.devices.size}):",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = darkTextColor,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            syncStatus.devices.forEach { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            if (targetUserIndex == 1) {
                                                p1Device = device.address
                                            } else {
                                                p2Device = device.address
                                            }
                                            scanTargetPersonIndex = null
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (device.isMicrolife) CleanSurfaceVariant else CleanBackground
                                    ),
                                    border = BorderStroke(1.dp, if (device.isMicrolife) CleanPrimary else CleanPrimary.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Bluetooth,
                                                    contentDescription = null,
                                                    tint = if (device.isMicrolife) CleanPrimary else darkMutedColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = device.name,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.5.sp,
                                                    color = darkTextColor
                                                )
                                            }
                                            Text(
                                                text = "MAC: ${device.address}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = CleanPrimary
                                            )
                                            Text(
                                                text = "Signal: ${device.rssi} dBm",
                                                fontSize = 10.5.sp,
                                                color = darkMutedColor
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (targetUserIndex == 1) {
                                                    p1Device = device.address
                                                } else {
                                                    p2Device = device.address
                                                }
                                                scanTargetPersonIndex = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Übernehmen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = CleanSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Kein Bluetooth-Gerät gefunden.",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = darkTextColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Hinweise zur Fehlerbehebung:\n" +
                                                "• Schalten Sie das Messgerät ein (Taste 'M' oder 'Bluetooth' gedrückt halten bis 'bt' / 'CN' blinkt)\n" +
                                                "• Halten Sie das Smartphone dicht an das Gerät (< 1 m)\n" +
                                                "• Bluetooth & Standort (GPS) am Smartphone müssen aktiviert sein\n" +
                                                "• Falls das Gerät bereits in Android gekoppelt ist, tippen Sie auf 'Erneut scannen'",
                                        fontSize = 11.sp,
                                        color = darkMutedColor,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { onStartBleScan() },
                    border = BorderStroke(1.dp, CleanPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = CleanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Erneut scannen", fontWeight = FontWeight.Bold, color = CleanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { scanTargetPersonIndex = null }) {
                    Text("Schließen", fontWeight = FontWeight.Bold, color = darkTextColor)
                }
            }
        )
    }
}
