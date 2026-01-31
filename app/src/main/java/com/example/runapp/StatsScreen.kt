package com.example.runapp

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runapp.ui.theme.HeaderBlueStart
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke

@Composable
fun StatsScreen(
    viewModel: RunViewModel,
    onBackClick: () -> Unit
) {
    val selectedType by viewModel.selectedStatsType.collectAsState()
    val chartData by viewModel.weeklyChartData.collectAsState()
    val totalValue by viewModel.currentWeekTotal.collectAsState()
    val weekStartMillis by viewModel.currentWeekStartMillis.collectAsState()
    val isAppDarkMode by viewModel.isAppDarkMode.collectAsState()

    // Determine unit string for the chart tooltip
    val unitStr = when (selectedType) {
        StatsType.CALORIES -> "kcal"
        StatsType.DISTANCE -> "km"
        StatsType.DURATION -> "min"
    }

    LaunchedEffect(Unit) { viewModel.calculateWeeklyStats() }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 10.dp, start = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Back")
                }
                Text("Statistics", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.background // Black in Dark Mode
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text("Weekly Report", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(20.dp))

            WeekDaysStrip(
                weekStartMillis = weekStartMillis,
                onPrevClick = { viewModel.previousWeek() },
                onNextClick = { viewModel.nextWeek() },
                isDarkMode = isAppDarkMode
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatsChip("Calories", selectedType == StatsType.CALORIES) { viewModel.setStatsType(StatsType.CALORIES) }
                StatsChip("Duration", selectedType == StatsType.DURATION) { viewModel.setStatsType(StatsType.DURATION) }
                StatsChip("Distance", selectedType == StatsType.DISTANCE) { viewModel.setStatsType(StatsType.DISTANCE) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (isAppDarkMode) Color.DarkGray else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(totalValue, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = weekStartMillis
                    val startText = dateFormat.format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, 6)
                    val endText = dateFormat.format(cal.time)

                    Text("$startText - $endText", fontSize = 14.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(30.dp))

                    SmoothLineGraph(
                        data = chartData,
                        unit = unitStr,
                        selectedType = selectedType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        lineColor = if(isSystemInDarkTheme()) Color.Cyan else MaterialTheme.colorScheme.primary,
                        textColor = if(isSystemInDarkTheme()) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#9E9E9E")
                    )
                }
            }
        }
    }
}

// --- COMPONENTS ---

@Composable
fun StatsChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun WeekDaysStrip(
    weekStartMillis: Long,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    isDarkMode: Boolean
) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val calendar = Calendar.getInstance()
    val todayCal = Calendar.getInstance()
    // ... (logic) ...
    val isCurrentWeek = todayCal.timeInMillis >= weekStartMillis &&
            todayCal.timeInMillis < (weekStartMillis + 7 * 24 * 60 * 60 * 1000)
    val currentDayInt = todayCal.get(Calendar.DAY_OF_WEEK)
    val todayIndex = if (currentDayInt == Calendar.SUNDAY) 6 else currentDayInt - 2

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), // <--- FIX
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isDarkMode) Color.DarkGray else Color.Transparent), // <--- FIX
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.Gray)
            }

            days.forEachIndexed { index, dayName ->
                calendar.timeInMillis = weekStartMillis
                calendar.add(Calendar.DAY_OF_YEAR, index)
                val dateNum = calendar.get(Calendar.DAY_OF_MONTH)
                val isToday = isCurrentWeek && (index == todayIndex)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayName, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dateNum.toString(), // (Use real date here)
                            fontSize = 14.sp,
                            color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            IconButton(onClick = onNextClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.Gray)
            }
        }
    }
}

