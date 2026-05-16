package com.example.biomemo.data

import java.util.concurrent.atomic.AtomicLong

object BioRecordChangeTracker {
    private val version = AtomicLong(0)

    fun currentVersion(): Long = version.get()

    fun markChanged(): Long = version.incrementAndGet()

    internal fun resetForTests() {
        version.set(0)
    }
}
