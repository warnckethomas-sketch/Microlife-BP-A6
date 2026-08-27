package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BpMeasurement
import com.example.ui.theme.CleanAfibContainer
import com.example.ui.theme.CleanAlertContainer
import com.example.ui.theme.CleanAlertText
import com.example.ui.theme.CleanMutedText
import com.example.ui.theme.CleanNormContainer
import com.example.ui.theme.CleanNormText
import com.example.ui.theme.CleanOnAfibContainer
import com.example.ui.theme.CleanOnAlertContainer
import com.example.ui.theme.CleanOnNormContainer
import com.example.ui.theme.CleanOnSurface
import com.example.ui.theme.CleanOnSurfaceVariant
import com.example.ui.theme.CleanOutline
import com.example.ui.theme.CleanSurface

@Composable
fun MeasurementCard(
    measurement: BpMeasurement,
    systoleNormMax: Int,
    diastoleNormMax: Int,
    onDeleteClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isNorm = measurement.isWithinNorm(systoleNormMax, diastoleNormMax)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("measurement_card_${measurement.id}"),
        colors = CardDefaults.cardColors(containerColor = CleanSurface),
        border = BorderStroke(1.dp, CleanOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Date Header, BP Numbers, Status Chips
            Column(modifier = Modifier.weight(1f)) {
                // Timestamp
                Text(
                    text = measurement.formattedDateTime().uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanOnSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                // BP Numbers with Clean Typography
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${measurement.systole}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (measurement.systole >= systoleNormMax) CleanAlertText else CleanNormText
                    )
                    Text(
                        text = "/",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Light,
                        color = CleanOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = "${measurement.diastole}",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (measurement.diastole >= diastoleNormMax) CleanAlertText else CleanNormText
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "mmHg",
                        fontSize = 10.sp,
                        color = CleanMutedText,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Status Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Normal / High Pill
                    Surface(
                        color = if (isNorm) CleanNormContainer else CleanAlertContainer,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = if (isNorm) "NORMAL" else "HOCH",
                            color = if (isNorm) CleanOnNormContainer else CleanOnAlertContainer,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // AFIB Pill
                    if (measurement.afibDetected) {
                        Surface(
                            color = CleanAfibContainer,
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⚠",
                                    color = CleanOnAfibContainer,
                                    fontSize = 9.sp
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "AFIB",
                                    color = CleanOnAfibContainer,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (measurement.notes.isNotEmpty()) {
                        Text(
                            text = "• ${measurement.notes}",
                            fontSize = 10.sp,
                            color = CleanMutedText,
                            maxLines = 1
                        )
                    }
                }
            }

            // Right Side: Pulse Section + Delete Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PULS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CleanMutedText,
                        letterSpacing = 0.5.sp
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${measurement.pulse}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CleanOnSurface
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "bpm",
                            fontSize = 9.sp,
                            color = CleanMutedText
                        )
                    }
                }

                IconButton(
                    onClick = { onDeleteClick(measurement.id) },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_measurement_${measurement.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Messung löschen",
                        tint = CleanMutedText.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
