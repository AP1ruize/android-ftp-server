package com.example.ftpembed

import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import java.net.InetSocketAddress

class AppFtplet(private val onEvent: (String) -> Unit) : DefaultFtplet() {

    override fun onConnect(session: FtpSession?): FtpletResult? {
        onEvent("客户端已连接：${clientIp(session)}")
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession?): FtpletResult? {
        onEvent("客户端已断开：${clientIp(session)}")
        return FtpletResult.DEFAULT
    }

    override fun onUploadStart(session: FtpSession?, request: FtpRequest?): FtpletResult? {
        val filename = request?.argument ?: "unknown"
        onEvent("📤 开始接收文件：$filename")
        return FtpletResult.DEFAULT
    }

    override fun onUploadEnd(session: FtpSession?, request: FtpRequest?): FtpletResult? {
        val filename = request?.argument ?: "unknown"
        onEvent("✅ 文件接收完成：$filename")
        return FtpletResult.DEFAULT
    }

    private fun clientIp(session: FtpSession?): String {
        val address = session?.clientAddress as? InetSocketAddress ?: return "unknown"
        return address.hostString ?: address.address?.hostAddress ?: "unknown"
    }
}
