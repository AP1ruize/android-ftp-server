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

        var rootLabel by remember { mutableStateOf("未选择目录") }
        var status by remember { mutableStateOf("状态：未启动") }
        var info by remember { mutableStateOf("连接信息会显示在这里") }
        var message by remember { mutableStateOf("") }
        var isRunning by remember { mutableStateOf(false) }
        var hasValidRoot by remember { mutableStateOf(false) }

        var username by remember { mutableStateOf(settings.getCredentials().username) }
        var password by remember { mutableStateOf(settings.getCredentials().password) }
        var allowAnonymous by remember { mutableStateOf(settings.getCredentials().allowAnonymous) }
        var portText by remember { mutableStateOf(settings.getPort().toString()) }

        val openDirLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri: Uri? ->
                uri?.let {
                    settings.saveRootDirectory(it)
                    hasValidRoot = true
                    rootLabel = settings.getRootLabel()
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

            if (settings.hasConfiguredRoot()) {
                if (settings.validateAndRepairSavedRoot()) {
                    hasValidRoot = true
                    rootLabel = settings.getRootLabel()
                } else {
                    hasValidRoot = false
                    rootLabel = "已保存的目录已失效，请重新选择"
                    Toast.makeText(context, "FTP 根目录权限已失效，请重新选择", Toast.LENGTH_LONG).show()
                }
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

                    if (msg != null) message = msg
                    status = if (running) "状态：已启动" else "状态：未启动"

                    val creds = settings.getCredentials()
                    val currentUser = if (creds.allowAnonymous) {
                        "${creds.username} / anonymous"
                    } else {
                        creds.username
                    }

                    info = if (running) {
                        "连接：ftp://$ip:$port  用户：$currentUser"
                    } else {
                        "连接信息会显示在这里"
                    }
                    rootLabel = if (rootDisplay != null) {
                        "根目录：$rootDisplay\n$root"
                    } else {
                        "根目录：$root"
                    }
                    err?.let { info = "启动失败：$it" }
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
                            if (!hasValidRoot || !settings.hasConfiguredRoot()) {
                                Toast.makeText(context, "请先选择 FTP 根目录", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!settings.validateAndRepairSavedRoot()) {
                                hasValidRoot = false
                                rootLabel = "已保存的目录已失效，请重新选择"
                                Toast.makeText(context, "目录权限已失效，请重新选择", Toast.LENGTH_LONG).show()
                                return@Button
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
                if (message.isNotEmpty()) {
                    Text("📂 新文件事件：$message", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