// --- UPDATED CHART FUNCTION ---
@Composable
fun SmoothLineGraph(
    data: List<Float>,
    unit: String,
    selectedType: StatsType,
    modifier: Modifier = Modifier,
    lineColor: Color = HeaderBlueStart,
    textColor: Int
) {
    val daysLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var selectedDayIndex by remember { mutableStateOf(-1) }

    val axisLabelPaint = remember {
        Paint().apply {
            color = textColor
            textSize = 32f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
    }
    val bottomLabelPaint = remember {
        Paint().apply {
            color = textColor
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val tooltipTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val paddingStart = 100f
                val paddingEnd = 40f
                val chartStartX = paddingStart
                val chartEndX = size.width - paddingEnd

                if (offset.x in chartStartX..chartEndX) {
                    val chartWidth = chartEndX - chartStartX
                    val spacing = chartWidth / (data.size - 1)
                    val relativeX = offset.x - chartStartX
                    val rawIndex = (relativeX / spacing).roundToInt()
                    selectedDayIndex = rawIndex.coerceIn(0, data.size - 1)
                } else {
                    selectedDayIndex = -1
                }
            }
        }
    ) {
        val paddingStart = 100f
        val paddingBottom = 60f
        val paddingTop = 80f
        val paddingEnd = 40f

        val chartWidth = size.width - paddingStart - paddingEnd
        val chartHeight = size.height - paddingBottom - paddingTop

        val spacing = chartWidth / (data.size - 1)

        val maxDataValue = data.maxOrNull() ?: 0f
        val maxY = if (maxDataValue <= 0f) 1f else maxDataValue * 1.2f

        // Draw Y-Axis
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val value = maxY * (i.toFloat() / gridSteps)
            val yPos = paddingTop + chartHeight - (value / maxY * chartHeight)

            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(paddingStart, yPos),
                end = Offset(size.width - paddingEnd, yPos),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            // USES 'selectedType' HERE TO FORMAT LABEL
            val labelText = if (selectedType == StatsType.DISTANCE) "%.1f".format(value) else value.toInt().toString()
            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                paddingStart - 20f,
                yPos + axisLabelPaint.textSize / 3,
                axisLabelPaint
            )
        }

        // Draw X-Axis
        daysLabels.forEachIndexed { index, day ->
            val xPos = paddingStart + index * spacing
            drawContext.canvas.nativeCanvas.drawText(
                day,
                xPos,
                size.height - 10f,
                bottomLabelPaint
            )
        }

        if (data.isEmpty()) return@Canvas

        val path = Path()
        var currentX = paddingStart
        var currentY = paddingTop + chartHeight - (data[0] / maxY * chartHeight)
        path.moveTo(currentX, currentY)

        for (i in 0 until data.size - 1) {
            val x1 = currentX
            val y1 = currentY
            val x2 = paddingStart + (i + 1) * spacing
            val y2 = paddingTop + chartHeight - (data[i + 1] / maxY * chartHeight)

            val controlX1 = (x1 + x2) / 2f
            val controlY1 = y1
            val controlX2 = (x1 + x2) / 2f
            val controlY2 = y2

            path.cubicTo(controlX1, controlY1, controlX2, controlY2, x2, y2)

            currentX = x2
            currentY = y2
        }

        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(paddingStart + chartWidth, paddingTop + chartHeight)
        fillPath.lineTo(paddingStart, paddingTop + chartHeight)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.3f),
                    lineColor.copy(alpha = 0.05f)
                ),
                startY = paddingTop,
                endY = paddingTop + chartHeight
            )
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )

        for (i in data.indices) {
            val x = paddingStart + i * spacing
            val y = paddingTop + chartHeight - (data[i] / maxY * chartHeight)

            if (i == selectedDayIndex) {
                drawCircle(color = lineColor.copy(alpha = 0.3f), radius = 12.dp.toPx(), center = Offset(x, y))
                drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(x, y))
                drawCircle(color = lineColor, radius = 6.dp.toPx(), center = Offset(x, y))

                val tooltipValue = if (selectedType == StatsType.DISTANCE) "%.2f".format(data[i]) else data[i].toInt().toString()
                val tooltipText = "$tooltipValue $unit"
                val textWidth = tooltipTextPaint.measureText(tooltipText)
                val bubbleWidth = textWidth + 60f
                val bubbleHeight = 70f
                val bubbleBottomY = y - 30f

                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(x - bubbleWidth / 2, bubbleBottomY - bubbleHeight),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = CornerRadius(16f),
                    style = Fill
                )

                drawContext.canvas.nativeCanvas.drawText(
                    tooltipText,
                    x,
                    bubbleBottomY - bubbleHeight / 2 + tooltipTextPaint.textSize / 3,
                    tooltipTextPaint
                )

            } else if (data[i] > 0) {
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(x, y))
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}