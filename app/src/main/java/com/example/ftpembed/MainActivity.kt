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
        var rootUri by remember { mutableStateOf<String?>(null) }
        var rootLabel by remember { mutableStateOf("未选择目录") }
        var status by remember { mutableStateOf("状态：未启动") }
        var info by remember { mutableStateOf("连接信息会显示在这里") }
        var message by remember { mutableStateOf("") }
        var isRunning by remember { mutableStateOf(false) }
        val prefs = context.getSharedPreferences("ftp_prefs", MODE_PRIVATE)

        var username by remember { mutableStateOf(prefs.getString("ftp_username", "user") ?: "user") }
        var password by remember { mutableStateOf(prefs.getString("ftp_password", "1234") ?: "1234") }
        var allowAnonymous by remember { mutableStateOf(prefs.getBoolean("ftp_allow_anon", true)) }



        rootUri = prefs.getString("rootUri", null)
        if (rootUri != null) rootLabel = "已选择目录：$rootUri"

        val openDirLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri: Uri? ->
                uri?.let {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    prefs.edit().putString("rootUri", it.toString()).apply()
                    rootUri = it.toString()
                    rootLabel = "已选择目录：$it"
                }
            }
        )

        val notifPermissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33)
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            // ✅ 启动前台服务，确保通知常驻（未运行状态）
            val intent = Intent(context, FtpForegroundService::class.java).apply {
                action = FtpForegroundService.ACTION_PING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        // 监听广播（包括新文件事件）
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    intent ?: return
                    if (intent.action == ACTION_STATUS) {
                        val running = intent.getBooleanExtra(EXTRA_RUNNING, false)
                        isRunning = running  // ✅ 保存运行状态
                        val ip = intent.getStringExtra(EXTRA_IP) ?: "0.0.0.0"
                        val port = intent.getIntExtra(EXTRA_PORT, BuildConfig.DEFAULT_FTP_PORT)
                        val root = intent.getStringExtra(EXTRA_ROOT) ?: "-"
                        val err = intent.getStringExtra(EXTRA_ERR)
                        val msg = intent.getStringExtra(EXTRA_MESSAGE)

                        if (msg != null) message = msg
                        status = if (running) "状态：已启动" else "状态：未启动"

                        val prefs = getSharedPreferences("ftp_prefs", MODE_PRIVATE)
                        val username = prefs.getString("ftp_username", "user") ?: "user"
                        val allowAnon = prefs.getBoolean("ftp_allow_anon", true)
                        val currentUser = if (allowAnon) "$username / anonymous" else username

                        info = if (running)
                            "连接：ftp://$ip:$port  用户：$currentUser"
                        else "连接信息会显示在这里"
                        rootLabel = "根目录：$root"
                        err?.let { info = "启动失败：$it" }
                    }
                }
            }
            context.applicationContext.registerReceiver(receiver, IntentFilter(ACTION_STATUS), RECEIVER_NOT_EXPORTED)

            onDispose { context.applicationContext.unregisterReceiver(receiver) }
        }

        var portText by remember { mutableStateOf(BuildConfig.DEFAULT_FTP_PORT.toString()) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
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
                        .padding(top = 12.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        prefs.edit().putString("ftp_username", it).apply()
                    },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        prefs.edit().putString("ftp_password", it).apply()
                    },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Checkbox(
                        checked = allowAnonymous,
                        onCheckedChange = {
                            allowAnonymous = it
                            prefs.edit().putBoolean("ftp_allow_anon", it).apply()
                        },
                        enabled = !isRunning
                    )
                    Text("允许匿名访问")
                }


                Row(Modifier.padding(top = 20.dp)) {
                    Button(
                        onClick = {
                            if (rootUri == null) {
                                Toast.makeText(context, "请先选择FTP根目录", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val port = portText.toIntOrNull() ?: BuildConfig.DEFAULT_FTP_PORT
                            val intent = Intent(context, FtpForegroundService::class.java).apply {
                                action = FtpForegroundService.ACTION_START
                                putExtra(FtpForegroundService.EXTRA_PORT, port)
                                putExtra(FtpForegroundService.EXTRA_ROOT_URI, rootUri)

                                putExtra(FtpForegroundService.EXTRA_USERNAME, username)
                                putExtra(FtpForegroundService.EXTRA_PASSWORD, password)
                                putExtra(FtpForegroundService.EXTRA_ALLOW_ANON, allowAnonymous)
                            }
                            ContextCompat.startForegroundService(context, intent)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isRunning // ✅ 仅在未启动时可点
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
                        enabled = isRunning // ✅ 仅在运行时可点
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
