package com.deadline.ui.journal

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.data.local.DailyReflection
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.PomodoroType
import com.deadline.domain.AdherenceScorer
import com.deadline.domain.BackupManager
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.FocusOrange
import com.deadline.ui.theme.UrgentAmber
import com.deadline.ui.theme.WorkIndigo
import kotlinx.coroutines.launch

@Composable
fun JournalStatsScreen(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userSettings by repository.userSettings.collectAsState()
    val todaySessions by repository.todaySessions.collectAsState()
    val todayBlocks by repository.todayTimeBlocks.collectAsState()
    val todayReflection by repository.todayReflection.collectAsState()
    val todayCompletions by repository.todayCompletions.collectAsState()

    val totalFocusMinutes = todaySessions
        .filter { it.sessionType == PomodoroType.FOCUS && it.completed }
        .sumOf { it.durationMinutes }
    val completedSessionsCount = todaySessions
        .count { it.sessionType == PomodoroType.FOCUS && it.completed }
    val adherenceScore = AdherenceScorer.computeScore(todayBlocks, todaySessions)

    var mood by remember(todayReflection) { mutableStateOf(todayReflection?.mood ?: 4) }
    var energy by remember(todayReflection) { mutableStateOf(todayReflection?.energyLevel ?: 4) }
    var winText by remember(todayReflection) { mutableStateOf(todayReflection?.win ?: "") }
    var challengeText by remember(todayReflection) { mutableStateOf(todayReflection?.challenge ?: "") }
    var tomorrowText by remember(todayReflection) { mutableStateOf(todayReflection?.tomorrowIntent ?: "") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                text = "Reflection & Stats",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. Hero Adherence & Focus Metrics Card
        item {
            SquircleCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "TODAY'S ADHERENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$adherenceScore%",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (adherenceScore >= 80) BreakGreen else FocusOrange
                        )
                        Text(
                            text = "Score",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Focus Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${totalFocusMinutes}m",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = FocusOrange
                            )
                        }
                        Column {
                            Text(
                                text = "Sessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$completedSessionsCount/${userSettings.dailyGoalPomodoros}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = WorkIndigo
                            )
                        }
                        Column {
                            Text(
                                text = "Current Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "🔥 ${userSettings.currentStreak}d",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = UrgentAmber
                            )
                        }
                    }
                }
            }
        }

        // 2. Evening Wind-Down Reflection Card
        item {
            SquircleCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Daily Mindful Reflection",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Calm your mind and capture your daily wisdom",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mood selector
                    Text(
                        text = "How are you feeling?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val moods = listOf("😢" to 1, "😕" to 2, "😐" to 3, "🙂" to 4, "🤩" to 5)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        moods.forEach { (emoji, rating) ->
                            val isSelected = mood == rating
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) BreakGreen else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        HapticHelper.click(context)
                                        mood = rating
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Energy level
                    Text(
                        text = "Energy Level (1 - 5)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { lvl ->
                            val isSelected = energy == lvl
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) FocusOrange else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        HapticHelper.click(context)
                                        energy = lvl
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$lvl",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = winText,
                        onValueChange = { winText = it },
                        label = { Text("What was your biggest win today?") },
                        placeholder = { Text("Shipped the project deadline code") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = challengeText,
                        onValueChange = { challengeText = it },
                        label = { Text("What challenge did you overcome?") },
                        placeholder = { Text("Overcame afternoon distraction") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tomorrowText,
                        onValueChange = { tomorrowText = it },
                        label = { Text("Core intent for tomorrow?") },
                        placeholder = { Text("Start morning with 90m deep work") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    SquircleCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = BreakGreen,
                        onClick = {
                            HapticHelper.success(context)
                            scope.launch {
                                repository.saveDailyReflection(
                                    DailyReflection(
                                        id = todayReflection?.id ?: java.util.UUID.randomUUID().toString(),
                                        date = repository.getTodayString(),
                                        mood = mood,
                                        energyLevel = energy,
                                        win = winText,
                                        challenge = challengeText,
                                        tomorrowIntent = tomorrowText,
                                        totalFocusMinutes = totalFocusMinutes,
                                        habitsCompleted = todayCompletions.size
                                    )
                                )
                                Toast.makeText(context, "Reflection Saved! ✨", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save Reflection",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 3. Local-First Data Sovereignty & Export
        item {
            SquircleCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                onClick = {
                    HapticHelper.click(context)
                    val json = BackupManager.exportToJson(context)
                    val file = BackupManager.saveBackupToFile(context, json)
                    Toast.makeText(context, "Saved local backup: ${file.name}", Toast.LENGTH_LONG).show()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "📦 Local Data Backup",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Export full database as offline JSON",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Export",
                        style = MaterialTheme.typography.labelLarge,
                        color = WorkIndigo
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
