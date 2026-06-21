package com.example.ftpembed

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

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

        private const val MAX_LOG_ENTRIES = 200
    }

    data class FtpLogEntry(
        val timestamp: String,
        val text: String,
    ) {
        fun formatted(): String = "[$timestamp] $text"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppUI() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun AppUI() {
        val context = LocalContext.current
        val settings = remember { FtpSettingsRepository(context) }

        var rootLabel by remember { mutableStateOf(settings.getConfiguredRootLabel()) }
        var status by remember { mutableStateOf("状态：未启动") }
        var info by remember { mutableStateOf("连接信息会显示在这里") }
        var isRunning by remember { mutableStateOf(false) }
        val eventLogs = remember { mutableStateListOf<FtpLogEntry>() }

        var username by remember { mutableStateOf(settings.getCredentials().username) }
        var password by remember { mutableStateOf(settings.getCredentials().password) }
        var allowAnonymous by remember { mutableStateOf(settings.getCredentials().allowAnonymous) }
        var portText by remember { mutableStateOf(settings.getPort().toString()) }

        fun appendLog(text: String) {
            eventLogs.add(0, FtpLogEntry(FtpLogFormatter.currentTimestamp(), text))
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
                    val port = intent.getIntExtra(EXTRA_PORT, BuildConfig.DEFAULT_FTP_PORT)
                    val root = intent.getStringExtra(EXTRA_ROOT) ?: "-"
                    val rootDisplay = intent.getStringExtra(EXTRA_ROOT_LABEL)
                    val err = intent.getStringExtra(EXTRA_ERR)
                    val msg = intent.getStringExtra(EXTRA_MESSAGE)

                    if (msg != null) appendLog(msg)
                    status = if (running) "状态：已启动" else "状态：未启动"

                    val creds = settings.getCredentials()
                    val currentUser = if (creds.allowAnonymous) {
                        "${creds.username} / anonymous"
                    } else {
                        creds.username
                    }

                    info = when {
                        err != null -> "启动失败：$err"
                        running -> "连接：ftp://$ip:$port  用户：$currentUser"
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
                modifier = Modifier.padding(20.dp),
            ) {
                Text("FTP Server 控制台", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

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
                            val port = portText.toIntOrNull() ?: BuildConfig.DEFAULT_FTP_PORT
                            settings.setPort(port)
                            val intent = Intent(context, FtpForegroundService::class.java).apply {
                                action = FtpForegroundService.ACTION_START
                                putExtra(FtpForegroundService.EXTRA_PORT, port)
                            }
                            ContextCompat.startForegroundService(context, intent)
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

                Text("事件日志", style = MaterialTheme.typography.titleSmall)
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
                        items(eventLogs, key = { "${it.timestamp}_${it.text}_${it.hashCode()}" }) { entry ->
                            Text(
                                entry.formatted(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
