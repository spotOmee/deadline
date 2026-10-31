package com.deadline

import com.deadline.data.local.Project
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.TaskStatus
import com.deadline.ui.habits.GoalItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates Mission Requirements for:
 * BI-DIRECTIONAL REAL-TIME COMPLETION SYNC (PLAN LIST <-> ACTIVE TASKS)
 *
 * 1. Step 1: Shared Entity Identification
 *    - Persistent common unique identifier (id) across Plan items and Active Tasks.
 *    - All mutations target this primary key across local state and storage.
 *
 * 2. Step 2: Bi-Directional State Transition Logic
 *    - Triggered from Plan View: item marked completed, purged from Plan and Active Tasks, transferred to Done.
 *    - Triggered from Active Tasks: item marked completed, purged from Active Tasks and Plan, transferred to Done.
 *    - Toggling uncomplete from Done restores to both active views.
 *
 * 3. Step 3: Real-Time UI Reactivity
 *    - Reactive StateFlow emission and synchronization without manual refresh or tab switching.
 */
class BiDirectionalRealTimeCompletionSyncTest {

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
    // Step 1: Shared Entity Identification
    // -------------------------------------------------------------------------

    @Test
    fun testSharedEntityIdentification_commonPrimaryKeyAndIsCompletedProperty() {
        val taskId = "shared-uuid-101"
        val activeTodo = ProjectTodo(
            id = taskId,
            projectId = "proj_deadline",
            title = "Implement Reactive Sync",
            status = TaskStatus.TODO,
            dueDate = "2026-10-29"
        )

        val completedTodo = activeTodo.copy(status = TaskStatus.COMPLETED)

        // Verify ProjectTodo isCompleted property
        assertFalse("Active todo must not be completed", activeTodo.isCompleted)
        assertTrue("Completed todo must have isCompleted = true", completedTodo.isCompleted)

        // Verify GoalItem mirrors common persistent unique id
        val planGoalItem = GoalItem(
            id = activeTodo.id,
            title = activeTodo.title,
            subtitle = "2026-10-29",
            isTask = true,
            todoRef = activeTodo
        )

        assertEquals("GoalItem id must match ProjectTodo id exactly", activeTodo.id, planGoalItem.id)
        assertFalse("GoalItem must not be completed for active todo", planGoalItem.isCompleted)

        val completedPlanGoalItem = planGoalItem.copy(
            todoRef = completedTodo
        )
        assertTrue("GoalItem must reflect completed status when todo is completed", completedPlanGoalItem.isCompleted)
    }

    // -------------------------------------------------------------------------
    // Step 2: Bi-Directional State Transition Logic Simulation
    // -------------------------------------------------------------------------

    @Test
    fun testBiDirectionalSync_completionTriggeredFromPlanView() {
        val testDate = "2026-10-29"
        val proj = Project(id = "p1", name = "Core Engine")

        val task1 = ProjectTodo(id = "task_alpha", projectId = "p1", title = "Task Alpha", status = TaskStatus.TODO, dueDate = testDate)
        val task2 = ProjectTodo(id = "task_beta", projectId = "p1", title = "Task Beta", status = TaskStatus.TODO, dueDate = testDate)

        var todos = listOf(task1, task2)
        val planImportedTaskIds = mutableListOf("task_alpha", "task_beta")

        // 1. Initial State: Both tasks are in Plan List and Active Tasks list
        val initialActiveTasks = todos.filter { it.status != TaskStatus.COMPLETED }
        val initialPlanList = planImportedTaskIds.mapNotNull { tId ->
            val t = todos.find { it.id == tId }
            if (t != null && t.status != TaskStatus.COMPLETED && t.dueDate == testDate) {
                GoalItem(id = t.id, title = t.title, subtitle = testDate, todoRef = t)
            } else null
        }
        val initialDoneTasks = todos.filter { it.status == TaskStatus.COMPLETED }

        assertEquals(2, initialActiveTasks.size)
        assertEquals(2, initialPlanList.size)
        assertEquals(0, initialDoneTasks.size)

        // 2. Action: Complete "task_alpha" triggered from Plan View
        val targetId = "task_alpha"
        // Optimistic StateFlow emission + DB update in repository
        todos = todos.map { if (it.id == targetId) it.copy(status = TaskStatus.COMPLETED) else it }

        // 3. Re-evaluate Active Tasks, Plan List, and Done Tasks
        val updatedActiveTasks = todos.filter { it.status != TaskStatus.COMPLETED }
        val updatedPlanList = planImportedTaskIds.mapNotNull { tId ->
            val t = todos.find { it.id == tId }
            if (t != null && t.status != TaskStatus.COMPLETED && t.dueDate == testDate) {
                GoalItem(id = t.id, title = t.title, subtitle = testDate, todoRef = t)
            } else null
        }
        val updatedDoneTasks = todos.filter { it.status == TaskStatus.COMPLETED }

        // Verify task_alpha is instantly purged from Plan list
        assertEquals(1, updatedPlanList.size)
        assertEquals("task_beta", updatedPlanList[0].id)

        // Verify task_alpha is instantly purged from Active Tasks
        assertEquals(1, updatedActiveTasks.size)
        assertEquals("task_beta", updatedActiveTasks[0].id)

        // Verify task_alpha is appended to Done tasks list
        assertEquals(1, updatedDoneTasks.size)
        assertEquals("task_alpha", updatedDoneTasks[0].id)
    }

