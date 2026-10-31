package com.deadline

import com.deadline.data.local.QuickNote
import com.deadline.ui.notes.NotesSectioningLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MinimalistUIPurgeTest {

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
    // 1. Notes List Minimalism: Secondary Metadata & Fallback Removal
    // -------------------------------------------------------------------------

    @Test
    fun testNotesScreen_previewTextAndTimestampsRemovedFromNoteCards() {
        val notesFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/notes/NotesScreen.kt")
        assertTrue("NotesScreen.kt must exist", notesFile.exists())
        val content = notesFile.readText()

        // 1. Preview text fallback 'No additional text' must be completely absent from note cards
        assertFalse(
            "Note card must not render 'No additional text' fallback preview text",
            content.contains("\"No additional text\"")
        )

        // 2. Secondary metadata timestamp format/display on note cards must be removed
        assertFalse(
            "Note card must not format or render dateString timestamps on list cards",
            content.contains("val dateString =") || content.contains("text = dateString")
        )

        // 3. Redundant note type badges (ACTIVE, NOTE, IMPORTANT) must be removed from list cards
        assertFalse(
            "Note card must not render displayType badge",
            content.contains("val displayType = if (note.noteType")
        )
    }

    @Test
    fun testNotesSectioning_cardDisplayRetainsTitleAndPinningLifecycle() {
        val note = QuickNote(
            id = "note-minimal",
            title = "Clean Title Only",
            content = "Hidden body content not shown on card",
            isPinned = false
        )

        // Ensure title is preserved
        assertEquals("Clean Title Only", note.title)

        // Sectioning and pin toggle must continue functioning seamlessly
        val list = listOf(note)
        val partitioned = NotesSectioningLogic.partitionNotes(list)
        assertFalse(partitioned.isSectioned)
        assertEquals(1, partitioned.otherNotes.size)
        assertEquals("note-minimal", partitioned.otherNotes.first().id)

        val pinned = NotesSectioningLogic.togglePin(list, "note-minimal")
        val repatitioned = NotesSectioningLogic.partitionNotes(pinned)
        assertTrue(repatitioned.isSectioned)
        assertEquals(1, repatitioned.pinnedNotes.size)
        assertEquals("note-minimal", repatitioned.pinnedNotes.first().id)
    }

    // -------------------------------------------------------------------------
    // 2. Import Modal Decluttering
    // -------------------------------------------------------------------------

    @Test
    fun testPlanView_importModalPurgedOfInstructionalPromptAndMetadataTags() {
        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", planViewFile.exists())
        val content = planViewFile.readText()

        val importBottomSheetCode = content.substringAfter("fun ImportTaskBottomSheet")

        // 1. Instructional prompt text must be eliminated
        assertFalse(
            "Import modal must eliminate instructional prompt text",
            importBottomSheetCode.contains("Select tasks to import directly into your plan")
        )

        // 2. Task metadata tags (priority badges, duration indicators, project descriptions) must be stripped
        assertFalse(
            "Import modal items must strip priority badges",
            importBottomSheetCode.contains("todo.priority")
        )
        assertFalse(
            "Import modal items must strip duration indicators",
            importBottomSheetCode.contains("estimatedMinutes")
        )

        // 3. Clean task titles remain
        assertTrue(
            "Import modal must display clean task title",
            importBottomSheetCode.contains("text = todo.title")
        )
    }

    // -------------------------------------------------------------------------
    // 3. General Header & List Purification
    // -------------------------------------------------------------------------

    @Test
    fun testPlanScreen_redundantSubtextPurged() {
        val planScreenFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/plan/PlanScreen.kt")
        assertTrue("PlanScreen.kt must exist", planScreenFile.exists())
        val content = planScreenFile.readText()

        assertFalse(
            "PlanScreen header must not contain redundant instructional subtext",
            content.contains("Structured visual blocks for deep focus")
        )
    }

    @Test
    fun testJournalStatsScreen_redundantSubtextPurged() {
        val statsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/journal/JournalStatsScreen.kt")
        assertTrue("JournalStatsScreen.kt must exist", statsFile.exists())
        val content = statsFile.readText()

        assertFalse(
            "JournalStatsScreen header must not contain redundant instructional subtext",
            content.contains("Daily adherence score, focus metrics & evening journal")
        )
    }

    @Test
    fun testProjectsScreen_reorderInstructionSubtextPurged() {
        val projectsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", projectsFile.exists())
        val content = projectsFile.readText()

        assertFalse(
            "ProjectsScreen modal must not contain redundant instructional subtext",
            content.contains("Use ▲ ▼ to reorder")
        )
    }

    @Test
    fun testProfileScreen_backupInstructionsPurged() {
        val profileFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/profile/ProfileScreen.kt")
        assertTrue("ProfileScreen.kt must exist", profileFile.exists())
        val content = profileFile.readText()

        assertFalse(
            "ProfileScreen backup sheet must not contain redundant instructional explanation",
            content.contains("Choose a backup source to restore the database")
        )
    }
}
