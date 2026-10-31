package com.deadline.ui.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.deadline.ui.components.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateSlotTargetIndex
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.animateDragToSettle
import com.deadline.ui.components.detectFastDragGesturesAfterLongPress
import com.deadline.ui.components.DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS
import com.deadline.ui.components.getReorderSpringSpec
import com.deadline.ui.components.triggerPickupHaptic
import kotlinx.coroutines.Job
import com.deadline.ui.components.UniversalAutoScrollCoordinator
import com.deadline.ui.components.ReorderGrabHandleIcon
import com.deadline.ui.components.MorphingLeadingActionIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import com.deadline.ui.navigation.NavigationZoningDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.deadline.ui.components.PencilEditIcon
import kotlin.math.abs
import kotlin.math.roundToInt
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.QuickNote
import com.deadline.domain.HeadlineScaler
import com.deadline.ui.components.AppleHeader
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.calculateDynamicHeadlineSize
import com.deadline.ui.components.NotesDocumentIcon
import com.deadline.ui.components.PinIcon
import com.deadline.ui.components.RedoIcon
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
import com.deadline.ui.components.StandardEmptyState
import com.deadline.ui.components.UndoIcon
import com.deadline.ui.theme.BrandAmberStart
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.StatusUrgent
import com.deadline.ui.theme.WorkIndigo
import com.deadline.domain.GlobalTimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NoteSwipeAction {
    NONE,
    EDIT,
    DELETE
}

/**
 * Dynamic Sectioning Logic for Unified Notes Architecture:
 * - When zero notes are pinned, partitionNotes returns isSectioned = false.
 * - When one or more notes are pinned, partitionNotes returns isSectioned = true,
 *   splitting the notes into 'Pinned' and 'Others'.
 * - togglePin immediately re-indexes the note and returns the updated sorted list.
 */
object NotesSectioningLogic {
    data class SectionedNotes(
        val isSectioned: Boolean,
        val pinnedNotes: List<QuickNote>,
        val otherNotes: List<QuickNote>
    )

    fun partitionNotes(notes: List<QuickNote>): SectionedNotes {
        val sorted = notes.sortedWith(
            compareByDescending<QuickNote> { it.isPinned }
                .thenBy { it.sortOrder }
        )
        val pinned = sorted.filter { it.isPinned }
        val others = sorted.filter { !it.isPinned }
        return SectionedNotes(
            isSectioned = pinned.isNotEmpty(),
            pinnedNotes = pinned,
            otherNotes = others
        )
    }

    fun togglePin(notes: List<QuickNote>, noteId: String): List<QuickNote> {
        val target = notes.find { it.id == noteId } ?: return notes
        val newPinned = !target.isPinned
        return notes.map {
            if (it.id == noteId) it.copy(isPinned = newPinned, updatedAt = System.currentTimeMillis()) else it
        }.sortedWith(
            compareByDescending<QuickNote> { it.isPinned }
                .thenBy { it.sortOrder }
        )
    }
}

/**
 * Section Header for Pinned / Others sections in Apple HIG aesthetic.
 */
