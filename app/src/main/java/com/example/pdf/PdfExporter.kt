package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import com.example.data.model.BpMeasurement
import com.example.data.repository.UserSettings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val TAG = "PdfExporter"

    // Dimensions for A4 in Points (72 DPI) - Hochformat (Portrait)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 30f

    private const val PAGE1_MAX_DAY_ROWS = 31
    private const val PAGE_LATER_MAX_DAY_ROWS = 40

    data class DayRow(
        val dayLabel: String,
        val dayTimestamp: Long,
        val m1: BpMeasurement?,
        val m2: BpMeasurement?,
        val m3: BpMeasurement?,
        val m4: BpMeasurement?
    )

    /**
     * Groups measurements of the month into daily rows with 4 measurement slots:
     * - Messung 1: Morgens (< 11:00)
     * - Messung 2: Mittags (11:00 - 14:30)
     * - Messung 3: Abends (>= 18:00) - Vertauscht mit 4. Messung
     * - Messung 4: Kontroll / Nachmittag (14:30 - 18:00) - Vertauscht mit 3. Messung
     */
    private fun groupMeasurementsByDay(measurements: List<BpMeasurement>): List<DayRow> {
        val dfDayKey = SimpleDateFormat("yyyyMMdd", Locale.GERMANY)
        val dfDayLabel = SimpleDateFormat("EE, dd.MM.", Locale.GERMANY)

        val grouped = measurements.groupBy { dfDayKey.format(Date(it.timestamp)) }
            .toSortedMap()

        return grouped.map { (_, dayList) ->
            val sorted = dayList.sortedBy { it.timestamp }
            val dayTimestamp = sorted.first().timestamp
            val rawLabel = dfDayLabel.format(Date(dayTimestamp))
            val dayLabel = rawLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }

            var slot1: BpMeasurement? = null
            var slot2: BpMeasurement? = null
            var slot3: BpMeasurement? = null
            var slot4: BpMeasurement? = null

            val unassigned = mutableListOf<BpMeasurement>()

            sorted.forEach { m ->
                val cal = Calendar.getInstance().apply { timeInMillis = m.timestamp }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val min = cal.get(Calendar.MINUTE)
                val timeDec = hour + min / 60f

                when {
                    timeDec < 11.0f && slot1 == null -> slot1 = m
                    timeDec in 11.0f..<14.5f && slot2 == null -> slot2 = m
                    // 4. Messung (Abends >= 18:00) in die 3. Spalte vertauscht:
                    timeDec >= 18.0f && slot3 == null -> slot3 = m
                    // 3. Messung (Nachmittags 14:30 - 18:00) in die 4. Spalte vertauscht:
                    timeDec in 14.5f..<18.0f && slot4 == null -> slot4 = m
                    else -> unassigned.add(m)
                }
            }

            // Put any overflow/unassigned measurements into remaining open slots chronologically
            unassigned.forEach { m ->
                when {
                    slot1 == null -> slot1 = m
                    slot2 == null -> slot2 = m
                    slot3 == null -> slot3 = m
                    slot4 == null -> slot4 = m
                }
            }

            DayRow(
                dayLabel = dayLabel,
                dayTimestamp = dayTimestamp,
                m1 = slot1,
                m2 = slot2,
                m3 = slot3,
                m4 = slot4
            )
        }
    }

    /**
     * Filters measurements for the current month (or newest month available if none in current)
     * and sorts chronologically ASCENDING from the 01. of the month onwards!
     */
    private fun filterCurrentMonthMeasurements(measurements: List<BpMeasurement>): Pair<String, List<BpMeasurement>> {
        if (measurements.isEmpty()) {
            val dfMonth = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
            return Pair(dfMonth.format(Date()), emptyList())
        }

        val calNow = Calendar.getInstance()
        val curYear = calNow.get(Calendar.YEAR)
        val curMonth = calNow.get(Calendar.MONTH)
        val dfMonth = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)

        val inCurrentMonth = measurements.filter { m ->
            val cal = Calendar.getInstance().apply { timeInMillis = m.timestamp }
            cal.get(Calendar.YEAR) == curYear && cal.get(Calendar.MONTH) == curMonth
        }

        if (inCurrentMonth.isNotEmpty()) {
            val label = dfMonth.format(calNow.time)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }
            // Sort ascending from 01. of the month
            return Pair(label, inCurrentMonth.sortedBy { it.timestamp })
        }

        // If no data in current calendar month, take the newest month present in the data
        val latest = measurements.maxByOrNull { it.timestamp }!!
        val lCal = Calendar.getInstance().apply { timeInMillis = latest.timestamp }
        val lY = lCal.get(Calendar.YEAR)
        val lM = lCal.get(Calendar.MONTH)

        val monthData = measurements.filter { m ->
            val c = Calendar.getInstance().apply { timeInMillis = m.timestamp }
            c.get(Calendar.YEAR) == lY && c.get(Calendar.MONTH) == lM
        }.sortedBy { it.timestamp } // Sort ascending from 01.

        val label = dfMonth.format(lCal.time)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }
        return Pair(label, monthData)
    }

    /**
     * Generates a Hochformat (Portrait) PDF document:
     * - Page 1 (Portrait): Header + Patient stats + Graphic Chart +
     *                      Compact daily table (1 row per day, 4 measurement slots: Morgens / Mittags / Abends / Nachmittags)
     * - Subsequent Pages (Portrait): Continuation of daily rows if > 31 days.
     */
    fun generatePdfStream(
        outputStream: OutputStream,
        allMeasurements: List<BpMeasurement>,
        settings: UserSettings,
        targetMonthTitle: String? = null
    ): Boolean {
        if (allMeasurements.isEmpty()) {
            return false
        }

        val (monthTitle, monthMeasurements) = if (!targetMonthTitle.isNullOrBlank()) {
            Pair(targetMonthTitle, allMeasurements.sortedBy { it.timestamp })
        } else {
            filterCurrentMonthMeasurements(allMeasurements)
        }
        val pdfDocument = PdfDocument()

        try {
            val dayRows = groupMeasurementsByDay(monthMeasurements)

            val totalPages = if (dayRows.size <= PAGE1_MAX_DAY_ROWS) {
                1
            } else {
                1 + Math.ceil((dayRows.size - PAGE1_MAX_DAY_ROWS).toDouble() / PAGE_LATER_MAX_DAY_ROWS).toInt()
            }

            // ==========================================
            // SEITE 1: HOCHFORMAT - GRAFIK + TAGESWEISE TABELLE
            // ==========================================
            val page1Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page1 = pdfDocument.startPage(page1Info)

            val page1Days = dayRows.take(PAGE1_MAX_DAY_ROWS)

            drawPage1(
                canvas = page1.canvas,
                monthTitle = monthTitle,
                allMonthMeasurements = monthMeasurements,
                dayRowsForPage = page1Days,
                settings = settings,
                currentPage = 1,
                totalPages = totalPages
            )
            pdfDocument.finishPage(page1)

            // ==========================================
            // FOLGESEITEN: HOCHFORMAT (falls mehr als 31 Tage)
            // ==========================================
            var currentIndex = PAGE1_MAX_DAY_ROWS
            var pageNum = 2

            while (currentIndex < dayRows.size) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                val page = pdfDocument.startPage(pageInfo)

                val pageDays = dayRows.drop(currentIndex).take(PAGE_LATER_MAX_DAY_ROWS)

                drawSubsequentPage(
                    canvas = page.canvas,
                    monthTitle = monthTitle,
                    dayRowsForPage = pageDays,
                    settings = settings,
                    currentPage = pageNum,
                    totalPages = totalPages
                )
                pdfDocument.finishPage(page)

                currentIndex += PAGE_LATER_MAX_DAY_ROWS
                pageNum++
            }

            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.flush()
            outputStream.close()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PDF stream", e)
            try { pdfDocument.close() } catch (_: Exception) {}
            return false
        }
    }

    /**
     * Renders Page 1:
     * 1. Header Banner
     * 2. Patient & Statistics Summary
     * 3. Chart (30 to 210 mmHg)
     * 4. Compact Daily Table (1 row per day, 4 columns: Morgens, Mittags, Nachmittags, Abends)
     */
    private fun drawPage1(
        canvas: Canvas,
        monthTitle: String,
        allMonthMeasurements: List<BpMeasurement>,
        dayRowsForPage: List<DayRow>,
        settings: UserSettings,
        currentPage: Int,
        totalPages: Int
    ) {
        val paint = Paint().apply { isAntiAlias = true }
        val width = PAGE_WIDTH.toFloat()
        val height = PAGE_HEIGHT.toFloat()
        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())

        var currentY = MARGIN

        // 1. Header Banner
        paint.color = Color.rgb(13, 71, 161) // Deep Primary Blue
        canvas.drawRoundRect(RectF(MARGIN, currentY, width - MARGIN, currentY + 34f), 5f, 5f, paint)

        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLUTDRUCK- & AFIB-PROTOKOLL  •  $monthTitle", MARGIN + 12f, currentY + 22f, paint)

        paint.textSize = 8f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Erstellt: $nowStr", width - MARGIN - 110f, currentY + 22f, paint)

        currentY += 38f

        // 2. Patient & Statistics Summary Bar
        paint.color = Color.rgb(224, 236, 252) // Stronger soft blue background
        canvas.drawRoundRect(RectF(MARGIN, currentY, width - MARGIN, currentY + 30f), 4f, 4f, paint)
        val statsBorderPaint = Paint().apply {
            color = Color.rgb(186, 212, 248)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(MARGIN, currentY, width - MARGIN, currentY + 30f), 4f, 4f, statsBorderPaint)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val displayName = settings.patientName.ifBlank { settings.activePerson.name }
        canvas.drawText("Patient: $displayName", MARGIN + 10f, currentY + 14f, paint)

        paint.textSize = 7.5f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("Monat: $monthTitle | Zielwert: < ${settings.systoleNormMax} / ${settings.diastoleNormMax} mmHg", MARGIN + 10f, currentY + 25f, paint)

        // Statistics computation over all measurements of the month
        if (allMonthMeasurements.isNotEmpty()) {
            val avgSys = allMonthMeasurements.map { it.systole }.average().toInt()
            val avgDia = allMonthMeasurements.map { it.diastole }.average().toInt()
            val avgPulse = allMonthMeasurements.map { it.pulse }.average().toInt()
            val afibCount = allMonthMeasurements.count { it.afibDetected }
            val count = allMonthMeasurements.size

            paint.color = Color.rgb(13, 71, 161)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8f
            val statsText = "Ø $avgSys / $avgDia mmHg  |  Ø $avgPulse bpm  |  N = $count  |  AFIB: $afibCount"
            val textWidth = paint.measureText(statsText)
            canvas.drawText(statsText, width - MARGIN - textWidth - 10f, currentY + 19f, paint)
        }

        currentY += 34f

        // 3. Graphical Chart Area (Scale 30 .. 210 mmHg)
        val chartTop = currentY
        val chartHeight = 125f
        val chartBottom = chartTop + chartHeight
        val chartLeft = MARGIN + 36f // room for Y axis labels
        val chartRight = width - MARGIN - 8f
        val chartWidth = chartRight - chartLeft

        // Background area for chart - Stronger distinct chart background with border
        paint.color = Color.rgb(238, 244, 254)
        canvas.drawRoundRect(RectF(chartLeft, chartTop, chartRight, chartBottom), 4f, 4f, paint)
        val chartBorderPaint = Paint().apply {
            color = Color.rgb(195, 215, 240)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(chartLeft, chartTop, chartRight, chartBottom), 4f, 4f, chartBorderPaint)

        val minScale = settings.chartScaleMin.toFloat().coerceAtLeast(30f)
        val maxScale = settings.chartScaleMax.toFloat().coerceAtLeast(minScale + 50f)
        val ySteps = when {
            maxScale <= 180 -> listOf(30, 60, 90, 120, 150, 180)
            maxScale <= 220 -> listOf(30, 60, 90, 120, 150, 180, 210)
            maxScale <= 260 -> listOf(30, 70, 110, 150, 190, 230)
            else -> listOf(30, 80, 130, 180, 230, 280)
        }

        fun getY(valMmHg: Float): Float {
            val clamped = valMmHg.coerceIn(minScale, maxScale)
            val frac = (clamped - minScale) / (maxScale - minScale)
            return chartBottom - frac * chartHeight
        }

        // Draw Y Axis Grid lines & Labels (30 .. 210)
        val scalePaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        ySteps.forEach { stepVal ->
            val y = getY(stepVal.toFloat())

            // Visible thin black line
            paint.color = Color.BLACK
            paint.strokeWidth = 0.5f
            canvas.drawLine(chartLeft, y, chartRight, y, paint)

            // Label
            canvas.drawText("$stepVal", chartLeft - 4f, y + 2.5f, scalePaint)
        }

        // Draw Target Norm Reference Lines & Badges
        val sysNormY = getY(settings.systoleNormMax.toFloat())
        val diaNormY = getY(settings.diastoleNormMax.toFloat())
        val dashPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            strokeWidth = 0.9f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            isAntiAlias = true
        }
        canvas.drawLine(chartLeft, sysNormY, chartRight, sysNormY, dashPaint)
        canvas.drawLine(chartLeft, diaNormY, chartRight, diaNormY, dashPaint)

        // Draw Norm Indicator Badges on Left
        val badgePaint = Paint().apply {
            color = Color.rgb(232, 245, 233)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeBorder = Paint().apply {
            color = Color.rgb(76, 175, 80)
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            isAntiAlias = true
        }
        val badgeTextPaint = Paint().apply {
            color = Color.rgb(27, 94, 32)
            textSize = 6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Systole Norm Badge
        canvas.drawRoundRect(RectF(chartLeft - 30f, sysNormY - 5f, chartLeft - 5f, sysNormY + 5f), 2f, 2f, badgePaint)
        canvas.drawRoundRect(RectF(chartLeft - 30f, sysNormY - 5f, chartLeft - 5f, sysNormY + 5f), 2f, 2f, badgeBorder)
        canvas.drawText("${settings.systoleNormMax}", chartLeft - 17.5f, sysNormY + 2f, badgeTextPaint)

        // Diastole Norm Badge
        canvas.drawRoundRect(RectF(chartLeft - 30f, diaNormY - 5f, chartLeft - 5f, diaNormY + 5f), 2f, 2f, badgePaint)
        canvas.drawRoundRect(RectF(chartLeft - 30f, diaNormY - 5f, chartLeft - 5f, diaNormY + 5f), 2f, 2f, badgeBorder)
        canvas.drawText("${settings.diastoleNormMax}", chartLeft - 17.5f, diaNormY + 2f, badgeTextPaint)

        // Plot curves if measurements exist
        if (allMonthMeasurements.isNotEmpty()) {
            // Determine the month and year of the measurements to get days in month
            val firstTimestamp = allMonthMeasurements.first().timestamp
            val monthCal = Calendar.getInstance(Locale.GERMANY).apply {
                timeInMillis = firstTimestamp
            }
            val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH) // 28, 29, 30 or 31

            // Exact X-Coordinate based on day of month (1.0 to daysInMonth) and time of day
            fun getXForTimestamp(ts: Long): Float {
                val cal = Calendar.getInstance(Locale.GERMANY).apply { timeInMillis = ts }
                val day = cal.get(Calendar.DAY_OF_MONTH) // 1..daysInMonth
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val min = cal.get(Calendar.MINUTE)
                val dayFraction = (day - 1) + (hour * 60f + min) / (24f * 60f)
                val totalDays = (daysInMonth - 1).toFloat().coerceAtLeast(1f)
                val frac = (dayFraction / totalDays).coerceIn(0f, 1f)
                return chartLeft + frac * chartWidth
            }

            fun getXForDay(dayNumber: Int): Float {
                val frac = ((dayNumber - 1).toFloat() / (daysInMonth - 1).toFloat()).coerceIn(0f, 1f)
                return chartLeft + frac * chartWidth
            }

            // Draw vertical day grid ticks / subtle markers along the month scale
            val dayTickPaint = Paint().apply {
                color = Color.rgb(235, 240, 248)
                strokeWidth = 0.5f
                isAntiAlias = true
            }
            for (d in 1..daysInMonth) {
                val dx = getXForDay(d)
                canvas.drawLine(dx, chartTop, dx, chartBottom, dayTickPaint)
            }

            // Draw Paths for Systole and Diastole
            val sysPath = Path()
            val diaPath = Path()

            allMonthMeasurements.forEachIndexed { idx, m ->
                val x = getXForTimestamp(m.timestamp)
                val sy = getY(m.systole.toFloat())
                val dy = getY(m.diastole.toFloat())
                if (idx == 0) {
                    sysPath.moveTo(x, sy)
                    diaPath.moveTo(x, dy)
                } else {
                    sysPath.lineTo(x, sy)
                    diaPath.lineTo(x, dy)
                }
            }

            // Draw Curve Lines
            val sysLinePaint = Paint().apply {
                color = Color.rgb(25, 118, 210)
                strokeWidth = 1.8f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            val diaLinePaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                strokeWidth = 1.6f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            canvas.drawPath(sysPath, sysLinePaint)
            canvas.drawPath(diaPath, diaLinePaint)

            // Draw Node Points & Day Scale Labels
            val pointFill = Paint().apply { isAntiAlias = true }
            val pointBorder = Paint().apply {
                color = Color.WHITE
                strokeWidth = 1f
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val dayLabelPaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 6f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            // Draw Bottom Day Scale (01., 05., 10., 15., 20., 25., 30./31.)
            val scaleDays = mutableListOf<Int>()
            for (d in 1..daysInMonth) {
                if (d == 1 || d % 5 == 0 || d == daysInMonth) {
                    scaleDays.add(d)
                }
            }
            scaleDays.distinct().forEach { d ->
                val dx = getXForDay(d)
                val dayStr = if (d < 10) "0$d." else "$d."
                canvas.drawText(dayStr, dx, chartBottom + 9f, dayLabelPaint)
            }

            // Draw Measurement Points at exact positions
            allMonthMeasurements.forEach { m ->
                val x = getXForTimestamp(m.timestamp)
                val sy = getY(m.systole.toFloat())
                val dy = getY(m.diastole.toFloat())

                val sysAlert = m.systole >= settings.systoleNormMax
                val diaAlert = m.diastole >= settings.diastoleNormMax

                // Systole Dot
                canvas.drawCircle(x, sy, 2.5f, pointBorder)
                pointFill.color = if (m.afibDetected || sysAlert) Color.rgb(211, 47, 47) else Color.rgb(25, 118, 210)
                canvas.drawCircle(x, sy, 1.8f, pointFill)

                // Diastole Dot
                canvas.drawCircle(x, dy, 2.5f, pointBorder)
                pointFill.color = if (diaAlert) Color.rgb(211, 47, 47) else Color.rgb(46, 125, 50)
                canvas.drawCircle(x, dy, 1.8f, pointFill)
            }
        }

        // Legend below chart
        currentY = chartBottom + 13f

        paint.color = Color.rgb(25, 118, 210)
        canvas.drawCircle(MARGIN + 30f, currentY + 3f, 3f, paint)
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 7f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Systole (mmHg)", MARGIN + 37f, currentY + 5f, paint)

        paint.color = Color.rgb(46, 125, 50)
        canvas.drawCircle(MARGIN + 125f, currentY + 3f, 3f, paint)
        paint.color = Color.rgb(30, 41, 59)
        canvas.drawText("Diastole (mmHg)", MARGIN + 132f, currentY + 5f, paint)

        paint.color = Color.rgb(211, 47, 47)
        canvas.drawCircle(MARGIN + 220f, currentY + 3f, 3f, paint)
        paint.color = Color.rgb(30, 41, 59)
        canvas.drawText("Erhöht / AFIB", MARGIN + 227f, currentY + 5f, paint)

        paint.color = Color.rgb(46, 125, 50)
        canvas.drawLine(MARGIN + 305f, currentY + 3f, MARGIN + 325f, currentY + 3f, dashPaint)
        paint.color = Color.rgb(30, 41, 59)
        canvas.drawText("Normbereich", MARGIN + 330f, currentY + 5f, paint)

        currentY += 13f

        // 4. Compact Daily Table (1 Zeile pro Tag)
        drawDailyMeasurementsTable(
            canvas = canvas,
            startX = MARGIN,
            startY = currentY,
            tableWidth = width - 2 * MARGIN,
            days = dayRowsForPage,
            settings = settings
        )

        // Footer
        drawPageFooter(canvas, width, height, currentPage, totalPages)
    }

    /**
     * Renders Subsequent Pages (Portrait continuation if > 31 days in month)
     */
    private fun drawSubsequentPage(
        canvas: Canvas,
        monthTitle: String,
        dayRowsForPage: List<DayRow>,
        settings: UserSettings,
        currentPage: Int,
        totalPages: Int
    ) {
        val paint = Paint().apply { isAntiAlias = true }
        val width = PAGE_WIDTH.toFloat()
        val height = PAGE_HEIGHT.toFloat()
        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())

        var currentY = MARGIN

        // 1. Continuation Header
        paint.color = Color.rgb(13, 71, 161) // Deep Primary Blue
        canvas.drawRoundRect(RectF(MARGIN, currentY, width - MARGIN, currentY + 30f), 4f, 4f, paint)

        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLUTDRUCK-PROTOKOLL  •  $monthTitle (Fortsetzung)", MARGIN + 12f, currentY + 19f, paint)

        paint.textSize = 7.5f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Erstellt: $nowStr", width - MARGIN - 100f, currentY + 19f, paint)

        currentY += 34f

        // 2. Patient Short Bar
        paint.color = Color.rgb(224, 236, 252)
        canvas.drawRoundRect(RectF(MARGIN, currentY, width - MARGIN, currentY + 22f), 4f, 4f, paint)
        val shortBarBorder = Paint().apply {
            color = Color.rgb(186, 212, 248)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(MARGIN, currentY, width - MARGIN, currentY + 22f), 4f, 4f, shortBarBorder)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val displayName = settings.patientName.ifBlank { settings.activePerson.name }
        canvas.drawText("Patient: $displayName", MARGIN + 8f, currentY + 15f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(71, 85, 105)
        val limitText = "Zielwert: < ${settings.systoleNormMax} / ${settings.diastoleNormMax} mmHg"
        val limitWidth = paint.measureText(limitText)
        canvas.drawText(limitText, width - MARGIN - limitWidth - 8f, currentY + 15f, paint)

        currentY += 28f

        // 3. Table continuation
        drawDailyMeasurementsTable(
            canvas = canvas,
            startX = MARGIN,
            startY = currentY,
            tableWidth = width - 2 * MARGIN,
            days = dayRowsForPage,
            settings = settings
        )

        // Footer
        drawPageFooter(canvas, width, height, currentPage, totalPages)
    }

    /**
     * Draws the compact horizontal table:
     * 1 Zeile pro Tag mit 4 Spaltenblöcken:
     * - Datum (z.B. "Mo, 24.08.")
     * - Messung 1 (Morgens): Sys / Dia / Puls
     * - Messung 2 (Mittags): Sys / Dia / Puls
     * - Messung 3 (Abends): Sys / Dia / Puls
     * - Messung 4 (Kontroll): Sys / Dia / Puls
     */
    private fun drawDailyMeasurementsTable(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        tableWidth: Float,
        days: List<DayRow>,
        settings: UserSettings
    ) {
        val paint = Paint().apply { isAntiAlias = true }
        val headerHeight = 22f
        val rowHeight = 14.5f

        // Column layout: Datum (75pt), then 4 slots of equal width (115pt each)
        val colDate = startX
        val colDateWidth = 75f
        val slotWidth = (tableWidth - colDateWidth) / 4f // ~115pt each

        val col1 = colDate + colDateWidth
        val col2 = col1 + slotWidth
        val col3 = col2 + slotWidth
        val col4 = col3 + slotWidth

        // Header Background - Stronger soft blue-gray header
        paint.color = Color.rgb(191, 219, 254)
        canvas.drawRoundRect(RectF(startX, startY, startX + tableWidth, startY + headerHeight), 4f, 4f, paint)

        // Header Titles (Line 1: bold title, Line 2: Sys / Dia / Puls subtitle)
        val headerTitlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 7.3f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerSubPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 6.2f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        // Datum header
        canvas.drawText("Datum", colDate + 6f, startY + 9.5f, headerTitlePaint)
        canvas.drawText("(Tag)", colDate + 6f, startY + 18f, headerSubPaint)

        // Slot headers: 4. Messung (Abends) in 3. Spalte, 3. Messung (Kontroll) in 4. Spalte
        val slotHeaders = listOf(
            Pair("Messung 1 (Morgens)", col1),
            Pair("Messung 2 (Mittags)", col2),
            Pair("Messung 3 (Abends)", col3),
            Pair("Messung 4 (Kontroll)", col4)
        )

        slotHeaders.forEach { (title, colX) ->
            canvas.drawText(title, colX + 6f, startY + 9.5f, headerTitlePaint)
            canvas.drawText("Sys / Dia / Puls", colX + 6f, startY + 18f, headerSubPaint)
        }

        var curY = startY + headerHeight

        days.forEachIndexed { idx, day ->
            // Day Zebra background - Stronger contrasting alternate rows
            val isEven = idx % 2 == 0
            paint.color = if (isEven) Color.rgb(255, 255, 255) else Color.rgb(228, 238, 250)
            canvas.drawRect(startX, curY, startX + tableWidth, curY + rowHeight, paint)

            val textY = curY + rowHeight * 0.72f

            // Datum Column
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 7.2f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(day.dayLabel, colDate + 6f, textY, paint)

            // Draw 4 measurement slots
            val slots = listOf(
                Pair(day.m1, col1),
                Pair(day.m2, col2),
                Pair(day.m3, col3),
                Pair(day.m4, col4)
            )

            slots.forEach { (m, colX) ->
                if (m == null) {
                    // Empty slot placeholder
                    paint.color = Color.rgb(148, 163, 184)
                    paint.textSize = 7.2f
                    paint.typeface = Typeface.DEFAULT
                    canvas.drawText("— / — / —", colX + 6f, textY, paint)
                } else {
                    val isSysHigh = m.systole >= settings.systoleNormMax
                    val isDiaHigh = m.diastole >= settings.diastoleNormMax
                    val isAfib = m.afibDetected

                    // Cell highlight if AFIB or elevated - stronger rich background fills
                    if (isAfib) {
                        val hlPaint = Paint().apply {
                            color = Color.rgb(254, 202, 202) // Stronger soft red
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(colX + 1f, curY + 1f, colX + slotWidth - 1f, curY + rowHeight - 1f, hlPaint)
                    } else if (isSysHigh || isDiaHigh) {
                        val hlPaint = Paint().apply {
                            color = Color.rgb(254, 240, 138) // Stronger amber/yellow
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(colX + 1f, curY + 1f, colX + slotWidth - 1f, curY + rowHeight - 1f, hlPaint)
                    }

                    var drawX = colX + 6f

                    // Systole
                    paint.textSize = 7.2f
                    if (isSysHigh) {
                        paint.color = Color.rgb(220, 38, 38)
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    } else {
                        paint.color = Color.rgb(22, 101, 52)
                        paint.typeface = Typeface.DEFAULT
                    }
                    val sysStr = "${m.systole}"
                    canvas.drawText(sysStr, drawX, textY, paint)
                    drawX += paint.measureText(sysStr)

                    // Slash 1
                    paint.color = Color.rgb(100, 116, 139)
                    paint.typeface = Typeface.DEFAULT
                    val slash1 = " / "
                    canvas.drawText(slash1, drawX, textY, paint)
                    drawX += paint.measureText(slash1)

                    // Diastole
                    if (isDiaHigh) {
                        paint.color = Color.rgb(220, 38, 38)
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    } else {
                        paint.color = Color.rgb(22, 101, 52)
                        paint.typeface = Typeface.DEFAULT
                    }
                    val diaStr = "${m.diastole}"
                    canvas.drawText(diaStr, drawX, textY, paint)
                    drawX += paint.measureText(diaStr)

                    // Slash 2
                    paint.color = Color.rgb(100, 116, 139)
                    paint.typeface = Typeface.DEFAULT
                    val slash2 = " / "
                    canvas.drawText(slash2, drawX, textY, paint)
                    drawX += paint.measureText(slash2)

                    // Pulse
                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.DEFAULT
                    val pulseStr = "${m.pulse}"
                    canvas.drawText(pulseStr, drawX, textY, paint)
                    drawX += paint.measureText(pulseStr)

                    // AFIB indicator
                    if (isAfib) {
                        paint.color = Color.rgb(185, 28, 28)
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText(" ⚠️", drawX, textY, paint)
                    }
                }
            }

            // Row bottom line
            paint.color = Color.rgb(203, 213, 225)
            paint.strokeWidth = 0.5f
            canvas.drawLine(startX, curY + rowHeight, startX + tableWidth, curY + rowHeight, paint)

            curY += rowHeight
        }

        // Vertical Column Dividers
        val colDividers = listOf(col1, col2, col3, col4)
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 0.6f
        colDividers.forEach { divX ->
            canvas.drawLine(divX, startY, divX, curY, paint)
        }

        // Outer Border
        paint.color = Color.rgb(148, 175, 210)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        canvas.drawRect(startX, startY, startX + tableWidth, curY, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPageFooter(canvas: Canvas, width: Float, height: Float, currentPage: Int, totalPages: Int) {
        val paint = Paint().apply { isAntiAlias = true }
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 8f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Seite $currentPage von $totalPages • Microlife BP A6 BT", MARGIN, height - 14f, paint)

        val noteText = "Tagesweise Schattierung • Werte über Grenzwert oder AFIB farblich hervorgehoben"
        val noteWidth = paint.measureText(noteText)
        canvas.drawText(noteText, width - MARGIN - noteWidth, height - 14f, paint)
    }

    /**
     * Native Android Print Spooler Integration
     */
    fun printPdf(
        context: Context,
        measurements: List<BpMeasurement>,
        settings: UserSettings,
        monthTitle: String? = null
    ) {
        if (measurements.isEmpty()) {
            Toast.makeText(context, "Keine Messdaten zum Drucken vorhanden.", Toast.LENGTH_SHORT).show()
            return
        }

        val masterFile = exportToMasterFile(context, measurements, settings, monthTitle) ?: run {
            Toast.makeText(context, "Fehler beim Vorbereiten des Ausdrucks.", Toast.LENGTH_SHORT).show()
            return
        }

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager != null) {
            val sanitizedMonth = monthTitle?.replace(" ", "_") ?: "Monat"
            val jobName = "Blutdruck_Ausdruck_${settings.activePerson.name}_$sanitizedMonth"
            val printAdapter = PdfPrintDocumentAdapter(masterFile)
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build()
            printManager.print(jobName, printAdapter, printAttributes)
        } else {
            Toast.makeText(context, "Druckdienst auf diesem Gerät nicht verfügbar.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Custom PrintDocumentAdapter that feeds the generated PDF into the Android Print Spooler
     */
    class PdfPrintDocumentAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val pdi = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback?.onLayoutFinished(pdi, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            var input: FileInputStream? = null
            var output: FileOutputStream? = null
            try {
                input = FileInputStream(file)
                output = FileOutputStream(destination?.fileDescriptor)
                val buf = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buf).also { bytesRead = it } >= 0) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onWriteCancelled()
                        return
                    }
                    output.write(buf, 0, bytesRead)
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            } finally {
                try { input?.close() } catch (_: Exception) {}
                try { output?.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Export PDF to a SAF Destination Uri
     */
    fun exportToUri(
        context: Context,
        destinationUri: Uri,
        measurements: List<BpMeasurement>,
        settings: UserSettings,
        monthTitle: String? = null
    ): Boolean {
        if (measurements.isEmpty()) {
            Toast.makeText(context, "Keine Messwerte vorhanden.", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val outputStream = context.contentResolver.openOutputStream(destinationUri, "wt")
            if (outputStream != null) {
                val success = generatePdfStream(outputStream, measurements, settings, monthTitle)
                if (success) {
                    Toast.makeText(context, "PDF erfolgreich gespeichert!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Fehler beim Erstellen der PDF.", Toast.LENGTH_SHORT).show()
                }
                success
            } else {
                Toast.makeText(context, "Zugriff auf den Speicherort nicht möglich.", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to SAF Uri $destinationUri", e)
            Toast.makeText(context, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Exports to local file for printing / file storage
     */
    fun exportToMasterFile(
        context: Context,
        measurements: List<BpMeasurement>,
        settings: UserSettings,
        monthTitle: String? = null
    ): File? {
        if (measurements.isEmpty()) {
            return null
        }

        return try {
            val docsFolder = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            if (!docsFolder.exists()) {
                docsFolder.mkdirs()
            }

            val fileName = if (!monthTitle.isNullOrBlank()) {
                val sanitized = monthTitle.replace(" ", "_")
                "Blutdruck_Protokoll_${settings.activePerson.name}_$sanitized.pdf"
            } else {
                settings.defaultFileName
            }

            val masterFile = File(docsFolder, fileName)
            val outputStream = FileOutputStream(masterFile, false)

            val success = generatePdfStream(outputStream, measurements, settings, monthTitle)
            if (success) {
                masterFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing master PDF file", e)
            null
        }
    }
}