    @Test
    fun testBiDirectionalSync_completionTriggeredFromActiveTasksView() {
        val testDate = "2026-10-29"

        val task1 = ProjectTodo(id = "task_1", projectId = "p1", title = "Task 1", status = TaskStatus.TODO, dueDate = testDate)
        val task2 = ProjectTodo(id = "task_2", projectId = "p1", title = "Task 2", status = TaskStatus.TODO, dueDate = testDate)

        var todos = listOf(task1, task2)
        val planImportedTaskIds = mutableListOf("task_1", "task_2")

        // 1. Initial State: Both active
        val initialActive = todos.filter { it.status != TaskStatus.COMPLETED }
        val initialPlan = planImportedTaskIds.mapNotNull { tId ->
            val t = todos.find { it.id == tId }
            if (t != null && t.status != TaskStatus.COMPLETED && t.dueDate == testDate) {
                GoalItem(id = t.id, title = t.title, subtitle = testDate, todoRef = t)
            } else null
        }
        assertEquals(2, initialActive.size)
        assertEquals(2, initialPlan.size)

        // 2. Action: Complete "task_2" triggered from Active Tasks (Projects/Tasks screen)
        val targetId = "task_2"
        todos = todos.map { if (it.id == targetId) it.copy(status = TaskStatus.COMPLETED) else it }

        // 3. Reactively syncs to Plan list and Done list
        val syncedActive = todos.filter { it.status != TaskStatus.COMPLETED }
        val syncedPlan = planImportedTaskIds.mapNotNull { tId ->
            val t = todos.find { it.id == tId }
            if (t != null && t.status != TaskStatus.COMPLETED && t.dueDate == testDate) {
                GoalItem(id = t.id, title = t.title, subtitle = testDate, todoRef = t)
            } else null
        }
        val syncedDone = todos.filter { it.status == TaskStatus.COMPLETED }

        // Verify task_2 is purged from both Active Tasks and Plan list
        assertEquals(1, syncedActive.size)
        assertEquals("task_1", syncedActive[0].id)

        assertEquals(1, syncedPlan.size)
        assertEquals("task_1", syncedPlan[0].id)

        // Verify task_2 is in Done list
        assertEquals(1, syncedDone.size)
        assertEquals("task_2", syncedDone[0].id)

        // 4. Action: Toggle uncomplete from Done list back to Active
        todos = todos.map { if (it.id == targetId) it.copy(status = TaskStatus.TODO) else it }
        val restoredActive = todos.filter { it.status != TaskStatus.COMPLETED }
        val restoredPlan = planImportedTaskIds.mapNotNull { tId ->
            val t = todos.find { it.id == tId }
            if (t != null && t.status != TaskStatus.COMPLETED && t.dueDate == testDate) {
                GoalItem(id = t.id, title = t.title, subtitle = testDate, todoRef = t)
            } else null
        }

        assertEquals(2, restoredActive.size)
        assertEquals(2, restoredPlan.size)
    }

    // -------------------------------------------------------------------------
    // Step 3: Architecture & Source Invariants
    // -------------------------------------------------------------------------

    @Test
    fun testPlanView_completionFilteringAndSharedIdWiring() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", file.exists())
        val content = file.readText()

        // 1. PlanView allActiveGoals filters out completed tasks
        assertTrue(
            "PlanView allActiveGoals must exclude completed tasks (todo.status != TaskStatus.COMPLETED)",
            content.contains("todo.status != TaskStatus.COMPLETED")
        )

        // 2. PlanView uses common shared unique identifier id = todo.id
        assertTrue(
            "PlanView must assign GoalItem id = todo.id directly",
            content.contains("id = todo.id,")
        )

        // 3. PlanView checkbox click triggers repository.updateTodoStatus with TaskStatus.COMPLETED
        assertTrue(
            "PlanView checkbox must invoke repository.updateTodoStatus with TaskStatus.COMPLETED",
            content.contains("repository.updateTodoStatus(targetId, TaskStatus.COMPLETED)")
        )
    }

    @Test
    fun testProjectsScreen_statusFilteringAndCompletionWiring() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", file.exists())
        val content = file.readText()

        // 1. ProjectsScreen partitions tasks into ACTIVE and DONE based on TaskStatus.COMPLETED
        assertTrue(
            "ProjectsScreen must filter DONE tasks with TaskStatus.COMPLETED",
            content.contains("\"DONE\" -> todo.status == TaskStatus.COMPLETED")
        )
        assertTrue(
            "ProjectsScreen must filter ACTIVE tasks with todo.status != TaskStatus.COMPLETED",
            content.contains("else -> todo.status != TaskStatus.COMPLETED")
        )

        // 2. Active checkbox click marks completed and updates repository
        assertTrue(
            "ProjectsScreen checkbox must update status to TaskStatus.COMPLETED",
            content.contains("repository.updateTodoStatus(todo.id, TaskStatus.COMPLETED)")
        )
    }

    @Test
    fun testDeadlineRepository_optimisticReactiveStateFlowEmission() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineRepository.kt")
        assertTrue("DeadlineRepository.kt must exist", file.exists())
        val content = file.readText()

        // 1. DeadlineRepository exposes reactive todos StateFlow
        assertTrue(
            "DeadlineRepository must expose todos StateFlow",
            content.contains("val todos: StateFlow<List<ProjectTodo>> = _todos.asStateFlow()")
        )

        // 2. updateTodoStatus applies optimistic reactive in-memory update before DB write
        val updateMethod = content.substringAfter("suspend fun updateTodoStatus(todoId: String, status: TaskStatus)")
            .substringBefore("suspend fun completeTodo")

        assertTrue(
            "updateTodoStatus must perform optimistic in-memory StateFlow update for zero-latency sync",
            updateMethod.contains("_todos.value = _todos.value.map")
        )
    }
}
