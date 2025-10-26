package com.example.ftpembed

import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult

class AppFtplet(private val onEvent: (String) -> Unit) : DefaultFtplet() {
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
}