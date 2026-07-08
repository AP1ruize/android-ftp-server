package com.example.ftpembed

import com.example.ftpembed.ftp.FtpClientState
import com.example.ftpembed.ftp.FtpClientStateMachine
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import java.net.InetSocketAddress

class AppFtplet(
    private val stateMachine: FtpClientStateMachine = FtpClientStateMachine(),
    private val onEvent: (String, FtpClientState) -> Unit,
) : DefaultFtplet() {

    override fun onConnect(session: FtpSession?): FtpletResult {
        val state = stateMachine.onConnect(sessionId(session))
        onEvent("客户端已连接：${clientIp(session)}", state)
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession?): FtpletResult {
        val state = stateMachine.onDisconnect(sessionId(session))
        onEvent("客户端已断开：${clientIp(session)}", state)
        return FtpletResult.DEFAULT
    }

    override fun onUploadStart(session: FtpSession?, request: FtpRequest?): FtpletResult {
        val filename = request?.argument ?: "unknown"
        val state = stateMachine.onUploadStart(sessionId(session), filename)
        onEvent("📤 开始接收文件：$filename", state)
        return FtpletResult.DEFAULT
    }

    override fun onUploadEnd(session: FtpSession?, request: FtpRequest?): FtpletResult {
        val filename = request?.argument ?: "unknown"
        val state = stateMachine.onUploadEnd(sessionId(session), filename)
        onEvent("✅ 文件接收完成：$filename", state)
        return FtpletResult.DEFAULT
    }

    private fun clientIp(session: FtpSession?): String {
        val address: InetSocketAddress = session?.clientAddress ?: return "unknown"
        return address.hostString ?: address.address?.hostAddress ?: "unknown"
    }

    private fun sessionId(session: FtpSession?): String =
        "${clientIp(session)}:${session?.hashCode() ?: "unknown"}"
}
