package com.example.ftpembed

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

class FtpForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ftp_server_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PING = "ACTION_PING"
        const val ACTION_TOGGLE = "ACTION_TOGGLE"
        const val EXTRA_PORT = "port"
        const val EXTRA_ROOT_URI = "root_uri"

        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_ALLOW_ANON = "allow_anonymous"

    }

    private var ftpServer: FtpServer? = null
    private var running = AtomicBoolean(false)
    private var currentPort = BuildConfig.DEFAULT_FTP_PORT
    private lateinit var rootDir: File
    private var lastIp = "0.0.0.0"
    private var fileObserver: FileObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // ✅ 给 rootDir 一个默认值，防止空引用
        rootDir = File("/storage/emulated/0/Pictures/ftptest").apply {
            if (!exists()) mkdirs()
        }

        startForeground(NOTIF_ID, buildNotification(false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, BuildConfig.DEFAULT_FTP_PORT)
                val rootUriStr = intent.getStringExtra(EXTRA_ROOT_URI)
                startServer(port, rootUriStr)
            }
            ACTION_STOP -> stopServer()
            ACTION_TOGGLE -> {
                if (running.get()) stopServer()
                else startServer(currentPort, null)
            }
            ACTION_PING -> {
                updateForegroundNotification() // ✅ 确保通知存在
                sendStatus()
            }
        }
        return START_STICKY
    }

    private fun startServer(port: Int, rootUriStr: String?) {
        if (running.get()) {
            updateForegroundNotification()
            sendStatus()
            return
        }

        try {
            currentPort = port
//            val rootUri = rootUriStr?.let { Uri.parse(it) }
//            val realPath = File("/storage/emulated/0", getTreePathFromUri(rootUri))
//            rootDir = realPath

            // add user info
            val prefs = getSharedPreferences("ftp_prefs", MODE_PRIVATE)
            val username = prefs.getString("ftp_username", "user") ?: "user"
            val passwd = prefs.getString("ftp_password", "1234") ?: "1234"
            val allowAnon = prefs.getBoolean("ftp_allow_anon", true)

            // 固定使用 Pictures/ftptest 作为根目录
            rootDir = File("/storage/emulated/0/Pictures/ftptest").apply {
                if (!exists()) mkdirs()
            }

            val serverFactory = FtpServerFactory()
            // ✅ 注册 Ftplet，用于回调上传事件
            serverFactory.ftplets = mapOf(
                "appFtplet" to AppFtplet { msg ->
                    sendStatus(message = msg)
                }
            )

            val listenerFactory = ListenerFactory()
            listenerFactory.setPort(currentPort)
            serverFactory.addListener("default", listenerFactory.createListener())

            // ftp user config, anon only
//            val user = BaseUser().apply {
//                name = "anonymous"
//                homeDirectory = rootDir.absolutePath
//                authorities = listOf<Authority>(WritePermission())
//            }
//            serverFactory.userManager.save(user)


            // ftp user config, user+anon
            val users = mutableListOf<BaseUser>()

            val mainUser = BaseUser().apply {
                name = username
                password = passwd
                homeDirectory = rootDir.absolutePath
                authorities = listOf<Authority>(WritePermission())
            }
            users.add(mainUser)

            if (allowAnon) {
                val anon = BaseUser().apply {
                    name = "anonymous"
                    homeDirectory = rootDir.absolutePath
                    authorities = listOf<Authority>(WritePermission())
                }
                users.add(anon)
            }

            val userManager = serverFactory.userManager
            users.forEach { userManager.save(it) }

            // create server & start
            ftpServer = serverFactory.createServer()
            ftpServer?.start()
            running.set(true)

            // 启动文件监听器
            startWatchingFiles(rootDir)

            startForeground(NOTIF_ID, buildNotification(true))
            updateForegroundNotification()
            sendStatus()
        } catch (e: Exception) {
            running.set(false)
            ftpServer = null
            startForeground(
                NOTIF_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("FTP 启动失败")
                    .setContentText(e.message)
                    .build()
            )
            sendStatus(error = e.message ?: "启动失败")
            stopSelf()
        }
    }

    private fun startWatchingFiles(dir: File) {
        fileObserver?.stopWatching()
        fileObserver = object : FileObserver(dir.path, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) {
                    sendStatus(message = "新文件: $path")
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun stopServer() {
        try { ftpServer?.stop() } catch (_: Exception) {}
        ftpServer = null
        running.set(false)
        fileObserver?.stopWatching()
        fileObserver = null
//        stopForeground(STOP_FOREGROUND_REMOVE)
        // ✅ 改为仅刷新为“未运行”通知
        updateForegroundNotification()
        sendStatus()
//        stopSelf()
    }

    private fun sendStatus(error: String? = null, message: String? = null) {
        val intent = Intent(MainActivity.ACTION_STATUS).apply {
            setPackage(packageName) // 限定仅在本应用内分发
            putExtra(MainActivity.EXTRA_RUNNING, running.get())
            putExtra(MainActivity.EXTRA_IP, getLocalIpv4().also { lastIp = it })
            putExtra(MainActivity.EXTRA_PORT, currentPort)
            putExtra(MainActivity.EXTRA_ROOT, rootDir.absolutePath)
            if (error != null) putExtra(MainActivity.EXTRA_ERR, error)
            if (message != null) putExtra(MainActivity.EXTRA_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun getTreePathFromUri(uri: Uri?): String {
        if (uri == null) return "Pictures"
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        return if (parts.size == 2) parts[1] else ""
    }

    private fun buildNotification(isRunning: Boolean): Notification {
        // ✅ 从 SharedPreferences 获取用户名与匿名访问状态
        val prefs = getSharedPreferences("ftp_prefs", MODE_PRIVATE)
        val username = prefs.getString("ftp_username", "user") ?: "user"
        val allowAnon = prefs.getBoolean("ftp_allow_anon", true)
        val currentUser = if (allowAnon) "$username / anonymous" else username

        val ip = getLocalIpv4().also { lastIp = it }
        val clickIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, FtpForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val content = if (isRunning)
            "ftp://$ip:$currentPort  \n用户： $currentUser" else "FTP 服务未运行"

        val toggleIntent = Intent(this, FtpForegroundService::class.java).apply {
            action = if (isRunning) ACTION_STOP else ACTION_TOGGLE
        }
        val togglePi = PendingIntent.getService(this, 2, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
        val toggleText = if (isRunning) "停止" else "启动"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FTP 服务器")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("连接：$content\n根目录：${rootDir.absolutePath}"))
            .setContentIntent(pi)
            .addAction(0, toggleText, togglePi) // ✅ 按状态切换
            .setOngoing(true) // ✅ 永远常驻
            .build()
    }

    private fun updateForegroundNotification() {
//        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
//            .notify(NOTIF_ID, buildNotification(true))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(running.get()))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FTP Server", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun getLocalIpv4(): String {
        return try {
            val en = NetworkInterface.getNetworkInterfaces()
            en.toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress ?: "0.0.0.0"
        } catch (e: Exception) {
            lastIp.ifEmpty { "0.0.0.0" }
        }
    }

    fun terminateService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

}