@Composable
fun NotesSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val userSettings by repository.userSettings.collectAsState()
    val quickNotes by repository.quickNotes.collectAsState()

    var isAnyCardSwiping by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<QuickNote?>(null) }
    var isCreatingNewNote by remember { mutableStateOf(false) }

    // In-List Reordering & Drag-and-Drop state
    var draggedNoteId by remember { mutableStateOf<String?>(null) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var currentHoverIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var draggedInitialPointerInRoot by remember { mutableStateOf<Offset?>(null) }
    var isNoteSettling by remember { mutableStateOf(false) }
    var noteSettleJob by remember { mutableStateOf<Job?>(null) }

    var localQuickNotes by remember { mutableStateOf(quickNotes) }
    var exitingNoteIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(quickNotes) {
        if (draggedNoteId == null) {
            localQuickNotes = quickNotes
        }
    }

    val itemHeightPx = with(density) { (64.dp + 12.dp).toPx() }
    var measuredNoteItemHeightPx by remember { mutableFloatStateOf(itemHeightPx) }

    val sectioned = remember(localQuickNotes) {
        NotesSectioningLogic.partitionNotes(localQuickNotes)
    }
    val pinnedNotes = sectioned.pinnedNotes
    val otherNotes = sectioned.otherNotes
    val isSectioned = sectioned.isSectioned

    fun togglePin(note: QuickNote) {
        HapticHelper.click(context)
        val updatedList = NotesSectioningLogic.togglePin(localQuickNotes, note.id)
        localQuickNotes = updatedList
        scope.launch {
            repository.togglePinNote(note.id)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Single Unified Top Header: Notes with SF Pro profile avatar and typography
            AppleHeader(
                title = "Notes",
                avatarUri = userSettings.avatarUri,
                onProfileClick = onOpenProfile
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Dynamic Conditional Sectioning Notes Content
            if (localQuickNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
            } else {
                val notesListState = androidx.compose.foundation.lazy.rememberLazyListState()
                var notesViewportHeight by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
                var notesListBounds by remember { mutableStateOf(Rect.Zero) }

                val pointerYInViewport = if (draggedInitialPointerInRoot != null) {
                    draggedInitialPointerInRoot!!.y + dragOffsetY - notesListBounds.top
                } else 0f

                UniversalAutoScrollCoordinator(
                    lazyListState = notesListState,
                    isDragging = draggedNoteId != null,
                    pointerYInViewport = pointerYInViewport,
                    viewportHeight = notesViewportHeight
                )

                LazyColumn(
                    state = notesListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            notesViewportHeight = coords.size.height.toFloat()
                            notesListBounds = coords.boundsInRoot()
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isSectioned) {
                        // When zero notes are pinned, display all notes in a single standard list without section headers
                        items(otherNotes, key = { it.id }) { note ->
                            NoteCardContainer(
                                note = note,
                                sectionNotes = otherNotes,
                                draggedNoteId = draggedNoteId,
                                draggedIndex = draggedIndex,
                                dragOffsetX = dragOffsetX,
                                dragOffsetY = dragOffsetY,
                                itemHeightPx = itemHeightPx,
                                isExiting = note.id in exitingNoteIds,
                                is24Hour = userSettings.timeFormat24Hour,
                                onSwipeStart = { isAnyCardSwiping = true },
                                onSwipeEnd = { isAnyCardSwiping = false },
                                onTogglePin = { togglePin(note) },
                                onClick = { editingNote = note },
                                onEdit = { editingNote = note },
                                onDelete = {
                                    exitingNoteIds = exitingNoteIds + note.id
                                    scope.launch {
                                        delay(200)
                                        localQuickNotes = localQuickNotes.filter { it.id != note.id }
                                        exitingNoteIds = exitingNoteIds - note.id
                                        withContext(Dispatchers.IO) {
                                            repository.deleteQuickNote(note.id)
                                        }
                                    }
                                },
                                onToggleChecklist = { updatedContent ->
                                    scope.launch {
                                        repository.updateQuickNote(note.copy(content = updatedContent))
                                    }
                                },
                                onDragStart = { offset, cardBoundsInRoot ->
                                    noteSettleJob?.cancel()
                                    isNoteSettling = false
                                    triggerPickupHaptic(context, hapticFeedback)
                                    measuredNoteItemHeightPx = cardBoundsInRoot.height + with(density) { 12.dp.toPx() }
                                    val index = otherNotes.indexOfFirst { it.id == note.id }
                                    draggedNoteId = note.id
                                    draggedIndex = index
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    draggedInitialPointerInRoot = cardBoundsInRoot.topLeft + offset
                                    currentHoverIndex = index
                                },
                                onDragDelta = onDragDelta@ { dx, dy ->
                                    if (isNoteSettling) return@onDragDelta
                                    dragOffsetX += dx
                                    dragOffsetY += dy
                                    val currentIdx = draggedIndex ?: otherNotes.indexOfFirst { it.id == note.id }
                                    val effectiveHeight = if (measuredNoteItemHeightPx > 0f) measuredNoteItemHeightPx else itemHeightPx
                                    val targetIdx = calculateSlotTargetIndex(currentIdx, dragOffsetY, effectiveHeight, otherNotes.size)
                                    if (targetIdx != currentIdx && targetIdx in otherNotes.indices) {
                                        val reordered = otherNotes.toMutableList()
                                        val direction = if (targetIdx > currentIdx) 1 else -1
                                        var iterIdx = currentIdx
                                        while (iterIdx != targetIdx) {
                                            val nextIdx = iterIdx + direction
                                            java.util.Collections.swap(reordered, iterIdx, nextIdx)
                                            iterIdx = nextIdx
                                        }
                                        val slotsMoved = targetIdx - currentIdx
                                        val itemHeightPx = effectiveHeight
                                        dragOffsetY -= slotsMoved * itemHeightPx
                                        draggedIndex = targetIdx
                                        currentHoverIndex = targetIdx
                                        val updated = reordered.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
                                        localQuickNotes = updated
                                        HapticHelper.tick(context)
                                    }
                                },
                                onDragEnd = onDragEnd@ {
                                    if (isNoteSettling) return@onDragEnd
                                    val startIdx = draggedIndex
                                    if (startIdx != null) {
                                        val effectiveHeight = if (measuredNoteItemHeightPx > 0f) measuredNoteItemHeightPx else itemHeightPx
                                        val targetSettleY = calculateSettleTargetOffset(startIdx, startIdx, effectiveHeight)
                                        isNoteSettling = true
                                        noteSettleJob = scope.launch {
                                            animateDragToSettle(
                                                currentOffset = dragOffsetY,
                                                targetOffset = targetSettleY,
                                                springSpec = getReorderSpringSpec(context)
                                            ) { animatedY ->
                                                dragOffsetY = animatedY
                                            }
                                            withContext(Dispatchers.IO) {
                                                repository.reorderQuickNotes(localQuickNotes)
                                            }
                                            HapticHelper.success(context)
                                            draggedNoteId = null
                                            draggedIndex = null
                                            currentHoverIndex = null
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                            isNoteSettling = false
                                        }
                                    } else {
                                        draggedNoteId = null
                                        draggedIndex = null
                                        currentHoverIndex = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                    }
                                },
                                onDragCancel = onDragCancel@ {
                                    if (isNoteSettling) return@onDragCancel
                                    val startIdx = draggedIndex
                                    if (startIdx != null) {
                                        isNoteSettling = true
                                        noteSettleJob = scope.launch {
                                            animateDragToSettle(
                                                currentOffset = dragOffsetY,
                                                targetOffset = 0f,
                                                springSpec = getReorderSpringSpec(context)
                                            ) { animatedY ->
                                                dragOffsetY = animatedY
                                            }
                                            withContext(Dispatchers.IO) {
                                                repository.reorderQuickNotes(localQuickNotes)
                                            }
                                            draggedNoteId = null
                                            draggedIndex = null
                                            currentHoverIndex = null
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                            isNoteSettling = false
                                        }
                                    } else {
                                        draggedNoteId = null
                                        draggedIndex = null
                                        currentHoverIndex = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                    }
                                }
                            )
                        }
                    } else {
                        // When one or more notes are pinned, dynamically split the view into two distinct sections:
                        // a 'Pinned' section at the top and an 'Others' section below
                        item(key = "header_pinned") {
                            NotesSectionHeader(title = "Pinned")
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCardContainer(
                                note = note,
                                sectionNotes = pinnedNotes,
                                draggedNoteId = draggedNoteId,
                                draggedIndex = draggedIndex,
                                dragOffsetX = dragOffsetX,
                                dragOffsetY = dragOffsetY,
                                itemHeightPx = itemHeightPx,
                                isExiting = note.id in exitingNoteIds,
                                is24Hour = userSettings.timeFormat24Hour,
                                onSwipeStart = { isAnyCardSwiping = true },
                                onSwipeEnd = { isAnyCardSwiping = false },
                                onTogglePin = { togglePin(note) },
                                onClick = { editingNote = note },
                                onEdit = { editingNote = note },
                                onDelete = {
                                    exitingNoteIds = exitingNoteIds + note.id
                                    scope.launch {
                                        delay(200)
                                        localQuickNotes = localQuickNotes.filter { it.id != note.id }
                                        exitingNoteIds = exitingNoteIds - note.id
                                        withContext(Dispatchers.IO) {
                                            repository.deleteQuickNote(note.id)
                                        }
                                    }
                                },
                                onToggleChecklist = { updatedContent ->
                                    scope.launch {
                                        repository.updateQuickNote(note.copy(content = updatedContent))
                                    }
                                },
                                onDragStart = { offset, cardBoundsInRoot ->
                                    noteSettleJob?.cancel()
                                    isNoteSettling = false
                                    triggerPickupHaptic(context, hapticFeedback)
                                    measuredNoteItemHeightPx = cardBoundsInRoot.height + with(density) { 12.dp.toPx() }
                                    val index = pinnedNotes.indexOfFirst { it.id == note.id }
                                    draggedNoteId = note.id
                                    draggedIndex = index
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    draggedInitialPointerInRoot = cardBoundsInRoot.topLeft + offset
                                    currentHoverIndex = index
                                },
                                onDragDelta = onDragDelta@ { dx, dy ->
                                    if (isNoteSettling) return@onDragDelta
                                    dragOffsetX += dx
                                    dragOffsetY += dy
                                    val currentIdx = draggedIndex ?: pinnedNotes.indexOfFirst { it.id == note.id }
                                    val effectiveHeight = if (measuredNoteItemHeightPx > 0f) measuredNoteItemHeightPx else itemHeightPx
                                    val targetIdx = calculateSlotTargetIndex(currentIdx, dragOffsetY, effectiveHeight, pinnedNotes.size)
                                    if (targetIdx != currentIdx && targetIdx in pinnedNotes.indices) {
                                        val reordered = pinnedNotes.toMutableList()
                                        val direction = if (targetIdx > currentIdx) 1 else -1
                                        var iterIdx = currentIdx
                                        while (iterIdx != targetIdx) {
                                            val nextIdx = iterIdx + direction
                                            java.util.Collections.swap(reordered, iterIdx, nextIdx)
                                            iterIdx = nextIdx
                                        }
                                        val slotsMoved = targetIdx - currentIdx
                                        val itemHeightPx = effectiveHeight
                                        dragOffsetY -= slotsMoved * itemHeightPx
                                        draggedIndex = targetIdx
                                        currentHoverIndex = targetIdx
                                        val updated = reordered.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
                                        localQuickNotes = updated + otherNotes
                                        HapticHelper.tick(context)
                                    }
                                },
                                onDragEnd = onDragEnd@ {
                                    if (isNoteSettling) return@onDragEnd
                                    val startIdx = draggedIndex
                                    if (startIdx != null) {
                                        val effectiveHeight = if (measuredNoteItemHeightPx > 0f) measuredNoteItemHeightPx else itemHeightPx
                                        val targetSettleY = calculateSettleTargetOffset(startIdx, startIdx, effectiveHeight)
                                        isNoteSettling = true
                                        noteSettleJob = scope.launch {
                                            animateDragToSettle(
                                                currentOffset = dragOffsetY,
                                                targetOffset = targetSettleY,
                                                springSpec = getReorderSpringSpec(context)
                                            ) { animatedY ->
                                                dragOffsetY = animatedY
                                            }
                                            withContext(Dispatchers.IO) {
                                                repository.reorderQuickNotes(localQuickNotes.filter { it.isPinned })
                                            }
                                            HapticHelper.success(context)
                                            draggedNoteId = null
                                            draggedIndex = null
                                            currentHoverIndex = null
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                            isNoteSettling = false
                                        }
                                    } else {
                                        draggedNoteId = null
                                        draggedIndex = null
                                        currentHoverIndex = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                    }
                                },
                                onDragCancel = onDragCancel@ {
                                    if (isNoteSettling) return@onDragCancel
                                    val startIdx = draggedIndex
                                    if (startIdx != null) {
                                        isNoteSettling = true
                                        noteSettleJob = scope.launch {
                                            animateDragToSettle(
                                                currentOffset = dragOffsetY,
                                                targetOffset = 0f,
                                                springSpec = getReorderSpringSpec(context)
                                            ) { animatedY ->
                                                dragOffsetY = animatedY
                                            }
                                            withContext(Dispatchers.IO) {
                                                repository.reorderQuickNotes(pinnedNotes)
                                            }
                                            draggedNoteId = null
                                            draggedIndex = null
                                            currentHoverIndex = null
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                            isNoteSettling = false
                                        }
                                    } else {
                                        draggedNoteId = null
                                        draggedIndex = null
                                        currentHoverIndex = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                    }
                                }
                            )
                        }

                        if (otherNotes.isNotEmpty()) {
                            item(key = "header_others") {
                                NotesSectionHeader(title = "Others")
                            }
                            items(otherNotes, key = { it.id }) { note ->
                                NoteCardContainer(
                                    note = note,
                                    sectionNotes = otherNotes,
                                    draggedNoteId = draggedNoteId,
                                    draggedIndex = draggedIndex,
                                    dragOffsetX = dragOffsetX,
                                    dragOffsetY = dragOffsetY,
                                    itemHeightPx = itemHeightPx,
                                    isExiting = note.id in exitingNoteIds,
                                    is24Hour = userSettings.timeFormat24Hour,
                                    onSwipeStart = { isAnyCardSwiping = true },
                                    onSwipeEnd = { isAnyCardSwiping = false },
                                    onTogglePin = { togglePin(note) },
                                    onClick = { editingNote = note },
                                    onEdit = { editingNote = note },
                                    onDelete = {
                                        exitingNoteIds = exitingNoteIds + note.id
                                        scope.launch {
                                            delay(200)
                                            localQuickNotes = localQuickNotes.filter { it.id != note.id }
                                            exitingNoteIds = exitingNoteIds - note.id
                                            withContext(Dispatchers.IO) {
                                                repository.deleteQuickNote(note.id)
                                            }
                                        }
                                    },
                                    onToggleChecklist = { updatedContent ->
                                        scope.launch {
                                            repository.updateQuickNote(note.copy(content = updatedContent))
                                        }
                                    },
                                    onDragStart = { offset, cardBoundsInRoot ->
                                        noteSettleJob?.cancel()
                                        isNoteSettling = false
                                        triggerPickupHaptic(context, hapticFeedback)
                                        measuredNoteItemHeightPx = cardBoundsInRoot.height + with(density) { 12.dp.toPx() }
                                        val index = otherNotes.indexOfFirst { it.id == note.id }
                                        draggedNoteId = note.id
                                        draggedIndex = index
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        draggedInitialPointerInRoot = cardBoundsInRoot.topLeft + offset
                                        currentHoverIndex = index
                                    },
                                    onDragDelta = onDragDelta@ { dx, dy ->
                                        if (isNoteSettling) return@onDragDelta
                                        dragOffsetX += dx
                                        dragOffsetY += dy
                                        val currentIdx = draggedIndex ?: otherNotes.indexOfFirst { it.id == note.id }
                                        val effectiveHeight = if (measuredNoteItemHeightPx > 0f) measuredNoteItemHeightPx else itemHeightPx
                                        val targetIdx = calculateSlotTargetIndex(currentIdx, dragOffsetY, effectiveHeight, otherNotes.size)
                                        if (targetIdx != currentIdx && targetIdx in otherNotes.indices) {
                                            val reordered = otherNotes.toMutableList()
                                            val direction = if (targetIdx > currentIdx) 1 else -1
                                            var iterIdx = currentIdx
                                            while (iterIdx != targetIdx) {
                                                val nextIdx = iterIdx + direction
                                                java.util.Collections.swap(reordered, iterIdx, nextIdx)
                                                iterIdx = nextIdx
                                            }
                                            val slotsMoved = targetIdx - currentIdx
                                            val itemHeightPx = effectiveHeight
                                            dragOffsetY -= slotsMoved * itemHeightPx
                                            draggedIndex = targetIdx
                                            currentHoverIndex = targetIdx
                                            val updated = reordered.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
                                            localQuickNotes = pinnedNotes + updated
                                            HapticHelper.tick(context)
                                        }
                                    },
                                    onDragEnd = onDragEnd@ {
                                        if (isNoteSettling) return@onDragEnd
                                        val startIdx = draggedIndex
                                        if (startIdx != null) {
                                            val effectiveHeight = if (measuredNoteItemHeightPx > 0f) measuredNoteItemHeightPx else itemHeightPx
                                            val targetSettleY = calculateSettleTargetOffset(startIdx, startIdx, effectiveHeight)
                                            isNoteSettling = true
                                            noteSettleJob = scope.launch {
                                                animateDragToSettle(
                                                    currentOffset = dragOffsetY,
                                                    targetOffset = targetSettleY,
                                                    springSpec = getReorderSpringSpec(context)
                                                ) { animatedY ->
                                                    dragOffsetY = animatedY
                                                }
                                                withContext(Dispatchers.IO) {
                                                    repository.reorderQuickNotes(localQuickNotes.filter { !it.isPinned })
                                                }
                                                HapticHelper.success(context)
                                                draggedNoteId = null
                                                draggedIndex = null
                                                currentHoverIndex = null
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                draggedInitialPointerInRoot = null
                                                isNoteSettling = false
                                            }
                                        } else {
                                            draggedNoteId = null
                                            draggedIndex = null
                                            currentHoverIndex = null
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                        }
                                    },
                                    onDragCancel = onDragCancel@ {
                                        if (isNoteSettling) return@onDragCancel
                                        val startIdx = draggedIndex
                                        if (startIdx != null) {
                                            isNoteSettling = true
                                            noteSettleJob = scope.launch {
                                                animateDragToSettle(
                                                    currentOffset = dragOffsetY,
                                                    targetOffset = 0f,
                                                    springSpec = getReorderSpringSpec(context)
                                                ) { animatedY ->
                                                    dragOffsetY = animatedY
                                                }
                                                withContext(Dispatchers.IO) {
                                                    repository.reorderQuickNotes(otherNotes)
                                                }
                                                draggedNoteId = null
                                                draggedIndex = null
                                                currentHoverIndex = null
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                draggedInitialPointerInRoot = null
                                                isNoteSettling = false
                                            }
                                        } else {
                                            draggedNoteId = null
                                            draggedIndex = null
                                            currentHoverIndex = null
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item(key = "notes_list_bottom_spacer") {
                        Spacer(modifier = Modifier.height(90.dp))
                    }
                }
            }
        }

        // 3. Apple Notes-style Floating Action Button (+) at bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
                .size(54.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, spotColor = BrandAmberStart)
                .clip(CircleShape)
                .background(BrandAmberStart)
                .clickable {
                    HapticHelper.click(context)
                    editingNote = QuickNote(
                        title = "",
                        content = "",
                        noteType = "NOTE",
                        isChecklist = false,
                        isPinned = false
                    )
                    isCreatingNewNote = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Note",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // 4. Apple-Grade Rich Text Editor Modal / Sheet
        if (editingNote != null) {
            AppleRichTextEditorModal(
                note = editingNote!!,
                isNew = isCreatingNewNote,
                onDismiss = {
                    editingNote = null
                    isCreatingNewNote = false
                },
                onSave = { updatedNote ->
                    scope.launch {
                        if (isCreatingNewNote) {
                            repository.addQuickNote(
                                title = updatedNote.title.ifBlank { "Untitled Note" },
                                content = updatedNote.content,
                                noteType = updatedNote.noteType,
                                isPinned = updatedNote.isPinned,
                                isChecklist = updatedNote.isChecklist,
                                fontSize = updatedNote.fontSize,
                                textColor = updatedNote.textColor,
                                alignment = updatedNote.alignment
                            )
                        } else {
                            repository.updateQuickNote(updatedNote)
                        }
                        editingNote = null
                        isCreatingNewNote = false
                    }
                },
                onDelete = { noteId ->
                    localQuickNotes = localQuickNotes.filter { it.id != noteId }
                    scope.launch {
                        repository.deleteQuickNote(noteId)
                        editingNote = null
                        isCreatingNewNote = false
                    }
                }
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.NoteCardContainer(
    note: QuickNote,
    sectionNotes: List<QuickNote>,
    draggedNoteId: String?,
    draggedIndex: Int?,
    dragOffsetX: Float,
    dragOffsetY: Float,
    itemHeightPx: Float,
    isExiting: Boolean,
    is24Hour: Boolean,
    onSwipeStart: () -> Unit,
    onSwipeEnd: () -> Unit,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleChecklist: (String) -> Unit,
    onDragStart: (Offset, Rect) -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val isThisItemDragged = draggedNoteId == note.id

    AnimatedVisibility(
        visible = !isExiting,
        modifier = Modifier
            .zIndex(if (isThisItemDragged) 100f else 1f)
            .then(
                if (!isThisItemDragged) {
                    Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                } else Modifier
            ),
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)),
        exit = shrinkVertically(
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioNoBouncy
            )
        ) + fadeOut(tween(160))
    ) {
        NoteSwipeableCardItem(
            modifier = Modifier.fillMaxWidth(),
            note = note,
            is24Hour = is24Hour,
            isThisItemDragged = isThisItemDragged,
            activeDragColor = BrandAmberStart,
            dragOffsetX = dragOffsetX,
            dragOffsetY = dragOffsetY,
            nonDraggedDisplacement = 0f,
            onSwipeStart = onSwipeStart,
            onSwipeEnd = onSwipeEnd,
            onTogglePin = onTogglePin,
            onClick = onClick,
            onEdit = onEdit,
            onDelete = onDelete,
            onToggleChecklistItem = onToggleChecklist,
            onDragStart = onDragStart,
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel
        )
    }
}

/**
 * Apple-Grade Note Card with custom swipe gestures & drag-and-drop reordering:
 * - Delete Action (Right-to-Left): reveals red background with Trash icon, triggers shake animation, slides out and immediately deletes note.
 * - Edit Action (Left-to-Right): reveals blue background with Edit icon, springs back and directly opens edit mode.
 * - Touch-and-Hold Drag & Drop: lifts item with fluid elevation, reorders in list and transfers across tabs.
 */
@Composable
private fun NoteSwipeableCardItem(
    note: QuickNote,
    is24Hour: Boolean = false,
    isThisItemDragged: Boolean,
    activeDragColor: Color? = null,
    dragOffsetX: Float,
    dragOffsetY: Float,
    nonDraggedDisplacement: Float,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onDragStart: (Offset, Rect) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onSwipeStart: () -> Unit = {},
    onSwipeEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipePx = with(density) { 140.dp.toPx() }

    var cardBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val swipeOffsetX = remember(note.id) { Animatable(0f) }
    val shakeRotation = remember(note.id) { Animatable(0f) }
    var activeSwipeAction by remember(note.id) { mutableStateOf(NoteSwipeAction.NONE) }
    var hasTriggeredHaptic by remember(note.id) { mutableStateOf(false) }

    val springSpec = remember(context) { getReorderSpringSpec<Float>(context) }

    // Native fluid physics spring animations for pin interactions
    val pinScale by animateFloatAsState(
        targetValue = if (note.isPinned) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pinScale"
    )
    val pinRotation by animateFloatAsState(
        targetValue = if (note.isPinned) -25f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pinRotation"
    )

    // Fluid drag feedback: scales up smoothly to 1.03f with elevated drop shadow when picked up
    val dragScale by animateFloatAsState(
        targetValue = if (isThisItemDragged) 1.03f else 1.0f,
        animationSpec = springSpec,
        label = "dragScale"
    )

    val dragElevation by animateFloatAsState(
        targetValue = if (isThisItemDragged) with(density) { 16.dp.toPx() } else 0f,
        animationSpec = springSpec,
        label = "dragElevation"
    )

    val dragAlpha by animateFloatAsState(
        targetValue = if (isThisItemDragged) 0.95f else 1.0f,
        animationSpec = springSpec,
        label = "dragAlpha"
    )

    var isDraggingCard by remember(note.id) { mutableStateOf(false) }
    var isSwipingHorizontal by remember(note.id) { mutableStateOf(false) }
    var lastDragEndTime by remember(note.id) { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isThisItemDragged) 100f else 1f)
            .onGloballyPositioned { coords ->
                cardBoundsInRoot = coords.boundsInRoot()
            }
            .graphicsLayer {
                translationX = if (isThisItemDragged) dragOffsetX else 0f
                translationY = if (isThisItemDragged) dragOffsetY else nonDraggedDisplacement
                scaleX = dragScale
                scaleY = dragScale
                alpha = dragAlpha
                shadowElevation = dragElevation
                shape = RoundedCornerShape(20.dp)
                clip = false
            }
    ) {
        // Swipe Background Action Indicators Layer
        if (activeSwipeAction != NoteSwipeAction.NONE && abs(swipeOffsetX.value) > 1f) {
            val isEdit = activeSwipeAction == NoteSwipeAction.EDIT
            val actionBgColor = if (isEdit) Color(0xFF2563EB) else StatusUrgent
            val actionProgress = (abs(swipeOffsetX.value) / thresholdPx).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(actionBgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isEdit) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.5f + 0.5f * actionProgress
                        scaleX = 0.85f + 0.15f * actionProgress
                        scaleY = 0.85f + 0.15f * actionProgress
                    }
                ) {
                    if (isEdit) {
                        PencilEditIcon(
                            color = Color.White,
                            size = 18.dp
                        )
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        val currentOnDragStart by rememberUpdatedState(onDragStart)
        val currentOnDragEnd by rememberUpdatedState(onDragEnd)
        val currentOnDragCancel by rememberUpdatedState(onDragCancel)
        val currentOnDragDelta by rememberUpdatedState(onDragDelta)

        // Foreground Card Surface (with full-surface tap-and-hold drag & drop)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = if (isThisItemDragged) 0f else swipeOffsetX.value
                    rotationZ = shakeRotation.value
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            isDraggingCard = true
                            triggerPickupHaptic(context, hapticFeedback)
                            currentOnDragStart(offset, cardBoundsInRoot)
                        },
                        onDragEnd = {
                            isDraggingCard = false
                            lastDragEndTime = System.currentTimeMillis()
                            currentOnDragEnd()
                        },
                        onDragCancel = {
                            isDraggingCard = false
                            lastDragEndTime = System.currentTimeMillis()
                            currentOnDragCancel()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOnDragDelta(dragAmount.x, dragAmount.y)
                        }
                    )
                }
                .pointerInput(note.id, isThisItemDragged) {
                    if (isThisItemDragged) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!isDraggingCard) {
                                isSwipingHorizontal = true
                                hasTriggeredHaptic = false
                                activeSwipeAction = NoteSwipeAction.NONE
                                onSwipeStart()
                            }
                        },
                        onDragEnd = {
                            if (isDraggingCard) return@detectHorizontalDragGestures
                            onSwipeEnd()
                            coroutineScope.launch {
                                val currentAction = activeSwipeAction
                                val offset = swipeOffsetX.value
                                when (currentAction) {
                                    NoteSwipeAction.EDIT -> {
                                        if (offset >= thresholdPx) {
                                            HapticHelper.click(context)
                                            swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                            onEdit()
                                        } else {
                                            swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                        }
                                    }
                                    NoteSwipeAction.DELETE -> {
                                        if (offset <= -thresholdPx) {
                                            HapticHelper.heavy(context)
                                            // Designated tactile shake feedback animation
                                            coroutineScope.launch {
                                                shakeRotation.animateTo(-2f, tween(35))
                                                shakeRotation.animateTo(1.8f, tween(35))
                                                shakeRotation.animateTo(-1.2f, tween(35))
                                                shakeRotation.animateTo(0f, tween(35))
                                            }
                                            val base = swipeOffsetX.value
                                            swipeOffsetX.animateTo(base - 18f, tween(35))
                                            swipeOffsetX.animateTo(base + 14f, tween(35))
                                            swipeOffsetX.animateTo(base - 10f, tween(35))
                                            swipeOffsetX.animateTo(base + 6f, tween(35))
                                            swipeOffsetX.animateTo(-1200f, tween(160))
                                            onDelete()
                                        } else {
                                            swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                        }
                                    }
                                    NoteSwipeAction.NONE -> {
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                                activeSwipeAction = NoteSwipeAction.NONE
                                isSwipingHorizontal = false
                                hasTriggeredHaptic = false
                            }
                        },
                        onDragCancel = {
                            if (isDraggingCard) return@detectHorizontalDragGestures
                            onSwipeEnd()
                            coroutineScope.launch {
                                swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                activeSwipeAction = NoteSwipeAction.NONE
                                isSwipingHorizontal = false
                                hasTriggeredHaptic = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isDraggingCard || isThisItemDragged) return@detectHorizontalDragGestures

                            // Universal Gestures: Left-to-Right = EDIT (> 10px), Right-to-Left = DELETE (< -10px)
                            if (activeSwipeAction == NoteSwipeAction.NONE) {
                                val testDelta = swipeOffsetX.value + dragAmount
                                if (testDelta > 10f) {
                                    activeSwipeAction = NoteSwipeAction.EDIT
                                } else if (testDelta < -10f) {
                                    activeSwipeAction = NoteSwipeAction.DELETE
                                } else {
                                    return@detectHorizontalDragGestures
                                }
                            }

                            change.consume()

                            val rawNext = swipeOffsetX.value + dragAmount
                            val clamped = when (activeSwipeAction) {
                                NoteSwipeAction.EDIT -> {
                                    if (rawNext < 0f) 0f
                                    else if (rawNext <= maxSwipePx) rawNext
                                    else maxSwipePx + (rawNext - maxSwipePx) * 0.25f
                                }
                                NoteSwipeAction.DELETE -> {
                                    if (rawNext > 0f) 0f
                                    else if (rawNext >= -maxSwipePx) rawNext
                                    else -maxSwipePx + (rawNext - (-maxSwipePx)) * 0.25f
                                }
                                NoteSwipeAction.NONE -> 0f
                            }

                            coroutineScope.launch {
                                swipeOffsetX.snapTo(clamped)
                            }

                            val crossed = when (activeSwipeAction) {
                                NoteSwipeAction.EDIT -> clamped >= thresholdPx
                                NoteSwipeAction.DELETE -> clamped <= -thresholdPx
                                NoteSwipeAction.NONE -> false
                            }
                            if (crossed && !hasTriggeredHaptic) {
                                hasTriggeredHaptic = true
                                HapticHelper.selection(context)
                            } else if (!crossed && hasTriggeredHaptic) {
                                hasTriggeredHaptic = false
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = if (isThisItemDragged) {
                BorderStroke(1.5.dp, (activeDragColor ?: BrandAmberStart).copy(alpha = 0.85f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            },
            shadowElevation = if (isThisItemDragged) 8.dp else 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val isCurrentlyDragged = isThisItemDragged
                        val timeSinceDrag = System.currentTimeMillis() - lastDragEndTime
                        if (!isCurrentlyDragged && System.currentTimeMillis() - lastDragEndTime > 300L) {
                            if (!isDraggingCard && !isSwipingHorizontal && !isThisItemDragged && timeSinceDrag > 300L && abs(swipeOffsetX.value) < 5f) {
                                HapticHelper.click(context)
                                onClick()
                            }
                        }
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        MorphingLeadingActionIcon(
                            isDragging = isThisItemDragged,
                            dragHandleColor = BrandAmberStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReorderGrabHandleIcon(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    size = 14.dp
                                )
                                if (note.isPinned) {
                                    PinIcon(
                                        color = BrandAmberStart,
                                        isPinned = true,
                                        size = 14.dp,
                                        modifier = Modifier.graphicsLayer {
                                            rotationZ = pinRotation
                                            scaleX = pinScale
                                            scaleY = pinScale
                                        }
                                    )
                                }
                            }
                        }
                        val noteHeadline = HeadlineScaler.clamp(note.title.ifBlank { "Untitled Note" })
                        Text(
                            text = noteHeadline,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = calculateDynamicHeadlineSize(noteHeadline, baseSize = 17.sp, minSize = 12.sp)
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            HapticHelper.click(context)
                            onTogglePin()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = pinScale
                                scaleY = pinScale
                                rotationZ = pinRotation
                            }
                    ) {
                        PinIcon(
                            color = if (note.isPinned) BrandAmberStart else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            isPinned = note.isPinned,
                            size = 15.dp
                        )
                    }
                }

                // Checklist or Preview Body
                if (note.isChecklist) {
                    val lines = note.content.lines().filter { it.isNotBlank() }
                    var draggedChecklistIdx by remember { mutableStateOf<Int?>(null) }
                    var dragChecklistOffsetY by remember { mutableFloatStateOf(0f) }
                    var lastChecklistDragEndTime by remember { mutableLongStateOf(0L) }
                    var currentHoverChecklistIdx by remember { mutableStateOf<Int?>(null) }
                    val checklistItemHeightDp = 28.dp
                    val checklistItemHeightPx = with(LocalDensity.current) { checklistItemHeightDp.toPx() }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lines.take(6).forEachIndexed { index, line ->
                            val isChecked = line.startsWith("[x] ", ignoreCase = true)
                            val text = line.removePrefix("[x] ").removePrefix("[ ] ")
                            val isThisChecklistDragged = draggedChecklistIdx == index

                            val nonDraggedDisplacement by animateFloatAsState(
                                targetValue = if (draggedChecklistIdx != null && !isThisChecklistDragged && currentHoverChecklistIdx != null) {
                                    val startIdx = draggedChecklistIdx!!
                                    val targetIdx = currentHoverChecklistIdx!!
                                    if (targetIdx > startIdx && index in (startIdx + 1)..targetIdx) {
                                        -checklistItemHeightPx
                                    } else if (targetIdx < startIdx && index in targetIdx until startIdx) {
                                        checklistItemHeightPx
                                    } else {
                                        0f
                                    }
                                } else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "checklistDisplacement"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isThisChecklistDragged) 10f else 1f)
                                    .graphicsLayer {
                                        translationY = if (isThisChecklistDragged) dragChecklistOffsetY else nonDraggedDisplacement
                                        scaleX = if (isThisChecklistDragged) 0.96f else 1f
                                        scaleY = if (isThisChecklistDragged) 0.96f else 1f
                                        shadowElevation = if (isThisChecklistDragged) 6.dp.toPx() else 0f
                                    }
                                    .pointerInput(lines, index) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                HapticHelper.heavy(context)
                                                draggedChecklistIdx = index
                                                currentHoverChecklistIdx = index
                                                dragChecklistOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragChecklistOffsetY += dragAmount.y
                                                val startIdx = draggedChecklistIdx ?: index
                                                val computedTarget = (startIdx + (dragChecklistOffsetY / checklistItemHeightPx).roundToInt()).coerceIn(0, lines.take(6).lastIndex)
                                                if (computedTarget != currentHoverChecklistIdx) {
                                                    HapticHelper.selection(context)
                                                    currentHoverChecklistIdx = computedTarget
                                                }
                                            },
                                            onDragEnd = {
                                                val startIdx = draggedChecklistIdx
                                                val targetIdx = currentHoverChecklistIdx
                                                if (startIdx != null && targetIdx != null && startIdx != targetIdx) {
                                                    val reordered = lines.toMutableList()
                                                    val moved = reordered.removeAt(startIdx)
                                                    reordered.add(targetIdx, moved)
                                                    onToggleChecklistItem(reordered.joinToString("\n"))
                                                    HapticHelper.success(context)
                                                }
                                                draggedChecklistIdx = null
                                                currentHoverChecklistIdx = null
                                                dragChecklistOffsetY = 0f
                                                lastChecklistDragEndTime = System.currentTimeMillis()
                                            },
                                            onDragCancel = {
                                                draggedChecklistIdx = null
                                                currentHoverChecklistIdx = null
                                                dragChecklistOffsetY = 0f
                                                lastChecklistDragEndTime = System.currentTimeMillis()
                                            }
                                        )
                                    }
                                    .clickable {
                                        val isCurrentlyDragged = draggedChecklistIdx != null
                                        if (!isCurrentlyDragged && System.currentTimeMillis() - lastChecklistDragEndTime > 300L) {
                                            HapticHelper.click(context)
                                            val newLines = lines.toMutableList()
                                            newLines[index] = if (isChecked) "[ ] $text" else "[x] $text"
                                            onToggleChecklistItem(newLines.joinToString("\n"))
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isChecked) BreakGreen else Color.Transparent)
                                        .border(
                                            width = 1.2.dp,
                                            color = if (isChecked) BreakGreen else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "⋮⋮",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Apple-Grade Rich Text Editor Modal with Undo/Redo, Hierarchy Selector,
 * Typographic Formatting, 16 Font-Size Stepper, Alignment, and Color Palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleRichTextEditorModal(
    note: QuickNote,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (QuickNote) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isDismissing by remember { mutableStateOf(false) }

    fun safeDismiss(action: () -> Unit) {
        if (!isDismissing) {
            isDismissing = true
            scope.launch {
                sheetState.smoothDismiss()
                action()
            }
        }
    }

    val initialFullText = remember {
        val clampedTitle = HeadlineScaler.clamp(note.title)
        if (clampedTitle.isNotBlank()) {
            if (note.content.isNotBlank()) "$clampedTitle\n${note.content}"
            else clampedTitle
        } else {
            note.content
        }
    }
    var fullText by remember { mutableStateOf(initialFullText) }
    var noteType by remember { mutableStateOf(note.noteType) }
    var isPinned by remember { mutableStateOf(note.isPinned) }
    var isChecklist by remember { mutableStateOf(note.isChecklist) }

    // Contextual Styling Bar State
    var showFormattingToolbar by remember { mutableStateOf(false) }
    var selectedHierarchy by remember { mutableStateOf("Body") } // Title, Subtitle, Heading, Body, Note
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }
    var isStrikethrough by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableStateOf(note.fontSize.coerceIn(12, 40)) }
    var alignment by remember { mutableStateOf(note.alignment) } // LEFT, CENTER, RIGHT, JUSTIFIED
    var selectedColor by remember { mutableStateOf(note.textColor) } // DEFAULT, GREY, RED, ORANGE, YELLOW, GREEN, BLUE

    // Undo / Redo History Stack
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    fun updateContentWithUndo(newText: String) {
        val clamped = HeadlineScaler.clampFirstLine(newText)
        if (clamped != fullText) {
            undoStack.add(fullText)
            redoStack.clear()
            fullText = clamped
        }
    }

    ModalBottomSheet(
        onDismissRequest = { safeDismiss(onDismiss) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // 1. Top Header Bar Actions (Close, Undo, Redo, Pin, Delete, Done)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Close Button
                IconButton(onClick = { safeDismiss(onDismiss) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Center: Undo, Redo, Pin, Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val canUndo = undoStack.isNotEmpty()
                    IconButton(
                        onClick = {
                            if (canUndo) {
                                HapticHelper.click(context)
                                val previous = undoStack.removeLast()
                                redoStack.add(fullText)
                                fullText = previous
                            }
                        },
                        enabled = canUndo,
                        modifier = Modifier.size(36.dp)
                    ) {
                        UndoIcon(
                            color = MaterialTheme.colorScheme.onSurface,
                            enabled = canUndo,
                            size = 20.dp
                        )
                    }

                    val canRedo = redoStack.isNotEmpty()
                    IconButton(
                        onClick = {
                            if (canRedo) {
                                HapticHelper.click(context)
                                val next = redoStack.removeLast()
                                undoStack.add(fullText)
                                fullText = next
                            }
                        },
                        enabled = canRedo,
                        modifier = Modifier.size(36.dp)
                    ) {
                        RedoIcon(
                            color = MaterialTheme.colorScheme.onSurface,
                            enabled = canRedo,
                            size = 20.dp
                        )
                    }
                }

                // Right: Done / Save CTA Pill Button
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(BrandAmberStart)
                        .clickable(enabled = !isDismissing) {
                            if (!isDismissing) {
                                HapticHelper.success(context)
                                val lines = fullText.lines()
                                val derivedTitle = HeadlineScaler.clamp(lines.firstOrNull()?.trim() ?: "")
                                val derivedContent = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
                                val updated = note.copy(
                                    title = derivedTitle.ifBlank { "Untitled Note" },
                                    content = derivedContent,
                                    noteType = noteType,
                                    isPinned = isPinned,
                                    isChecklist = isChecklist,
                                    fontSize = fontSizeSp,
                                    textColor = selectedColor,
                                    alignment = alignment
                                )
                                safeDismiss { onSave(updated) }
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Unified Input Area: First Line Automatically Functions as Title
            val resolvedTextColor = when (selectedColor.uppercase(Locale.ROOT)) {
                "GREY" -> Color(0xFF8E8E93)
                "RED" -> Color(0xFFFF3B30)
                "ORANGE" -> Color(0xFFFF9500)
                "YELLOW" -> Color(0xFFFFCC00)
                "GREEN" -> Color(0xFF34C759)
                "BLUE" -> Color(0xFF007AFF)
                else -> MaterialTheme.colorScheme.onSurface
            }
            val resolvedTextAlign = when (alignment.uppercase(Locale.ROOT)) {
                "CENTER" -> TextAlign.Center
                "RIGHT" -> TextAlign.Right
                "JUSTIFIED" -> TextAlign.Justify
                else -> TextAlign.Left
            }

            OutlinedTextField(
                value = fullText,
                onValueChange = { updateContentWithUndo(it) },
                placeholder = {
                    Text(
                        text = "Title",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = fontSizeSp.sp
                    )
                },
                textStyle = TextStyle(
                    fontSize = fontSizeSp.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = when {
                        isUnderline && isStrikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        isUnderline -> TextDecoration.Underline
                        isStrikethrough -> TextDecoration.LineThrough
                        else -> TextDecoration.None
                    },
                    textAlign = resolvedTextAlign,
                    color = resolvedTextColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Contextual Formatting Toolbar (Appears above the AA button when tapped)
            AnimatedVisibility(
                visible = showFormattingToolbar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row A: Hierarchy Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Title" to 24, "Subtitle" to 20, "Heading" to 18, "Body" to 16, "Note" to 13).forEach { (name, size) ->
                            val isSel = selectedHierarchy == name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) BrandAmberStart else Color.Transparent)
                                    .clickable {
                                        selectedHierarchy = name
                                        fontSizeSp = size
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Row B: Typographic Formatting & Font-Size Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FormatToggleBtn(label = "B", isSelected = isBold, onClick = { isBold = !isBold })
                            FormatToggleBtn(label = "I", isSelected = isItalic, onClick = { isItalic = !isItalic })
                            FormatToggleBtn(label = "U", isSelected = isUnderline, onClick = { isUnderline = !isUnderline })
                            FormatToggleBtn(label = "S", isSelected = isStrikethrough, onClick = { isStrikethrough = !isStrikethrough })
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FormatToggleBtn(label = "•", isSelected = false, onClick = {
                                updateContentWithUndo(if (fullText.isEmpty()) "• " else "$fullText\n• ")
                            })
                            FormatToggleBtn(label = "1.", isSelected = false, onClick = {
                                updateContentWithUndo(if (fullText.isEmpty()) "1. " else "$fullText\n1. ")
                            })
                            FormatToggleBtn(label = "☑", isSelected = isChecklist, onClick = {
                                isChecklist = !isChecklist
                                if (isChecklist) {
                                    updateContentWithUndo(if (fullText.isEmpty()) "[ ] " else "$fullText\n[ ] ")
                                }
                            })
                        }

                        // Font size stepper
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (fontSizeSp > 12) fontSizeSp -= 2
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "${fontSizeSp}pt",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (fontSizeSp < 40) fontSizeSp += 2
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Row C: Alignment & Native Color Palette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("LEFT" to "L", "CENTER" to "C", "RIGHT" to "R", "JUSTIFIED" to "J").forEach { (alignKey, label) ->
                                val isSel = alignment.equals(alignKey, true)
                                FormatToggleBtn(
                                    label = label,
                                    isSelected = isSel,
                                    onClick = { alignment = alignKey }
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val palette = listOf(
                                "DEFAULT" to MaterialTheme.colorScheme.onSurface,
                                "GREY" to Color(0xFF8E8E93),
                                "RED" to Color(0xFFFF3B30),
                                "ORANGE" to Color(0xFFFF9500),
                                "YELLOW" to Color(0xFFFFCC00),
                                "GREEN" to Color(0xFF34C759),
                                "BLUE" to Color(0xFF007AFF)
                            )
                            palette.forEach { (colorKey, colorVal) ->
                                val isColorSel = selectedColor.equals(colorKey, true)
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colorVal)
                                        .border(
                                            width = if (isColorSel) 2.dp else 0.dp,
                                            color = if (isColorSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = colorKey }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Bottom Accessory Bar with Contextual Styling 'AA' Button positioned at bottom right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dedicated Styling Button 'AA' matching FAB yellow/amber accent color positioned specifically at the bottom right
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (showFormattingToolbar) BrandAmberStart else BrandAmberStart.copy(alpha = 0.18f))
                        .clickable {
                            HapticHelper.click(context)
                            showFormattingToolbar = !showFormattingToolbar
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showFormattingToolbar) Color.White else BrandAmberStart
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FormatToggleBtn(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) BrandAmberStart else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
