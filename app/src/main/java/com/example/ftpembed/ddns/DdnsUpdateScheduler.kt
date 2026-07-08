package com.example.ftpembed.ddns

import com.example.ftpembed.network.LocalIpProvider
import com.example.ftpembed.network.NetworkKind
import com.example.ftpembed.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DdnsUpdateScheduler(
    private val repository: DdnsRepository,
    private val networkMonitor: NetworkMonitor,
    private val getLanIpv4: () -> String? = LocalIpProvider::getLanIpv4,
    private val prefs: DdnsPrefs,
    private val scope: CoroutineScope,
) {
    private var networkJob: Job? = null
    private var periodicJob: Job? = null
    private var debounceJob: Job? = null
    private var throttleUntilEpochMs = 0L

    fun start() {
        if (networkJob != null) return
        networkMonitor.start()
        networkJob = scope.launch {
            networkMonitor.networkKind.collect { kind ->
                if (kind != NetworkKind.None) {
                    scheduleDebouncedSync()
                }
            }
        }
        periodicJob = scope.launch {
            while (isActive) {
                delay(PERIODIC_INTERVAL_MS)
                trySync()
            }
        }
        scheduleDebouncedSync()
    }

    fun stop() {
        debounceJob?.cancel()
        networkJob?.cancel()
        periodicJob?.cancel()
        debounceJob = null
        networkJob = null
        periodicJob = null
        networkMonitor.stop()
    }

    suspend fun syncNow() {
        trySync()
    }

    private fun scheduleDebouncedSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            trySync()
        }
    }

    private suspend fun trySync() {
        if (System.currentTimeMillis() < throttleUntilEpochMs) return
        if (!networkMonitor.isUsableNetwork()) return

        val label = prefs.selectedLabel ?: return
        val ipv4 = getLanIpv4() ?: return

        repository.updateIp(label, ipv4).onSuccess { result ->
            when (result) {
                is DdnsUpdateResult.Updated,
                is DdnsUpdateResult.NoChange,
                -> Unit
                is DdnsUpdateResult.Throttled -> {
                    throttleUntilEpochMs = System.currentTimeMillis() + THROTTLE_RETRY_MS
                }
            }
        }
    }

    companion object {
        private const val PERIODIC_INTERVAL_MS = 60_000L
        private const val DEBOUNCE_MS = 2_000L
        private const val THROTTLE_RETRY_MS = 60_000L
    }
}
