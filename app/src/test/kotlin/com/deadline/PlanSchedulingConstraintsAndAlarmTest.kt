package com.deadline

import com.deadline.service.TaskReminderScheduler
import com.deadline.ui.habits.generatePlanDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class PlanSchedulingConstraintsAndAlarmTest {

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(relativePath.removePrefix("app/")),
            File("app", relativePath),
            File("app", relativePath.removePrefix("app/"))
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    // -------------------------------------------------------------------------
    // 1. Real-Time Scheduling Constraints (Past Date & Time Prevention)
    // -------------------------------------------------------------------------

    @Test
    fun testGeneratePlanDates_defaultsToTodayWithoutPastDates() {
        val anchorCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 28)
        }

        // By default, pastDays = 0, so the first element must be today
        val dates = generatePlanDates(anchorCalendar = anchorCal)
        assertEquals(61, dates.size)
        assertTrue("First date in plan picker must be today", dates[0].isToday)
        assertFalse("Today must not be marked as past", dates[0].isPast)
        assertEquals("2026-10-28", dates[0].dateString)

        // All subsequent days must be in the future
        for (i in 1 until dates.size) {
            assertFalse("Future dates must not be marked as past", dates[i].isPast)
            assertFalse("Future dates must not be marked as today", dates[i].isToday)
        }
    }

    @Test
    fun testGeneratePlanDates_flagsPastDatesWhenPastDaysSpecified() {
        val anchorCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 28)
        }

        val dates = generatePlanDates(anchorCalendar = anchorCal, pastDays = 3, futureDays = 5)
        assertEquals(9, dates.size)

        // Indices 0, 1, 2 are in the past
        assertTrue("Past date Oct 25 must be marked isPast", dates[0].isPast)
        assertEquals("2026-10-25", dates[0].dateString)
        assertTrue("Past date Oct 26 must be marked isPast", dates[1].isPast)
        assertEquals("2026-10-26", dates[1].dateString)
        assertTrue("Past date Oct 27 must be marked isPast", dates[2].isPast)
        assertEquals("2026-10-27", dates[2].dateString)

        // Index 3 is today
        assertFalse("Today must not be marked isPast", dates[3].isPast)
        assertTrue("Index 3 must be today", dates[3].isToday)
        assertEquals("2026-10-28", dates[3].dateString)

        // Indices 4..8 are future days
        assertFalse("Future date Oct 29 must not be isPast", dates[4].isPast)
        assertEquals("2026-10-29", dates[4].dateString)
    }

    @Test
    fun testPastTimeCalculation_distinguishesPastAndFutureMoments() {
        val nowCal = Calendar.getInstance()
        val curYear = nowCal.get(Calendar.YEAR)
        val curMonth = nowCal.get(Calendar.MONTH) + 1
        val curDay = nowCal.get(Calendar.DAY_OF_MONTH)
        val todayStr = String.format("%04d-%02d-%02d", curYear, curMonth, curDay)

        val curHour = nowCal.get(Calendar.HOUR_OF_DAY)
        val curMin = nowCal.get(Calendar.MINUTE)
        val currentTotalMinutes = curHour * 60 + curMin

        // 1. Earlier today (10 minutes ago) -> Must be in the past
        if (currentTotalMinutes >= 10) {
            val pastMinute = currentTotalMinutes - 10
            val pastTimestamp = TaskReminderScheduler.calculateScheduledTimestamp(todayStr, pastMinute)
            assertTrue("Timestamp 10 minutes ago must be <= current system time", pastTimestamp <= System.currentTimeMillis())
        }

        // 2. Later today (30 minutes from now) -> Must be in the future
        if (currentTotalMinutes <= 1400) {
            val futureMinute = currentTotalMinutes + 30
            val futureTimestamp = TaskReminderScheduler.calculateScheduledTimestamp(todayStr, futureMinute)
            assertTrue("Timestamp 30 minutes in future must be > current system time", futureTimestamp > System.currentTimeMillis())
        }

        // 3. Tomorrow -> Always in the future
        val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrowStr = String.format(
            "%04d-%02d-%02d",
            tomorrowCal.get(Calendar.YEAR),
            tomorrowCal.get(Calendar.MONTH) + 1,
            tomorrowCal.get(Calendar.DAY_OF_MONTH)
        )
        val tomorrowTimestamp = TaskReminderScheduler.calculateScheduledTimestamp(tomorrowStr, 9 * 60)
        assertTrue("Tomorrow 9:00 AM must be in the future", tomorrowTimestamp > System.currentTimeMillis())

        // 4. Yesterday -> Always in the past
        val yesterdayCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val yesterdayStr = String.format(
            "%04d-%02d-%02d",
            yesterdayCal.get(Calendar.YEAR),
            yesterdayCal.get(Calendar.MONTH) + 1,
            yesterdayCal.get(Calendar.DAY_OF_MONTH)
        )
        val yesterdayTimestamp = TaskReminderScheduler.calculateScheduledTimestamp(yesterdayStr, 15 * 60)
        assertTrue("Yesterday 3:00 PM must be in the past", yesterdayTimestamp <= System.currentTimeMillis())
    }

    @Test
    fun testPlanView_enforcesPastDateAndPastTimePreventionWiring() {
        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", planViewFile.exists())
        val content = planViewFile.readText()

        // 1. DayPillCapsule click disabled for past dates
        assertTrue(
            "DayPillCapsule must disable clickable on past dates",
            content.contains(".clickable(enabled = !day.isPast")
        )

        // 2. HorizontalPlanDatePicker ignores past centered items
        assertTrue(
            "HorizontalPlanDatePicker must not select past centered items",
            content.contains("if (!centeredItem.isPast")
        )

        // 3. ScheduleTimePickerSheet accepts selectedDateString
        assertTrue(
            "ScheduleTimePickerSheet must accept selectedDateString",
            content.contains("selectedDateString: String? = null")
        )

        // 4. ScheduleTimePickerSheet validates past time against current minute
        assertTrue(
            "ScheduleTimePickerSheet must evaluate isTimeInPast",
            content.contains("val isTimeInPast = isPastDate || (isToday && startMinute <= currentMinuteOfDay)")
        )
        assertTrue(
            "ScheduleTimePickerSheet must gate schedule creation with canSchedule",
            content.contains("val canSchedule = !isTimeInPast")
        )

        // 5. Warning indicator when past time selected
        assertTrue(
            "ScheduleTimePickerSheet must warn about scheduling in the past",
            content.contains("Cannot schedule in the past")
        )

        // 6. Action button must guard against past time submissions
        assertTrue(
            "Schedule button must guard with canSchedule",
            content.contains("if (!isSubmitting && canSchedule)")
        )
    }

    // -------------------------------------------------------------------------
    // 2. Date Selection & Timeline Sync (Independent Daily Timelines)
    // -------------------------------------------------------------------------

    @Test
    fun testTimelineSync_architectureWiringAcrossScreens() {
        val repoFile = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineRepository.kt")
        assertTrue("DeadlineRepository.kt must exist", repoFile.exists())
        val repoContent = repoFile.readText()

        // 1. DeadlineRepository exposes selectedPlanDate and timelineTimeBlocks
        assertTrue("Repository must expose selectedPlanDate", repoContent.contains("val selectedPlanDate: StateFlow<String>"))
        assertTrue("Repository must expose timelineTimeBlocks", repoContent.contains("val timelineTimeBlocks: StateFlow<List<TimeBlock>>"))
        assertTrue("Repository must have setSelectedPlanDate", repoContent.contains("fun setSelectedPlanDate(dateStr: String)"))
        assertTrue("Repository must have loadTimelineTimeBlocks", repoContent.contains("fun loadTimelineTimeBlocks("))

        // 2. PlanView updates repository.setSelectedPlanDate upon date selection
        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        val planViewContent = planViewFile.readText()
        assertTrue(
            "PlanView must update repository.setSelectedPlanDate",
            planViewContent.contains("repository.setSelectedPlanDate(date.dateString)") ||
            planViewContent.contains("repository.setSelectedPlanDate(dateStr)")
        )

        // 3. ProjectsScreen observes timelineTimeBlocks and gates current-time indicator
        val projectsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        val projectsContent = projectsFile.readText()
        assertTrue(
            "ProjectsScreen must observe repository.timelineTimeBlocks",
            projectsContent.contains("repository.timelineTimeBlocks.collectAsState()")
        )
        assertTrue(
            "ProjectsScreen must observe repository.selectedPlanDate",
            projectsContent.contains("repository.selectedPlanDate.collectAsState()")
        )
        assertTrue(
            "ProjectsScreen must gate live indicator to only today",
            projectsContent.contains("val isCurrentHour = isViewingToday && (currentHour == hour)")
        )

        // 4. PlanScreen observes timelineTimeBlocks and gates current-time indicator
        val planScreenFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/plan/PlanScreen.kt")
        val planScreenContent = planScreenFile.readText()
        assertTrue(
            "PlanScreen must observe repository.timelineTimeBlocks",
            planScreenContent.contains("repository.timelineTimeBlocks.collectAsState()")
        )
        assertTrue(
            "PlanScreen must gate live indicator to only today",
            planScreenContent.contains("val isCurrentHour = isViewingToday && (currentHour == hour)")
        )
    }

    // -------------------------------------------------------------------------
    // 3. Proactive Alarm Management (Synchronized Deletion)
    // -------------------------------------------------------------------------

    @Test
    fun testSynchronizedDeletion_cancelTaskRemindersCancelsPendingIntentsAndSilencesAudio() {
        val schedulerFile = getSourceFile("app/src/main/kotlin/com/deadline/service/TaskReminderScheduler.kt")
        assertTrue("TaskReminderScheduler.kt must exist", schedulerFile.exists())
        val schedulerContent = schedulerFile.readText()

        // 1. cancelTaskReminders cancels both pre-alert and at-time pending intents
        assertTrue("cancelTaskReminders must cancel preAlertPendingIntent", schedulerContent.contains("alarmManager.cancel(preAlertPendingIntent)"))
        assertTrue("cancelTaskReminders must cancel atTimePendingIntent", schedulerContent.contains("alarmManager.cancel(atTimePendingIntent)"))

        // 2. Immediately cancels active notification and stops alarm audio
        assertTrue("cancelTaskReminders must dismiss notification", schedulerContent.contains("notificationManager?.cancel(baseRequestCode)"))
        assertTrue("cancelTaskReminders must stop alarm audio", schedulerContent.contains("AlarmRingtonePlayer.stop()"))
    }

    @Test
    fun testSynchronizedDeletion_removingTaskFromPlanCancelsBlocksAndAlarms() {
        val repoFile = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineRepository.kt")
        val repoContent = repoFile.readText()

        val removeTaskMethod = repoContent.substringAfter("suspend fun removeTaskFromPlan(taskId: String)")
            .substringBefore("suspend fun reorderPlanTasks")

        // 1. Deletes associated timeBlocks from DB
        assertTrue(
            "removeTaskFromPlan must query associated block IDs",
            removeTaskMethod.contains("SELECT id FROM timeBlocks WHERE todoId = ?")
        )
        assertTrue(
            "removeTaskFromPlan must delete timeBlocks where todoId = taskId",
            removeTaskMethod.contains("db.delete(\"timeBlocks\", \"todoId = ?\", arrayOf(taskId))")
        )

        // 2. Immediately cancels reminders for each associated block
        assertTrue(
            "removeTaskFromPlan must cancel reminders for each associated block",
            removeTaskMethod.contains("com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, blockId)")
        )

        // 3. Cancels reminder for the task itself
        assertTrue(
            "removeTaskFromPlan must cancel reminder for the task itself",
            removeTaskMethod.contains("com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, taskId)")
        )

        // 4. Reloads timeline and today timeblocks
        assertTrue(
            "removeTaskFromPlan must reload today time blocks",
            removeTaskMethod.contains("loadTodayTimeBlocks()")
        )
        assertTrue(
            "removeTaskFromPlan must reload timeline time blocks",
            removeTaskMethod.contains("loadTimelineTimeBlocks(_selectedPlanDate.value)")
        )
    }

    @Test
    fun testSynchronizedDeletion_deletingTimeBlockCancelsAlarmsAndTodoLinks() {
        val repoFile = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineRepository.kt")
        val repoContent = repoFile.readText()

        val deleteBlockMethod = repoContent.substringAfter("suspend fun deleteTimeBlock(blockId: String)")
            .substringBefore("suspend fun updateTimeBlock")

        // 1. Deletes timeblock from database
        assertTrue(
            "deleteTimeBlock must delete block by id",
            deleteBlockMethod.contains("db.delete(\"timeBlocks\", \"id = ?\", arrayOf(blockId))")
        )

        // 2. Cancels reminders for blockId
        assertTrue(
            "deleteTimeBlock must cancel reminders for blockId",
            deleteBlockMethod.contains("com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, blockId)")
        )

        // 3. Cancels reminders for linked todoId if present
        assertTrue(
            "deleteTimeBlock must cancel reminders for linked todoId",
            deleteBlockMethod.contains("com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, todoId)")
        )

        // 4. Reloads timeline and today timeblocks
        assertTrue(
            "deleteTimeBlock must reload today time blocks",
            deleteBlockMethod.contains("loadTodayTimeBlocks()")
        )
        assertTrue(
            "deleteTimeBlock must reload timeline time blocks",
            deleteBlockMethod.contains("loadTimelineTimeBlocks(_selectedPlanDate.value)")
        )
    }

    @Test
    fun testSynchronizedDeletion_timeBlockCardSwipeToDeleteInPlanScreen() {
        val planScreenFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/plan/PlanScreen.kt")
        val planScreenContent = planScreenFile.readText()

        // TimeBlockCard must be wrapped in UniversalSwipeableItem with onDelete invoking repository.deleteTimeBlock
        assertTrue(
            "PlanScreen must wrap TimeBlockCard in UniversalSwipeableItem",
            planScreenContent.contains("UniversalSwipeableItem(") &&
            planScreenContent.contains("repository.deleteTimeBlock(block.id)")
        )
    }
}
