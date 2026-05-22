package com.example.biomemo

import com.example.biomemo.screens.bio.BioCollectionSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BioCollectionSelectionStateTest {
    @Test
    fun togglesSelectionAndReportsSingleSelectedId() {
        val state = BioCollectionSelectionState()

        state.toggle("a")

        assertTrue(state.contains("a"))
        assertEquals("a", state.singleSelectedId())

        state.toggle("a")

        assertFalse(state.contains("a"))
        assertEquals(null, state.singleSelectedId())
    }

    @Test
    fun retainsOnlyVisibleIds() {
        val state = BioCollectionSelectionState()
        state.selectAll(listOf("a", "b", "c"))

        state.retainVisibleIds(listOf("b", "c"))

        assertEquals(listOf("b", "c"), state.ids())
    }
}
