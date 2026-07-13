package com.example.ftpembed.ftp

import org.junit.Assert.assertEquals
import org.junit.Test

class FtpClientStateMachineTest {
    @Test
    fun state_tracksConnectionAndTransferLifecycle() {
        val machine = FtpClientStateMachine()

        assertEquals(FtpClientState.Disconnected, machine.state)
        assertEquals(FtpClientState.Connected, machine.onConnect("camera"))
        assertEquals(FtpClientState.Transferring, machine.onUploadStart("camera", "image-1"))
        assertEquals(FtpClientState.Connected, machine.onUploadEnd("camera", "image-1"))
        assertEquals(FtpClientState.Disconnected, machine.onDisconnect("camera"))
    }

    @Test
    fun state_staysConnectedWhenOneOfMultipleSessionsDisconnects() {
        val machine = FtpClientStateMachine()

        machine.onConnect("camera-a")
        machine.onConnect("camera-b")

        assertEquals(FtpClientState.Connected, machine.onDisconnect("camera-a"))
        assertEquals(FtpClientState.Disconnected, machine.onDisconnect("camera-b"))
    }

    @Test
    fun disconnect_clearsTransfersForThatSessionOnly() {
        val machine = FtpClientStateMachine()

        machine.onUploadStart("camera-a", "image-1")
        machine.onUploadStart("camera-b", "image-2")

        assertEquals(FtpClientState.Transferring, machine.onDisconnect("camera-a"))
        assertEquals(FtpClientState.Connected, machine.onUploadEnd("camera-b", "image-2"))
    }

    @Test
    fun disconnect_withoutUploadEnd_returnsToDisconnected() {
        val machine = FtpClientStateMachine()
        val sessionId = "stable-session-uuid"

        machine.onConnect(sessionId)
        machine.onUploadStart(sessionId, "DSC0001.JPG")

        // Camera hard-drops TCP before onUploadEnd — disconnect must clear transfers.
        assertEquals(FtpClientState.Disconnected, machine.onDisconnect(sessionId))
    }

    @Test
    fun disconnect_mismatchedSessionId_leavesTransferStuck() {
        // Documents the old bug: if disconnect uses a different id, transfers remain.
        val machine = FtpClientStateMachine()
        machine.onUploadStart("192.168.1.2:abc", "photo.jpg")

        assertEquals(FtpClientState.Transferring, machine.onDisconnect("unknown:abc"))
        assertEquals(FtpClientState.Transferring, machine.state)
    }

    @Test
    fun reconnect_afterCleanDisconnect_showsConnectedThenTransferring() {
        val machine = FtpClientStateMachine()
        val session1 = "session-1"
        val session2 = "session-2"

        machine.onConnect(session1)
        machine.onUploadStart(session1, "a.jpg")
        assertEquals(FtpClientState.Disconnected, machine.onDisconnect(session1))

        assertEquals(FtpClientState.Connected, machine.onConnect(session2))
        assertEquals(FtpClientState.Transferring, machine.onUploadStart(session2, "b.jpg"))
    }
}
