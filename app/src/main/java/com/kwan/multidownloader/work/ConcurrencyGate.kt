package com.kwan.multidownloader.work

import com.kwan.multidownloader.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Limits how many downloads run at once (settings.maxConcurrent, default 2).
 * WorkManager has no per-tag limit, so each worker holds a slot for the whole download.
 */
class ConcurrencyGate(private val settings: SettingsRepository) {
    private val running = MutableStateFlow(0)
    private val lock = Mutex()

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    private suspend fun acquire() {
        while (true) {
            val limit = settings.current().maxConcurrent
            val acquired = lock.withLock {
                (running.value < limit).also { free -> if (free) running.update { it + 1 } }
            }
            if (acquired) return
            // Wake up when a slot frees or the limit is raised, then race for it again.
            combine(running, settings.settings) { count, s -> count < s.maxConcurrent }.first { it }
        }
    }
}
