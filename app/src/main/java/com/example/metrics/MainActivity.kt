package com.example.metrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DATE_FORMAT = "yyyy-MM-dd"

private fun dateKey(calendar: Calendar): String =
    SimpleDateFormat(DATE_FORMAT, Locale.US).format(calendar.time)

private fun todayKey(): String =
    dateKey(Calendar.getInstance())

private fun addDays(date: String, days: Int): String {

    val parser = SimpleDateFormat(DATE_FORMAT, Locale.US)

    val calendar = Calendar.getInstance()
    calendar.time = parser.parse(date) ?: Date()

    calendar.add(Calendar.DAY_OF_YEAR, days)

    return dateKey(calendar)
}

private fun displayDate(date: String): String {

    val parser = SimpleDateFormat(DATE_FORMAT, Locale.US)

    val formatter =
        SimpleDateFormat("EEE, dd MMM", Locale.US)

    return formatter.format(
        parser.parse(date) ?: Date()
    )
}

data class DailySummary(
    val date: String,
    val percentage: Int,
    val completed: Int,
    val total: Int
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)

        setContent {
            MaterialTheme {
                MainAppScreen(db.metricDao())
            }
        }
    }
}

@Composable
fun MainAppScreen(dao: MetricDao) {

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    Scaffold(

        bottomBar = {

            NavigationBar {

                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Tracker"
                        )
                    },
                    label = {
                        Text("Daily Targets")
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Analytics"
                        )
                    },
                    label = {
                        Text("Analytics")
                    }
                )
            }
        }

    ) { innerPadding ->

        Box(
            modifier = Modifier.padding(innerPadding)
        ) {

            if (selectedTab == 0) {
                TrackerScreen(dao)
            } else {
                AnalyticsScreen(dao)
            }
        }
    }
}

@Composable
fun TrackerScreen(dao: MetricDao) {

    val metrics by dao
        .getAllMetrics()
        .collectAsState(initial = emptyList())

    val today = todayKey()

    val todayEntries by dao
        .getEntriesForDate(today)
        .collectAsState(initial = emptyList())

    val achievedByMetric =
        todayEntries.associateBy { it.metricId }

    var newMetricName by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Today's Metrics",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            displayDate(today),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = newMetricName,
                onValueChange = {
                    newMetricName = it
                },
                label = {
                    Text("New Target/Metric")
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {

                    if (newMetricName.isNotBlank()) {

                        scope.launch {

                            dao.insertMetric(
                                MetricEntity(
                                    name = newMetricName.trim()
                                )
                            )

                            newMetricName = ""
                        }
                    }
                }
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (metrics.isEmpty()) {

            Text("Add your first metric above.")

        } else {

            LazyColumn {

                items(
                    metrics,
                    key = { it.id }
                ) { metric ->

                    val achieved =
                        achievedByMetric[metric.id]?.isAchieved == true

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                metric.name,
                                style =
                                    MaterialTheme.typography.bodyLarge
                            )

                            Checkbox(

                                checked = achieved,

                                onCheckedChange = { isChecked ->

                                    scope.launch {

                                        dao.upsertDailyEntry(

                                            DailyMetricEntry(
                                                metricId = metric.id,
                                                date = today,
                                                isAchieved = isChecked
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(dao: MetricDao) {

    val metrics by dao
        .getAllMetrics()
        .collectAsState(initial = emptyList())

    var rangeDays by remember {
        mutableIntStateOf(7)
    }

    var selectedDate by remember {
        mutableStateOf(todayKey())
    }

    val endDate = todayKey()

    val startDate =
        addDays(
            endDate,
            -(rangeDays - 1)
        )

    val entries by dao
        .getEntriesBetween(
            startDate,
            endDate
        )
        .collectAsState(initial = emptyList())

    val entriesByDate =
        entries.groupBy { it.date }

    val summaries =
        (0 until rangeDays).map { offset ->

            val date =
                addDays(startDate, offset)

            val dayEntries =
                entriesByDate[date].orEmpty()

            val completed =
                dayEntries.count {
                    it.isAchieved
                }

            val total =
                metrics.size

            DailySummary(
                date = date,

                percentage =
                    if (total == 0)
                        0
                    else
                        completed * 100 / total,

                completed = completed,

                total = total
            )
        }

    val selectedEntries =
        entriesByDate[selectedDate].orEmpty()

    val selectedByMetric =
        selectedEntries.associateBy {
            it.metricId
        }

    val average =
        if (summaries.isEmpty())
            0
        else
            summaries.sumOf {
                it.percentage
            } / summaries.size

    val chartColor =
        MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Analytics",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Historical performance",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            FilterChip(

                selected =
                    rangeDays == 7,

                onClick = {
                    rangeDays = 7
                },

                label = {
                    Text("7 Days")
                }
            )

            FilterChip(

                selected =
                    rangeDays == 30,

                onClick = {
                    rangeDays = 30
                },

                label = {
                    Text("30 Days")
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    "Average completion",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    "$average%",
                    style =
                        MaterialTheme.typography.headlineLarge,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "${summaries.lastOrNull()?.completed ?: 0} " +
                            "of ${metrics.size} completed today"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Daily trend",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        CompletionChart(
            summaries = summaries,
            chartColor = chartColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Daily history",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {

            items(
                summaries.reversed(),
                key = { it.date }
            ) { summary ->

                OutlinedButton(

                    onClick = {
                        selectedDate = summary.date
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),

                    shape =
                        RoundedCornerShape(12.dp)
                ) {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        Text(
                            displayDate(summary.date)
                        )

                        Text(
                            "${summary.percentage}%"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Entries for ${displayDate(selectedDate)}",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (metrics.isEmpty()) {

            Text("No metrics created yet.")

        } else {

            metrics.forEach { metric ->

                val achieved =
                    selectedByMetric[metric.id]
                        ?.isAchieved == true

                Row(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(metric.name)

                    Text(
                        if (achieved)
                            "✓ Completed"
                        else
                            "Not completed"
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionChart(
    summaries: List<DailySummary>,
    chartColor: androidx.compose.ui.graphics.Color
) {

    if (summaries.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Canvas(

            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(16.dp)

        ) {

            val maxValue = 100f

            val stepX =
                if (summaries.size == 1)
                    size.width
                else
                    size.width /
                            (summaries.size - 1)

            val points =
                summaries.mapIndexed {
                        index,
                        summary ->

                    Offset(

                        x = index * stepX,

                        y =
                            size.height -
                                    (summary.percentage /
                                            maxValue) *
                                    size.height
                    )
                }

            for (i in 0 until points.lastIndex) {

                drawLine(

                    color = chartColor,

                    start = points[i],

                    end = points[i + 1],

                    strokeWidth = 6f,

                    cap = StrokeCap.Round
                )
            }

            points.forEach { point ->

                drawCircle(

                    color = chartColor,

                    radius = 6f,

                    center = point
                )
            }
        }
    }
}