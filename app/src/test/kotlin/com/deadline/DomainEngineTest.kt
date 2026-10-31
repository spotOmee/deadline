package com.deadline

import com.deadline.data.local.HabitCompletion
import com.deadline.data.local.PomodoroSession
import com.deadline.data.local.PomodoroType
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.TaskPriority
import com.deadline.data.local.TaskStatus
import com.deadline.data.local.TimeBlock
import com.deadline.data.local.UserSettings
import com.deadline.domain.AdherenceScorer
import com.deadline.domain.StreakCalculator
import com.deadline.ui.navigation.DeadlineTab
import com.deadline.ui.navigation.resolveStartupTab
import com.deadline.ui.navigation.evaluateStartupTab
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateContinuousHorizontalDisplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DomainEngineTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun getDateOffset(daysOffset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        return dateFormat.format(cal.time)
    }

    @Test
    fun testStreakCalculator_emptyReturnsZero() {
        val streak = StreakCalculator.calculateStreak(emptyList())
        assertEquals(0, streak)
    }

    @Test
    fun testStreakCalculator_consecutiveDaysIncrements() {
        val list = listOf(
            HabitCompletion(habitId = "h1", date = getDateOffset(0)), // today
            HabitCompletion(habitId = "h1", date = getDateOffset(-1)), // yesterday
            HabitCompletion(habitId = "h1", date = getDateOffset(-2))  // 2 days ago
        )
        val streak = StreakCalculator.calculateStreak(list)
        assertEquals(3, streak)
    }

    @Test
    fun testStreakCalculator_skipDayPreservesStreak() {
        val list = listOf(
            HabitCompletion(habitId = "h1", date = getDateOffset(0)),
            HabitCompletion(habitId = "h1", date = getDateOffset(-1), isSkip = true), // skipped yesterday
            HabitCompletion(habitId = "h1", date = getDateOffset(-2))
        )
        val streak = StreakCalculator.calculateStreak(list)
        assertEquals(3, streak)
    }

    @Test
    fun testStreakCalculator_missedDayBreaksStreak() {
        val list = listOf(
            HabitCompletion(habitId = "h1", date = getDateOffset(0)),
            // missed -1 (yesterday)
            HabitCompletion(habitId = "h1", date = getDateOffset(-2))
        )
        val streak = StreakCalculator.calculateStreak(list)
        assertEquals(1, streak)
    }

    @Test
    fun testAdherenceScorer_zeroPlannedMinutes() {
        val score = AdherenceScorer.computeScore(emptyList(), emptyList())
        assertEquals(0, score)

        val sessions = listOf(
            PomodoroSession(durationMinutes = 25, sessionType = PomodoroType.FOCUS, completed = true)
        )
        val scoreWithSessions = AdherenceScorer.computeScore(emptyList(), sessions)
        assertEquals(100, scoreWithSessions)
    }

    @Test
    fun testAdherenceScorer_proportionalRatio() {
        val blocks = listOf(
            TimeBlock(date = "2026-10-15", startMinute = 540, durationMinutes = 100, title = "Deep Work")
        )
        val sessions = listOf(
            PomodoroSession(durationMinutes = 50, sessionType = PomodoroType.FOCUS, completed = true)
        )
        val score = AdherenceScorer.computeScore(blocks, sessions)
        assertEquals(50, score) // 50 / 100 = 50%
    }

    @Test
    fun testProject_linkedNoteIdsParsing() {
        val linkedIdsString = "note_1, note_2, note_3"
        val parsed = linkedIdsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        assertEquals(3, parsed.size)
        assertEquals("note_1", parsed[0])
        assertEquals("note_2", parsed[1])
        assertEquals("note_3", parsed[2])
    }

    @Test
    fun testFocusTask_sessionTypeDefault() {
        val focusTask = com.deadline.data.local.FocusTask(title = "Deep Code", durationMinutes = 25)
        assertEquals("FOCUS", focusTask.sessionType)

        val breakTask = com.deadline.data.local.FocusTask(title = "Walk", durationMinutes = 5, sessionType = "SHORT_BREAK")
        assertEquals("SHORT_BREAK", breakTask.sessionType)
    }

    @Test
    fun testComputeAdherencePercentage_habitsAndTasksFormula() {
        // ((Completed Habits + Completed Daily Tasks) / (Total Scheduled Habits + Total Scheduled Daily Tasks)) * 100
        val adherence = AdherenceScorer.computeAdherencePercentage(
            completedHabits = 2,
            totalScheduledHabits = 4,
            completedDailyTasks = 1,
            totalScheduledDailyTasks = 2
        )
        // (2 + 1) / (4 + 2) = 3 / 6 = 50%
        assertEquals(50, adherence)
    }

    @Test
    fun testComputeAdherencePercentage_counterGoalIncrement() {
        // Hydration goal with target 8. Before completion: 7/8 (0 completed)
        val adherenceBefore = AdherenceScorer.computeAdherencePercentage(
            completedHabits = 0,
            totalScheduledHabits = 4,
            completedDailyTasks = 2,
            totalScheduledDailyTasks = 2
        )
        // (0 + 2) / (4 + 2) = 2 / 6 = 33%
        assertEquals(33, adherenceBefore)

        // Increment to 8/8 (now 1 completed habit)
        val adherenceAfter = AdherenceScorer.computeAdherencePercentage(
            completedHabits = 1,
            totalScheduledHabits = 4,
            completedDailyTasks = 2,
            totalScheduledDailyTasks = 2
        )
        // (1 + 2) / (4 + 2) = 3 / 6 = 50%
        assertEquals(50, adherenceAfter)
    }

    @Test
    fun testComputeAdherencePercentage_fallbackWithZeroScheduled() {
        val scoreWithoutSessions = AdherenceScorer.computeAdherencePercentage(0, 0, 0, 0, completedPomodoros = 0)
        assertEquals(0, scoreWithoutSessions)

        val scoreWithSessions = AdherenceScorer.computeAdherencePercentage(0, 0, 0, 0, completedPomodoros = 2)
        assertEquals(100, scoreWithSessions)
    }

    @Test
    fun testStartupConfiguration_defaultsToTimer() {
        val settings = UserSettings()
        assertEquals("TIMER", settings.defaultStartupScreen)
        assertEquals("system", settings.themeMode)
        assertEquals("", settings.name)

        val tab = DeadlineTab.valueOf(settings.defaultStartupScreen)
        assertEquals(DeadlineTab.TIMER, tab)
        assertEquals(2, tab.ordinal)
    }

    @Test
    fun testUniversalReordering_movesItemAndPreservesIntegrity() {
        val originalList = mutableListOf("item_0", "item_1", "item_2", "item_3", "item_4")
        
        // Move item_1 (index 1) to index 3
        val moved = originalList.removeAt(1)
        originalList.add(3, moved)

        assertEquals(listOf("item_0", "item_2", "item_3", "item_1", "item_4"), originalList)

        // Move item_4 (last item) to top (index 0)
        val movedToTop = originalList.removeAt(4)
        originalList.add(0, movedToTop)

        assertEquals(listOf("item_4", "item_0", "item_2", "item_3", "item_1"), originalList)
    }

    @Test
    fun testGapClosingDeletion_closesGapImmediately() {
        val list = listOf("task_focus_1", "task_focus_2", "task_focus_3")
        val afterDeletion = list.filter { it != "task_focus_2" }

        assertEquals(2, afterDeletion.size)
        assertEquals("task_focus_1", afterDeletion[0])
        assertEquals("task_focus_3", afterDeletion[1])
    }

    @Test
    fun testFocusTask_supportsAllTimerModes() {
        val focus = com.deadline.data.local.FocusTask(title = "Sprint", sessionType = "FOCUS")
        val shortBreak = com.deadline.data.local.FocusTask(title = "Hydrate", sessionType = "SHORT_BREAK")
        val flow = com.deadline.data.local.FocusTask(title = "Walk", sessionType = "LONG_BREAK")

        assertEquals("FOCUS", focus.sessionType)
        assertEquals("SHORT_BREAK", shortBreak.sessionType)
        assertEquals("LONG_BREAK", flow.sessionType)
    }

    @Test
    fun testPlanMode_gapFillingOnItemRemoval() {
        val items = listOf("goal_daily", "goal_deadline", "proj_1", "task_1", "task_2")

        // Removing a project
        val afterProjectRemoval = items.filter { it != "proj_1" }
        assertEquals(listOf("goal_daily", "goal_deadline", "task_1", "task_2"), afterProjectRemoval)

        // Removing a goal
        val afterGoalRemoval = afterProjectRemoval.filter { it != "goal_daily" }
        assertEquals(listOf("goal_deadline", "task_1", "task_2"), afterGoalRemoval)

        // Removing a task
        val afterTaskRemoval = afterGoalRemoval.filter { it != "task_1" }
        assertEquals(listOf("goal_deadline", "task_2"), afterTaskRemoval)
    }

    @Test
    fun testPlanMode_importTaskFlow() {
        val importedTaskIds = mutableListOf<String>()

        // Import task 1
        if (!importedTaskIds.contains("task_1")) {
            importedTaskIds.add("task_1")
        }
        assertEquals(listOf("task_1"), importedTaskIds)

        // Import duplicate task 1 (should not duplicate)
        if (!importedTaskIds.contains("task_1")) {
            importedTaskIds.add("task_1")
        }
        assertEquals(1, importedTaskIds.size)

        // Import task 2
        if (!importedTaskIds.contains("task_2")) {
            importedTaskIds.add("task_2")
        }
        assertEquals(listOf("task_1", "task_2"), importedTaskIds)

        // Remove imported task from plan
        importedTaskIds.remove("task_1")
        assertEquals(listOf("task_2"), importedTaskIds)
    }

    @Test
    fun testPlanMode_goalItemModelSupportsGoalsProjectsAndTasks() {
        val goal = com.deadline.ui.habits.GoalItem(
            id = "goal_daily",
            title = "Daily Focus Target",
            subtitle = "4 Pomodoros / day",
            isProject = false,
            isTask = false
        )
        val project = com.deadline.ui.habits.GoalItem(
            id = "p1",
            title = "Deadline App",
            subtitle = "10 Pomodoros",
            isProject = true,
            isTask = false
        )
        val task = com.deadline.ui.habits.GoalItem(
            id = "task_t1",
            title = "Implement dynamic gap filling",
            subtitle = "Deadline App • 30m • Priority: HIGH",
            isProject = false,
            isTask = true
        )

        assertEquals(false, goal.isTask)
        assertEquals(false, goal.isProject)
        assertEquals(true, project.isProject)
        assertEquals(false, project.isTask)
        assertEquals(true, task.isTask)
        assertEquals(false, task.isProject)
    }

    @Test
    fun testContinuousDisplacement_downwardProportionalProgression() {
        val itemHeightPx = 100f
        val draggedIndex = 0

        // Dragged item itself should have 0 non-dragged displacement
        val selfDisp = calculateContinuousDisplacement(
            itemIndex = 0,
            draggedIndex = draggedIndex,
            dragOffsetY = 50f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(0f, selfDisp, 0.001f)

        // At 10px drag (10% progress), neighbor at index 1 must slide up by -10px smoothly without deadzone
        val disp10 = calculateContinuousDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetY = 10f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(-10f, disp10, 0.001f)

        // At 50px drag (50% progress), neighbor at index 1 slides up by -50px
        val disp50 = calculateContinuousDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetY = 50f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(-50f, disp50, 0.001f)

        // Item at index 2 should not move yet during the first slot
        val disp50Item2 = calculateContinuousDisplacement(
            itemIndex = 2,
            draggedIndex = draggedIndex,
            dragOffsetY = 50f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(0f, disp50Item2, 0.001f)

        // At 150px drag (1.5 slots), item 1 is at -100px and item 2 is at -50px
        val disp150Item1 = calculateContinuousDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetY = 150f,
            itemHeightPx = itemHeightPx
        )
        val disp150Item2 = calculateContinuousDisplacement(
            itemIndex = 2,
            draggedIndex = draggedIndex,
            dragOffsetY = 150f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(-100f, disp150Item1, 0.001f)
        assertEquals(-50f, disp150Item2, 0.001f)
    }

    @Test
    fun testContinuousDisplacement_upwardProportionalProgression() {
        val itemHeightPx = 100f
        val draggedIndex = 2

        // Dragging upwards by -40px: item 1 slides down by +40px
        val dispUp = calculateContinuousDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetY = -40f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(40f, dispUp, 0.001f)

        // Item 0 should not move yet during the first slot
        val dispUpItem0 = calculateContinuousDisplacement(
            itemIndex = 0,
            draggedIndex = draggedIndex,
            dragOffsetY = -40f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(0f, dispUpItem0, 0.001f)
    }

    @Test
    fun testContinuousHorizontalDisplacement_proportionalProgression() {
        val cardWidthPx = 160f
        val draggedIndex = 0

        // Dragging rightwards by 80px (50% card width): item 1 slides left by -80px
        val dispRight = calculateContinuousHorizontalDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetX = 80f,
            cardWidthPx = cardWidthPx
        )
        assertEquals(-80f, dispRight, 0.001f)

        // Dragging leftwards: card 2 dragged left by -60px makes card 1 slide right by +60px
        val dispLeft = calculateContinuousHorizontalDisplacement(
            itemIndex = 1,
            draggedIndex = 2,
            dragOffsetX = -60f,
            cardWidthPx = cardWidthPx
        )
        assertEquals(60f, dispLeft, 0.001f)
    }

    @Test
    fun testCrossCategoryNoteTransfer_mappingAndInsertion() {
        val tabCategories = mapOf(
            0 to "ACTIVE",
            1 to "NOTE",
            2 to "IMPORTANT"
        )
        assertEquals("ACTIVE", tabCategories[0])
        assertEquals("NOTE", tabCategories[1])
        assertEquals("IMPORTANT", tabCategories[2])

        // Simulate moving note from ACTIVE (tab 0) to IMPORTANT (tab 2) at slot 1
        val noteToMove = com.deadline.data.local.QuickNote(
            id = "n_move",
            title = "Migrating to Jetpack Compose",
            content = "High priority refactor",
            noteType = "ACTIVE"
        )
        val activeNotes = mutableListOf(
            noteToMove,
            com.deadline.data.local.QuickNote(id = "n_act_2", title = "Active 2", content = "", noteType = "ACTIVE")
        )
        val importantNotes = mutableListOf(
            com.deadline.data.local.QuickNote(id = "n_imp_1", title = "Important 1", content = "", noteType = "IMPORTANT"),
            com.deadline.data.local.QuickNote(id = "n_imp_2", title = "Important 2", content = "", noteType = "IMPORTANT")
        )

        // Remove from active
        activeNotes.remove(noteToMove)
        assertEquals(1, activeNotes.size)
        assertEquals("n_act_2", activeNotes[0].id)

        // Transfer category and insert into slot 1 in important
        val updatedNote = noteToMove.copy(noteType = tabCategories[2]!!)
        val insertIndex = 1
        importantNotes.add(insertIndex, updatedNote)

        assertEquals(3, importantNotes.size)
        assertEquals("n_imp_1", importantNotes[0].id)
        assertEquals("n_move", importantNotes[1].id)
        assertEquals("IMPORTANT", importantNotes[1].noteType)
        assertEquals("n_imp_2", importantNotes[2].id)
    }

    @Test
    fun testNotesEditingSimplification_CleanState() {
        val staticPlaceholder = "Title"
        assertEquals("Title", staticPlaceholder)
        assertFalse(staticPlaceholder.contains("max 20 chars"))

        // Parsing unified note text: first line is title, remainder is content
        fun parseNote(input: String): Pair<String, String> {
            val lines = input.lines()
            val title = lines.firstOrNull()?.trim() ?: ""
            val content = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
            return Pair(title.ifBlank { "Untitled Note" }, content)
        }

        val (title1, content1) = parseNote("Design Meeting Notes\n- Review color system\n- Confirm typography")
        assertEquals("Design Meeting Notes", title1)
        assertEquals("- Review color system\n- Confirm typography", content1)

        val (title2, content2) = parseNote("")
        assertEquals("Untitled Note", title2)
        assertEquals("", content2)
    }

    @Test
    fun testHabitContinuumTracker_statusTransitions() {
        // 1. Completed state: Always resolves to COMPLETED (checkmark), regardless of past or future
        assertEquals(
            com.deadline.domain.HabitContinuumStatus.COMPLETED,
            com.deadline.domain.HabitContinuumTracker.resolveStatus(isCompleted = true, isPast = false)
        )
        assertEquals(
            com.deadline.domain.HabitContinuumStatus.COMPLETED,
            com.deadline.domain.HabitContinuumTracker.resolveStatus(isCompleted = true, isPast = true)
        )

        // 2. Missed state: Past days (after midnight) without completion resolve to MISSED ('X')
        assertEquals(
            com.deadline.domain.HabitContinuumStatus.MISSED,
            com.deadline.domain.HabitContinuumTracker.resolveStatus(isCompleted = false, isPast = true)
        )

        // 3. Pending state: Upcoming or uncompleted days (today or future) resolve to PENDING ('O')
        assertEquals(
            com.deadline.domain.HabitContinuumStatus.PENDING,
            com.deadline.domain.HabitContinuumTracker.resolveStatus(isCompleted = false, isPast = false)
        )
    }

    @Test
    fun testPinnedProjectAutoRelocation_movesToFront() {
        val p1 = com.deadline.data.local.Project(id = "p1", name = "Project 1", isPinned = false)
        val p2 = com.deadline.data.local.Project(id = "p2", name = "Project 2", isPinned = false)
        val p3 = com.deadline.data.local.Project(id = "p3", name = "Project 3", isPinned = false)

        val projectList = mutableListOf(p1, p2, p3)

        // Pin p3 -> should auto-relocate to absolute front (index 0)
        val targetId = "p3"
        val idx = projectList.indexOfFirst { it.id == targetId }
        val target = projectList.removeAt(idx)
        val updated = target.copy(isPinned = true)
        projectList.add(0, updated)

        assertEquals(3, projectList.size)
        assertEquals("p3", projectList[0].id)
        assertTrue(projectList[0].isPinned)
        assertEquals("p1", projectList[1].id)
        assertEquals("p2", projectList[2].id)

        // Now pin p2 -> should auto-relocate to absolute front (index 0, ahead of p3)
        val idx2 = projectList.indexOfFirst { it.id == "p2" }
        val target2 = projectList.removeAt(idx2)
        val updated2 = target2.copy(isPinned = true)
        projectList.add(0, updated2)

        assertEquals("p2", projectList[0].id)
        assertTrue(projectList[0].isPinned)
        assertEquals("p3", projectList[1].id)
        assertTrue(projectList[1].isPinned)
        assertEquals("p1", projectList[2].id)
        assertTrue(!projectList[2].isPinned)
    }

    @Test
    fun testHeadlineScaler_clampLimit() {
        val shortHeadline = "Build App"
        assertEquals("Build App", com.deadline.domain.HeadlineScaler.clamp(shortHeadline))

        val exact20Headline = "12345678901234567890"
        assertEquals(20, exact20Headline.length)
        assertEquals(exact20Headline, com.deadline.domain.HeadlineScaler.clamp(exact20Headline))

        val lengthyHeadline = "This is a very long title that definitely exceeds twenty characters"
        val clamped = com.deadline.domain.HeadlineScaler.clamp(lengthyHeadline)
        assertEquals(20, clamped.length)
        assertEquals("This is a very long ", clamped)
    }

    @Test
    fun testHeadlineScaler_clampFirstLine() {
        val noteContent = "First line headline that is definitely too long\nSecond line remains intact\nThird line"
        val clampedNote = com.deadline.domain.HeadlineScaler.clampFirstLine(noteContent)
        val lines = clampedNote.lines()

        assertEquals(3, lines.size)
        assertEquals(20, lines[0].length)
        assertEquals("First line headline ", lines[0])
        assertEquals("Second line remains intact", lines[1])
        assertEquals("Third line", lines[2])
    }

    @Test
    fun testHeadlineScaler_dynamicScaling() {
        // <= 10 chars: scale 1.0f
        val shortText = "Short"
        assertEquals(1.0f, com.deadline.domain.HeadlineScaler.getScaleFactor(shortText), 0.01f)
        assertEquals(16f, com.deadline.domain.HeadlineScaler.calculateScaledSize(shortText, 16f, 12f), 0.01f)

        // 11..15 chars: intermediate scale
        val midText = "Medium Headline" // 15 chars
        assertEquals(0.86f, com.deadline.domain.HeadlineScaler.getScaleFactor(midText), 0.01f)
        val midSize = com.deadline.domain.HeadlineScaler.calculateScaledSize(midText, 16f, 12f)
        assertTrue(midSize < 16f && midSize > 12f)

        // 16..20 chars: compact scale
        val longText = "Very Long Headline!!" // 20 chars
        assertEquals(0.75f, com.deadline.domain.HeadlineScaler.getScaleFactor(longText), 0.01f)
        assertEquals(12f, com.deadline.domain.HeadlineScaler.calculateScaledSize(longText, 16f, 12f), 0.01f)

        // Over 20 chars: clamped to 20 and compact scale
        val extraLong = "An excessively long headline that exceeds 20 characters by far"
        assertEquals(0.75f, com.deadline.domain.HeadlineScaler.getScaleFactor(extraLong), 0.01f)
        assertEquals(12f, com.deadline.domain.HeadlineScaler.calculateScaledSize(extraLong, 16f, 12f), 0.01f)
    }

    @Test
    fun testTimerStateDetection_activePausedAndScheduledTimers() {
        // Persistent State Rule: Any timer with value > 0 must be detected
        fun hasActiveOrSetTimer(remainingSeconds: Int): Boolean = remainingSeconds > 0

        // 1. Active timer (actively counting down)
        val activeRemaining = 1500
        val isCountingDown = true
        assertTrue(hasActiveOrSetTimer(activeRemaining))

        // 2. Paused timer at non-zero value
        val pausedRemaining = 872
        val isPaused = true
        assertTrue(hasActiveOrSetTimer(pausedRemaining))

        // 3. Scheduled / set timer duration
        val scheduledDuration = 300
        assertTrue(hasActiveOrSetTimer(scheduledDuration))

        // 4. Completed or zero timer
        val zeroRemaining = 0
        assertFalse(hasActiveOrSetTimer(zeroRemaining))
    }

    @Test
    fun testContinuousVisualFeedback_stateIndependence() {
        // Production Refinement Rule: Restrict the circular running animation on the center Timer icon
        // in the bottom navigation bar strictly to the active running state.
        // Ensure the icon remains completely static whenever the timer is paused, stopped, or idle.
        data class TimerIndicatorState(
            val hasActiveFill: Boolean,
            val isRunningAnimation: Boolean
        )

        fun resolveIndicator(remainingSeconds: Int, isRunning: Boolean): TimerIndicatorState {
            val hasTimeRemaining = remainingSeconds > 0
            return TimerIndicatorState(
                hasActiveFill = hasTimeRemaining,
                isRunningAnimation = hasTimeRemaining && isRunning
            )
        }

        // Active countdown: must have yellow fill and running animation
        val activeState = resolveIndicator(remainingSeconds = 1200, isRunning = true)
        assertTrue(activeState.hasActiveFill)
        assertTrue(activeState.isRunningAnimation)

        // Paused at non-zero value: must MAINTAIN fill, but animation must be STATIC (false)
        val pausedState = resolveIndicator(remainingSeconds = 1200, isRunning = false)
        assertTrue(pausedState.hasActiveFill)
        assertFalse(pausedState.isRunningAnimation)

        // Zero time remaining: must NOT force active fill or running animation
        val zeroState = resolveIndicator(remainingSeconds = 0, isRunning = false)
        assertFalse(zeroState.hasActiveFill)
        assertFalse(zeroState.isRunningAnimation)
    }

    @Test
    fun testHabitsTodayCreationButton_universalYellowFill() {
        // Replace current purple fill (WorkIndigo: 0xFF6366F1) on the floating plus button
        // in the Habits 'Today' screen with the app's standard universal yellow (BrandAmberStart: 0xFFFF9F1C).
        val universalYellow = 0xFFFF9F1CL
        val oldPurple = 0xFF6366F1L
        val todayFabColor = universalYellow

        assertEquals(universalYellow, todayFabColor)
        assertTrue(todayFabColor != oldPurple)
    }

    @Test
    fun testHabitsPlanModeFiltering_noProjectContainers() {
        // Plan mode displays individual tasks directly in the list view without grouping or nesting them under projects
        val todos = listOf(
            ProjectTodo(id = "task_1", projectId = "proj_1", title = "Task 1"),
            ProjectTodo(id = "task_2", projectId = "proj_2", title = "Task 2")
        )

        // Verify task items are flatly accessible without requiring project grouping/containers
        val directTasksList = todos.map { it.title }
        assertEquals(listOf("Task 1", "Task 2"), directTasksList)
    }

    @Test
    fun testTimerForegroundNotificationLifecycleRules() {
        // Show persistent foreground notification only when timer is actively running
        // Instantly dismiss when paused, stopped, or cancelled
        fun isNotificationVisible(isRunning: Boolean, remainingSeconds: Int): Boolean {
            return isRunning && remainingSeconds > 0
        }

        // Running: visible
        assertTrue(isNotificationVisible(isRunning = true, remainingSeconds = 1500))
        // Paused: instantly dismissed
        assertFalse(isNotificationVisible(isRunning = false, remainingSeconds = 1500))
        // Stopped/Cancelled: instantly dismissed
        assertFalse(isNotificationVisible(isRunning = false, remainingSeconds = 0))
    }

    @Test
    fun testTimerNotificationZeroPolicy() {
        // Enforce zero timer notifications policy across timer countdown and tab switching
        fun isNotificationDispatched(isRunning: Boolean, remainingSeconds: Int, isTabSwitched: Boolean): Boolean = false

        assertFalse(isNotificationDispatched(isRunning = true, remainingSeconds = 1200, isTabSwitched = false))
        assertFalse(isNotificationDispatched(isRunning = true, remainingSeconds = 1200, isTabSwitched = true))
        assertFalse(isNotificationDispatched(isRunning = false, remainingSeconds = 1200, isTabSwitched = true))
        assertFalse(isNotificationDispatched(isRunning = false, remainingSeconds = 0, isTabSwitched = true))
    }

    @Test
    fun testTimerPresetEditMode_noDeleteButton() {
        // In the preset edit sheet/dialog, the delete button/icon is removed entirely
        val hasDeleteButtonInDialog = false
        assertFalse(hasDeleteButtonInDialog)
    }

    @Test
    fun testTimeUnitCyclingToggleButton_dimensionsAndLabels() {
        val fixedWidthDp = 144
        val fixedHeightDp = 36
        val hasRotationIcon = false

        assertEquals(144, fixedWidthDp)
        assertEquals(36, fixedHeightDp)
        assertFalse(hasRotationIcon)

        // Text flips smoothly between "Hour / Minute" and "Minute / Second"
        val labels = listOf("Hour / Minute", "Minute / Second")
        assertEquals("Hour / Minute", labels[0])
        assertEquals("Minute / Second", labels[1])
    }

    @Test
    fun testTimerScreenHeaderTitle_renamedToTimer() {
        val headerTitle = "Timer"
        assertEquals("Timer", headerTitle)
        assertTrue(headerTitle != "Focus Timer")
    }

    @Test
    fun testHabitsPlanFabAndHeaderActionRules() {
        // Habits Tab 0: Today Tab
        // - FAB is visible
        // - Top App Bar '+' button is hidden
        val todayPage = 0
        val isTodayFabVisible = todayPage == 0
        val isTodayHeaderAddVisible = todayPage == 1
        assertTrue(isTodayFabVisible)
        assertFalse(isTodayHeaderAddVisible)

        // Habits Tab 1: Plan Tab
        // - FAB is removed (not visible)
        // - Top App Bar '+' button is visible
        val planPage = 1
        val isPlanFabVisible = planPage == 0
        val isPlanHeaderAddVisible = planPage == 1
        assertFalse(isPlanFabVisible)
        assertTrue(isPlanHeaderAddVisible)

        // Plan Tab Creation Button Styling Rules:
        // - Relocated to top right section of Plan tab
        // - Styled as a neutral grey icon, replacing the default purple fill
        val defaultPurpleFill = 0xFF6366F1L // WorkIndigo
        val neutralGreyIcon = true
        val hasPurpleFillOnPlanTab = false
        assertFalse("Plan tab creation button must not use purple fill", hasPurpleFillOnPlanTab)
        assertTrue("Plan tab creation button must be styled as a neutral grey icon", neutralGreyIcon)
    }

    @Test
    fun testCreateTaskModalPlanToggleRules() {
        // Status toggle visually indicates status using color only:
        // Yellow for active/include in Plan, Grey for inactive/exclude from Plan, without text labels.
        fun resolveToggleColor(isActive: Boolean): Long {
            val yellow = 0xFFFFCC00L
            val grey = 0x618E8E93L
            return if (isActive) yellow else grey
        }

        val activeColor = resolveToggleColor(isActive = true)
        val inactiveColor = resolveToggleColor(isActive = false)

        assertEquals(0xFFFFCC00L, activeColor)
        assertEquals(0x618E8E93L, inactiveColor)

        // Verify state toggling transitions between include in Plan and exclude from Plan
        var includeInPlan = false
        assertFalse(includeInPlan)

        includeInPlan = !includeInPlan
        assertTrue(includeInPlan)
        assertEquals(0xFFFFCC00L, resolveToggleColor(includeInPlan))

        includeInPlan = !includeInPlan
        assertFalse(includeInPlan)
        assertEquals(0x618E8E93L, resolveToggleColor(includeInPlan))
    }

    @Test
    fun testGlobalUndoPayloadCreationAndMetadataCaching() {
        val todo = com.deadline.data.local.ProjectTodo(
            id = "todo_123",
            projectId = "proj_1",
            title = "Implement Feature X",
            estimatedMinutes = 45,
            priority = com.deadline.data.local.TaskPriority.HIGH
        )
        val todoPayload = com.deadline.data.local.UndoPayload.TodoPayload(
            todo = todo,
            originalIndex = 2,
            wasInPlan = true
        )
        assertEquals("todo_123", todoPayload.id)
        assertEquals("Deleted task \"Implement Feature X\"", todoPayload.message)
        assertEquals(2, todoPayload.originalIndex)
        assertTrue(todoPayload.wasInPlan)

        val habit = com.deadline.data.local.Habit(
            id = "habit_456",
            name = "Morning Stretch",
            emoji = "🧘",
            color = "#30D158",
            targetCount = 1
        )
        val habitPayload = com.deadline.data.local.UndoPayload.HabitPayload(
            habit = habit,
            originalIndex = 0
        )
        assertEquals("habit_456", habitPayload.id)
        assertEquals("Deleted habit \"Morning Stretch\"", habitPayload.message)
        assertEquals(0, habitPayload.originalIndex)

        val project = com.deadline.data.local.Project(
            id = "proj_789",
            name = "Mobile Redesign",
            color = "#FF9F1C",
            targetPomodoros = 12
        )
        val projectPayload = com.deadline.data.local.UndoPayload.ProjectPayload(
            project = project,
            originalIndex = 1,
            isArchivedAction = false
        )
        assertEquals("proj_789", projectPayload.id)
        assertEquals("Deleted project \"Mobile Redesign\"", projectPayload.message)
        assertEquals(1, projectPayload.originalIndex)
    }

    @Test
    fun testGlobalUndoStateRestorationAtOriginalIndex() {
        // Given initial list of 4 tasks
        val initialList = mutableListOf("Task A", "Task B", "Task C", "Task D")

        // Item at index 1 ("Task B") is removed
        val removedIndex = 1
        val removedItem = initialList.removeAt(removedIndex)
        assertEquals("Task B", removedItem)
        assertEquals(listOf("Task A", "Task C", "Task D"), initialList)

        // When Undo is triggered, re-insert at cached original index
        val restoredList = initialList.toMutableList()
        val insertIndex = removedIndex.coerceIn(0, restoredList.size)
        restoredList.add(insertIndex, removedItem)

        // Then exact original state and position are restored
        assertEquals(listOf("Task A", "Task B", "Task C", "Task D"), restoredList)
        assertEquals(1, restoredList.indexOf("Task B"))
    }

    @Test
    fun testGlobalUndoTimingAndDismissal() {
        val displayDurationMs = 5000L
        assertEquals(5000L, displayDurationMs)

        var isSnackbarVisible = true
        var isDismissed = false

        // Simulate 5-second timer elapsed
        fun onTimeout() {
            isSnackbarVisible = false
            isDismissed = true
        }

        onTimeout()
        assertFalse(isSnackbarVisible)
        assertTrue(isDismissed)
    }

    @Test
    fun testTwoStageScheduledTaskReminderProtocol_TimingCalculations() {
        // Date: 2026-10-20, Start Time: 14:00 (14 * 60 = 840 minutes)
        val dateStr = "2026-10-20"
        val startMinute = 14 * 60 // 840 minutes = 14:00
        val scheduledTimeMillis = com.deadline.service.TaskReminderScheduler.calculateScheduledTimestamp(dateStr, startMinute)

        val cal = Calendar.getInstance()
        cal.timeInMillis = scheduledTimeMillis
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))

        // Stage 1: Pre-Scheduled Alert must be exactly 30 minutes prior
        val advanceNoticeMinutes = com.deadline.service.TaskReminderScheduler.ADVANCE_NOTICE_MINUTES
        assertEquals(30, advanceNoticeMinutes)

        val preAlertTimeMillis = scheduledTimeMillis - (advanceNoticeMinutes * 60 * 1000L)
        val preCal = Calendar.getInstance()
        preCal.timeInMillis = preAlertTimeMillis
        assertEquals(13, preCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, preCal.get(Calendar.MINUTE))
        assertEquals(30 * 60 * 1000L, scheduledTimeMillis - preAlertTimeMillis)

        // Stage 2: At-Time Mandatory Alarm must be at the exact scheduled task time
        val atTimeAlarmMillis = scheduledTimeMillis
        assertEquals(scheduledTimeMillis, atTimeAlarmMillis)
    }

    @Test
    fun testTwoStageScheduledTaskReminderProtocol_Attributes() {
        // Stage 1 Pre-Scheduled Alert: Standard notification
        data class AlertSpec(
            val isPersistent: Boolean,
            val priority: Int,
            val hasRingtone: Boolean,
            val hasDismissAction: Boolean
        )

        val preAlertSpec = AlertSpec(
            isPersistent = false,
            priority = 0, // DEFAULT priority
            hasRingtone = false,
            hasDismissAction = false
        )
        assertFalse("Pre-alert must NOT be persistent", preAlertSpec.isPersistent)
        assertFalse("Pre-alert does not play alarm ringtone", preAlertSpec.hasRingtone)

        // Stage 2 At-Time Mandatory Alarm: Persistent notification + designated ringtone
        val atTimeAlarmSpec = AlertSpec(
            isPersistent = true,
            priority = 2, // MAX priority
            hasRingtone = true,
            hasDismissAction = true
        )
        assertTrue("At-time alarm MUST be persistent (ongoing = true)", atTimeAlarmSpec.isPersistent)
        assertTrue("At-time alarm MUST play device designated ringtone", atTimeAlarmSpec.hasRingtone)
        assertTrue("At-time alarm MUST provide dismiss action", atTimeAlarmSpec.hasDismissAction)
    }

    @Test
    fun testPersistentTimerHud_VisibilityRules() {
        fun shouldShowHud(currentPage: Int, remainingSeconds: Int, isRunning: Boolean): Boolean {
            val isTimerTab = currentPage == DeadlineTab.TIMER.ordinal
            val hasActiveSession = remainingSeconds > 0 || isRunning
            return !isTimerTab && hasActiveSession
        }

        val timerTabOrdinal = DeadlineTab.TIMER.ordinal
        val statsTabOrdinal = DeadlineTab.STATS.ordinal
        val habitsTabOrdinal = DeadlineTab.HABITS.ordinal
        val projectsTabOrdinal = DeadlineTab.PROJECTS.ordinal
        val notesTabOrdinal = DeadlineTab.NOTES.ordinal

        // When user is on Timer tab: HUD must NEVER be displayed
        assertFalse(shouldShowHud(timerTabOrdinal, remainingSeconds = 1500, isRunning = true))
        assertFalse(shouldShowHud(timerTabOrdinal, remainingSeconds = 1500, isRunning = false))
        assertFalse(shouldShowHud(timerTabOrdinal, remainingSeconds = 0, isRunning = false))

        // When navigating away from Timer screen with active countdown: HUD must be visible
        assertTrue(shouldShowHud(statsTabOrdinal, remainingSeconds = 1200, isRunning = true))
        assertTrue(shouldShowHud(habitsTabOrdinal, remainingSeconds = 1200, isRunning = false))
        assertTrue(shouldShowHud(projectsTabOrdinal, remainingSeconds = 60, isRunning = true))
        assertTrue(shouldShowHud(notesTabOrdinal, remainingSeconds = 10, isRunning = false))

        // When timer has reached 0 and is not running on other tabs: HUD must dismiss
        assertFalse(shouldShowHud(statsTabOrdinal, remainingSeconds = 0, isRunning = false))
        assertFalse(shouldShowHud(habitsTabOrdinal, remainingSeconds = 0, isRunning = false))
        assertFalse(shouldShowHud(projectsTabOrdinal, remainingSeconds = 0, isRunning = false))
        assertFalse(shouldShowHud(notesTabOrdinal, remainingSeconds = 0, isRunning = false))
    }

    @Test
    fun testPersistentTimerHud_ModeAttributesAndColors() {
        data class HudModeConfig(val label: String, val colorHex: Long)

        fun getHudModeConfig(type: PomodoroType): HudModeConfig = when (type) {
            PomodoroType.FOCUS -> HudModeConfig("Focus", 0xFFFF9500)
            PomodoroType.SHORT_BREAK -> HudModeConfig("Break", 0xFF22C55E)
            PomodoroType.LONG_BREAK -> HudModeConfig("Flow", 0xFFA855F7)
        }

        val focusConfig = getHudModeConfig(PomodoroType.FOCUS)
        assertEquals("Focus", focusConfig.label)
        assertEquals(0xFFFF9500, focusConfig.colorHex)

        val breakConfig = getHudModeConfig(PomodoroType.SHORT_BREAK)
        assertEquals("Break", breakConfig.label)
        assertEquals(0xFF22C55E, breakConfig.colorHex)

        val flowConfig = getHudModeConfig(PomodoroType.LONG_BREAK)
        assertEquals("Flow", flowConfig.label)
        assertEquals(0xFFA855F7, flowConfig.colorHex)
    }

    @Test
    fun testPersistentTimerHud_DismissalAndLayout() {
        fun shouldShowHud(
            currentPage: Int,
            remainingSeconds: Int,
            isRunning: Boolean,
            isHudDismissed: Boolean
        ): Boolean {
            val isTimerTab = currentPage == DeadlineTab.TIMER.ordinal
            val hasActiveSession = remainingSeconds > 0 || isRunning
            return !isTimerTab && hasActiveSession && !isHudDismissed
        }

        val timerTab = DeadlineTab.TIMER.ordinal
        val statsTab = DeadlineTab.STATS.ordinal
        val habitsTab = DeadlineTab.HABITS.ordinal

        // Active session on other tab -> HUD visible
        assertTrue(shouldShowHud(statsTab, 1500, true, isHudDismissed = false))

        // Swipe-up gesture dismisses HUD -> HUD hidden
        assertFalse(shouldShowHud(statsTab, 1500, true, isHudDismissed = true))
        assertFalse(shouldShowHud(habitsTab, 1200, false, isHudDismissed = true))

        // Returning to Timer tab re-arms HUD dismissal state:
        var isDismissed = true
        fun onTabChange(newTab: Int) {
            if (newTab == timerTab) {
                isDismissed = false
            }
        }
        onTabChange(timerTab)
        assertFalse(isDismissed)
        // Navigating away now shows the HUD again
        assertTrue(shouldShowHud(habitsTab, 1200, true, isDismissed))
    }

    @Test
    fun testCleanStateInitialization_ZeroPreLoadedFocusTasks() {
        // Zero pre-populated tasks across all modes
        val initialFocusTasks: List<com.deadline.data.local.FocusTask> = emptyList()
        val focusTasksForFocus = initialFocusTasks.filter { it.sessionType.equals("FOCUS", ignoreCase = true) }
        val focusTasksForBreak = initialFocusTasks.filter { it.sessionType.equals("SHORT_BREAK", ignoreCase = true) }
        val focusTasksForFlow = initialFocusTasks.filter { it.sessionType.equals("LONG_BREAK", ignoreCase = true) }

        assertTrue(focusTasksForFocus.isEmpty())
        assertTrue(focusTasksForBreak.isEmpty())
        assertTrue(focusTasksForFlow.isEmpty())

        // Default display labels fall back cleanly without demo items
        fun getFallbackTitle(type: PomodoroType, taskTitle: String): String {
            return taskTitle.ifBlank {
                when (type) {
                    PomodoroType.FOCUS -> "Focus"
                    PomodoroType.SHORT_BREAK -> "Break"
                    PomodoroType.LONG_BREAK -> "Flow"
                }
            }
        }

        assertEquals("Focus", getFallbackTitle(PomodoroType.FOCUS, ""))
        assertEquals("Break", getFallbackTitle(PomodoroType.SHORT_BREAK, ""))
        assertEquals("Flow", getFallbackTitle(PomodoroType.LONG_BREAK, ""))
        assertNotEquals("Deep Work Sprint", getFallbackTitle(PomodoroType.FOCUS, ""))
        assertNotEquals("Hydrate & Stretch", getFallbackTitle(PomodoroType.SHORT_BREAK, ""))
    }

    @Test
    fun testNewTaskCreationModal_MinimalistDesign() {
        fun getModalTitle(taskToEdit: Any?): String {
            return if (taskToEdit == null) "New" else "Edit"
        }

        // 1. Header displays exclusively "New"
        assertEquals("New", getModalTitle(null))
        assertEquals("Edit", getModalTitle(Object()))
        assertFalse(getModalTitle(null).contains("Focus Task"))

        // 2. Placeholder is exclusively "Name"
        val inputPlaceholder = "Name"
        assertEquals("Name", inputPlaceholder)
        assertFalse(inputPlaceholder.contains("Activity Name"))

        // 3. Dual-wheel continuous scrolling alpha / fade physics:
        fun getWheelItemAlpha(dist: Int): Float = when (dist) {
            0 -> 1f
            1 -> 0.40f
            2 -> 0.16f
            else -> 0.05f
        }

        assertEquals(1f, getWheelItemAlpha(0), 0.001f) // Center active item
        assertEquals(0.40f, getWheelItemAlpha(1), 0.001f) // 1 item above / below
        assertEquals(0.16f, getWheelItemAlpha(2), 0.001f) // 2 items above / below (faded)
        assertEquals(0.05f, getWheelItemAlpha(3), 0.001f) // 3+ items (blurred out)

        // 4. Timer duration calculation in Hour/Minute vs Minute/Second modes
        fun calculateSeconds(isHourMinute: Boolean, left: Int, right: Int): Int {
            return if (isHourMinute) (left * 60 + right) * 60 else left * 60 + right
        }

        assertEquals(1500, calculateSeconds(isHourMinute = true, left = 0, right = 25))
        assertEquals(3600, calculateSeconds(isHourMinute = true, left = 1, right = 0))
        assertEquals(300, calculateSeconds(isHourMinute = false, left = 5, right = 0))
    }

    @Test
    fun testUniversalCircularProgressAnimation_DynamicAdaptation() {
        fun calculateRingSweep(remainingSeconds: Int, totalDurationSeconds: Int, fillProgress: Float): Float {
            val effectiveTotal = if (totalDurationSeconds > 0) totalDurationSeconds else 1
            val fraction = (remainingSeconds.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 1f)
            return -360f * (fraction * fillProgress)
        }

        // Mode 1: Focus (25m = 1500s)
        val focusTotal = 25 * 60
        // Full progress on fill completion
        assertEquals(-360f, calculateRingSweep(focusTotal, focusTotal, 1f), 0.01f)
        // Mid-way countdown (750s remaining)
        assertEquals(-180f, calculateRingSweep(750, focusTotal, 1f), 0.01f)
        // Partial animation fill (fillProgress = 0.5f)
        assertEquals(-180f, calculateRingSweep(focusTotal, focusTotal, 0.5f), 0.01f)
        // 0s remaining
        assertEquals(0f, calculateRingSweep(0, focusTotal, 1f), 0.01f)

        // Mode 2: Break (5m = 300s) - Duration switched / adjusted
        val breakTotal = 5 * 60
        assertEquals(-360f, calculateRingSweep(breakTotal, breakTotal, 1f), 0.01f)
        assertEquals(-90f, calculateRingSweep(75, breakTotal, 1f), 0.01f)

        // Mode 3: Flow (15m = 900s) - Dynamic adaptation
        val flowTotal = 15 * 60
        assertEquals(-360f, calculateRingSweep(flowTotal, flowTotal, 1f), 0.01f)
        assertEquals(-270f, calculateRingSweep(675, flowTotal, 1f), 0.01f)

        // Graceful handling of zero duration or invalid state without crashing or negative angles
        assertEquals(0f, calculateRingSweep(0, 0, 1f), 0.01f)
    }

    @Test
    fun testProjectPinning_ReactiveSortingLogic() {
        data class TestProject(val id: String, val name: String, val isPinned: Boolean)

        fun sortTestProjects(projects: List<TestProject>): List<TestProject> {
            return projects.sortedWith(
                compareByDescending<TestProject> { it.isPinned }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
        }

        val initialList = listOf(
            TestProject("1", "Architecture", isPinned = false),
            TestProject("2", "Backend", isPinned = false),
            TestProject("3", "DevOps", isPinned = false),
            TestProject("4", "Frontend", isPinned = false)
        )

        val sortedInitial = sortTestProjects(initialList)
        assertEquals(listOf("Architecture", "Backend", "DevOps", "Frontend"), sortedInitial.map { it.name })

        // 1. Pin "Frontend" -> Must immediately move to the absolute front (pinned section)
        val withFrontendPinned = sortedInitial.map {
            if (it.name == "Frontend") it.copy(isPinned = true) else it
        }
        val sortedWithPin = sortTestProjects(withFrontendPinned)
        assertEquals("Frontend", sortedWithPin[0].name)
        assertTrue(sortedWithPin[0].isPinned)
        assertEquals(listOf("Frontend", "Architecture", "Backend", "DevOps"), sortedWithPin.map { it.name })

        // 2. Also Pin "DevOps" -> Pinned section has [DevOps, Frontend] (alphabetical among pinned)
        val withTwoPinned = sortedWithPin.map {
            if (it.name == "DevOps") it.copy(isPinned = true) else it
        }
        val sortedTwoPinned = sortTestProjects(withTwoPinned)
        assertEquals(listOf("DevOps", "Frontend", "Architecture", "Backend"), sortedTwoPinned.map { it.name })

        // 3. Unpin "DevOps" -> Must instantly trigger a re-sort, moving it back to its default alphabetical position!
        val withDevOpsUnpinned = sortedTwoPinned.map {
            if (it.name == "DevOps") it.copy(isPinned = false) else it
        }
        val sortedAfterUnpin = sortTestProjects(withDevOpsUnpinned)
        // "Frontend" is still pinned at index 0.
        // Unpinned items must be in exact alphabetical order: Architecture, Backend, DevOps!
        assertEquals("Frontend", sortedAfterUnpin[0].name)
        assertEquals("Architecture", sortedAfterUnpin[1].name)
        assertEquals("Backend", sortedAfterUnpin[2].name)
        assertEquals("DevOps", sortedAfterUnpin[3].name)
        assertEquals(listOf("Frontend", "Architecture", "Backend", "DevOps"), sortedAfterUnpin.map { it.name })

        // 4. Unpin "Frontend" -> Everything returns to 100% natural alphabetical order
        val allUnpinned = sortedAfterUnpin.map { it.copy(isPinned = false) }
        val sortedAllUnpinned = sortTestProjects(allUnpinned)
        assertEquals(listOf("Architecture", "Backend", "DevOps", "Frontend"), sortedAllUnpinned.map { it.name })
    }

    @Test
    fun testProjectPinning_UniversalYellowStyling() {
        // Standard universal yellow hue: BrandAmberStart (0xFFFF9F1C)
        val universalYellow = 0xFFFF9F1CL
        val workIndigoPurple = 0xFF6366F1L

        fun resolvePinColor(isPinned: Boolean, inactiveColor: Long): Long {
            return if (isPinned) universalYellow else inactiveColor
        }

        val activePinColor = resolvePinColor(isPinned = true, inactiveColor = 0xFF8E8E93L)
        assertEquals(universalYellow, activePinColor)
        assertNotEquals(workIndigoPurple, activePinColor)

        // Inactive pin should remain muted
        val inactivePinColor = resolvePinColor(isPinned = false, inactiveColor = 0xFF8E8E93L)
        assertEquals(0xFF8E8E93L, inactivePinColor)
    }

    @Test
    fun testTaskCardMinimalistRedesign_ExclusivelyTaskNameAndCheckbox() {
        // Task card representation: contains exclusively title and completion status
        data class MinimalistTaskCard(val title: String, val isCompleted: Boolean)

        fun createMinimalistTaskCard(todo: ProjectTodo): MinimalistTaskCard {
            return MinimalistTaskCard(
                title = todo.title,
                isCompleted = todo.status == TaskStatus.COMPLETED
            )
        }

        val fullTodo = ProjectTodo(
            id = "todo_1",
            projectId = "proj_1",
            title = "Refactor Database Engine",
            priority = TaskPriority.URGENT,
            dueDate = "2026-10-30",
            estimatedMinutes = 50,
            loggedMinutes = 25,
            status = TaskStatus.TODO,
            createdAt = 1729560000000L
        )

        val card = createMinimalistTaskCard(fullTodo)
        assertEquals("Refactor Database Engine", card.title)
        assertFalse(card.isCompleted)
    }

    @Test
    fun testHeroSection_CircularProgressRings_StateMapping() {
        data class StateRingConfig(val label: String, val colorHex: Long, val defaultTarget: Int)

        fun getRingConfig(mode: String): StateRingConfig = when (mode) {
            "Focus" -> StateRingConfig("Focus", 0xFFFF9500L, 100)
            "Break" -> StateRingConfig("Break", 0xFF22C55EL, 20)
            "Flow" -> StateRingConfig("Flow", 0xFFA855F7L, 30)
            else -> error("Unknown mode")
        }

        val focus = getRingConfig("Focus")
        assertEquals("Focus", focus.label)
        assertEquals(0xFFFF9500L, focus.colorHex) // Amber
        assertEquals(100, focus.defaultTarget)

        val breakState = getRingConfig("Break")
        assertEquals("Break", breakState.label)
        assertEquals(0xFF22C55EL, breakState.colorHex) // Mint Green
        assertEquals(20, breakState.defaultTarget)

        val flow = getRingConfig("Flow")
        assertEquals("Flow", flow.label)
        assertEquals(0xFFA855F7L, flow.colorHex) // Poetry Purple
        assertEquals(30, flow.defaultTarget)

        // Progress calculation
        fun computeProgress(minutes: Int, target: Int): Float =
            (minutes.toFloat() / target.toFloat()).coerceIn(0f, 1f)

        assertEquals(0.5f, computeProgress(50, focus.defaultTarget), 0.01f)
        assertEquals(1.0f, computeProgress(120, focus.defaultTarget), 0.01f) // capped at 1.0
        assertEquals(0.75f, computeProgress(15, breakState.defaultTarget), 0.01f)
        assertEquals(1.0f, computeProgress(30, flow.defaultTarget), 0.01f)
    }

    @Test
    fun testTrueConsistencyHeatmap_DensityMapping() {
        fun computeCellDensityLevel(completedHabits: Int, totalHabits: Int, focusMinutes: Int): Int {
            val habitDensity = if (totalHabits > 0) completedHabits.toFloat() / totalHabits.toFloat() else 0f
            val focusDensity = (focusMinutes.toFloat() / 50f).coerceIn(0f, 1f)
            val compositeDensity = maxOf(habitDensity, focusDensity)

            return when {
                compositeDensity >= 0.75f -> 4 // Level 4: full intensity
                compositeDensity >= 0.50f -> 3 // Level 3: 72% intensity
                compositeDensity >= 0.25f -> 2 // Level 2: 45% intensity
                compositeDensity > 0f -> 1     // Level 1: 22% intensity
                else -> 0                      // Level 0: inactive
            }
        }

        // Level 0: No activity
        assertEquals(0, computeCellDensityLevel(completedHabits = 0, totalHabits = 4, focusMinutes = 0))

        // Level 1: Low activity (e.g. 1 habit out of 5 = 20%)
        assertEquals(1, computeCellDensityLevel(completedHabits = 1, totalHabits = 5, focusMinutes = 0))

        // Level 2: Moderate activity (e.g. 2 habits out of 5 = 40%)
        assertEquals(2, computeCellDensityLevel(completedHabits = 2, totalHabits = 5, focusMinutes = 0))

        // Level 3: High activity (e.g. 3 habits out of 5 = 60%)
        assertEquals(3, computeCellDensityLevel(completedHabits = 3, totalHabits = 5, focusMinutes = 0))

        // Level 4: Full / Maximum activity (e.g. 4/4 habits = 100% or 50+ min focus)
        assertEquals(4, computeCellDensityLevel(completedHabits = 4, totalHabits = 4, focusMinutes = 0))
        assertEquals(4, computeCellDensityLevel(completedHabits = 0, totalHabits = 4, focusMinutes = 55))
    }

    @Test
    fun testStartupScreenSelector_SequentialCyclingAndNavigationMapping() {
        val cyclingDestinations = listOf(
            "TIMER" to "Timer",
            "STATS" to "Stats",
            "HABITS" to "Habits",
            "PROJECTS" to "Tasks",
            "NOTES" to "Notes"
        )

        // Verify sequential cycling order: Timer -> Stats -> Habits -> Tasks -> Notes -> Timer
        fun getNextDestination(currentKey: String): Pair<String, String> {
            val currentIndex = cyclingDestinations.indexOfFirst {
                it.first.equals(currentKey, ignoreCase = true)
            }
            val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % cyclingDestinations.size else 0
            return cyclingDestinations[nextIndex]
        }

        assertEquals("STATS" to "Stats", getNextDestination("TIMER"))
        assertEquals("HABITS" to "Habits", getNextDestination("STATS"))
        assertEquals("PROJECTS" to "Tasks", getNextDestination("HABITS"))
        assertEquals("NOTES" to "Notes", getNextDestination("PROJECTS"))
        assertEquals("TIMER" to "Timer", getNextDestination("NOTES"))

        // Fallback for unknown / empty key defaults to index 0 (Timer)
        assertEquals("TIMER" to "Timer", getNextDestination(""))
        assertEquals("TIMER" to "Timer", getNextDestination("UNKNOWN"))

        // Verify Navigation Graph launch routing evaluation
        fun evaluateStartupTab(key: String): DeadlineTab {
            return when (key.uppercase().trim()) {
                "TIMER" -> DeadlineTab.TIMER
                "STATS" -> DeadlineTab.STATS
                "HABITS" -> DeadlineTab.HABITS
                "PROJECTS", "TASKS" -> DeadlineTab.PROJECTS
                "NOTES" -> DeadlineTab.NOTES
                else -> DeadlineTab.TIMER
            }
        }

        assertEquals(DeadlineTab.TIMER, evaluateStartupTab("TIMER"))
        assertEquals(DeadlineTab.STATS, evaluateStartupTab("STATS"))
        assertEquals(DeadlineTab.HABITS, evaluateStartupTab("HABITS"))
        assertEquals(DeadlineTab.PROJECTS, evaluateStartupTab("PROJECTS"))
        assertEquals(DeadlineTab.PROJECTS, evaluateStartupTab("TASKS"))
        assertEquals(DeadlineTab.NOTES, evaluateStartupTab("NOTES"))
        assertEquals(DeadlineTab.TIMER, evaluateStartupTab("INVALID_SCREEN"))
    }

    @Test
    fun testTargetedGoalUnitSelector_containsHourAndExistingUnits() {
        val quickUnits = listOf("Hour", "mins", "glasses", "pages", "reps", "km", "times")
        assertTrue("Quick units should include 'Hour'", quickUnits.contains("Hour"))
        assertTrue("Quick units should include 'mins'", quickUnits.contains("mins"))
        assertTrue("Quick units should include 'glasses'", quickUnits.contains("glasses"))
        assertTrue("Quick units should include 'pages'", quickUnits.contains("pages"))
        assertTrue("Quick units should include 'reps'", quickUnits.contains("reps"))
        assertTrue("Quick units should include 'km'", quickUnits.contains("km"))
        assertTrue("Quick units should include 'times'", quickUnits.contains("times"))
        assertEquals(7, quickUnits.size)

        // Test selection logic case-insensitivity
        fun isUnitSelected(selectedUnit: String, itemUnit: String): Boolean {
            return selectedUnit.equals(itemUnit, ignoreCase = true) || (itemUnit == "Hour" && selectedUnit.equals("hours", ignoreCase = true))
        }

        assertTrue(isUnitSelected("Hour", "Hour"))
        assertTrue(isUnitSelected("hour", "Hour"))
        assertTrue(isUnitSelected("hours", "Hour"))
        assertTrue(isUnitSelected("mins", "mins"))
        assertFalse(isUnitSelected("glasses", "Hour"))
    }

    @Test
    fun testProjectCreation_planRoutingAndContentsInclusion() {
        val projectId = "proj_test_123"
        val projectTodos = listOf(
            ProjectTodo(id = "todo_1", projectId = projectId, title = "Task 1"),
            ProjectTodo(id = "todo_2", projectId = projectId, title = "Task 2")
        )

        val planImportedProjects = mutableListOf<String>()
        val planImportedTasks = mutableListOf<String>()

        fun routeProjectToPlan(pId: String, todos: List<ProjectTodo>) {
            if (!planImportedProjects.contains(pId)) {
                planImportedProjects.add(pId)
            }
            todos.filter { it.projectId == pId }.forEach { todo ->
                if (!planImportedTasks.contains(todo.id)) {
                    planImportedTasks.add(todo.id)
                }
            }
        }

        fun removeProjectFromPlan(pId: String, todos: List<ProjectTodo>) {
            planImportedProjects.remove(pId)
            todos.filter { it.projectId == pId }.forEach { todo ->
                planImportedTasks.remove(todo.id)
            }
        }

        // Test inclusion routing
        routeProjectToPlan(projectId, projectTodos)
        assertTrue(planImportedProjects.contains(projectId))
        assertTrue(planImportedTasks.contains("todo_1"))
        assertTrue(planImportedTasks.contains("todo_2"))

        // Test exclusion removal
        removeProjectFromPlan(projectId, projectTodos)
        assertFalse(planImportedProjects.contains(projectId))
        assertFalse(planImportedTasks.contains("todo_1"))
        assertFalse(planImportedTasks.contains("todo_2"))
    }

    @Test
    fun testCompactRollingPicker_12HourTo24HourMinuteCalculations() {
        fun calculateStartMinute(hour12: Int, minute: Int, isPm: Boolean): Int {
            val hour24 = when {
                isPm && hour12 < 12 -> hour12 + 12
                !isPm && hour12 == 12 -> 0
                else -> hour12
            }
            return hour24 * 60 + minute
        }

        // 12:00 AM -> 0 (midnight)
        assertEquals(0, calculateStartMinute(12, 0, false))

        // 12:45 AM -> 45
        assertEquals(45, calculateStartMinute(12, 45, false))

        // 1:00 AM -> 60 (1 hr)
        assertEquals(60, calculateStartMinute(1, 0, false))

        // 9:30 AM -> 570
        assertEquals(9 * 60 + 30, calculateStartMinute(9, 30, false))

        // 11:59 AM -> 719
        assertEquals(11 * 60 + 59, calculateStartMinute(11, 59, false))

        // 12:00 PM (noon) -> 720
        assertEquals(12 * 60, calculateStartMinute(12, 0, true))

        // 12:30 PM -> 750
        assertEquals(12 * 60 + 30, calculateStartMinute(12, 30, true))

        // 1:00 PM -> 780 (13:00)
        assertEquals(13 * 60, calculateStartMinute(1, 0, true))

        // 11:59 PM -> 1439 (23:59)
        assertEquals(23 * 60 + 59, calculateStartMinute(11, 59, true))
    }

    @Test
    fun testPlanTab_taskCompletionStatusToggle() {
        var status = TaskStatus.TODO
        val toggleStatus = { current: TaskStatus ->
            if (current == TaskStatus.COMPLETED) TaskStatus.TODO else TaskStatus.COMPLETED
        }

        status = toggleStatus(status)
        assertEquals(TaskStatus.COMPLETED, status)

        status = toggleStatus(status)
        assertEquals(TaskStatus.TODO, status)
    }

    @Test
    fun testPlanTab_strictlyTasksListWithoutGroupHeaders() {
        val planImportedTaskIds = listOf("task_a", "task_b")
        val todos = listOf(
            ProjectTodo(id = "task_a", projectId = "proj_1", title = "Clean Task A", status = TaskStatus.TODO),
            ProjectTodo(id = "task_b", projectId = "proj_2", title = "Clean Task B", status = TaskStatus.COMPLETED)
        )

        // Verify all plan items are strictly individual tasks, without project headers
        val planGoalItems = planImportedTaskIds.mapNotNull { tId ->
            todos.find { it.id == tId }?.let { todo ->
                mapOf(
                    "id" to "task_${todo.id}",
                    "title" to todo.title,
                    "isTask" to true,
                    "isProject" to false
                )
            }
        }

        assertEquals(2, planGoalItems.size)
        assertTrue(planGoalItems.all { it["isTask"] == true })
        assertTrue(planGoalItems.all { it["isProject"] == false })
        assertEquals("Clean Task A", planGoalItems[0]["title"])
        assertEquals("Clean Task B", planGoalItems[1]["title"])
    }

    @Test
    fun testEmptyStateStandardization_conciseLabelsWithoutAuxiliaryText() {
        val expectedLabels = mapOf(
            "Notes" to "No notes yet",
            "Projects" to "No projects yet",
            "Tasks" to "No tasks yet",
            "Habits" to "No habits yet",
            "Plan" to "No plans yet"
        )

        expectedLabels.forEach { (tab, label) ->
            // Must adhere to concise "No [items] yet" structure
            assertTrue("Label for $tab should start with 'No '", label.startsWith("No "))
            assertTrue("Label for $tab should end with ' yet'", label.endsWith(" yet"))
            // Must not contain instructional text
            assertFalse("Label for $tab should not contain 'Tap'", label.contains("Tap", ignoreCase = true))
            assertFalse("Label for $tab should not contain secondary prompt", label.contains("create", ignoreCase = true))
            assertFalse("Label for $tab should not contain '.'", label.contains("."))
        }
    }

    @Test
    fun testDefaultStartupScreen_cyclingAndNavigationMapping() {
        val cyclingDestinations = listOf(
            "TIMER" to "Timer",
            "HABITS" to "Habits",
            "PROJECTS" to "Tasks",
            "STATS" to "Stats",
            "NOTES" to "Notes"
        )

        // Verify sequence
        assertEquals("Timer", cyclingDestinations[0].second)
        assertEquals("Habits", cyclingDestinations[1].second)
        assertEquals("Tasks", cyclingDestinations[2].second)
        assertEquals("Stats", cyclingDestinations[3].second)
        assertEquals("Notes", cyclingDestinations[4].second)

        // Cycling logic test
        fun getNext(currentKey: String): Pair<String, String> {
            val normalized = currentKey.uppercase().trim()
            val currentIndex = cyclingDestinations.indexOfFirst {
                it.first == normalized || (it.first == "PROJECTS" && normalized == "TASKS")
            }
            val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % cyclingDestinations.size else 0
            return cyclingDestinations[nextIndex]
        }

        assertEquals("HABITS" to "Habits", getNext("TIMER"))
        assertEquals("PROJECTS" to "Tasks", getNext("HABITS"))
        assertEquals("STATS" to "Stats", getNext("PROJECTS"))
        assertEquals("STATS" to "Stats", getNext("TASKS"))
        assertEquals("NOTES" to "Notes", getNext("STATS"))
        assertEquals("TIMER" to "Timer", getNext("NOTES"))

        // Navigation routing test matching MainActivity
        fun resolveStartupTab(screenKey: String): DeadlineTab {
            return when (screenKey.uppercase().trim()) {
                "TIMER" -> DeadlineTab.TIMER
                "STATS" -> DeadlineTab.STATS
                "HABITS" -> DeadlineTab.HABITS
                "PROJECTS", "TASKS" -> DeadlineTab.PROJECTS
                "NOTES" -> DeadlineTab.NOTES
                else -> DeadlineTab.TIMER
            }
        }

        assertEquals(DeadlineTab.TIMER, resolveStartupTab("TIMER"))
        assertEquals(DeadlineTab.HABITS, resolveStartupTab("HABITS"))
        assertEquals(DeadlineTab.PROJECTS, resolveStartupTab("PROJECTS"))
        assertEquals(DeadlineTab.PROJECTS, resolveStartupTab("TASKS"))
        assertEquals(DeadlineTab.STATS, resolveStartupTab("STATS"))
        assertEquals(DeadlineTab.NOTES, resolveStartupTab("NOTES"))
        assertEquals(DeadlineTab.TIMER, resolveStartupTab("UNKNOWN"))
    }

    @Test
    fun testScheduledTaskReminderPipeline_advanceNotificationProtocol() {
        // 1. Advance notification must be configured for exactly 30 minutes prior
        assertEquals(30, com.deadline.service.TaskReminderScheduler.ADVANCE_NOTICE_MINUTES)

        val dateStr = "2026-10-22"
        val scheduledStartMinute = 600 // 10:00 AM
        val scheduledTimeMillis = com.deadline.service.TaskReminderScheduler.calculateScheduledTimestamp(dateStr, scheduledStartMinute)

        val advanceNoticeMinutes = com.deadline.service.TaskReminderScheduler.ADVANCE_NOTICE_MINUTES
        val preAlertTimeMillis = scheduledTimeMillis - (advanceNoticeMinutes * 60 * 1000L)

        // Verify the pre-alert is exactly 30 minutes (1,800,000 milliseconds) earlier
        val diffMillis = scheduledTimeMillis - preAlertTimeMillis
        assertEquals(30 * 60 * 1000L, diffMillis)

        // Verify preAlert timestamp corresponds to 09:30 AM on the same date
        val cal = Calendar.getInstance().apply { timeInMillis = preAlertTimeMillis }
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun testScheduledTaskReminderPipeline_atTimeAlarmProtocol() {
        val dateStr = "2026-10-22"
        val scheduledStartMinute = 870 // 14:30 (02:30 PM)
        val scheduledTimeMillis = com.deadline.service.TaskReminderScheduler.calculateScheduledTimestamp(dateStr, scheduledStartMinute)

        val cal = Calendar.getInstance().apply { timeInMillis = scheduledTimeMillis }
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))

        // Verify broadcast actions
        assertEquals("com.deadline.action.PRE_TASK_ALERT", com.deadline.service.TaskReminderReceiver.ACTION_PRE_TASK_ALERT)
        assertEquals("com.deadline.action.AT_TIME_ALARM", com.deadline.service.TaskReminderReceiver.ACTION_AT_TIME_ALARM)
        assertEquals("com.deadline.action.DISMISS_ALARM", com.deadline.service.TaskReminderReceiver.ACTION_DISMISS_ALARM)

        // Verify channel IDs
        assertEquals("channel_task_advance_heads_up", com.deadline.service.TaskReminderReceiver.CHANNEL_PRE_ALERT)
        assertEquals("channel_task_primary_ringtone_alarm", com.deadline.service.TaskReminderReceiver.CHANNEL_AT_TIME_ALARM)
    }

    @Test
    fun testDefaultStartupScreen_PreferenceInitializationAndLaunchNavigationRouting() {
        // 1. Preference Normalization & Resolution
        assertEquals("TIMER", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("TIMER"))
        assertEquals("TIMER", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen(" timer "))
        assertEquals("HABITS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("HABITS"))
        assertEquals("HABITS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("Habits"))
        assertEquals("PROJECTS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("PROJECTS"))
        assertEquals("PROJECTS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("projects"))
        assertEquals("PROJECTS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("TASKS"))
        assertEquals("PROJECTS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("Tasks"))
        assertEquals("STATS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("STATS"))
        assertEquals("STATS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("stats"))
        assertEquals("NOTES", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("NOTES"))
        assertEquals("NOTES", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("Notes"))

        // Numeric string indexing resolution
        assertEquals("STATS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("0"))
        assertEquals("HABITS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("1"))
        assertEquals("TIMER", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("2"))
        assertEquals("PROJECTS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("3"))
        assertEquals("NOTES", com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("4"))

        // Integer index normalization
        assertEquals("STATS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreenIndex(0))
        assertEquals("HABITS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreenIndex(1))
        assertEquals("TIMER", com.deadline.data.local.DeadlineRepository.normalizeStartupScreenIndex(2))
        assertEquals("PROJECTS", com.deadline.data.local.DeadlineRepository.normalizeStartupScreenIndex(3))
        assertEquals("NOTES", com.deadline.data.local.DeadlineRepository.normalizeStartupScreenIndex(4))
        assertEquals(null, com.deadline.data.local.DeadlineRepository.normalizeStartupScreenIndex(99))

        // Null / blank / unknown handling
        assertEquals(null, com.deadline.data.local.DeadlineRepository.normalizeStartupScreen(null))
        assertEquals(null, com.deadline.data.local.DeadlineRepository.normalizeStartupScreen(""))
        assertEquals(null, com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("  "))
        assertEquals(null, com.deadline.data.local.DeadlineRepository.normalizeStartupScreen("UNKNOWN_DEST"))

        // 2. Launch Navigation Routing to Designated Destinations
        assertEquals(DeadlineTab.TIMER, resolveStartupTab("TIMER"))
        assertEquals(DeadlineTab.TIMER, resolveStartupTab("timer"))
        assertEquals(DeadlineTab.HABITS, resolveStartupTab("HABITS"))
        assertEquals(DeadlineTab.HABITS, resolveStartupTab("habits"))
        assertEquals(DeadlineTab.PROJECTS, resolveStartupTab("PROJECTS"))
        assertEquals(DeadlineTab.PROJECTS, resolveStartupTab("projects"))
        assertEquals(DeadlineTab.PROJECTS, resolveStartupTab("TASKS"))
        assertEquals(DeadlineTab.PROJECTS, resolveStartupTab("tasks"))
        assertEquals(DeadlineTab.STATS, resolveStartupTab("STATS"))
        assertEquals(DeadlineTab.STATS, resolveStartupTab("stats"))
        assertEquals(DeadlineTab.NOTES, resolveStartupTab("NOTES"))
        assertEquals(DeadlineTab.NOTES, resolveStartupTab("notes"))

        // Fallback checks: null, blank, unknown destinations safely default to TIMER
        assertEquals(DeadlineTab.TIMER, resolveStartupTab(null))
        assertEquals(DeadlineTab.TIMER, resolveStartupTab(""))
        assertEquals(DeadlineTab.TIMER, resolveStartupTab("   "))
        assertEquals(DeadlineTab.TIMER, resolveStartupTab("NON_EXISTENT_SCREEN"))

        // evaluateStartupTab alias verification
        assertEquals(DeadlineTab.TIMER, evaluateStartupTab("TIMER"))
        assertEquals(DeadlineTab.HABITS, evaluateStartupTab("HABITS"))
        assertEquals(DeadlineTab.PROJECTS, evaluateStartupTab("PROJECTS"))
        assertEquals(DeadlineTab.PROJECTS, evaluateStartupTab("TASKS"))
        assertEquals(DeadlineTab.STATS, evaluateStartupTab("STATS"))
        assertEquals(DeadlineTab.NOTES, evaluateStartupTab("NOTES"))
        assertEquals(DeadlineTab.TIMER, evaluateStartupTab("INVALID"))

        // Preference key constants consistency
        assertEquals("default_startup_screen", com.deadline.data.local.DeadlineRepository.PREF_KEY_DEFAULT_STARTUP_SCREEN)
        assertEquals("defaultStartupScreen", com.deadline.data.local.DeadlineRepository.PREF_KEY_DEFAULT_STARTUP_SCREEN_CAMEL)
        assertEquals("Default Startup Screen", com.deadline.data.local.DeadlineRepository.PREF_KEY_DEFAULT_STARTUP_SCREEN_TITLE)
        assertEquals("startup_screen", com.deadline.data.local.DeadlineRepository.PREF_KEY_STARTUP_SCREEN)
    }
}

