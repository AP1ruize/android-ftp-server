package com.example.ftpembed

import android.content.Context
import com.example.ftpembed.auth.AuthRepository
import com.example.ftpembed.ddns.DdnsApiClient
import com.example.ftpembed.ddns.DdnsBindingEnsurer
import com.example.ftpembed.ddns.DdnsPrefs
import com.example.ftpembed.ddns.DdnsRepository
import com.example.ftpembed.ddns.DdnsUpdateScheduler
import com.example.ftpembed.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class AppServices private constructor(context: Context) {
    val authRepository: AuthRepository = AuthRepository.create(context)
    val ddnsPrefs: DdnsPrefs = DdnsPrefs(context)
    val ddnsRepository: DdnsRepository = DdnsRepository(
        apiClient = DdnsApiClient(authRepository),
        prefs = ddnsPrefs,
        tokenProvider = authRepository,
    )
    val ddnsEnsurer: DdnsBindingEnsurer = DdnsBindingEnsurer(
        repository = ddnsRepository,
        prefs = ddnsPrefs,
    )
    val networkMonitor: NetworkMonitor = NetworkMonitor(context)
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob())
    val ddnsScheduler: DdnsUpdateScheduler = DdnsUpdateScheduler(
        ensurer = ddnsEnsurer,
        prefs = ddnsPrefs,
        networkMonitor = networkMonitor,
        scope = appScope,
    )

    companion object {
        @Volatile
        private var instance: AppServices? = null

        fun get(context: Context): AppServices {
            return instance ?: synchronized(this) {
                instance ?: AppServices(context.applicationContext).also { instance = it }
            }
        }
    }
}
