package com.example.ftpembed.ftp

enum class FtpClientState {
    Disconnected,
    Connected,
    Transferring,
}

class FtpClientStateMachine {
    private val activeSessions = mutableSetOf<String>()
    private val activeTransfers = mutableSetOf<String>()

    val state: FtpClientState
        get() = when {
            activeTransfers.isNotEmpty() -> FtpClientState.Transferring
            activeSessions.isNotEmpty() -> FtpClientState.Connected
            else -> FtpClientState.Disconnected
        }

    fun onConnect(sessionId: String): FtpClientState {
        activeSessions += sessionId
        return state
    }

    fun onDisconnect(sessionId: String): FtpClientState {
        activeSessions -= sessionId
        activeTransfers.removeAll { it.startsWith("$sessionId:") }
        return state
    }

    fun onUploadStart(sessionId: String, transferId: String): FtpClientState {
        activeSessions += sessionId
        activeTransfers += "$sessionId:$transferId"
        return state
    }

    fun onUploadEnd(sessionId: String, transferId: String): FtpClientState {
        activeTransfers -= "$sessionId:$transferId"
        return state
    }

    fun reset(): FtpClientState {
        activeSessions.clear()
        activeTransfers.clear()
        return state
    }
}
