package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ble.BleSyncStatus
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanNormContainer
import com.example.ui.theme.CleanNormText
import com.example.ui.theme.CleanOnPrimary
import com.example.ui.theme.CleanOnSurface
import com.example.ui.theme.CleanOnSurfaceVariant
import com.example.ui.theme.CleanOutline
import com.example.ui.theme.CleanPrimary
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.CleanSurfaceVariant
import com.example.ui.viewmodel.BpViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleDiagnoseDialog(
    viewModel: BpViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val listState = rememberLazyListState()

    // Auto-scroll to latest log entry
    LaunchedEffect(diagnosticLogs.size) {
        if (diagnosticLogs.isNotEmpty()) {
            listState.animateScrollToItem(diagnosticLogs.size - 1)
        }
    }

    // SAF File Creator for saving .txt directly
    val createTextFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.writeDiagnosticLogToUri(context, uri)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = CleanSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "GATT & Bluetooth Diagnose",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = CleanOnSurface
                            )
                            Text(
                                text = "Aponorm BP3Gu1-6B • SN 2000717",
                                fontSize = 12.sp,
                                color = CleanOnSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_diagnose")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = CleanOnSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Info banner explaining the GATT architecture inspection
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CleanSurfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, CleanOutline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = CleanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Liest alle GATT-Services, Eigenschaften (READ, WRITE, NOTIFY) und Rohdaten-Bytes live aus dem Chip aus.",
                            fontSize = 11.sp,
                            color = CleanOnSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Status Badge & Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (syncStatus) {
                        is BleSyncStatus.Idle -> "Bereit"
                        is BleSyncStatus.Scanning -> "Scanne nach Geräten..."
                        is BleSyncStatus.Connecting -> "Verbinde..."
                        is BleSyncStatus.TimeSyncing -> "Zeit-Sync & GATT Analyse..."
                        is BleSyncStatus.Downloading -> "Lese Speicher aus..."
                        is BleSyncStatus.Success -> "Erfolgreich abgeschlossen"
                        is BleSyncStatus.Error -> "Fehler / Getrennt"
                        else -> "Inaktiv"
                    }

                    val statusColor = when (syncStatus) {
                        is BleSyncStatus.Success -> Color(0xFF10B981)
                        is BleSyncStatus.Error -> Color(0xFFEF4444)
                        is BleSyncStatus.Idle -> CleanOnSurfaceVariant
                        else -> CleanPrimary
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColor
                        )
                    }

                    // Buttons for live GATT actions & test tools
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.sendManualReadDeviceTime()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_read_device_time")
                        ) {
                            Text(
                                text = "🔍 Uhrzeit lesen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.sendManualTimeSync()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_send_time_sync")
                        ) {
                            Text(
                                text = "⏰ Uhrzeit senden",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.startBleScan()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CleanPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_start_full_read")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = CleanOnPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Starten",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Terminal Console Window
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F172A) // Rich dark Slate
                    ),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Terminal Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "LOGCAT / GATT STREAM (${diagnosticLogs.size} Zeilen)",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    viewModel.clearDiagnosticLogs()
                                    Toast.makeText(context, "Diagnoseprotokoll geleert!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF334155),
                                    contentColor = Color(0xFFF87171)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("btn_clear_diagnose_top")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFFF87171)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Leeren",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF87171)
                                )
                            }
                        }

                        // Terminal Log Content
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            if (diagnosticLogs.isEmpty()) {
                                item {
                                    Text(
                                        text = "Warte auf Verbindung zum Aponorm-Gerät...\nSchalten Sie das Gerät ein (Bluetooth-Symbol 'bt' blinkt).\n",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            } else {
                                items(diagnosticLogs) { line ->
                                    val textColor = when {
                                        line.contains("SERVICE UUID") || line.contains("Modell:") -> Color(0xFF38BDF8) // Bright Cyan
                                        line.contains("Charakteristik:") -> Color(0xFF818CF8) // Indigo
                                        line.contains("READ (") -> Color(0xFF34D399) // Mint Green
                                        line.contains("WRITE (") || line.contains("WRITE_NO_RESPONSE") -> Color(0xFFFCD34D) // Yellow
                                        line.contains("NOTIFY (") || line.contains("INDICATE (") -> Color(0xFFA78BFA) // Purple
                                        line.contains("TX ->") -> Color(0xFFF472B6) // Pink
                                        line.contains("RX <-") -> Color(0xFF4ADE80) // Light Green
                                        line.contains("GERÄT PHYSISCH VERBUNDEN") || line.contains("ERFOLGREICH") || line.contains("✓") -> Color(0xFF22C55E) // Green
                                        line.contains("FEHLER") || line.contains("❌") || line.contains("⚠️") -> Color(0xFFF87171) // Red
                                        line.contains("SCHRITT") || line.contains("▶") -> Color(0xFFFBBF24) // Amber
                                        line.contains("====") -> Color(0xFF64748B) // Slate Divider
                                        else -> Color(0xFFE2E8F0) // Off white
                                    }

                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = textColor,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Save to File (.txt) Button
                    Button(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            val fileName = "Aponorm_Diagnose_$timeStamp.txt"
                            createTextFileLauncher.launch(fileName)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp)
                            .testTag("btn_save_diagnose_file")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Speichern",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Copy to Clipboard Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Aponorm Diagnose", viewModel.getFormattedDiagnosticLog())
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Diagnoseprotokoll kopiert!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CleanOutline),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_copy_diagnose")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = CleanOnSurface
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Kopieren",
                            fontSize = 11.sp,
                            color = CleanOnSurface
                        )
                    }

                    // Share Button
                    OutlinedButton(
                        onClick = {
                            viewModel.shareDiagnosticLog(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CleanOutline),
                        modifier = Modifier
                            .weight(0.85f)
                            .height(44.dp)
                            .testTag("btn_share_diagnose")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = CleanOnSurface
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Teilen",
                            fontSize = 11.sp,
                            color = CleanOnSurface
                        )
                    }

                    // Leeren Button
                    OutlinedButton(
                        onClick = {
                            viewModel.clearDiagnosticLogs()
                            Toast.makeText(context, "Diagnoseprotokoll geleert!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        ),
                        modifier = Modifier
                            .weight(0.95f)
                            .height(44.dp)
                            .testTag("btn_clear_diagnose_bottom")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Leeren",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}
