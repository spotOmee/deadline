package com.deadline.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.data.local.DeadlineRepository
import com.deadline.domain.BackupManager
import com.deadline.domain.GlobalTimeManager
import com.deadline.ui.components.EllipsisIcon
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.PersonIcon
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.navigation.DeadlineTab
import com.deadline.ui.navigation.resolveStartupTab
import com.deadline.ui.theme.BrandAmberStart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onSelectTab: ((DeadlineTab) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userSettings by repository.userSettings.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showEditIdentityDialog by remember { mutableStateOf(false) }

    // Editable state for the edit modal
    var editName by remember(userSettings) { mutableStateOf(userSettings.name) }
    var editGoal by remember(userSettings) { mutableStateOf(if (userSettings.hasExplicitDailyGoal) userSettings.dailyGoalPomodoros.toString() else "") }
    var editDeadline by remember(userSettings) { mutableStateOf(userSettings.targetDeadline) }
    var editCurrentState by remember(userSettings) { mutableStateOf(userSettings.currentState) }
    var editTargetIdentity by remember(userSettings) { mutableStateOf(userSettings.targetIdentity) }
    var editBio by remember(userSettings) { mutableStateOf(userSettings.bioNarrative) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Take persistable URI permissions if available
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            scope.launch {
                repository.updateAvatar(uri.toString())
                Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showRestoreDialog by remember { mutableStateOf(false) }

    // Activity Result Launcher for exporting backup JSON to external storage
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = BackupManager.exportToJson(context)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                }
                HapticHelper.success(context)
                Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                HapticHelper.warning(context)
                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Activity Result Launcher for picking a JSON backup file to validate and restore
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
                val validation = BackupManager.validateBackup(jsonString)
                when (validation) {
                    is BackupManager.ValidationResult.Success -> {
                        val restored = BackupManager.restoreFromJson(context, jsonString!!)
                        if (restored) {
                            scope.launch { repository.refreshAll() }
                            HapticHelper.success(context)
                            val totalItems = validation.tableCounts.values.sum()
                            Toast.makeText(
                                context,
                                "Backup restored successfully ($totalItems records across ${validation.tableCounts.size} tables)",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            HapticHelper.warning(context)
                            Toast.makeText(context, "Failed to restore database from backup.", Toast.LENGTH_LONG).show()
                        }
                    }
                    is BackupManager.ValidationResult.Failure -> {
                        HapticHelper.warning(context)
                        Toast.makeText(context, "Invalid backup: ${validation.reason}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                HapticHelper.warning(context)
                Toast.makeText(context, "Failed to read backup file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val avatarBitmap = remember(userSettings.avatarUri) {
        val uriStr = userSettings.avatarUri
        if (!uriStr.isNullOrBlank()) {
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(uriStr))
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // 1. Header with Title & Ellipsis (•••) Settings Menu Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                HapticHelper.click(context)
                                onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = {
                    HapticHelper.click(context)
                    showSettingsSheet = true
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                EllipsisIcon(color = MaterialTheme.colorScheme.onSurface, size = 18.dp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Avatar & Core Identity (No placeholder initials, supports photo upload)
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    HapticHelper.click(context)
                    imagePickerLauncher.launch("image/*")
                },
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = "Profile Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PersonIcon(color = BrandAmberStart, size = 52.dp)
            }

            // Subtle badge indicator showing photo can be changed
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(BrandAmberStart),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Simple placeholder displaying the word "Name" in grey text
        Text(
            text = userSettings.name.ifBlank { "Name" },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (userSettings.name.isNotBlank()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Goal & Deadline Card (Unified Dual-Column Card)
        SquircleCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            onClick = {
                HapticHelper.click(context)
                showEditIdentityDialog = true
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Goal
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Goal",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (userSettings.hasExplicitDailyGoal) "${userSettings.dailyGoalPomodoros} Pomodoros" else "Set goal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (userSettings.hasExplicitDailyGoal) BrandAmberStart else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                )

                // Right Column: Deadline
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Deadline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = userSettings.targetDeadline.ifBlank { "Set deadline" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (userSettings.targetDeadline.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Card 1: Current
        SquircleCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            onClick = {
                HapticHelper.click(context)
                showEditIdentityDialog = true
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Current",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = userSettings.currentState.ifBlank { "Current state & discipline..." },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (userSettings.currentState.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Targeted Identity
        SquircleCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            onClick = {
                HapticHelper.click(context)
                showEditIdentityDialog = true
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Targeted Identity",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = userSettings.targetIdentity.ifBlank { "Target identity..." },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (userSettings.targetIdentity.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Self Narrative
        SquircleCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            onClick = {
                HapticHelper.click(context)
                showEditIdentityDialog = true
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Self Narrative",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = userSettings.bioNarrative.ifBlank {
                        "Self narrative..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = if (userSettings.bioNarrative.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // --- Modal Bottom Sheet: Settings (•••) ---
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var isClosingSettings by remember { mutableStateOf(false) }

        fun dismissSettings() {
            if (!isClosingSettings) {
                isClosingSettings = true
                sheetState.animateDismiss(scope) {
                    showSettingsSheet = false
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { dismissSettings() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preferences & System",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { dismissSettings() },
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

                // 1. Dynamic Global Time Synchronization Toggle Button
                val is24HourGlobal = userSettings.timeFormat24Hour

                val minThumbOffsetDp = 4.dp
                val maxThumbOffsetDp = 38.dp

                val animatedTimingThumbOffset by animateDpAsState(
                    targetValue = if (is24HourGlobal) maxThumbOffsetDp else minThumbOffsetDp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "timingThumbOffset"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            HapticHelper.selection(context)
                            scope.launch { repository.updateTimeFormat(!is24HourGlobal) }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "24-hour Global Timing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    )

                    // Permanently styled in the app's universal yellow accent
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 34.dp)
                            .clip(CircleShape)
                            .background(BrandAmberStart)
                            .border(1.dp, BrandAmberStart, CircleShape)
                            .clickable {
                                HapticHelper.selection(context)
                                scope.launch { repository.updateTimeFormat(!is24HourGlobal) }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = animatedTimingThumbOffset)
                                .size(26.dp)
                                .shadow(3.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (is24HourGlobal) "24h" else "12h",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandAmberStart,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // 2. Interactive Cycling Default Startup Screen Selector
                val cyclingDestinations = listOf(
                    "TIMER" to "Timer",
                    "HABITS" to "Habits",
                    "PROJECTS" to "Tasks",
                    "STATS" to "Stats",
                    "NOTES" to "Notes"
                )
                val activeTab = resolveStartupTab(userSettings.defaultStartupScreen)
                val activeDestination = when (activeTab) {
                    DeadlineTab.TIMER -> "TIMER" to "Timer"
                    DeadlineTab.HABITS -> "HABITS" to "Habits"
                    DeadlineTab.PROJECTS -> "PROJECTS" to "Tasks"
                    DeadlineTab.STATS -> "STATS" to "Stats"
                    DeadlineTab.NOTES -> "NOTES" to "Notes"
                }

                val cycleStartupScreen = {
                    HapticHelper.selection(context)
                    val currentIndex = cyclingDestinations.indexOfFirst { it.first == activeDestination.first }
                    val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % cyclingDestinations.size else 0
                    val nextDestination = cyclingDestinations[nextIndex]
                    scope.launch {
                        repository.updateDefaultStartupScreen(nextDestination.first)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { cycleStartupScreen() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Default Startup Screen",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    )

                    // Interactive toggle button matching the physical dimensions of the global timing toggle
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 34.dp)
                            .clip(CircleShape)
                            .background(BrandAmberStart)
                            .border(1.dp, BrandAmberStart, CircleShape)
                            .clickable { cycleStartupScreen() },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = activeDestination.second,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn(tween(150))) togetherWith
                                    (slideOutVertically { height -> -height } + fadeOut(tween(150)))
                            },
                            label = "startupScreenText"
                        ) { targetText ->
                            Text(
                                text = targetText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // 3. Theme Switching (Warm Cream Light, OLED Dark Mode, System Auto)
                val themes = listOf(
                    "light" to "Warm Cream Light",
                    "dark" to "OLED Dark Mode",
                    "system" to "System Auto"
                )

                val normalizedTheme = when (userSettings.themeMode.lowercase().trim()) {
                    "dark", "oled", "oled dark", "black" -> "dark"
                    "light" -> "light"
                    else -> "system"
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    themes.forEach { (mode, title) ->
                        val isSelected = normalizedTheme == mode
                        val rowBgColor by animateColorAsState(
                            targetValue = if (isSelected) BrandAmberStart.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            animationSpec = tween(durationMillis = 350),
                            label = "themeRowBg"
                        )
                        val rowBorderColor by animateColorAsState(
                            targetValue = if (isSelected) BrandAmberStart else Color.Transparent,
                            animationSpec = tween(durationMillis = 350),
                            label = "themeRowBorder"
                        )
                        val rowTextColor by animateColorAsState(
                            targetValue = if (isSelected) BrandAmberStart else MaterialTheme.colorScheme.onSurface,
                            animationSpec = tween(durationMillis = 350),
                            label = "themeRowText"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(rowBgColor)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = rowBorderColor,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    HapticHelper.click(context)
                                    scope.launch { repository.updateThemeMode(mode) }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Visual Theme Swatch Dot
                                val swatchColor = when (mode) {
                                    "light" -> Color(0xFFFAF7F2)
                                    "dark" -> Color(0xFF000000)
                                    else -> BrandAmberStart
                                }
                                val swatchBorder = when (mode) {
                                    "light" -> Color(0xFFD4CEC4)
                                    "dark" -> Color(0xFF3F3F46)
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(1.5.dp, swatchBorder, CircleShape)
                                )

                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = rowTextColor
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(BrandAmberStart),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✓",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // 4. Data Import/Export Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Data & Backup",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BrandAmberStart)
                                .clickable {
                                    HapticHelper.success(context)
                                    val file = BackupManager.exportBackup(context)
                                    exportJsonLauncher.launch("deadline_backup_${System.currentTimeMillis()}.json")
                                    if (file != null) {
                                        Toast.makeText(context, "Saved local backup: ${file.name}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Export JSON",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    HapticHelper.click(context)
                                    val latest = BackupManager.getLatestBackupFile(context)
                                    if (latest != null) {
                                        showRestoreDialog = true
                                    } else {
                                        importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Import Backup",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // --- Confirmation Modal: Import & Restore Backup ---
    if (showRestoreDialog) {
        val restoreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val latestFile = remember { BackupManager.getLatestBackupFile(context) }
        var isDismissingRestore by remember { mutableStateOf(false) }

        fun dismissRestore(action: () -> Unit = {}) {
            if (!isDismissingRestore) {
                isDismissingRestore = true
                restoreSheetState.animateDismiss(scope) {
                    showRestoreDialog = false
                    action()
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { dismissRestore() },
            sheetState = restoreSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Import Backup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { dismissRestore() },
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

                if (latestFile != null) {
                    Text(
                        text = "Latest auto-backup found:\n${latestFile.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandAmberStart,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            dismissRestore {
                                importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                        }
                    ) {
                        Text("Pick File...", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { dismissRestore() }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (latestFile != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                dismissRestore {
                                    val restored = BackupManager.restoreFromFile(context, latestFile)
                                    if (restored) {
                                        scope.launch { repository.refreshAll() }
                                        HapticHelper.success(context)
                                        Toast.makeText(context, "Restored state from ${latestFile.name}", Toast.LENGTH_LONG).show()
                                    } else {
                                        HapticHelper.warning(context)
                                        Toast.makeText(context, "Failed to restore backup.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAmberStart)
                        ) {
                            Text("Restore Latest", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- Modal Bottom Sheet: Edit Identity Fields ---
    if (showEditIdentityDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var isSubmittingIdentity by remember { mutableStateOf(false) }

        fun dismissEditIdentity() {
            if (!isSubmittingIdentity) {
                isSubmittingIdentity = true
                sheetState.animateDismiss(scope) {
                    showEditIdentityDialog = false
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { dismissEditIdentity() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Profile & Narrative",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { dismissEditIdentity() },
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
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name") },
                    placeholder = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editGoal,
                        onValueChange = { editGoal = it },
                        label = { Text("Goal") },
                        placeholder = { Text("e.g. 4") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editDeadline,
                        onValueChange = { editDeadline = it },
                        label = { Text("Deadline") },
                        placeholder = { Text("e.g. Dec 31") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = editCurrentState,
                    onValueChange = { editCurrentState = it },
                    label = { Text("Current") },
                    placeholder = { Text("Describe your current state...") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editTargetIdentity,
                    onValueChange = { editTargetIdentity = it },
                    label = { Text("Targeted Identity") },
                    placeholder = { Text("Define your target identity...") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editBio,
                    onValueChange = { editBio = it },
                    label = { Text("Self Narrative") },
                    placeholder = { Text("Write your self narrative...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BrandAmberStart)
                        .clickable(enabled = !isSubmittingIdentity) {
                            if (!isSubmittingIdentity) {
                                isSubmittingIdentity = true
                                HapticHelper.success(context)
                                val goalInt = editGoal.trim().toIntOrNull()
                                scope.launch {
                                    if (goalInt != null) {
                                        repository.updateDailyGoal(goalInt, setExplicit = true)
                                    } else if (editGoal.isBlank()) {
                                        repository.setHasExplicitDailyGoal(false)
                                    }
                                    repository.updateProfile(
                                        name = editName.trim(),
                                        targetDeadline = editDeadline.trim(),
                                        currentState = editCurrentState.trim(),
                                        targetIdentity = editTargetIdentity.trim(),
                                        bioNarrative = editBio.trim()
                                    )
                                    sheetState.smoothDismiss()
                                    showEditIdentityDialog = false
                                    Toast.makeText(context, "Identity saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save Identity",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
