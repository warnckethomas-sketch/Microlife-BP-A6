package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.backup.BackupResult
import com.example.ui.components.BleDiagnoseDialog
import com.example.ui.components.BleSyncBottomSheet
import com.example.ui.components.BpLineChart
import com.example.ui.components.MeasurementCard
import com.example.ui.components.SettingsDialog
import com.example.ui.components.calculateAgeYears
import com.example.ui.theme.CleanAlertContainer
import com.example.ui.theme.CleanAlertText
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanMutedText
import com.example.ui.theme.CleanNormContainer
import com.example.ui.theme.CleanNormText
import com.example.ui.theme.CleanOnPrimary
import com.example.ui.theme.CleanOnSurface
import com.example.ui.theme.CleanOnSurfaceVariant
import com.example.ui.theme.CleanOutline
import com.example.ui.theme.CleanPrimary
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.CleanSurfaceVariant
import com.example.ui.theme.WarningOrange
import com.example.ui.theme.WarningOrangeContainer
import com.example.ui.viewmodel.BpViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: BpViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val measurements by viewModel.measurements.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    var showBleBottomSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDiagnoseDialog by remember { mutableStateOf(false) }
    var showPrintMonthDialog by remember { mutableStateOf(false) }
    var selectedPrintMonthKey by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var expandedMonthKeys by rememberSaveable { mutableStateOf(setOf<String>()) }

    var isExitBackupInProgress by remember { mutableStateOf(false) }
    var exitBackupStatusText by remember { mutableStateOf("") }
    var exitBackupSuccess by remember { mutableStateOf<Boolean?>(null) }

    val hasAnyOpenDialog = showBleBottomSheet || showSettingsDialog || showDiagnoseDialog || showPrintMonthDialog

    fun performBackupAndExit() {
        if (isExitBackupInProgress) return
        isExitBackupInProgress = true
        exitBackupSuccess = null
        exitBackupStatusText = "Datenbank wird gesichert..."

        coroutineScope.launch {
            try {
                val result = viewModel.backupDatabaseDirect(context)
                if (result.success) {
                    exitBackupSuccess = true
                    exitBackupStatusText = result.message
                } else {
                    exitBackupSuccess = false
                    exitBackupStatusText = "Fehler beim Sichern: ${result.message}"
                }
            } catch (e: Exception) {
                exitBackupSuccess = false
                exitBackupStatusText = "Fehler beim Sichern: ${e.message}"
            }
        }
    }

    // Beim Betätigen der Android-Zurück-Taste auf dem Hauptbildschirm: Automatisch sichern und beenden
    BackHandler(enabled = !hasAnyOpenDialog && !isExitBackupInProgress) {
        performBackupAndExit()
    }

    // Group measurements by Month and Year (descending), always ensuring current calendar month is present
    val groupedMonths = remember(measurements) {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.GERMAN)
        val map = linkedMapOf<String, Triple<Int, Int, MutableList<com.example.data.model.BpMeasurement>>>()

        // 1. Immer den aktuellen Kalendermonat anlegen (auch wenn noch 0 Messungen vorhanden sind)
        val nowCal = Calendar.getInstance()
        val curYear = nowCal.get(Calendar.YEAR)
        val curMonth = nowCal.get(Calendar.MONTH)
        val curKey = String.format(Locale.US, "%04d-%02d", curYear, curMonth)
        map[curKey] = Triple(curYear, curMonth, mutableListOf())

        // 2. Alle vorhandenen Messwerte ihren Monaten zuordnen
        measurements.forEach { m ->
            val cal = Calendar.getInstance().apply { timeInMillis = m.timestamp }
            val y = cal.get(Calendar.YEAR)
            val mon = cal.get(Calendar.MONTH)
            val key = String.format(Locale.US, "%04d-%02d", y, mon)
            val entry = map.getOrPut(key) { Triple(y, mon, mutableListOf()) }
            entry.third.add(m)
        }

        // 3. Nach Jahr und Monat absteigend sortieren
        val sortedEntries = map.entries.sortedWith(
            compareByDescending<Map.Entry<String, Triple<Int, Int, MutableList<com.example.data.model.BpMeasurement>>>> { it.value.first }
                .thenByDescending { it.value.second }
        )

        sortedEntries.map { (key, triple) ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, triple.first)
                set(Calendar.MONTH, triple.second)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val rawLabel = monthFormat.format(cal.time)
            val label = rawLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString() }
            MonthGroup(
                key = key,
                year = triple.first,
                month = triple.second,
                monthYearLabel = label,
                measurements = triple.third,
                isCurrentMonth = (triple.first == curYear && triple.second == curMonth)
            )
        }
    }

    // SAF File Creator Launcher for saving PDF to custom file
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPdfToUri(context, uri)
        }
    }

    // Bluetooth Permission launcher
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            showBleBottomSheet = true
            viewModel.startBleScan()
        } else {
            Toast.makeText(
                context,
                "Bluetooth- & Standort-Berechtigungen werden für den Microlife BLE-Scan benötigt.",
                Toast.LENGTH_LONG
            ).show()
            showBleBottomSheet = true
        }
    }

    // Toast notification upon successful sync with new count
    androidx.compose.runtime.LaunchedEffect(syncStatus) {
        if (syncStatus is com.example.ble.BleSyncStatus.Success) {
            val status = syncStatus as com.example.ble.BleSyncStatus.Success
            val msg = when {
                status.newlyInserted > 0 ->
                    "${status.newlyInserted} neue Messwerte übertragen."
                status.newlyInserted == 0 ->
                    "Keine neuen Messwerte übertragen."
                else ->
                    "${status.count} neue Messwerte übertragen."
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndStartSync() {
        val hasPermissions = permissionsToRequest.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            showBleBottomSheet = true
            viewModel.startBleScan()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Pulse animation for sync banner
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Scaffold(
        containerColor = CleanBackground,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CleanPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = CleanOnPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Microlife Monitor",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                    color = CleanOnSurface
                                )
                                Text(
                                    text = "2-PERSONEN VERWALTUNG",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CleanOnSurfaceVariant,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    },
                    actions = {
                        // Drei-Punkte-Menü als runder blauer Kreis mit Einstellungen, Diagnose & Beenden
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CleanPrimary)
                                    .clickable { showOverflowMenu = true }
                                    .testTag("btn_overflow_menu"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menü Optionen",
                                    tint = CleanOnPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier.background(CleanSurface)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Einstellungen",
                                            fontWeight = FontWeight.Medium,
                                            color = CleanOnSurface
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = CleanPrimary
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showSettingsDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_settings")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "GATT BLE-Diagnose",
                                            fontWeight = FontWeight.Medium,
                                            color = CleanOnSurface
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = null,
                                            tint = Color(0xFF0284C7)
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDiagnoseDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_diagnose")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Uhr stellen",
                                            fontWeight = FontWeight.Medium,
                                            color = CleanOnSurface
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = CleanPrimary
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showBleBottomSheet = true
                                        viewModel.sendManualTimeSync()
                                    },
                                    modifier = Modifier.testTag("menu_set_clock")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Beenden",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFDC2626)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626)
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        performBackupAndExit()
                                    },
                                    modifier = Modifier.testTag("menu_exit_app")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CleanSurface
                    )
                )
                // Dünne, dezente Trennlinie zwischen Menüleiste oben und Datenbereich
                HorizontalDivider(
                    thickness = 1.dp,
                    color = CleanOutline.copy(alpha = 0.8f)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drucksymbol Button links (gleicher Kreis wie Bluetooth)
                FloatingActionButton(
                    onClick = {
                        if (measurements.isEmpty()) {
                            Toast.makeText(context, "Keine Messdaten zum Drucken vorhanden.", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedPrintMonthKey = groupedMonths.firstOrNull { it.measurements.isNotEmpty() }?.key
                                ?: groupedMonths.firstOrNull()?.key ?: ""
                            showPrintMonthDialog = true
                        }
                    },
                    containerColor = CleanPrimary,
                    contentColor = CleanOnPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_print_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Protokoll drucken"
                    )
                }

                // Bluetooth Auslesen Button rechts
                FloatingActionButton(
                    onClick = { checkAndStartSync() },
                    containerColor = CleanPrimary,
                    contentColor = CleanOnPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_sync_ble")
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothSearching,
                        contentDescription = "Gerät auslesen"
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // 1. PERSON SWITCHER TABS (Person 1 vs Person 2)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CleanSurfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isP1 = settings.selectedUserIndex == 1
                    val isP2 = settings.selectedUserIndex == 2

                    // Person 1 Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isP1) CleanPrimary else Color.Transparent)
                            .clickable { viewModel.selectUser(1) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isP1) CleanOnPrimary else CleanOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = settings.person1.name,
                                fontSize = 13.sp,
                                fontWeight = if (isP1) FontWeight.Bold else FontWeight.Medium,
                                color = if (isP1) CleanOnPrimary else CleanOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Person 2 Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isP2) CleanPrimary else Color.Transparent)
                            .clickable { viewModel.selectUser(2) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isP2) CleanOnPrimary else CleanOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = settings.person2.name,
                                fontSize = 13.sp,
                                fontWeight = if (isP2) FontWeight.Bold else FontWeight.Medium,
                                color = if (isP2) CleanOnPrimary else CleanOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2. SPEICHER- & ÜBERTRAGUNGS-HINWEIS (Basierend auf Messungen/Tag & 80er Gerätespeicher-Schwellenwert)
            item {
                val activeProfile = settings.activePerson
                val dailyTarget = activeProfile.measurementsPerDay.coerceAtLeast(1)
                val latestMeasurement = remember(measurements) { measurements.maxByOrNull { it.timestamp } }

                val now = System.currentTimeMillis()
                val elapsedDays = remember(latestMeasurement, now) {
                    if (latestMeasurement != null) {
                        val calNow = Calendar.getInstance().apply {
                            timeInMillis = now
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val calMeas = Calendar.getInstance().apply {
                            timeInMillis = latestMeasurement.timestamp
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val diffMs = calNow.timeInMillis - calMeas.timeInMillis
                        (diffMs / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                    } else 0
                }
                val relativeDayLabel = when (elapsedDays) {
                    0 -> "Heute"
                    1 -> "Gestern"
                    else -> "vor $elapsedDays Tg."
                }
                val estimatedPendingCount = elapsedDays * dailyTarget
                val isLimitReached = estimatedPendingCount >= 80
                val isApproachingLimit = estimatedPendingCount in 50..79
                val lastDateStr = remember(latestMeasurement) {
                    latestMeasurement?.let {
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(it.timestamp))
                    } ?: "Noch keine Messung"
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLimitReached) {
                    // Dringende Warnmeldung bei Erreichen von 80 Messungen
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_memory_warning_80"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanAlertContainer),
                        border = BorderStroke(1.5.dp, CleanAlertText)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(CleanAlertText),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Gerätespeicher-Warnung (80 Messungen)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = CleanAlertText
                                    )
                                    Text(
                                        text = "Ca. $estimatedPendingCount Messungen seit letzter Speicherung",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = CleanAlertText.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Seit der letzten in der App gespeicherten Messung vom $lastDateStr ($relativeDayLabel) sind bei $dailyTarget Messungen/Tag ca. $estimatedPendingCount Messungen auf dem Gerät angefallen. Der Schwellenwert von 80 Messungen ist erreicht – bitte übertragen Sie die Daten jetzt per Bluetooth, um Datenverlust durch Ringspeicher-Überschreibung zu verhindern!",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = CleanAlertText,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { checkAndStartSync() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_sync_from_memory_warning"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CleanAlertText,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Messungen jetzt per Bluetooth übertragen",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                } else if (isApproachingLimit) {
                    // Vorwarnung bei 50..79 Messungen
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_memory_notice"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningOrangeContainer),
                        border = BorderStroke(1.2.dp, WarningOrange)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Übertragungs-Hinweis: ~$estimatedPendingCount von 80 Messungen",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF78350F)
                                )
                                Text(
                                    text = "Letzte Messung: $lastDateStr ($relativeDayLabel, $dailyTarget Messungen/Tag). Bald per Bluetooth übertragen.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { checkAndStartSync() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Übertragen",
                                    tint = WarningOrange
                                )
                            }
                        }
                    }
                } else if (latestMeasurement != null) {
                    // Normaler unaufdringlicher Status-Balken
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = CleanSurfaceVariant,
                        border = BorderStroke(1.dp, CleanOutline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = CleanPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Letzte Messung: $relativeDayLabel ($lastDateStr)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CleanOnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CleanSurface,
                                border = BorderStroke(0.8.dp, CleanOutline)
                            ) {
                                Text(
                                    text = "${dailyTarget}x/Tag (~$estimatedPendingCount/80)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CleanPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. LINE / TREND CURVE EVALUATION COMPONENT
            item {
                Spacer(modifier = Modifier.height(12.dp))
                BpLineChart(
                    measurements = measurements,
                    systoleNormMax = settings.systoleNormMax,
                    diastoleNormMax = settings.diastoleNormMax,
                    chartScaleMax = settings.chartScaleMax,
                    chartScaleMin = settings.chartScaleMin,
                    onUpdateChartScaleMax = { newMax ->
                        viewModel.updateChartScaleMax(newMax)
                    }
                )
            }

            // 3. Section Header
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "MESSWERTE PROTOKOLLE (${measurements.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanOnSurfaceVariant,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Scrollable Measurement Cards grouped by month
            val currentMonthGroup = groupedMonths.firstOrNull { it.isCurrentMonth } ?: groupedMonths.first()
            val previousMonthGroups = groupedMonths.filter { it != currentMonthGroup && it.measurements.isNotEmpty() }

            if (measurements.isEmpty()) {
                item(key = "header_group_${currentMonthGroup.key}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AKTUELLER MONAT • ${currentMonthGroup.monthYearLabel.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanPrimary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "0 Messungen",
                            fontSize = 11.sp,
                            color = CleanMutedText
                        )
                    }
                }

                item(key = "empty_global_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = CleanMutedText.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Keine Messwerte für ${settings.activePerson.name}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = CleanOnSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Schalten Sie Ihr Microlife / aponorm® Gerät ein und tippen Sie auf 'Gerät auslesen'.",
                                fontSize = 13.sp,
                                color = CleanMutedText,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { checkAndStartSync() },
                                colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                                modifier = Modifier.testTag("btn_empty_start_sync")
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gerät auslesen (BLE)")
                            }
                        }
                    }
                }
            } else {
                // Aktueller Kalendermonat: Header oben
                item(key = "header_group_${currentMonthGroup.key}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AKTUELLER MONAT • ${currentMonthGroup.monthYearLabel.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanPrimary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "${currentMonthGroup.measurements.size} ${if (currentMonthGroup.measurements.size == 1) "Messung" else "Messungen"}",
                            fontSize = 11.sp,
                            color = CleanMutedText
                        )
                    }
                }

                if (currentMonthGroup.measurements.isEmpty()) {
                    // Neuer Monat hat begonnen, aber noch keine Messwerte im aktuellen Monat
                    item(key = "empty_month_card_${currentMonthGroup.key}") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = CleanSurfaceVariant,
                            border = BorderStroke(1.dp, CleanOutline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    text = "Noch keine Messungen im ${currentMonthGroup.monthYearLabel} erfasst.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CleanOnSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = currentMonthGroup.measurements,
                        key = { it.id }
                    ) { item ->
                        MeasurementCard(
                            measurement = item,
                            systoleNormMax = settings.systoleNormMax,
                            diastoleNormMax = settings.diastoleNormMax,
                            onDeleteClick = { viewModel.deleteMeasurement(it) }
                        )
                    }
                }

                // Vorherige Monate (z. B. vorheriger aktueller Monat & frühere Monate) hinter Aufklapp-Button
                previousMonthGroups.forEach { prevGroup ->
                    val isExpanded = expandedMonthKeys.contains(prevGroup.key)

                    item(key = "btn_accordion_${prevGroup.key}") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            onClick = {
                                expandedMonthKeys = if (isExpanded) {
                                    expandedMonthKeys - prevGroup.key
                                } else {
                                    expandedMonthKeys + prevGroup.key
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isExpanded) CleanSurfaceVariant else CleanSurface,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isExpanded) CleanPrimary.copy(alpha = 0.6f) else CleanOutline
                            ),
                            shadowElevation = if (isExpanded) 2.dp else 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_month_accordion_${prevGroup.key}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isExpanded) CleanPrimary else CleanSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = if (isExpanded) CleanOnPrimary else CleanPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = prevGroup.monthYearLabel,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanOnSurface
                                        )
                                        Text(
                                            text = "${prevGroup.measurements.size} ${if (prevGroup.measurements.size == 1) "Messwert" else "Messwerte"} verfügbar",
                                            fontSize = 11.sp,
                                            color = CleanMutedText
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isExpanded) CleanPrimary.copy(alpha = 0.15f) else CleanSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isExpanded) "Schließen" else "Anzeigen",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isExpanded) CleanPrimary else CleanOnSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Einklappen" else "Ausklappen",
                                        tint = if (isExpanded) CleanPrimary else CleanOnSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isExpanded) {
                        items(
                            items = prevGroup.measurements,
                            key = { it.id }
                        ) { item ->
                            MeasurementCard(
                                measurement = item,
                                systoleNormMax = settings.systoleNormMax,
                                diastoleNormMax = settings.diastoleNormMax,
                                onDeleteClick = { viewModel.deleteMeasurement(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // BLE Sync Modal Dialog (Mittig)
    if (showBleBottomSheet) {
        BleSyncBottomSheet(
            syncStatus = syncStatus,
            onStartScan = { viewModel.startBleScan() },
            onConnectDevice = { address -> viewModel.connectToDevice(address) },
            onDismiss = {
                showBleBottomSheet = false
                viewModel.resetSyncStatus()
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settings,
            syncStatus = syncStatus,
            onStartBleScan = { viewModel.startBleScanForDevices() },
            onSaveSettings = { newSettings -> viewModel.updateSettings(newSettings) },
            onBackupDatabaseNow = { viewModel.backupDatabaseNow(context) },
            onBackupDatabaseToUri = { uri -> viewModel.backupDatabaseToUri(context, uri) },
            onRestoreDatabaseFromUri = { uri -> viewModel.restoreDatabaseFromUri(context, uri) },
            onSelectBackupFolder = { uri, display -> viewModel.setBackupDirectory(uri, display) },
            onClearAllData = { viewModel.deleteAllMeasurements() },
            onOpenDiagnose = {
                showSettingsDialog = false
                showDiagnoseDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // BLE & GATT Live Diagnose Fenster Modal
    if (showDiagnoseDialog) {
        BleDiagnoseDialog(
            viewModel = viewModel,
            onDismiss = { showDiagnoseDialog = false }
        )
    }

    // Monat-Auswahl für PDF Ausdruck Modal
    if (showPrintMonthDialog) {
        AlertDialog(
            onDismissRequest = { showPrintMonthDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CleanPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = CleanOnPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Monat für Ausdruck",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = CleanOnSurface
                        )
                        val patientDisplayName = settings.patientName.ifBlank { settings.activePerson.name }
                        val birthDate = settings.activePerson.birthDate.trim()
                        val age = calculateAgeYears(birthDate)
                        Text(
                            text = if (birthDate.isNotBlank()) {
                                if (age != null) "Patient: $patientDisplayName (geb. $birthDate, $age Jahre)" else "Patient: $patientDisplayName (geb. $birthDate)"
                            } else {
                                "Patient: $patientDisplayName"
                            },
                            fontSize = 12.sp,
                            color = CleanPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Wählen Sie den gewünschten Monat für das A4-Druckprotokoll aus:",
                        fontSize = 13.sp,
                        color = CleanOnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groupedMonths, key = { it.key }) { group ->
                            val isSelected = selectedPrintMonthKey == group.key
                            Surface(
                                onClick = { selectedPrintMonthKey = group.key },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) CleanPrimary.copy(alpha = 0.12f) else CleanSurfaceVariant,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CleanPrimary else CleanOutline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedPrintMonthKey = group.key },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = CleanPrimary,
                                            unselectedColor = CleanOnSurfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = group.monthYearLabel,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = CleanOnSurface
                                            )
                                            if (group == groupedMonths.firstOrNull()) {
                                                Surface(
                                                    color = CleanPrimary.copy(alpha = 0.18f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Aktuell",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CleanPrimary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${group.measurements.size} ${if (group.measurements.size == 1) "Messung" else "Messungen"}",
                                            fontSize = 11.sp,
                                            color = CleanMutedText
                                        )
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
                        showPrintMonthDialog = false
                        val chosenGroup = groupedMonths.find { it.key == selectedPrintMonthKey }
                            ?: groupedMonths.firstOrNull()
                        if (chosenGroup != null) {
                            viewModel.printPdf(
                                context = context,
                                targetMeasurements = chosenGroup.measurements,
                                monthTitle = chosenGroup.monthYearLabel
                            )
                        } else {
                            viewModel.printPdf(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Drucken", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPrintMonthDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Abbrechen", color = CleanOnSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = CleanSurface
        )
    }

    fun findActivity(ctx: Context): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    fun terminateApp() {
        isExitBackupInProgress = false
        val activity = findActivity(context)
        if (activity != null) {
            activity.finishAndRemoveTask()
            activity.finishAffinity()
        }
        // Beendet den Android-Prozess restlos (wichtig für Samsung One UI / Android 14)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        }, 150)
    }

    // Automatische Sicherung beim Beenden der App mit Statusmeldung (Zentriert und mit Abstand zu den Bildschirmrändern)
    if (isExitBackupInProgress) {
        Dialog(
            onDismissRequest = {
                isExitBackupInProgress = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 350.dp)
                    .wrapContentHeight()
                    .padding(vertical = 20.dp)
                    .testTag("dialog_exit_backup"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanOutline),
                elevation = CardDefaults.cardElevation(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    when (exitBackupSuccess) {
                        true -> Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CleanNormContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CleanNormText,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        false -> Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CleanAlertContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        null -> Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CleanPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = CleanPrimary,
                                strokeWidth = 3.5.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = when (exitBackupSuccess) {
                            true -> "Erfolgreich gesichert"
                            false -> "Sicherung fehlgeschlagen"
                            null -> "Sichern & Beenden"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = CleanOnSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Message
                    Text(
                        text = exitBackupStatusText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = CleanOnSurfaceVariant,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )

                    if (exitBackupSuccess == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sicherung wird durchgeführt...",
                            fontSize = 11.5.sp,
                            color = CleanMutedText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { isExitBackupInProgress = false },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CleanOutline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("btn_exit_backup_cancel_loading")
                        ) {
                            Text(
                                text = "Abbrechen",
                                fontSize = 13.sp,
                                color = CleanOnSurfaceVariant
                            )
                        }
                    }

                    if (exitBackupSuccess != null) {
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isExitBackupInProgress = false },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CleanOutline),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("btn_exit_backup_cancel")
                            ) {
                                Text(
                                    text = "Abbrechen",
                                    fontSize = 13.sp,
                                    color = CleanOnSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { terminateApp() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (exitBackupSuccess == true) CleanPrimary else Color(0xFFDC2626)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("btn_exit_backup_ok")
                            ) {
                                Text(
                                    text = "Beenden",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MonthGroup(
    val key: String,
    val year: Int,
    val month: Int,
    val monthYearLabel: String,
    val measurements: List<com.example.data.model.BpMeasurement>,
    val isCurrentMonth: Boolean = false
)


