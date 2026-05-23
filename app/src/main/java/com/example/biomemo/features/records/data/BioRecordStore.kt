package com.example.biomemo.features.records.data

import com.example.biomemo.features.records.domain.BioEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

class BioRecordStore(
    private val changeSource: BioRecordChangeSource = BioRecordChangeTracker,
    private val remoteChanges: () -> Flow<Unit>,
    private val loadFreshEntries: suspend () -> List<BioEntry>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun observeAllEntries(): Flow<List<BioEntry>> {
        val localChanges = changeSource.versions()
            .drop(1)
            .map { Unit }

        return merge(flowOf(Unit), localChanges, remoteChanges())
            .map { withContext(dispatcher) { loadFreshEntries() } }
    }

    suspend fun refreshAllEntries(): List<BioEntry> {
        return withContext(dispatcher) { loadFreshEntries() }
            .also { changeSource.markChanged() }
    }
}
