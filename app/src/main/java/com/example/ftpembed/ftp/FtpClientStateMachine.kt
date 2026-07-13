package com.example.ftpembed.ftp

enum class FtpClientState {
    Disconnected,
    Connected,
    Transferring,
}

class FtpClientStateMachine {
    private val lock = Any()
    private val activeSessions = mutableSetOf<String>()
    private val activeTransfers = mutableSetOf<String>()

    val state: FtpClientState
        get() = synchronized(lock) { computeState() }

    fun onConnect(sessionId: String): FtpClientState = synchronized(lock) {
        activeSessions += sessionId
        computeState()
    }

    fun onDisconnect(sessionId: String): FtpClientState = synchronized(lock) {
        activeSessions -= sessionId
        activeTransfers.removeAll { it.startsWith("$sessionId:") }
        computeState()
    }

    fun onUploadStart(sessionId: String, transferId: String): FtpClientState = synchronized(lock) {
        activeSessions += sessionId
        activeTransfers += "$sessionId:$transferId"
        computeState()
    }

    fun onUploadEnd(sessionId: String, transferId: String): FtpClientState = synchronized(lock) {
        activeTransfers -= "$sessionId:$transferId"
        computeState()
    }

    fun reset(): FtpClientState = synchronized(lock) {
        activeSessions.clear()
        activeTransfers.clear()
        computeState()
    }

    private fun computeState(): FtpClientState = when {
        activeTransfers.isNotEmpty() -> FtpClientState.Transferring
        activeSessions.isNotEmpty() -> FtpClientState.Connected
        else -> FtpClientState.Disconnected
    }
}
