package com.deadline

import com.deadline.data.local.QuickNote
import com.deadline.ui.notes.NotesSectioningLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NotesSectioningLifecycleTest {

    private fun createNote(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Note",
        content: String = "Test Content",
        isPinned: Boolean = false,
        sortOrder: Int = 0,
        updatedAt: Long = System.currentTimeMillis()
    ): QuickNote {
        return QuickNote(
            id = id,
            title = title,
            content = content,
            isPinned = isPinned,
            sortOrder = sortOrder,
            updatedAt = updatedAt
        )
    }

    // -------------------------------------------------------------------------
    // 1. Dynamic Conditional Sectioning: Zero Pinned Notes
    // -------------------------------------------------------------------------

    @Test
    fun testPartitionNotes_zeroPinnedNotes_returnsUnsectionedSingleList() {
        val notes = listOf(
            createNote(id = "1", title = "Note 1", isPinned = false, sortOrder = 0),
            createNote(id = "2", title = "Note 2", isPinned = false, sortOrder = 1),
            createNote(id = "3", title = "Note 3", isPinned = false, sortOrder = 2)
        )

        val result = NotesSectioningLogic.partitionNotes(notes)

        // When zero notes are pinned, display all notes in a single standard list without section headers
        assertFalse("isSectioned must be false when zero notes are pinned", result.isSectioned)
        assertTrue("pinnedNotes must be empty", result.pinnedNotes.isEmpty())
        assertEquals("otherNotes must contain all 3 notes", 3, result.otherNotes.size)
        assertEquals(listOf("1", "2", "3"), result.otherNotes.map { it.id })
    }

    @Test
    fun testPartitionNotes_emptyNotesList_returnsUnsectioned() {
        val notes = emptyList<QuickNote>()
        val result = NotesSectioningLogic.partitionNotes(notes)

        assertFalse(result.isSectioned)
        assertTrue(result.pinnedNotes.isEmpty())
        assertTrue(result.otherNotes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 2. Dynamic Conditional Sectioning: One or More Pinned Notes
    // -------------------------------------------------------------------------

    @Test
    fun testPartitionNotes_onePinnedNote_dynamicallySplitsIntoPinnedAndOthers() {
        val notes = listOf(
            createNote(id = "1", title = "Note 1", isPinned = false),
            createNote(id = "2", title = "Pinned Note", isPinned = true),
            createNote(id = "3", title = "Note 3", isPinned = false)
        )

        val result = NotesSectioningLogic.partitionNotes(notes)

        // When one or more notes are pinned, dynamically split into 'Pinned' and 'Others'
        assertTrue("isSectioned must be true when at least one note is pinned", result.isSectioned)
        assertEquals(1, result.pinnedNotes.size)
        assertEquals("2", result.pinnedNotes[0].id)
        assertEquals(2, result.otherNotes.size)
        assertEquals(listOf("1", "3"), result.otherNotes.map { it.id })
    }

    @Test
    fun testPartitionNotes_multiplePinnedNotes_preservesSubGrouping() {
        val notes = listOf(
            createNote(id = "1", title = "Pinned A", isPinned = true, sortOrder = 0),
            createNote(id = "2", title = "Other B", isPinned = false, sortOrder = 0),
            createNote(id = "3", title = "Pinned C", isPinned = true, sortOrder = 1),
            createNote(id = "4", title = "Other D", isPinned = false, sortOrder = 1)
        )

        val result = NotesSectioningLogic.partitionNotes(notes)

        assertTrue(result.isSectioned)
        assertEquals(2, result.pinnedNotes.size)
        assertEquals(listOf("1", "3"), result.pinnedNotes.map { it.id })
        assertEquals(2, result.otherNotes.size)
        assertEquals(listOf("2", "4"), result.otherNotes.map { it.id })
    }

    // -------------------------------------------------------------------------
    // 3. Real-Time Re-indexing: Pinning & Unpinning Lifecycle
    // -------------------------------------------------------------------------

    @Test
    fun testTogglePin_pinningUnpinnedNote_instantlyMovesToPinnedSection() {
        val note1 = createNote(id = "note-1", isPinned = false)
        val note2 = createNote(id = "note-2", isPinned = false)
        val initialList = listOf(note1, note2)

        // Initial: 0 pinned notes, unsectioned
        val initialSectioning = NotesSectioningLogic.partitionNotes(initialList)
        assertFalse(initialSectioning.isSectioned)

        // Action: Pin note-1
        val updatedList = NotesSectioningLogic.togglePin(initialList, "note-1")
        val pinnedTarget = updatedList.first { it.id == "note-1" }
        assertTrue("Target note must have isPinned = true after toggling", pinnedTarget.isPinned)

        // Real-time re-indexing
        val reindexedSectioning = NotesSectioningLogic.partitionNotes(updatedList)
        assertTrue("View must now be dynamically sectioned", reindexedSectioning.isSectioned)
        assertEquals(1, reindexedSectioning.pinnedNotes.size)
        assertEquals("note-1", reindexedSectioning.pinnedNotes.first().id)
        assertEquals(1, reindexedSectioning.otherNotes.size)
        assertEquals("note-2", reindexedSectioning.otherNotes.first().id)
    }

    @Test
    fun testTogglePin_unpinningPinnedNote_immediatelyReturnsToOthersSection() {
        val note1 = createNote(id = "note-1", isPinned = true)
        val note2 = createNote(id = "note-2", isPinned = false)
        val initialList = listOf(note1, note2)

        // Initial: 1 pinned note, sectioned
        val initialSectioning = NotesSectioningLogic.partitionNotes(initialList)
        assertTrue(initialSectioning.isSectioned)
        assertEquals(1, initialSectioning.pinnedNotes.size)

        // Action: Unpin note-1
        val updatedList = NotesSectioningLogic.togglePin(initialList, "note-1")
        val unpinnedTarget = updatedList.first { it.id == "note-1" }
        assertFalse("Target note must have isPinned = false after toggling", unpinnedTarget.isPinned)

        // Real-time re-indexing: With 0 pinned notes remaining, section headers disappear
        val reindexedSectioning = NotesSectioningLogic.partitionNotes(updatedList)
        assertFalse("View must return to a single standard list without section headers", reindexedSectioning.isSectioned)
        assertTrue("Pinned notes list must be empty", reindexedSectioning.pinnedNotes.isEmpty())
        assertEquals(2, reindexedSectioning.otherNotes.size)
        assertTrue("Unpinned note must be in otherNotes", reindexedSectioning.otherNotes.any { it.id == "note-1" })
    }

    @Test
    fun testTogglePin_multipleStepsSequence_retainsStateIntegrity() {
        var list = listOf(
            createNote(id = "A", isPinned = false, sortOrder = 0),
            createNote(id = "B", isPinned = false, sortOrder = 1),
            createNote(id = "C", isPinned = false, sortOrder = 2)
        )

        // Step 1: All unpinned -> unsectioned
        var s = NotesSectioningLogic.partitionNotes(list)
        assertFalse(s.isSectioned)
        assertEquals(3, s.otherNotes.size)

        // Step 2: Pin B -> sectioned with 1 pinned (B)
        list = NotesSectioningLogic.togglePin(list, "B")
        s = NotesSectioningLogic.partitionNotes(list)
        assertTrue(s.isSectioned)
        assertEquals(listOf("B"), s.pinnedNotes.map { it.id })
        assertEquals(listOf("A", "C"), s.otherNotes.map { it.id })

        // Step 3: Pin C -> sectioned with 2 pinned (B, C)
        list = NotesSectioningLogic.togglePin(list, "C")
        s = NotesSectioningLogic.partitionNotes(list)
        assertTrue(s.isSectioned)
        assertEquals(listOf("B", "C"), s.pinnedNotes.map { it.id })
        assertEquals(listOf("A"), s.otherNotes.map { it.id })

        // Step 4: Unpin B -> sectioned with 1 pinned (C)
        list = NotesSectioningLogic.togglePin(list, "B")
        s = NotesSectioningLogic.partitionNotes(list)
        assertTrue(s.isSectioned)
        assertEquals(listOf("C"), s.pinnedNotes.map { it.id })
        assertEquals(listOf("A", "B"), s.otherNotes.map { it.id })

        // Step 5: Unpin C -> unsectioned (0 pinned)
        list = NotesSectioningLogic.togglePin(list, "C")
        s = NotesSectioningLogic.partitionNotes(list)
        assertFalse(s.isSectioned)
        assertTrue(s.pinnedNotes.isEmpty())
        assertEquals(3, s.otherNotes.size)
        assertEquals(listOf("A", "B", "C"), s.otherNotes.map { it.id })
    }

    @Test
    fun testTogglePin_nonExistentId_returnsUnmodifiedList() {
        val list = listOf(createNote(id = "1", isPinned = false))
        val updated = NotesSectioningLogic.togglePin(list, "non-existent")
        assertEquals(list, updated)
    }
}
