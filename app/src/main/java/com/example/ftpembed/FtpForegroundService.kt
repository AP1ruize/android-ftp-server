package com.example.ftpembed

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
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
    }

    private var ftpServer: FtpServer? = null
    private var running = AtomicBoolean(false)
    private var currentPort = BuildConfig.DEFAULT_FTP_PORT
    private lateinit var rootDir: File
    private var rootDisplayLabel: String = ""
    private var lastIp = "0.0.0.0"
    private var fileObserver: FileObserver? = null
    private lateinit var settings: FtpSettingsRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = FtpSettingsRepository(this)
        createNotificationChannel()
        val effective = settings.getEffectiveRootInfo()
        rootDir = File(effective.absolutePath)
        rootDisplayLabel = effective.label
        startForeground(NOTIF_ID, buildNotification(false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, settings.getPort())
                startServer(port)
            }
            ACTION_STOP -> stopServer()
            ACTION_TOGGLE -> {
                if (running.get()) stopServer() else startServer(settings.getPort())
            }
            ACTION_PING -> {
                updateForegroundNotification()
                sendStatus()
            }
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        if (running.get()) {
            updateForegroundNotification()
            sendStatus()
            return
        }

        try {
            currentPort = port
            settings.setPort(port)

            when (val resolved = settings.resolveRootDirectory(requireSaf = false)) {
                is FtpSettingsRepository.RootResolveResult.Success -> {
                    rootDir = resolved.dir
                    rootDisplayLabel = resolved.displayLabel
                }
                is FtpSettingsRepository.RootResolveResult.Fallback -> {
                    rootDir = resolved.dir
                    rootDisplayLabel = resolved.displayLabel
                }
                is FtpSettingsRepository.RootResolveResult.Failure -> {
                    sendStatus(error = resolved.message)
                    updateForegroundNotification()
                    return
                }
            }

            val creds = settings.getCredentials()
            val serverFactory = FtpServerFactory()
            serverFactory.ftplets = mapOf(
                "appFtplet" to AppFtplet { msg ->
                    sendStatus(message = msg)
                },
            )

            val listenerFactory = ListenerFactory()
            listenerFactory.port = currentPort
            serverFactory.addListener("default", listenerFactory.createListener())

            val users = mutableListOf<BaseUser>()
            users.add(
                BaseUser().apply {
                    name = creds.username
                    password = creds.password
                    homeDirectory = rootDir.absolutePath
                    authorities = listOf<Authority>(WritePermission())
                },
            )
            if (creds.allowAnonymous) {
                users.add(
                    BaseUser().apply {
                        name = "anonymous"
                        homeDirectory = rootDir.absolutePath
                        authorities = listOf<Authority>(WritePermission())
                    },
                )
            }

            val userManager = serverFactory.userManager
            users.forEach { userManager.save(it) }

            ftpServer = serverFactory.createServer()
            ftpServer?.start()
            running.set(true)

            startWatchingFiles(rootDir)
            startForeground(NOTIF_ID, buildNotification(true))
            updateForegroundNotification()
            sendStatus(message = "FTP 服务已启动")
        } catch (e: Exception) {
            running.set(false)
            ftpServer = null
            startForeground(
                NOTIF_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("FTP 启动失败")
                    .setContentText(e.message)
                    .build(),
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
        try {
            ftpServer?.stop()
        } catch (_: Exception) {
        }
        ftpServer = null
        running.set(false)
        fileObserver?.stopWatching()
        fileObserver = null
        val effective = settings.getEffectiveRootInfo()
        rootDir = File(effective.absolutePath)
        rootDisplayLabel = effective.label
        updateForegroundNotification()
        sendStatus(message = "FTP 服务已停止")
    }

    private fun currentRootInfo(): FtpSettingsRepository.RootDisplayInfo {
        return if (running.get()) {
            FtpSettingsRepository.RootDisplayInfo(rootDisplayLabel, rootDir.absolutePath)
        } else {
            settings.getEffectiveRootInfo()
        }
    }

    private fun sendStatus(error: String? = null, message: String? = null) {
        val rootInfo = currentRootInfo()
        val intent = Intent(MainActivity.ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(MainActivity.EXTRA_RUNNING, running.get())
            putExtra(MainActivity.EXTRA_IP, getLocalIpv4().also { lastIp = it })
            putExtra(MainActivity.EXTRA_PORT, currentPort)
            putExtra(MainActivity.EXTRA_ROOT, rootInfo.absolutePath)
            putExtra(MainActivity.EXTRA_ROOT_LABEL, rootInfo.label)
            if (error != null) putExtra(MainActivity.EXTRA_ERR, error)
            if (message != null) putExtra(MainActivity.EXTRA_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(isRunning: Boolean): Notification {
        val creds = settings.getCredentials()
        val currentUser = if (creds.allowAnonymous) {
            "${creds.username} / anonymous"
        } else {
            creds.username
        }

        val ip = getLocalIpv4().also { lastIp = it }
        val clickIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE)

        val content = if (isRunning) {
            "ftp://$ip:$currentPort  \n用户： $currentUser"
        } else {
            "FTP 服务未运行"
        }

        val toggleIntent = Intent(this, FtpForegroundService::class.java).apply {
            action = if (isRunning) ACTION_STOP else ACTION_TOGGLE
        }
        val togglePi = PendingIntent.getService(this, 2, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
        val toggleText = if (isRunning) "停止" else "启动"

        val rootInfo = currentRootInfo()
        val rootLine = "${rootInfo.label}\n${rootInfo.absolutePath}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FTP 服务器")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("连接：$content\n根目录：$rootLine"))
            .setContentIntent(pi)
            .addAction(0, toggleText, togglePi)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(running.get()))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FTP Server", NotificationManager.IMPORTANCE_LOW),
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
