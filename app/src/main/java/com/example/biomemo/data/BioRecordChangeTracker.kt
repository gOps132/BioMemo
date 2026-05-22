package com.example.biomemo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

interface BioRecordChangeSource {
    fun versions(): Flow<Long>
    fun markChanged(): Long
}

object BioRecordChangeTracker : BioRecordChangeSource {
    private val version = AtomicLong(0)
    private val versionFlow = MutableStateFlow(0L)

    fun currentVersion(): Long = version.get()

    override fun versions(): StateFlow<Long> = versionFlow.asStateFlow()

    override fun markChanged(): Long {
        val nextVersion = version.incrementAndGet()
        versionFlow.value = nextVersion
        return nextVersion
    }

    internal fun resetForTests() {
        version.set(0)
        versionFlow.value = 0
    }
}
