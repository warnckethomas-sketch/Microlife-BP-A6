package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BpMeasurement
import com.example.ui.theme.CleanAlertContainer
import com.example.ui.theme.CleanAlertText
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanMutedText
import com.example.ui.theme.CleanNormContainer
import com.example.ui.theme.CleanNormText
import com.example.ui.theme.CleanOnNormContainer
import com.example.ui.theme.CleanOnPrimary
import com.example.ui.theme.CleanOnSurface
import com.example.ui.theme.CleanOnSurfaceVariant
import com.example.ui.theme.CleanOutline
import com.example.ui.theme.CleanPrimary
import com.example.ui.theme.CleanPrimaryContainer
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.CleanSurfaceVariant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ChartTimeFrame(val label: String) {
    DAY("Tag"),
    WEEK("Woche"),
    MONTH("Monat"),
    YEAR("Jahr")
}

data class PeriodBoundaries(
    val startMillis: Long,
    val endMillis: Long,
    val periodTitle: String,
    val startDateLabel: String,
    val endDateLabel: String
)

@Composable
fun BpLineChart(
    measurements: List<BpMeasurement>,
    systoleNormMax: Int,
    diastoleNormMax: Int,
    chartScaleMax: Int = 210,
    chartScaleMin: Int = 30,
    onUpdateChartScaleMax: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Collapsible card state (default collapsed per user preference)
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    // Timeframe selector state (Tag | Woche | Monat | Jahr)
    var selectedTimeFrame by remember { mutableStateOf(ChartTimeFrame.WEEK) }

    // Offset in periods (0 = current, -1 = previous, etc.)
    var pageOffset by remember { mutableIntStateOf(0) }

    // Scale selector states
    var showScaleMenu by remember { mutableStateOf(false) }
    var showCustomScaleDialog by remember { mutableStateOf(false) }
    var customScaleInput by remember { mutableStateOf(chartScaleMax.toString()) }

    // Calculate time window boundaries based on timeframe & offset
    val periodInfo = remember(selectedTimeFrame, pageOffset) {
        when (selectedTimeFrame) {
            ChartTimeFrame.DAY -> {
                val cal = Calendar.getInstance(Locale.GERMANY).apply {
                    timeInMillis = System.currentTimeMillis()
                    add(Calendar.DAY_OF_YEAR, pageOffset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                val dfDay = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                val title = when (pageOffset) {
                    0 -> "Heute (${dfDay.format(Date(start))})"
                    -1 -> "Gestern (${dfDay.format(Date(start))})"
                    else -> dfDay.format(Date(start))
                }
                PeriodBoundaries(
                    startMillis = start,
                    endMillis = end,
                    periodTitle = title,
                    startDateLabel = "${dfDay.format(Date(start))} 00:00",
                    endDateLabel = "${dfDay.format(Date(end))} 23:59"
                )
            }
            ChartTimeFrame.WEEK -> {
                val cal = Calendar.getInstance(Locale.GERMANY).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    timeInMillis = System.currentTimeMillis()
                    add(Calendar.WEEK_OF_YEAR, pageOffset)
                    val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                    val daysFromMonday = when (dayOfWeek) {
                        Calendar.MONDAY -> 0
                        Calendar.TUESDAY -> 1
                        Calendar.WEDNESDAY -> 2
                        Calendar.THURSDAY -> 3
                        Calendar.FRIDAY -> 4
                        Calendar.SATURDAY -> 5
                        Calendar.SUNDAY -> 6
                        else -> 0
                    }
                    add(Calendar.DAY_OF_MONTH, -daysFromMonday)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val endCal = (cal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_MONTH, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                val df = SimpleDateFormat("dd.MM.", Locale.GERMANY)
                val dfYear = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                val title = when (pageOffset) {
                    0 -> "Diese Woche (Mo ${df.format(Date(start))} – So ${dfYear.format(Date(end))})"
                    -1 -> "Letzte Woche (Mo ${df.format(Date(start))} – So ${dfYear.format(Date(end))})"
                    else -> "Mo ${df.format(Date(start))} – So ${dfYear.format(Date(end))}"
                }
                PeriodBoundaries(
                    startMillis = start,
                    endMillis = end,
                    periodTitle = title,
                    startDateLabel = "Mo ${dfYear.format(Date(start))}",
                    endDateLabel = "So ${dfYear.format(Date(end))}"
                )
            }
            ChartTimeFrame.MONTH -> {
                val cal = Calendar.getInstance(Locale.GERMANY).apply {
                    timeInMillis = System.currentTimeMillis()
                    add(Calendar.MONTH, pageOffset)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, maxDay)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                val dfMonth = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
                val dfFull = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                val title = when (pageOffset) {
                    0 -> "Diesen Monat (01. – ${maxDay}. ${dfMonth.format(Date(start))})"
                    -1 -> "Letzten Monat (01. – ${maxDay}. ${dfMonth.format(Date(start))})"
                    else -> "01. – ${maxDay}. ${dfMonth.format(Date(start))}"
                }
                PeriodBoundaries(
                    startMillis = start,
                    endMillis = end,
                    periodTitle = title,
                    startDateLabel = dfFull.format(Date(start)),
                    endDateLabel = dfFull.format(Date(end))
                )
            }
            ChartTimeFrame.YEAR -> {
                val cal = Calendar.getInstance(Locale.GERMANY).apply {
                    timeInMillis = System.currentTimeMillis()
                    add(Calendar.YEAR, pageOffset)
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val endCal = (cal.clone() as Calendar).apply {
                    val maxDayYear = getActualMaximum(Calendar.DAY_OF_YEAR)
                    set(Calendar.DAY_OF_YEAR, maxDayYear)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                val yearStr = SimpleDateFormat("yyyy", Locale.GERMANY).format(Date(start))
                val title = when (pageOffset) {
                    0 -> "Dieses Jahr ($yearStr)"
                    -1 -> "Letztes Jahr ($yearStr)"
                    else -> "Jahr $yearStr"
                }
                PeriodBoundaries(
                    startMillis = start,
                    endMillis = end,
                    periodTitle = title,
                    startDateLabel = "01.01.$yearStr",
                    endDateLabel = "31.12.$yearStr"
                )
            }
        }
    }

    // Filter measurements strictly within the selected period
    val periodMeasurements = remember(measurements, periodInfo.startMillis, periodInfo.endMillis) {
        measurements.filter { it.timestamp in periodInfo.startMillis..periodInfo.endMillis }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(selectedTimeFrame, pageOffset) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        // Swipe right (> 60px) -> Vorheriger Zeitraum
                        if (dragAccumulator > 60f) {
                            pageOffset -= 1
                        }
                        // Swipe left (< -60px) -> Nächster Zeitraum Richtung heute
                        else if (dragAccumulator < -60f) {
                            if (pageOffset < 0) {
                                pageOffset += 1
                            }
                        }
                        dragAccumulator = 0f
                    }
                )
            },
        colors = CardDefaults.cardColors(containerColor = CleanSurface),
        border = BorderStroke(1.dp, CleanOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row with Title, Timeframe Badge & Expand/Collapse Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CleanSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = CleanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GRAFISCHE AUSWERTUNG",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CleanOnSurfaceVariant,
                                letterSpacing = 1.1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = CleanSurfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = selectedTimeFrame.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CleanPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // "Heute" Icon wenn nicht im aktuellen Zeitraum
                            if (pageOffset != 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(CleanPrimary)
                                        .clickable { pageOffset = 0 }
                                        .testTag("btn_chart_header_today"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Today,
                                        contentDescription = "Zu Heute springen",
                                        tint = CleanOnPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isExpanded) periodInfo.periodTitle else "Tippen zum Aufklappen",
                            fontSize = 12.sp,
                            color = CleanMutedText
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("btn_toggle_chart_expand")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                        tint = CleanPrimary
                    )
                }
            }

            // Expandable Content Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // 1. Timeframe Filter Selector Tabs: Tag | Woche | Monat | Jahr
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CleanBackground)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ChartTimeFrame.values().forEach { timeFrame ->
                            val isSelected = selectedTimeFrame == timeFrame
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) CleanPrimary else Color.Transparent)
                                    .clickable {
                                        if (selectedTimeFrame != timeFrame) {
                                            selectedTimeFrame = timeFrame
                                            pageOffset = 0 // Reset to current when changing timeframe
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                                    .testTag("tab_timeframe_${timeFrame.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timeFrame.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CleanOnPrimary else CleanOnSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Legend Bar & Scale Range Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 10.dp, height = 3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(CleanPrimary)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Sys", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CleanPrimary)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 10.dp, height = 3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(CleanNormText)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Dia", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CleanNormText)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 8.dp, height = 7.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0x352196F3))
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Bereich", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = CleanPrimary)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CleanAlertText)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("> Norm", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CleanAlertText)
                            }
                        }

                        // Interactive Scale Selector Chip
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CleanSurfaceVariant,
                                border = BorderStroke(1.dp, CleanOutline),
                                modifier = Modifier.clickable { showScaleMenu = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Skala anpassen",
                                        tint = CleanPrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Skala: $chartScaleMax ▾",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CleanPrimary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showScaleMenu,
                                onDismissRequest = { showScaleMenu = false },
                                modifier = Modifier.background(CleanSurface)
                            ) {
                                listOf(180, 200, 210, 240).forEach { presetVal ->
                                    val isSelected = chartScaleMax == presetVal
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "30 – $presetVal mmHg",
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) CleanPrimary else CleanOnSurface,
                                                    fontSize = 13.sp
                                                )
                                                if (presetVal == 210) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("(Standard)", fontSize = 10.sp, color = CleanMutedText)
                                                } else if (presetVal == 180) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("(Kompakt)", fontSize = 10.sp, color = CleanMutedText)
                                                }
                                            }
                                        },
                                        onClick = {
                                            showScaleMenu = false
                                            onUpdateChartScaleMax?.invoke(presetVal)
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = CleanPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Manuell anpassen...",
                                                fontWeight = FontWeight.Medium,
                                                color = CleanOnSurface,
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        showScaleMenu = false
                                        customScaleInput = chartScaleMax.toString()
                                        showCustomScaleDialog = true
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (periodMeasurements.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Keine Messdaten in diesem Zeitraum.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CleanMutedText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Wischen Sie nach rechts oder tippen Sie auf 'Heute'.",
                                    fontSize = 10.sp,
                                    color = CleanMutedText.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        // Chronological sorting; for YEAR view aggregate by weekly averages (KW)
                        val chartItems = remember(periodMeasurements, selectedTimeFrame) {
                            if (selectedTimeFrame == ChartTimeFrame.YEAR) {
                                val cal = Calendar.getInstance(Locale.GERMANY)
                                val groupedByWeek = periodMeasurements.groupBy { m ->
                                    cal.timeInMillis = m.timestamp
                                    val week = cal.get(Calendar.WEEK_OF_YEAR)
                                    val year = cal.get(Calendar.YEAR)
                                    year to week
                                }

                                groupedByWeek.map { (yw, weekList) ->
                                    val avgSys = Math.round(weekList.map { it.systole }.average()).toInt()
                                    val avgDia = Math.round(weekList.map { it.diastole }.average()).toInt()
                                    val avgPulse = Math.round(weekList.map { it.pulse }.average()).toInt()
                                    val hasAfib = weekList.any { it.afibDetected }
                                    val avgTimestamp = weekList.map { it.timestamp }.average().toLong()

                                    BpMeasurement(
                                        id = yw.first * 100 + yw.second,
                                        systole = avgSys,
                                        diastole = avgDia,
                                        pulse = avgPulse,
                                        timestamp = avgTimestamp,
                                        afibDetected = hasAfib,
                                        notes = "KW ${yw.second} (Ø aus ${weekList.size} Messungen)"
                                    )
                                }.sortedBy { it.timestamp }
                            } else {
                                periodMeasurements.sortedBy { it.timestamp }
                            }
                        }
                        val minScale = chartScaleMin.toFloat()
                        val maxScale = chartScaleMax.toFloat().coerceAtLeast(minScale + 50f)
                        val ySteps = remember(minScale, maxScale) {
                            val step = when {
                                maxScale <= 180 -> 30
                                maxScale <= 220 -> 30
                                maxScale <= 260 -> 40
                                else -> 50
                            }
                            val list = mutableListOf<Int>()
                            var cur = minScale.toInt()
                            while (cur <= maxScale.toInt()) {
                                list.add(cur)
                                cur += step
                            }
                            if (list.lastOrNull() != maxScale.toInt() && (maxScale.toInt() - (list.lastOrNull() ?: 0) >= 15)) {
                                list.add(maxScale.toInt())
                            }
                            list
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Line Chart Canvas Area with 30..210 scale & Green Norm Badges
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            ) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val width = size.width
                                    val height = size.height

                                    val topPadding = 18.dp.toPx()
                                    val bottomPadding = 26.dp.toPx()
                                    val leftPadding = 52.dp.toPx() // Room for scale numbers & green badges
                                    val rightPadding = 16.dp.toPx()

                                    val drawWidth = width - leftPadding - rightPadding
                                    val drawHeight = height - topPadding - bottomPadding

                                    fun getYPosition(value: Float): Float {
                                        val clamped = value.coerceIn(minScale, maxScale)
                                        val fraction = (clamped - minScale) / (maxScale - minScale)
                                        return topPadding + (1f - fraction) * drawHeight
                                    }

                                    val totalDuration = (periodInfo.endMillis - periodInfo.startMillis).coerceAtLeast(1).toFloat()

                                    fun getXPosition(timestamp: Long): Float {
                                        val currentOffset = (timestamp - periodInfo.startMillis).toFloat()
                                        val fraction = (currentOffset / totalDuration).coerceIn(0f, 1f)
                                        return leftPadding + fraction * drawWidth
                                    }

                                    // Paint for Y-axis scale labels (30, 60, 90, 120, 150, 180, 210)
                                    val scaleTextPaint = Paint().apply {
                                        color = Color.Black.toArgb()
                                        textSize = 9.5.sp.toPx()
                                        typeface = Typeface.DEFAULT_BOLD
                                        textAlign = Paint.Align.RIGHT
                                        isAntiAlias = true
                                    }
                                    val scaleFontMetrics = scaleTextPaint.fontMetrics
                                    val scaleTextOffset = (scaleFontMetrics.descent + scaleFontMetrics.ascent) / 2f

                                    // 1. Draw horizontal grid lines and 30..210 labels in steps (visible thin black lines)
                                    ySteps.forEach { stepVal ->
                                        val y = getYPosition(stepVal.toFloat())
                                        
                                        // Visible thin black line across chart
                                        drawLine(
                                            color = Color.Black,
                                            start = Offset(leftPadding, y),
                                            end = Offset(width - rightPadding, y),
                                            strokeWidth = 0.9.dp.toPx()
                                        )

                                        // Left scale number label
                                        drawContext.canvas.nativeCanvas.drawText(
                                            "$stepVal",
                                            leftPadding - 8.dp.toPx(),
                                            y - scaleTextOffset,
                                            scaleTextPaint
                                        )
                                    }

                                    val axisTextPaint = Paint().apply {
                                        color = CleanPrimary.toArgb()
                                        textSize = 8.5.sp.toPx()
                                        typeface = Typeface.DEFAULT_BOLD
                                        textAlign = Paint.Align.CENTER
                                        isAntiAlias = true
                                    }
                                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

                                    // X-Axis Time/Date Divisions depending on TimeFrame
                                    when (selectedTimeFrame) {
                                        ChartTimeFrame.DAY -> {
                                            val timeSteps = listOf(
                                                Pair(0.0f, "00:00"),
                                                Pair(0.25f, "06:00"),
                                                Pair(0.50f, "12:00"),
                                                Pair(0.75f, "18:00"),
                                                Pair(1.0f, "24:00")
                                            )
                                            timeSteps.forEach { (fraction, label) ->
                                                val xPos = leftPadding + fraction * drawWidth
                                                if (fraction > 0f && fraction < 1f) {
                                                    drawLine(
                                                        color = CleanPrimary.copy(alpha = 0.2f),
                                                        start = Offset(xPos, topPadding),
                                                        end = Offset(xPos, topPadding + drawHeight),
                                                        strokeWidth = 1.dp.toPx(),
                                                        pathEffect = gridDash
                                                    )
                                                }
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    label,
                                                    xPos,
                                                    topPadding + drawHeight + 16.dp.toPx(),
                                                    axisTextPaint
                                                )
                                            }
                                        }
                                        ChartTimeFrame.WEEK -> {
                                            val days = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
                                            days.forEachIndexed { idx, dayName ->
                                                val fraction = (idx + 0.5f) / 7f
                                                val xPos = leftPadding + fraction * drawWidth
                                                if (idx > 0) {
                                                    val divX = leftPadding + (idx / 7f) * drawWidth
                                                    drawLine(
                                                        color = CleanPrimary.copy(alpha = 0.15f),
                                                        start = Offset(divX, topPadding),
                                                        end = Offset(divX, topPadding + drawHeight),
                                                        strokeWidth = 0.8.dp.toPx(),
                                                        pathEffect = gridDash
                                                    )
                                                }
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    dayName,
                                                    xPos,
                                                    topPadding + drawHeight + 16.dp.toPx(),
                                                    axisTextPaint
                                                )
                                            }
                                        }
                                        ChartTimeFrame.MONTH -> {
                                            // Month Day Scale (e.g. 01., 05., 10., 15., 20., 25., 30./31.)
                                            val cal = Calendar.getInstance(Locale.GERMANY).apply { timeInMillis = periodInfo.startMillis }
                                            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                                            val scaleDays = mutableListOf<Int>()
                                            for (d in 1..daysInMonth) {
                                                if (d == 1 || d % 5 == 0 || d == daysInMonth) {
                                                    scaleDays.add(d)
                                                }
                                            }
                                            // Fine vertical day ticks
                                            for (d in 1..daysInMonth) {
                                                val dayFraction = (d - 1).toFloat() / (daysInMonth - 1).toFloat()
                                                val xPos = leftPadding + dayFraction * drawWidth
                                                val isMajor = scaleDays.contains(d)
                                                drawLine(
                                                    color = if (isMajor) CleanPrimary.copy(alpha = 0.2f) else CleanOutline.copy(alpha = 0.25f),
                                                    start = Offset(xPos, topPadding),
                                                    end = Offset(xPos, topPadding + drawHeight),
                                                    strokeWidth = if (isMajor) 0.9.dp.toPx() else 0.5.dp.toPx(),
                                                    pathEffect = if (isMajor) gridDash else null
                                                )
                                            }
                                            // Day labels
                                            scaleDays.distinct().forEach { d ->
                                                val dayFraction = (d - 1).toFloat() / (daysInMonth - 1).toFloat()
                                                val xPos = leftPadding + dayFraction * drawWidth
                                                val dayStr = if (d < 10) "0$d." else "$d."
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    dayStr,
                                                    xPos,
                                                    topPadding + drawHeight + 16.dp.toPx(),
                                                    axisTextPaint
                                                )
                                            }
                                        }
                                        ChartTimeFrame.YEAR -> {
                                            val months = listOf("Jan", "Mär", "Mai", "Jul", "Sep", "Nov")
                                            months.forEachIndexed { idx, mName ->
                                                val fraction = (idx * 2 + 0.5f) / 12f
                                                val xPos = leftPadding + fraction * drawWidth
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    mName,
                                                    xPos,
                                                    topPadding + drawHeight + 16.dp.toPx(),
                                                    axisTextPaint
                                                )
                                            }
                                        }
                                    }

                                    // 2. Threshold Green Reference Lines & Badges for max Systole and Diastole
                                    val sysLimitY = getYPosition(systoleNormMax.toFloat())
                                    val diaLimitY = getYPosition(diastoleNormMax.toFloat())
                                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                                    // Green horizontal reference lines
                                    drawLine(
                                        color = CleanNormText.copy(alpha = 0.75f),
                                        start = Offset(leftPadding, sysLimitY),
                                        end = Offset(width - rightPadding, sysLimitY),
                                        strokeWidth = 1.5.dp.toPx(),
                                        pathEffect = dashEffect
                                    )

                                    drawLine(
                                        color = CleanNormText.copy(alpha = 0.75f),
                                        start = Offset(leftPadding, diaLimitY),
                                        end = Offset(width - rightPadding, diaLimitY),
                                        strokeWidth = 1.5.dp.toPx(),
                                        pathEffect = dashEffect
                                    )

                                    // Draw Green Shield / Badge for Systole max on the right edge of scale
                                    val badgeWidth = 32.dp.toPx()
                                    val badgeHeight = 15.dp.toPx()
                                    val badgeCorner = 4.dp.toPx()
                                    val badgeX = leftPadding - badgeWidth - 2.dp.toPx()

                                    // Systole Badge
                                    drawRoundRect(
                                        color = CleanNormContainer,
                                        topLeft = Offset(badgeX, sysLimitY - badgeHeight / 2),
                                        size = Size(badgeWidth, badgeHeight),
                                        cornerRadius = CornerRadius(badgeCorner, badgeCorner)
                                    )
                                    drawRoundRect(
                                        color = CleanNormText.copy(alpha = 0.6f),
                                        topLeft = Offset(badgeX, sysLimitY - badgeHeight / 2),
                                        size = Size(badgeWidth, badgeHeight),
                                        cornerRadius = CornerRadius(badgeCorner, badgeCorner),
                                        style = Stroke(width = 1.dp.toPx())
                                    )

                                    // Diastole Badge
                                    drawRoundRect(
                                        color = CleanNormContainer,
                                        topLeft = Offset(badgeX, diaLimitY - badgeHeight / 2),
                                        size = Size(badgeWidth, badgeHeight),
                                        cornerRadius = CornerRadius(badgeCorner, badgeCorner)
                                    )
                                    drawRoundRect(
                                        color = CleanNormText.copy(alpha = 0.6f),
                                        topLeft = Offset(badgeX, diaLimitY - badgeHeight / 2),
                                        size = Size(badgeWidth, badgeHeight),
                                        cornerRadius = CornerRadius(badgeCorner, badgeCorner),
                                        style = Stroke(width = 1.dp.toPx())
                                    )

                                    // Text inside green badges
                                    val badgeTextPaint = Paint().apply {
                                        color = CleanOnNormContainer.toArgb()
                                        textSize = 9.sp.toPx()
                                        typeface = Typeface.DEFAULT_BOLD
                                        textAlign = Paint.Align.CENTER
                                        isAntiAlias = true
                                    }

                                    val badgeFontMetrics = badgeTextPaint.fontMetrics
                                    val badgeTextOffset = (badgeFontMetrics.descent + badgeFontMetrics.ascent) / 2f
                                    val badgeCenterX = badgeX + badgeWidth / 2f

                                    drawContext.canvas.nativeCanvas.drawText(
                                        "$systoleNormMax",
                                        badgeCenterX,
                                        sysLimitY - badgeTextOffset,
                                        badgeTextPaint
                                    )

                                    drawContext.canvas.nativeCanvas.drawText(
                                        "$diastoleNormMax",
                                        badgeCenterX,
                                        diaLimitY - badgeTextOffset,
                                        badgeTextPaint
                                    )

                                    // 3. Fill Area between Systole and Diastole (Light Blue Transparent)
                                    if (chartItems.size > 1) {
                                        val betweenAreaPath = Path()
                                        // Systole line (left to right)
                                        chartItems.forEachIndexed { index, item ->
                                            val x = getXPosition(item.timestamp)
                                            val sysY = getYPosition(item.systole.toFloat())
                                            if (index == 0) {
                                                betweenAreaPath.moveTo(x, sysY)
                                            } else {
                                                betweenAreaPath.lineTo(x, sysY)
                                            }
                                        }
                                        // Diastole line (right to left)
                                        for (i in chartItems.indices.reversed()) {
                                            val item = chartItems[i]
                                            val x = getXPosition(item.timestamp)
                                            val diaY = getYPosition(item.diastole.toFloat())
                                            betweenAreaPath.lineTo(x, diaY)
                                        }
                                        betweenAreaPath.close()

                                        drawPath(
                                            path = betweenAreaPath,
                                            color = Color(0x352196F3) // Helle transparente blaue Füllung
                                        )
                                    }

                                    // 4. Plot Continuous Lines (Systole & Diastole)
                                    val sysPath = Path()
                                    val diaPath = Path()

                                    chartItems.forEachIndexed { index, item ->
                                        val x = getXPosition(item.timestamp)
                                        val sysY = getYPosition(item.systole.toFloat())
                                        val diaY = getYPosition(item.diastole.toFloat())

                                        if (index == 0) {
                                            sysPath.moveTo(x, sysY)
                                            diaPath.moveTo(x, diaY)
                                        } else {
                                            sysPath.lineTo(x, sysY)
                                            diaPath.lineTo(x, diaY)
                                        }
                                    }

                                    if (chartItems.size > 1) {
                                        drawPath(
                                            path = sysPath,
                                            color = CleanPrimary,
                                            style = Stroke(
                                                width = 3.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )

                                        drawPath(
                                            path = diaPath,
                                            color = CleanNormText,
                                            style = Stroke(
                                                width = 3.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    } else if (chartItems.size == 1) {
                                        // Single measurement fallback: draw clear single point
                                        val single = chartItems.first()
                                        val x = getXPosition(single.timestamp)
                                        val sysY = getYPosition(single.systole.toFloat())
                                        val diaY = getYPosition(single.diastole.toFloat())

                                        drawLine(
                                            color = Color(0x352196F3),
                                            start = Offset(x, sysY),
                                            end = Offset(x, diaY),
                                            strokeWidth = 6.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )

                                        drawCircle(color = CleanPrimary, radius = 5.dp.toPx(), center = Offset(x, sysY))
                                        drawCircle(color = CleanNormText, radius = 5.dp.toPx(), center = Offset(x, diaY))
                                    }

                                    // 5. Red Dots for Values Above Norm & AFIB Alerts & Measurement Times (for Day View)
                                    val pointTimePaint = Paint().apply {
                                        color = CleanOnSurface.toArgb()
                                        textSize = 8.sp.toPx()
                                        typeface = Typeface.DEFAULT_BOLD
                                        textAlign = Paint.Align.CENTER
                                        isAntiAlias = true
                                    }
                                    val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)

                                    chartItems.forEach { item ->
                                        val x = getXPosition(item.timestamp)
                                        val sysY = getYPosition(item.systole.toFloat())
                                        val diaY = getYPosition(item.diastole.toFloat())

                                        val isSysHigh = item.systole >= systoleNormMax
                                        val isDiaHigh = item.diastole >= diastoleNormMax

                                        // Systole Red Dot if above norm or AFIB
                                        if (isSysHigh || item.afibDetected) {
                                            drawCircle(
                                                color = Color.White,
                                                radius = 4.5.dp.toPx(),
                                                center = Offset(x, sysY)
                                            )
                                            drawCircle(
                                                color = CleanAlertText,
                                                radius = 3.dp.toPx(),
                                                center = Offset(x, sysY)
                                            )
                                        }

                                        // Diastole Red Dot if above norm
                                        if (isDiaHigh) {
                                            drawCircle(
                                                color = Color.White,
                                                radius = 4.5.dp.toPx(),
                                                center = Offset(x, diaY)
                                            )
                                            drawCircle(
                                                color = CleanAlertText,
                                                radius = 3.dp.toPx(),
                                                center = Offset(x, diaY)
                                            )
                                        }

                                        // Show time above line in Day view if points are few (<= 6)
                                        if (selectedTimeFrame == ChartTimeFrame.DAY && chartItems.size <= 6) {
                                            val timeStr = timeFormat.format(Date(item.timestamp))
                                            drawContext.canvas.nativeCanvas.drawText(
                                                timeStr,
                                                x,
                                                sysY - 8.dp.toPx(),
                                                pointTimePaint
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom Axis: Startdatum links & Enddatum rechts (bzw. 24h Zeitachse bei Tag)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CleanSurfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedTimeFrame == ChartTimeFrame.DAY) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = CleanPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = periodInfo.startDateLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanOnSurface
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "24-Stunden Tagesverlauf",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CleanPrimary
                                        )
                                    }
                                } else if (selectedTimeFrame == ChartTimeFrame.YEAR) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = CleanPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = periodInfo.startDateLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanOnSurface
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Wochendurchschnitte (Ø)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CleanPrimary
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = periodInfo.endDateLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanOnSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = CleanPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = CleanPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = periodInfo.startDateLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanOnSurface
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = periodInfo.endDateLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanOnSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = CleanPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. ZWEI RUNDE KREISE (Durchschnittswert links & Anzahl Messungen rechts)
                    val avgSys = if (periodMeasurements.isNotEmpty()) {
                        periodMeasurements.map { it.systole }.average().toInt()
                    } else 0

                    val avgDia = if (periodMeasurements.isNotEmpty()) {
                        periodMeasurements.map { it.diastole }.average().toInt()
                    } else 0

                    val avgPulse = if (periodMeasurements.isNotEmpty()) {
                        periodMeasurements.map { it.pulse }.average().toInt()
                    } else 0

                    val count = periodMeasurements.size
                    val isAvgNorm = avgSys < systoleNormMax && avgDia < diastoleNormMax

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LINKER KREIS: DURCHSCHNITTSWERT
                        Surface(
                            modifier = Modifier
                                .size(125.dp)
                                .clip(CircleShape),
                            color = if (count > 0 && !isAvgNorm) CleanAlertContainer.copy(alpha = 0.35f) else CleanPrimaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(
                                2.dp,
                                if (count > 0 && !isAvgNorm) CleanAlertText.copy(alpha = 0.5f) else CleanPrimary.copy(alpha = 0.5f)
                            ),
                            shape = CircleShape,
                            shadowElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "DURCHSCHNITT",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (count > 0 && !isAvgNorm) CleanAlertText else CleanPrimary,
                                    letterSpacing = 0.5.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                if (count > 0) {
                                    Text(
                                        text = "$avgSys / $avgDia",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (!isAvgNorm) CleanAlertText else CleanOnSurface
                                    )
                                    Text(
                                        text = "mmHg",
                                        fontSize = 9.sp,
                                        color = CleanMutedText
                                    )
                                    if (avgPulse > 0) {
                                        Text(
                                            text = "Ø $avgPulse bpm",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CleanOnSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "-- / --",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CleanMutedText
                                    )
                                    Text(
                                        text = "keine Daten",
                                        fontSize = 9.sp,
                                        color = CleanMutedText
                                    )
                                }
                            }
                        }

                        // RECHTER KREIS: ANZAHL DER MESSUNGEN
                        Surface(
                            modifier = Modifier
                                .size(125.dp)
                                .clip(CircleShape),
                            color = CleanNormContainer.copy(alpha = 0.45f),
                            border = BorderStroke(2.dp, CleanNormText.copy(alpha = 0.5f)),
                            shape = CircleShape,
                            shadowElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "ANZAHL",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CleanNormText,
                                    letterSpacing = 0.5.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "$count",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CleanOnSurface
                                )

                                Text(
                                    text = if (count == 1) "Messung" else "Messungen",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CleanOnSurfaceVariant
                                )

                                Text(
                                    text = selectedTimeFrame.label.lowercase(),
                                    fontSize = 8.5.sp,
                                    color = CleanMutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomScaleDialog) {
        AlertDialog(
            onDismissRequest = { showCustomScaleDialog = false },
            containerColor = CleanSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Diagramm-Skala anpassen",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanOnSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Geben Sie den gewünschten Maximalwert für die Y-Achse ein (z. B. 180, 200, 220 mmHg):",
                        fontSize = 13.sp,
                        color = CleanOnSurfaceVariant,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customScaleInput,
                        onValueChange = { customScaleInput = it },
                        label = { Text("Maximaler Skalenwert (mmHg)") },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CleanPrimary,
                            unfocusedBorderColor = CleanOutline,
                            focusedLabelColor = CleanPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = customScaleInput.toIntOrNull()
                        if (parsed != null && parsed in 100..300) {
                            onUpdateChartScaleMax?.invoke(parsed)
                        }
                        showCustomScaleDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary)
                ) {
                    Text("Übernehmen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomScaleDialog = false }) {
                    Text("Abbrechen", color = CleanOnSurfaceVariant)
                }
            }
        )
    }
}
