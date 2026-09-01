package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import java.util.*

private enum class DatePickerTab(val label: String) {
    DAY("Tag"),
    MONTH("Monat"),
    YEAR("Jahr")
}

val GERMAN_MONTH_NAMES = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember"
)

val GERMAN_MONTH_SHORT = listOf(
    "Jan", "Feb", "Mär", "Apr", "Mai", "Jun",
    "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"
)

fun calculateAgeYears(birthDateStr: String): Int? {
    if (birthDateStr.isBlank()) return null
    return try {
        val parts = birthDateStr.split(".")
        if (parts.size != 3) return null
        val day = parts[0].trim().toIntOrNull() ?: return null
        val month = parts[1].trim().toIntOrNull() ?: return null
        val year = parts[2].trim().toIntOrNull() ?: return null

        val birthCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val today = Calendar.getInstance()

        var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        if (age in 0..130) age else null
    } catch (_: Exception) {
        null
    }
}

@Composable
fun BirthDatePickerDialog(
    initialDate: String = "",
    personName: String = "",
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val now = Calendar.getInstance()
    val currentYear = now.get(Calendar.YEAR)

    // Parse initial date if valid, else default to 1965
    var selectedYear by remember {
        mutableIntStateOf(
            run {
                val parsed = parseDateString(initialDate)
                parsed?.first ?: 1965
            }
        )
    }

    var selectedMonth by remember {
        mutableIntStateOf(
            run {
                val parsed = parseDateString(initialDate)
                parsed?.second ?: 0 // 0 = Januar
            }
        )
    }

    var selectedDay by remember {
        mutableIntStateOf(
            run {
                val parsed = parseDateString(initialDate)
                parsed?.third ?: 15
            }
        )
    }

    var currentTab by remember {
        mutableStateOf(
            if (initialDate.isBlank()) DatePickerTab.YEAR else DatePickerTab.DAY
        )
    }

    // Determine max days in selected month and year
    val maxDaysInMonth = remember(selectedYear, selectedMonth) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonth)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Clamp day if necessary
    LaunchedEffect(maxDaysInMonth) {
        if (selectedDay > maxDaysInMonth) {
            selectedDay = maxDaysInMonth
        }
    }

    val formattedDatePreview = String.format(Locale.GERMAN, "%02d.%02d.%04d", selectedDay, selectedMonth + 1, selectedYear)
    val age = calculateAgeYears(formattedDatePreview)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 400.dp)
                .wrapContentHeight()
                .padding(vertical = 10.dp)
                .testTag("dialog_birth_date_picker"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CleanSurface),
            border = BorderStroke(1.dp, CleanOutline),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Title & Close Icon
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CleanPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cake,
                                contentDescription = null,
                                tint = CleanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Geburtsdatum wählen",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CleanOnSurface
                            )
                            if (personName.isNotBlank()) {
                                Text(
                                    text = "Profil: $personName",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CleanMutedText
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = CleanMutedText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Date Preview Banner (Clean & High Contrast)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = CleanPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, CleanPrimary.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$selectedDay. ${GERMAN_MONTH_NAMES.getOrElse(selectedMonth) { "" }} $selectedYear",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CleanPrimary
                            )
                            Text(
                                text = "Format: $formattedDatePreview",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = CleanOnSurfaceVariant
                            )
                        }

                        if (age != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CleanNormContainer,
                                border = BorderStroke(0.8.dp, CleanNormText.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "$age Jahre",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CleanOnNormContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Segmented Selector Tabs (Tag, Monat, Jahr)
                // Angepasst: Großzügigere Höhe (48dp), saubere Abstände und Leerzeile/Spacing zwischen Label & Wert,
                // damit die Schrift niemals auf dem unteren Rand aufsitzt!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CleanSurfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DatePickerTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val displaySubtext = when (tab) {
                            DatePickerTab.DAY -> "$selectedDay."
                            DatePickerTab.MONTH -> GERMAN_MONTH_SHORT.getOrElse(selectedMonth) { "" }
                            DatePickerTab.YEAR -> "$selectedYear"
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { currentTab = tab }
                                .testTag("tab_date_picker_${tab.name.lowercase()}"),
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) CleanPrimary else Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 3.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White.copy(alpha = 0.88f) else CleanMutedText,
                                    lineHeight = 11.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = displaySubtext,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White else CleanOnSurface,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dynamic Tab Content (Day, Month, or Year selection)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp)
                ) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "date_picker_content"
                    ) { targetTab ->
                        when (targetTab) {
                            DatePickerTab.DAY -> {
                                DayGrid(
                                    maxDays = maxDaysInMonth,
                                    selectedDay = selectedDay,
                                    onSelectDay = { day ->
                                        selectedDay = day
                                    }
                                )
                            }
                            DatePickerTab.MONTH -> {
                                MonthGrid(
                                    selectedMonth = selectedMonth,
                                    onSelectMonth = { month ->
                                        selectedMonth = month
                                        currentTab = DatePickerTab.DAY
                                    }
                                )
                            }
                            DatePickerTab.YEAR -> {
                                YearGrid(
                                    currentYear = currentYear,
                                    selectedYear = selectedYear,
                                    onSelectYear = { year ->
                                        selectedYear = year
                                        currentTab = DatePickerTab.MONTH
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = CleanOutline.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons (Optimiert für Samsung A55: Kein Zeilenumbruch bei "Übernehmen")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Löschen Button links
                    TextButton(
                        onClick = {
                            onDateSelected("")
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = CleanAlertText),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_clear_birth_date")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Löschen",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Abbrechen & Übernehmen Buttons rechts
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CleanOutline),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_cancel_birth_date")
                        ) {
                            Text(
                                text = "Abbrechen",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CleanOnSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = {
                                onDateSelected(formattedDatePreview)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CleanPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_confirm_birth_date")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Übernehmen",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayGrid(
    maxDays: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Tag auswählen (1 bis $maxDays):",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = CleanMutedText,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items((1..maxDays).toList()) { day ->
                val isSelected = day == selectedDay
                Surface(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onSelectDay(day) }
                        .testTag("btn_day_$day"),
                    shape = RoundedCornerShape(7.dp),
                    color = if (isSelected) CleanPrimary else CleanSurfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) CleanPrimary else CleanOutline.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else CleanOnSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    selectedMonth: Int,
    onSelectMonth: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Monat auswählen:",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = CleanMutedText,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(12) { index ->
                val isSelected = index == selectedMonth
                val monthName = GERMAN_MONTH_NAMES[index]
                val monthNumber = String.format(Locale.GERMAN, "%02d", index + 1)

                Surface(
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { onSelectMonth(index) }
                        .testTag("btn_month_${index + 1}"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) CleanPrimary else CleanSurfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) CleanPrimary else CleanOutline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$monthNumber. ",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else CleanMutedText
                        )
                        Text(
                            text = monthName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else CleanOnSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(
    currentYear: Int,
    selectedYear: Int,
    onSelectYear: (Int) -> Unit
) {
    val years = remember(currentYear) {
        (currentYear downTo 1920).toList()
    }

    val decades = remember {
        listOf(1930, 1940, 1950, 1960, 1970, 1980, 1990, 2000, 2010, 2020)
    }

    val gridState = rememberLazyGridState()
    val decadeListState = rememberLazyListState()

    // Scroll to the selected year on initial open
    LaunchedEffect(selectedYear) {
        val index = years.indexOf(selectedYear)
        if (index >= 0) {
            gridState.scrollToItem((index - 2).coerceAtLeast(0))
        }

        // Also scroll decade row to active decade
        val decadeIndex = decades.indexOfFirst { selectedYear in it..(it + 9) }
        if (decadeIndex >= 0) {
            decadeListState.scrollToItem((decadeIndex - 1).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Zehnerschritte mit Wischfunktion (LazyRow) - kein Button gequetscht, mit End-Padding
        LazyRow(
            state = decadeListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(decades) { decade ->
                val isDecade = selectedYear in decade..(decade + 9)
                Surface(
                    onClick = { onSelectYear(decade + 5) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDecade) CleanPrimary else CleanSurfaceVariant,
                    border = BorderStroke(1.dp, if (isDecade) CleanPrimary else CleanOutline.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "${decade}er",
                        fontSize = 11.sp,
                        fontWeight = if (isDecade) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isDecade) Color.White else CleanOnSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Anzeige der einzelnen Jahre direkt darunter platziert (kein Leerraum dazwischen)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(years) { year ->
                val isSelected = year == selectedYear
                Surface(
                    modifier = Modifier
                        .height(36.dp)
                        .clickable { onSelectYear(year) }
                        .testTag("btn_year_$year"),
                    shape = RoundedCornerShape(7.dp),
                    color = if (isSelected) CleanPrimary else CleanSurfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) CleanPrimary else CleanOutline.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = year.toString(),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else CleanOnSurface
                        )
                    }
                }
            }
        }
    }
}

private fun parseDateString(dateStr: String): Triple<Int, Int, Int>? {
    if (dateStr.isBlank()) return null
    return try {
        val parts = dateStr.split(".")
        if (parts.size == 3) {
            val day = parts[0].trim().toInt()
            val month = parts[1].trim().toInt() - 1 // 0-based
            val year = parts[2].trim().toInt()
            if (year in 1900..2100 && month in 0..11 && day in 1..31) {
                Triple(year, month, day)
            } else null
        } else null
    } catch (_: Exception) {
        null
    }
}
