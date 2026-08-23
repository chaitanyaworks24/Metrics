package com.example.metrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        setContent {
            MainAppScreen(db.metricDao())
        }
    }
}

@Composable
fun MainAppScreen(dao: MetricDao) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Check, contentDescription = "Tracker") },
                    label = { Text("Daily Targets") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Analytics") },
                    label = { Text("Analytics") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
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
    val metrics by dao.getAllMetrics().collectAsState(initial = emptyList())
    var newMetricName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newMetricName,
                onValueChange = { newMetricName = it },
                label = { Text("New Target/Metric") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newMetricName.isNotBlank()) {
                    scope.launch {
                        dao.insertMetric(MetricEntity(name = newMetricName))
                        newMetricName = ""
                    }
                }
            }) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(metrics) { metric ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(metric.name, style = MaterialTheme.typography.bodyLarge)
                        Checkbox(
                            checked = metric.isAchieved,
                            onCheckedChange = { isChecked ->
                                scope.launch { dao.updateMetric(metric.copy(isAchieved = isChecked)) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(dao: MetricDao) {
    val metrics by dao.getAllMetrics().collectAsState(initial = emptyList())
    val completedCount = metrics.count { it.isAchieved }
    val totalCount = metrics.size
    val percentage = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()) * 100 else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Daily Progress", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(
            progress = { if (totalCount > 0) completedCount.toFloat() / totalCount else 0f },
            modifier = Modifier.size(120.dp),
            strokeWidth = 8.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("${percentage.toInt()}% Completed", style = MaterialTheme.typography.titleLarge)
        Text("$completedCount of $totalCount targets achieved today", style = MaterialTheme.typography.bodyMedium)
    }
}