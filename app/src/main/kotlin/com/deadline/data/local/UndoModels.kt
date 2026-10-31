package com.deadline.data.local

/**
 * Encapsulates the cached metadata, list position, and associated relationships
 * for an item deleted, removed, or archived across any module (Tasks, Habits, Projects, Notes).
 */
sealed class UndoPayload {
    abstract val id: String
    abstract val message: String
    abstract val originalIndex: Int
    abstract val timestamp: Long

    data class TodoPayload(
        val todo: ProjectTodo,
        override val originalIndex: Int,
        val wasInPlan: Boolean,
        val associatedTimeBlocks: List<TimeBlock> = emptyList(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : UndoPayload() {
        override val id: String = todo.id
        override val message: String = "Deleted task \"${todo.title}\""
    }

    data class HabitPayload(
        val habit: Habit,
        override val originalIndex: Int,
        val completions: List<HabitCompletion> = emptyList(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : UndoPayload() {
        override val id: String = habit.id
        override val message: String = "Deleted habit \"${habit.name}\""
    }

    data class ProjectPayload(
        val project: Project,
        override val originalIndex: Int,
        val associatedTodos: List<ProjectTodo> = emptyList(),
        val associatedTimeBlocks: List<TimeBlock> = emptyList(),
        val planImportedIds: List<String> = emptyList(),
        val wasInPlan: Boolean = false,
        val isArchivedAction: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis()
    ) : UndoPayload() {
        override val id: String = project.id
        override val message: String = if (isArchivedAction) "Archived project \"${project.name}\"" else "Deleted project \"${project.name}\""
    }

    data class NotePayload(
        val note: QuickNote,
        override val originalIndex: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : UndoPayload() {
        override val id: String = note.id
        override val message: String = "Deleted note \"${note.title.ifBlank { "Note" }}\""
    }
}
