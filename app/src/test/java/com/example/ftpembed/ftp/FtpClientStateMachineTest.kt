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
}
