package com.example.biomemo

import com.example.biomemo.screens.bio.BioRecordDetailState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BioRecordDetailStateTest {
    @Test
    fun enrichmentLoadingOnlyShowsWhileAnalyzing() {
        assertTrue(
            BioRecordDetailState.shouldShowEnrichmentLoading(
                label = "Habitat",
                value = "Not enriched yet",
                verificationStatus = "analyzing"
            )
        )
        assertFalse(
            BioRecordDetailState.shouldShowEnrichmentLoading(
                label = "Habitat",
                value = "Not enriched yet",
                verificationStatus = "failed"
            )
        )
    }

    @Test
    fun retryShowsForFailedOrUnidentifiedRecordsOnly() {
        assertTrue(BioRecordDetailState.shouldShowRetryIdentification("failed", "Not available", "No organism identified"))
        assertTrue(BioRecordDetailState.shouldShowRetryIdentification("draft", "Awaiting identification", "Unidentified organism"))
        assertFalse(BioRecordDetailState.shouldShowRetryIdentification("analyzing", "Awaiting identification", "Unidentified organism"))
        assertFalse(BioRecordDetailState.shouldShowRetryIdentification("needs confirmation", "Duttaphrynus melanostictus", "Asian common toad"))
    }
}
