package com.deadline.ui.journal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.data.local.DailyReflection
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.KnowledgeArticle
import com.deadline.data.local.QuickNote
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.NotesDocumentIcon
import com.deadline.ui.components.SegmentedControl
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.components.StandardEmptyState
import com.deadline.ui.theme.BrandAmberStart
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.StatusUrgent
import com.deadline.domain.GlobalTimeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val todayReflection by repository.todayReflection.collectAsState()
    val todaySessions by repository.todaySessions.collectAsState()
    val quickNotes by repository.quickNotes.collectAsState()
    val knowledgeArticles by repository.knowledgeArticles.collectAsState()
    val userSettings by repository.userSettings.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeSubTab by remember { mutableStateOf(0) } // 0: Timeline, 1: Reflections, 2: Notes, 3: Read (CMS)

    var showCreateArticleSheet by remember { mutableStateOf(false) }
    var showCreateNoteSheet by remember { mutableStateOf(false) }
    var selectedArticleForRead by remember { mutableStateOf<KnowledgeArticle?>(null) }

    // Reflections state
    var mood by remember(todayReflection) { mutableStateOf(todayReflection?.mood ?: 4) }
    var energy by remember(todayReflection) { mutableStateOf(todayReflection?.energyLevel ?: 4) }
    var winText by remember(todayReflection) { mutableStateOf(todayReflection?.win ?: "") }
    var challengeText by remember(todayReflection) { mutableStateOf(todayReflection?.challenge ?: "") }
    var tomorrowText by remember(todayReflection) { mutableStateOf(todayReflection?.tomorrowIntent ?: "") }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            // 1. Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BrandAmberStart.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "D",
                                fontWeight = FontWeight.Bold,
                                color = BrandAmberStart,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = "Journal & Read",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Context action button
                    if (activeSubTab == 3) {
                        // + Create / Upload Pill for Custom User CMS
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(BrandAmberStart)
                                .clickable {
                                    HapticHelper.click(context)
                                    showCreateArticleSheet = true
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Upload", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else if (activeSubTab == 2) {
                        // + Note Button
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    HapticHelper.click(context)
                                    showCreateNoteSheet = true
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = BrandAmberStart, modifier = Modifier.size(16.dp))
                                Text("Note", color = BrandAmberStart, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. Global Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search reflections, notes, uploads...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }

            // 3. 4 Sub-Tabs (Segmented Pills): Timeline | Reflections | Notes | Read (CMS)
            item {
                SegmentedControl(
                    items = listOf("Timeline", "Reflections", "Notes", "Read"),
                    selectedIndex = activeSubTab,
                    onItemSelected = {
                        HapticHelper.selection(context)
                        activeSubTab = it
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Sub-Tab Content Views
            when (activeSubTab) {
                0 -> {
                    // --- 1. TIMELINE: Unified Chronological Feed ---
                    val totalFocus = todaySessions.filter { it.completed }.sumOf { it.durationMinutes }
                    item {
                        SquircleCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Today's Focus Activity",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Completed ${todaySessions.count { it.completed }} pomodoro sessions ($totalFocus mins logged).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (todayReflection != null) {
                        item {
                            SquircleCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Daily Reflection Logged",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BreakGreen
                                    )
                                    todayReflection?.win?.takeIf { it.isNotBlank() }?.let {
                                        Text(text = "Win: $it", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    todayReflection?.tomorrowIntent?.takeIf { it.isNotBlank() }?.let {
                                        Text(text = "Tomorrow: $it", style = MaterialTheme.typography.bodyMedium, color = BrandAmberStart)
                                    }
                                }
                            }
                        }
                    }

                    // Quick Notes in Timeline
                    items(quickNotes.filter { searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true) }) { note ->
                        SquircleCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(text = note.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Knowledge Articles in Timeline
                    items(knowledgeArticles.filter { searchQuery.isBlank() || it.headline.contains(searchQuery, true) || it.body.contains(searchQuery, true) }) { article ->
                        KnowledgeArticleCard(article = article, is24Hour = userSettings.timeFormat24Hour, onClick = { selectedArticleForRead = article })
                    }
                }
                1 -> {
                    // --- 2. REFLECTIONS: Mindful Daily Card ---
                    item {
                        SquircleCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Daily Mindful Reflection",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Mood Selector (5 clean faces with active glow)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "How was your focus mood today?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val moods = listOf(
                                        1 to "😔",
                                        2 to "😕",
                                        3 to "😐",
                                        4 to "🙂",
                                        5 to "🤩"
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        moods.forEach { (score, emoji) ->
                                            val isSelected = mood == score
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) BrandAmberStart.copy(alpha = 0.25f)
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    .clickable {
                                                        HapticHelper.click(context)
                                                        mood = score
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = emoji, fontSize = 22.sp)
                                            }
                                        }
                                    }
                                }

                                // Energy Level Selector (1 to 5)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Energy Level (1 = Exhausted, 5 = Peak Flow)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        (1..5).forEach { level ->
                                            val isSelected = energy == level
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) BrandAmberStart
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    .clickable {
                                                        HapticHelper.click(context)
                                                        energy = level
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$level",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                // Structured Prompts
                                OutlinedTextField(
                                    value = winText,
                                    onValueChange = { winText = it },
                                    label = { Text("What was your biggest win today?") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = challengeText,
                                    onValueChange = { challengeText = it },
                                    label = { Text("What challenge did you overcome?") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = tomorrowText,
                                    onValueChange = { tomorrowText = it },
                                    label = { Text("Core intent for tomorrow?") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Save Reflection CTA
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BrandAmberStart)
                                        .clickable {
                                            HapticHelper.success(context)
                                            scope.launch {
                                                val reflection = DailyReflection(
                                                    userId = "default_user",
                                                    date = repository.getTodayString(),
                                                    mood = mood,
                                                    energyLevel = energy,
                                                    win = winText,
                                                    challenge = challengeText,
                                                    tomorrowIntent = tomorrowText,
                                                    totalFocusMinutes = todaySessions.filter { it.completed }.sumOf { it.durationMinutes }
                                                )
                                                repository.saveDailyReflection(reflection)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Save Reflection",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // --- 3. NOTES: Apple Notes-style Quick Capture ---
                    val filteredNotes = quickNotes.filter {
                        searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true)
                    }

                    if (filteredNotes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StandardEmptyState(
                                    title = "No notes yet",
                                    icon = {
                                        NotesDocumentIcon(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            size = 28.dp
                                        )
                                    }
                                )
                            }
                        }
                    }

                    items(filteredNotes) { note ->
                        SquircleCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = note.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val dateStr = GlobalTimeManager.formatDate(note.createdAt, userSettings.timeFormat24Hour)
                                Text(
                                    text = "$dateStr • ${GlobalTimeManager.getTimeZoneLabel(userSettings.timeFormat24Hour)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // --- 4. READ: CUSTOM USER CMS (Personal Knowledge Library) ---
                    val filteredArticles = knowledgeArticles.filter {
                        searchQuery.isBlank() || it.headline.contains(searchQuery, true) || it.body.contains(searchQuery, true) || it.tags.contains(searchQuery, true)
                    }

                    if (filteredArticles.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Your Knowledge Library is empty.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Upload personal screenshots, design inspiration, or custom summaries.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(filteredArticles) { article ->
                        KnowledgeArticleCard(
                            article = article,
                            is24Hour = userSettings.timeFormat24Hour,
                            onClick = { selectedArticleForRead = article }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // Article Full-Screen Reader Dialog
        if (selectedArticleForRead != null) {
            val article = selectedArticleForRead!!
            val readerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var isClosingReader by remember { mutableStateOf(false) }

            fun dismissReader() {
                if (!isClosingReader) {
                    isClosingReader = true
                    readerSheetState.animateDismiss(scope) {
                        selectedArticleForRead = null
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { dismissReader() },
                sheetState = readerSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BrandAmberStart.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = article.tags,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandAmberStart
                            )
                        }
                        IconButton(
                            onClick = { dismissReader() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = article.headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = article.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // New Note Sheet
        if (showCreateNoteSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var noteTitle by remember { mutableStateOf("") }
            var noteContent by remember { mutableStateOf("") }
            var isSubmittingNote by remember { mutableStateOf(false) }

            fun dismissNoteSheet() {
                if (!isSubmittingNote) {
                    isSubmittingNote = true
                    sheetState.animateDismiss(scope) {
                        showCreateNoteSheet = false
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { dismissNoteSheet() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick Note",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { dismissNoteSheet() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it.take(20) },
                        label = { Text("Note Title (max 20)") },
                        supportingText = { Text("${noteTitle.length}/20", textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Write your thoughts...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BrandAmberStart)
                            .clickable(enabled = noteTitle.isNotBlank() && !isSubmittingNote) {
                                if (noteTitle.isNotBlank() && !isSubmittingNote) {
                                    isSubmittingNote = true
                                    HapticHelper.success(context)
                                    scope.launch {
                                        repository.addQuickNote(noteTitle.trim().take(20), noteContent)
                                        sheetState.smoothDismiss()
                                        showCreateNoteSheet = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Save Note", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // New Knowledge Article (CMS Upload) Sheet
        if (showCreateArticleSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var headline by remember { mutableStateOf("") }
            var body by remember { mutableStateOf("") }
            var tags by remember { mutableStateOf("DESIGN, FOCUS") }
            var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
            var isSubmittingArticle by remember { mutableStateOf(false) }

            fun dismissArticleSheet() {
                if (!isSubmittingArticle) {
                    isSubmittingArticle = true
                    sheetState.animateDismiss(scope) {
                        showCreateArticleSheet = false
                    }
                }
            }

            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                selectedImageUri = uri
            }

            ModalBottomSheet(
                onDismissRequest = { dismissArticleSheet() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Upload to Knowledge Library",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { dismissArticleSheet() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Media Upload Zone
                    SquircleCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (selectedImageUri != null) "✓ Image Selected" else "Tap to Upload Screenshot / Image",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedImageUri != null) BreakGreen else BrandAmberStart
                            )
                            if (selectedImageUri != null) {
                                Text(
                                    text = "Tap to change image",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = headline,
                        onValueChange = { headline = it },
                        label = { Text("Headline") },
                        placeholder = { Text("e.g. Architecture of High Focus State") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Article body, summary, or notes...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BrandAmberStart)
                            .clickable(enabled = headline.isNotBlank() && body.isNotBlank() && !isSubmittingArticle) {
                                if (headline.isNotBlank() && body.isNotBlank() && !isSubmittingArticle) {
                                    isSubmittingArticle = true
                                    HapticHelper.success(context)
                                    scope.launch {
                                        repository.addKnowledgeArticle(
                                            headline = headline,
                                            body = body,
                                            tags = tags,
                                            imageUri = selectedImageUri?.toString()
                                        )
                                        sheetState.smoothDismiss()
                                        showCreateArticleSheet = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Publish Article",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun KnowledgeArticleCard(
    article: KnowledgeArticle,
    is24Hour: Boolean = false,
    onClick: () -> Unit
) {
    SquircleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrandAmberStart.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = article.tags,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandAmberStart
                    )
                }
                val dateStr = GlobalTimeManager.formatDate(article.createdAt, is24Hour)
                Text(
                    text = "$dateStr • ${GlobalTimeManager.getTimeZoneLabel(is24Hour)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = article.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = article.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Read more →",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BrandAmberStart
            )
        }
    }
}
