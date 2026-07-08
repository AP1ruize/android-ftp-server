package com.example.ftpembed

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ftpembed.auth.AuthRepository
import com.example.ftpembed.auth.AuthState
import com.example.ftpembed.debug.AppEventLog
import com.example.ftpembed.ddns.DdnsApiException
import com.example.ftpembed.ddns.DdnsPrefs
import com.example.ftpembed.ddns.DdnsRecord
import com.example.ftpembed.ddns.DdnsRepository
import com.example.ftpembed.ddns.DdnsSyncStatus
import com.example.ftpembed.ddns.DdnsUpdateResult
import com.example.ftpembed.ddns.DdnsUpdateScheduler
import com.example.ftpembed.ddns.LabelValidationResult
import com.example.ftpembed.ddns.LabelValidator
import com.example.ftpembed.network.LocalIpProvider
import com.example.ftpembed.network.NetworkKind
import com.example.ftpembed.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FqdnUiState(
    val userShard: String? = null,
    val zone: String? = null,
    val labelInput: String = "",
    val labelError: String? = null,
    val activeRecord: DdnsRecord? = null,
    val syncStatus: DdnsSyncStatus = DdnsSyncStatus.Idle,
    val isSaving: Boolean = false,
)

class MainViewModel(
    private val authRepository: AuthRepository,
    private val ddnsRepository: DdnsRepository,
    private val ddnsPrefs: DdnsPrefs,
    private val networkMonitor: NetworkMonitor,
    private val ddnsScheduler: DdnsUpdateScheduler,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.state

    private val _networkKind = MutableStateFlow(NetworkKind.None)
    val networkKind: StateFlow<NetworkKind> = _networkKind.asStateFlow()

    private val _fqdnState = MutableStateFlow(FqdnUiState())
    val fqdnState: StateFlow<FqdnUiState> = _fqdnState.asStateFlow()

    private var ddnsStarted = false
    private var ddnsDataLoaded = false

    fun initialize() {
        viewModelScope.launch {
            authRepository.initialize()
        }
        viewModelScope.launch {
            networkMonitor.start()
            networkMonitor.networkKind.collect { kind ->
                _networkKind.value = kind
            }
        }
    }

    fun handleRedirectIntent(intent: Intent) {
        viewModelScope.launch {
            authRepository.handleRedirectIntent(intent)
        }
    }

    fun handleAuthActivityResult(resultCode: Int, data: Intent?) {
        if (data != null) {
            handleRedirectIntent(data)
            return
        }
        if (resultCode == Activity.RESULT_CANCELED) {
            onAuthCancelled()
        }
    }

    fun onAuthCancelled() {
        authRepository.onLoginCancelled()
    }

    fun login(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            try {
                authRepository.startLogin(activity, launcher)
            } catch (_: Exception) {
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            stopDdns()
            ddnsDataLoaded = false
            authRepository.logout()
            _fqdnState.value = FqdnUiState()
        }
    }

    fun onAuthStateChanged(state: AuthState) {
        if (state is AuthState.LoggedIn) {
            if (!ddnsDataLoaded) {
                ddnsDataLoaded = true
                viewModelScope.launch { onLoggedIn() }
            }
        } else {
            ddnsDataLoaded = false
            stopDdns()
            _fqdnState.value = FqdnUiState()
        }
    }

    fun updateLabelInput(value: String) {
        _fqdnState.update {
            it.copy(
                labelInput = value.take(4),
                labelError = null,
            )
        }
    }

    fun saveLabel() {
        val validation = LabelValidator.validate(_fqdnState.value.labelInput)
        if (validation is LabelValidationResult.Invalid) {
            _fqdnState.update { it.copy(labelError = validation.message) }
            return
        }
        val label = (validation as LabelValidationResult.Valid).label
        val ipv4 = LocalIpProvider.getLanIpv4()
        if (ipv4.isNullOrBlank()) {
            _fqdnState.update {
                it.copy(labelError = "请先连接 Wi-Fi 或开启热点以获取局域网 IP")
            }
            return
        }

        viewModelScope.launch {
            _fqdnState.update { it.copy(isSaving = true, labelError = null, syncStatus = DdnsSyncStatus.Syncing) }
            ddnsRepository.createRecord(label, ipv4)
                .onSuccess { record ->
                    _fqdnState.update {
                        it.copy(
                            activeRecord = record,
                            labelInput = record.label,
                            userShard = ddnsPrefs.userShard,
                            zone = ddnsPrefs.zone,
                            syncStatus = DdnsSyncStatus.Success(record.ipv4, System.currentTimeMillis()),
                            isSaving = false,
                        )
                    }
                    startDdnsIfNeeded()
                    ddnsScheduler.syncNow()
                }
                .onFailure { error ->
                    _fqdnState.update {
                        it.copy(
                            labelError = error.message ?: "保存失败",
                            syncStatus = mapSyncError(error),
                            isSaving = false,
                        )
                    }
                }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _fqdnState.update { it.copy(syncStatus = DdnsSyncStatus.Syncing) }
            ddnsScheduler.syncNow()
            refreshSyncStatusFromPrefs()
        }
    }

    fun copyText(): String? = _fqdnState.value.activeRecord?.fqdn

    fun ftpUrl(port: Int): String? {
        val fqdn = _fqdnState.value.activeRecord?.fqdn ?: return null
        return "ftp://$fqdn:$port"
    }

    fun syncStatusText(): String {
        val state = _fqdnState.value
        return when (val status = state.syncStatus) {
            DdnsSyncStatus.Idle -> "DDNS：待同步"
            DdnsSyncStatus.Syncing -> "DDNS：同步中…"
            is DdnsSyncStatus.Success -> {
                val time = formatTime(status.epochMillis)
                "DDNS：已同步 · ${status.ip} · $time"
            }
            is DdnsSyncStatus.Failed -> "DDNS：${status.error.message}"
        }
    }

    fun previewFqdn(): String? {
        val state = _fqdnState.value
        val label = LabelValidator.normalize(state.labelInput)
        val shard = state.userShard ?: ddnsPrefs.userShard
        val zone = state.zone ?: ddnsPrefs.zone
        if (label.length != 4 || shard.isNullOrBlank() || zone.isNullOrBlank()) return null
        return "$label.$shard.$zone"
    }

    private suspend fun onLoggedIn() {
        AppEventLog.log("DDNS", "Loading shard and records after login")
        ddnsRepository.fetchShard()
            .onSuccess { shard ->
                AppEventLog.log("DDNS", "Shard loaded: ${shard.userShard}.${shard.zone}")
                _fqdnState.update {
                    it.copy(userShard = shard.userShard, zone = shard.zone)
                }
            }
            .onFailure { e ->
                AppEventLog.log("DDNS", "fetchShard failed: ${e.message}")
            }
        ddnsRepository.listRecords()
            .onSuccess { (records, _) ->
                AppEventLog.log("DDNS", "Records loaded: count=${records.size}")
                val selected = records.firstOrNull { it.label == ddnsPrefs.selectedLabel }
                    ?: records.firstOrNull()
                if (selected != null) {
                    ddnsPrefs.selectedLabel = selected.label
                    _fqdnState.update {
                        it.copy(
                            activeRecord = selected,
                            labelInput = selected.label,
                            syncStatus = DdnsSyncStatus.Success(
                                ddnsPrefs.lastSyncedIp ?: selected.ipv4,
                                ddnsPrefs.lastSyncAtEpochMs.takeIf { ts -> ts > 0 }
                                    ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                    startDdnsIfNeeded()
                }
            }
            .onFailure { e ->
                AppEventLog.log("DDNS", "listRecords failed: ${e.message}")
            }
    }

    private fun startDdnsIfNeeded() {
        if (ddnsStarted || ddnsPrefs.selectedLabel.isNullOrBlank()) return
        ddnsScheduler.start()
        ddnsStarted = true
    }

    private fun stopDdns() {
        if (!ddnsStarted) return
        ddnsScheduler.stop()
        ddnsStarted = false
    }

    private suspend fun refreshSyncStatusFromPrefs() {
        val ip = ddnsPrefs.lastSyncedIp
        val at = ddnsPrefs.lastSyncAtEpochMs
        _fqdnState.update {
            it.copy(
                syncStatus = if (!ip.isNullOrBlank() && at > 0) {
                    DdnsSyncStatus.Success(ip, at)
                } else {
                    DdnsSyncStatus.Idle
                },
            )
        }
    }

    private fun mapSyncError(error: Throwable): DdnsSyncStatus {
        return when (error) {
            is DdnsApiException -> DdnsSyncStatus.Failed(error.error)
            else -> DdnsSyncStatus.Failed(
                com.example.ftpembed.ddns.DdnsApiError(
                    httpStatus = 0,
                    code = "client_error",
                    message = error.message ?: "操作失败",
                ),
            )
        }
    }

    private fun formatTime(epochMs: Long): String {
        if (epochMs <= 0L) return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    }

    override fun onCleared() {
        stopDdns()
        networkMonitor.stop()
        super.onCleared()
    }

    class Factory(private val services: AppServices) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                authRepository = services.authRepository,
                ddnsRepository = services.ddnsRepository,
                ddnsPrefs = services.ddnsPrefs,
                networkMonitor = services.networkMonitor,
                ddnsScheduler = services.ddnsScheduler,
            ) as T
        }
    }
}
