package com.deadline.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.data.local.HabitPlanItem
import com.deadline.data.local.ItemTimerPreset
import com.deadline.ui.timer.AppleWheelColumn
import com.deadline.ui.timer.bounceClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Apple-Style Item-Specific Timer Preset Configuration Bottom Sheet.
 * Allows configuring custom durations for Focus, Break, and Flow per item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemTimerPresetModal(
    item: HabitPlanItem,
    onDismiss: () -> Unit,
    onSavePreset: (ItemTimerPreset) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSubmitting by remember { mutableStateOf(false) }

    fun dismissModal() {
        if (!isSubmitting) {
            isSubmitting = true
            sheetState.animateDismiss(scope) {
                onDismiss()
            }
        }
    }

    val initialPreset = item.timerPreset ?: ItemTimerPreset()
    var focusDuration by remember(item.id, item.timerPreset) { mutableIntStateOf(initialPreset.focusDuration) }
    var breakDuration by remember(item.id, item.timerPreset) { mutableIntStateOf(initialPreset.breakDuration) }
    var flowDuration by remember(item.id, item.timerPreset) { mutableIntStateOf(initialPreset.flowDuration) }

    // Mode Selector: 3 segments [ Focus | Break | Flow ], default: Focus
    var selectedSegmentIndex by remember { mutableIntStateOf(0) }

    val activeAccentColor = when (selectedSegmentIndex) {
        0 -> Color(0xFFFFA726) // Focus Amber
        1 -> Color(0xFF00E676) // Break Green
        else -> Color(0xFFC084FC) // Flow Purple
    }

    val currentDuration = when (selectedSegmentIndex) {
        0 -> focusDuration
        1 -> breakDuration
        else -> flowDuration
    }

    val currentHour = currentDuration / 3600
    val currentMinute = (currentDuration % 3600) / 60

    var selectedHour by remember(selectedSegmentIndex, currentDuration) { mutableIntStateOf(currentHour) }
    var selectedMinute by remember(selectedSegmentIndex, currentDuration) { mutableIntStateOf(currentMinute) }

    val hourItems = remember { (0..23).toList() }
    val minuteItems = remember { (0..59).toList() }

    val updateCurrentDuration: (Int, Int) -> Unit = { h, m ->
        val newSeconds = (h * 3600) + (m * 60)
        when (selectedSegmentIndex) {
            0 -> focusDuration = newSeconds
            1 -> breakDuration = newSeconds
            2 -> flowDuration = newSeconds
        }
    }

    // Modal height ~52% screen height matching TimerScreen drawer architecture
    val modalHeight = (configuration.screenHeightDp * 0.52f).coerceAtLeast(420f).dp

    ModalBottomSheet(
        onDismissRequest = { dismissModal() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(modalHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 68.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Subtle top drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 2.dp, bottom = 2.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )

                // Header Row: Stripped of 'New' title and header text. Close button ('X') pinned to top right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { dismissModal() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Mode Selector (Apple Segmented Control): [ Focus | Break | Flow ], default Focus
                SegmentedControl(
                    items = listOf("Focus", "Break", "Flow"),
                    selectedIndex = selectedSegmentIndex,
                    onItemSelected = { index ->
                        selectedSegmentIndex = index
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Dual-Column Duration Wheel Picker (Hour : Minute) matching physical dimensions, typography & snap physics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Wheel Column (Hour)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        key(selectedSegmentIndex) {
                            AppleWheelColumn(
                                items = hourItems,
                                selectedIndex = selectedHour,
                                onSelectedIndexChanged = { h ->
                                    selectedHour = h
                                    updateCurrentDuration(h, selectedMinute)
                                },
                                accentColor = activeAccentColor,
                                itemHeightDp = 38.dp,
                                visibleCount = 5
                            )
                        }
                    }

                    // Colon separator
                    Text(
                        text = ":",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeAccentColor,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    // Right Wheel Column (Minute)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        key(selectedSegmentIndex) {
                            AppleWheelColumn(
                                items = minuteItems,
                                selectedIndex = selectedMinute,
                                onSelectedIndexChanged = { m ->
                                    selectedMinute = m
                                    updateCurrentDuration(selectedHour, m)
                                },
                                accentColor = activeAccentColor,
                                itemHeightDp = 38.dp,
                                visibleCount = 5
                            )
                        }
                    }
                }
            }

            // Sticky Bottom-Anchored Primary Action: Prominent full-width "Save" button pinned at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSubmitting) activeAccentColor.copy(alpha = 0.6f) else activeAccentColor)
                        .border(1.dp, activeAccentColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .bounceClick(
                            scaleDown = 0.96f,
                            enabled = !isSubmitting
                        ) {
                            if (isSubmitting) return@bounceClick
                            isSubmitting = true
                            HapticHelper.success(context)
                            val finalPreset = ItemTimerPreset(
                                focusDuration = if (focusDuration > 0) focusDuration else 25 * 60,
                                breakDuration = if (breakDuration > 0) breakDuration else 5 * 60,
                                flowDuration = if (flowDuration > 0) flowDuration else 15 * 60
                            )
                            scope.launch {
                                try {
                                    onSavePreset(finalPreset)
                                    sheetState.smoothDismiss()
                                } finally {
                                    onDismiss()
                                    isSubmitting = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Wrap list items in Habits and Plan lists with debounced tap / double-tap handling.
 * - Single Tap: Executes onSingleTap after debounce window.
 * - Double Tap: Executes onDoubleTap immediately, cancelling pending single-tap execution.
 */
fun Modifier.debouncedItemTapGestures(
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit
): Modifier = pointerInput(Unit) {
    detectTapGestures(
        onDoubleTap = {
            onDoubleTap()
        },
        onTap = {
            onSingleTap()
        }
    )
}

/**
 * Testable gesture disambiguation and routing engine for item-specific timer presets.
 */
class ItemTimerGestureRouter(
    val debounceMs: Long = 280L,
    private val scope: CoroutineScope? = null
) {
    private var pendingSingleTapJob: Job? = null
    var lastTapTimestamp: Long = 0L
    var lastTappedItemId: String? = null
    var isSingleTapDispatched: Boolean = false
        private set
    var isDoubleTapDispatched: Boolean = false
        private set

    fun handleItemTap(
        item: HabitPlanItem,
        onSingleTap: (HabitPlanItem) -> Unit,
        onDoubleTap: (HabitPlanItem) -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (lastTappedItemId == item.id && (now - lastTapTimestamp) <= debounceMs) {
            // Second tap arrived within debounce window -> Double Tap!
            pendingSingleTapJob?.cancel()
            pendingSingleTapJob = null
            lastTapTimestamp = 0L
            lastTappedItemId = null
            isDoubleTapDispatched = true
            onDoubleTap(item)
        } else {
            // First tap -> Debounce single tap
            lastTapTimestamp = now
            lastTappedItemId = item.id
            isSingleTapDispatched = false
            isDoubleTapDispatched = false
            pendingSingleTapJob?.cancel()
            if (scope != null) {
                pendingSingleTapJob = scope.launch {
                    delay(debounceMs)
                    if (lastTappedItemId == item.id) {
                        lastTapTimestamp = 0L
                        lastTappedItemId = null
                        isSingleTapDispatched = true
                        onSingleTap(item)
                    }
                }
            }
        }
    }

    fun simulateDoubleTap(
        item: HabitPlanItem,
        onSingleTap: (HabitPlanItem) -> Unit,
        onDoubleTap: (HabitPlanItem) -> Unit
    ) {
        lastTapTimestamp = System.currentTimeMillis()
        lastTappedItemId = item.id
        pendingSingleTapJob?.cancel()
        pendingSingleTapJob = null
        lastTapTimestamp = 0L
        lastTappedItemId = null
        isDoubleTapDispatched = true
        onDoubleTap(item)
    }

    fun simulateSingleTap(
        item: HabitPlanItem,
        onSingleTap: (HabitPlanItem) -> Unit
    ) {
        isSingleTapDispatched = true
        onSingleTap(item)
    }
}
