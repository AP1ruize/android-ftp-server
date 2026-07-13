package com.example.ftpembed

import android.Manifest
import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ftpembed.auth.AuthState
import com.example.ftpembed.debug.AppEventLog
import com.example.ftpembed.network.NetworkSettingsNavigator
import com.example.ftpembed.ui.FqdnSection
import com.example.ftpembed.ui.FtpConnectionStatusChip
import com.example.ftpembed.ui.LoginSection
import com.example.ftpembed.ui.NetworkBanner
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_STATUS = "com.example.ftpembed.STATUS"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"
        const val EXTRA_ROOT = "root"
        const val EXTRA_ROOT_LABEL = "root_label"
        const val EXTRA_ERR = "error"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_FTP_CLIENT_STATE = "ftp_client_state"

        private const val MAX_LOG_ENTRIES = 200
        private val nextLogId = AtomicLong(0)
    }

    data class FtpLogEntry(
        val id: Long,
        val timestamp: String,
        val text: String,
    ) {
        fun formatted(): String = "[$timestamp] $text"
    }

    private val services by lazy { AppServices.get(this) }
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(services) }

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.handleAuthActivityResult(result.resultCode, result.data)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initialize()
        lifecycleScope.launch {
            viewModel.handleRedirectIntent(intent)
        }
        setContent { AppUI() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            viewModel.handleRedirectIntent(intent)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun AppUI() {
        val context = LocalContext.current
        val activity = context as MainActivity
        val settings = remember { FtpSettingsRepository(context) }

        val authState by viewModel.authState.collectAsStateWithLifecycle()
        val networkKind by viewModel.networkKind.collectAsStateWithLifecycle()
        val fqdnState by viewModel.fqdnState.collectAsStateWithLifecycle()

        LaunchedEffect(authState) {
            viewModel.onAuthStateChanged(authState)
        }

        var ddnsAlert by remember { mutableStateOf<Pair<String, String>?>(null) }
        LaunchedEffect(Unit) {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is com.example.ftpembed.ddns.DdnsUiEvent.Alert -> {
                        ddnsAlert = event.title to event.message
                    }
                }
            }
        }

        if (ddnsAlert != null) {
            val (title, message) = ddnsAlert!!
            AlertDialog(
                onDismissRequest = { ddnsAlert = null },
                title = { Text(title) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { ddnsAlert = null }) {
                        Text("知道了")
                    }
                },
            )
        }

        var rootLabel by remember { mutableStateOf(settings.getConfiguredRootLabel()) }
        var status by remember { mutableStateOf("状态：未启动") }
        var info by remember { mutableStateOf("连接信息会显示在这里") }
        var isRunning by remember { mutableStateOf(false) }
        var ftpClientState by remember { mutableStateOf("Disconnected") }
        val eventLogs = remember { mutableStateListOf<FtpLogEntry>() }

        var username by remember { mutableStateOf(settings.getCredentials().username) }
        var password by remember { mutableStateOf(settings.getCredentials().password) }
        var allowAnonymous by remember { mutableStateOf(settings.getCredentials().allowAnonymous) }
        var portText by remember { mutableStateOf(settings.getPort().toString()) }
        val port = portText.toIntOrNull() ?: BuildConfig.DEFAULT_FTP_PORT

        fun appendLog(text: String) {
            eventLogs.add(
                0,
                FtpLogEntry(
                    id = nextLogId.incrementAndGet(),
                    timestamp = FtpLogFormatter.currentTimestamp(),
                    text = text,
                ),
            )
            while (eventLogs.size > MAX_LOG_ENTRIES) {
                eventLogs.removeAt(eventLogs.lastIndex)
            }
        }

        val openDirLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri: Uri? ->
                uri?.let {
                    settings.saveRootDirectory(it)
                    rootLabel = settings.getConfiguredRootLabel()
                    appendLog("已选择 FTP 根目录：${settings.getRootDisplayName() ?: it}")
                    Toast.makeText(context, "目录已保存", Toast.LENGTH_SHORT).show()
                }
            },
        )

        val notifPermissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            launch {
                AppEventLog.events.collect { message ->
                    appendLog(message)
                }
            }

            if (Build.VERSION.SDK_INT >= 33) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (settings.hasSafRoot()) {
                if (!settings.validateAndRepairSavedRoot()) {
                    rootLabel = settings.getConfiguredRootLabel()
                    Toast.makeText(context, "FTP 根目录权限已失效，已回退默认目录", Toast.LENGTH_LONG).show()
                } else {
                    rootLabel = settings.getConfiguredRootLabel()
                }
            } else {
                rootLabel = settings.getConfiguredRootLabel()
            }

            val intent = Intent(context, FtpForegroundService::class.java).apply {
                action = FtpForegroundService.ACTION_PING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    intent ?: return
                    if (intent.action != ACTION_STATUS) return

                    val running = intent.getBooleanExtra(EXTRA_RUNNING, false)
                    isRunning = running
                    val ip = intent.getStringExtra(EXTRA_IP) ?: "0.0.0.0"
                    val portExtra = intent.getIntExtra(EXTRA_PORT, BuildConfig.DEFAULT_FTP_PORT)
                    val root = intent.getStringExtra(EXTRA_ROOT) ?: "-"
                    val rootDisplay = intent.getStringExtra(EXTRA_ROOT_LABEL)
                    val err = intent.getStringExtra(EXTRA_ERR)
                    val msg = intent.getStringExtra(EXTRA_MESSAGE)
                    ftpClientState = intent.getStringExtra(EXTRA_FTP_CLIENT_STATE) ?: "Disconnected"

                    if (msg != null) appendLog(msg)
                    status = if (running) "状态：已启动" else "状态：未启动"

                    val creds = settings.getCredentials()
                    val currentUser = if (creds.allowAnonymous) {
                        "${creds.username} / anonymous"
                    } else {
                        creds.username
                    }

                    val fqdn = fqdnState.activeRecord?.fqdn
                    info = when {
                        err != null -> "启动失败：$err"
                        running && !fqdn.isNullOrBlank() ->
                            "局域网：ftp://$ip:$portExtra\nFQDN：ftp://$fqdn:$portExtra\n用户：$currentUser"
                        running -> "连接：ftp://$ip:$portExtra  用户：$currentUser"
                        else -> "连接信息会显示在这里"
                    }

                    rootLabel = if (running && rootDisplay != null) {
                        "根目录：$rootDisplay\n$root"
                    } else {
                        settings.getConfiguredRootLabel()
                    }
                }
            }
            context.applicationContext.registerReceiver(
                receiver,
                IntentFilter(ACTION_STATUS),
                RECEIVER_NOT_EXPORTED,
            )
            onDispose { context.applicationContext.unregisterReceiver(receiver) }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("FTP Server 控制台", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                NetworkBanner(
                    networkKind = networkKind,
                    onOpenWifiSettings = { NetworkSettingsNavigator.openWifiSettings(context) },
                    onOpenHotspotSettings = { NetworkSettingsNavigator.openHotspotSettings(context) },
                )
                Spacer(Modifier.height(12.dp))

                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                LoginSection(
                    authState = authState,
                    onLogin = { viewModel.login(activity, authLauncher) },
                    onLogout = { viewModel.logout() },
                )
                Spacer(Modifier.height(12.dp))

                FqdnSection(
                    authState = authState,
                    fqdnState = fqdnState,
                    previewFqdn = viewModel.previewFqdn(),
                    syncStatusText = viewModel.syncStatusText(),
                    ftpUrl = viewModel.ftpUrl(port),
                    onLabelChange = viewModel::updateLabelInput,
                    onSaveLabel = viewModel::saveLabel,
                    onCopyFqdn = {
                        viewModel.copyText()?.let { activity.copyToClipboard("fqdn", it) }
                    },
                    onCopyFtpUrl = {
                        viewModel.ftpUrl(port)?.let { activity.copyToClipboard("ftp", it) }
                    },
                    onSyncNow = viewModel::syncNow,
                )
                Spacer(Modifier.height(12.dp))

                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Button(onClick = { openDirLauncher.launch(null) }) {
                    Text("选择FTP根目录")
                }
                Text(rootLabel, Modifier.padding(top = 8.dp))

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("端口 (默认 2121)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        settings.setUsername(it)
                    },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        settings.setPassword(it)
                    },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Checkbox(
                        checked = allowAnonymous,
                        onCheckedChange = {
                            allowAnonymous = it
                            settings.setAllowAnonymous(it)
                        },
                        enabled = !isRunning,
                    )
                    Text("允许匿名访问")
                }

                Row(Modifier.padding(top = 20.dp)) {
                    Button(
                        onClick = {
                            if (!settings.canStartFtp()) {
                                Toast.makeText(context, "FTP 根目录不可用", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (settings.hasSafRoot() && !settings.validateAndRepairSavedRoot()) {
                                rootLabel = settings.getConfiguredRootLabel()
                                Toast.makeText(context, "目录权限已失效，已回退默认目录", Toast.LENGTH_LONG).show()
                            }
                            val ftpPort = portText.toIntOrNull() ?: BuildConfig.DEFAULT_FTP_PORT
                            settings.setPort(ftpPort)
                            val intent = Intent(context, FtpForegroundService::class.java).apply {
                                action = FtpForegroundService.ACTION_START
                                putExtra(FtpForegroundService.EXTRA_PORT, ftpPort)
                            }
                            ContextCompat.startForegroundService(context, intent)
                            if (authState is AuthState.LoggedIn && fqdnState.activeRecord != null) {
                                viewModel.syncNow()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isRunning,
                    ) { Text("启动 FTP") }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val intent = Intent(context, FtpForegroundService::class.java).apply {
                                action = FtpForegroundService.ACTION_STOP
                            }
                            context.startService(intent)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isRunning,
                    ) { Text("停止 FTP") }
                }

                Spacer(Modifier.height(20.dp))
                Text(status)
                Spacer(Modifier.height(8.dp))
                Text(info, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                FtpConnectionStatusChip(ftpClientState)
                Spacer(Modifier.height(16.dp))

                Text("事件日志", style = MaterialTheme.typography.titleSmall)
                Text(
                    "长按单条日志可复制",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (eventLogs.isEmpty()) {
                    Text(
                        "暂无事件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(eventLogs, key = { it.id }) { entry ->
                            val line = entry.formatted()
                            Text(
                                line,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            activity.copyToClipboard("event_log", line)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
