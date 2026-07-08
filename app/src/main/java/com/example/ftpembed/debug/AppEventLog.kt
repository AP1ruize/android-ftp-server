package com.example.ftpembed.debug

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppEventLog {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun log(tag: String, message: String) {
        _events.tryEmit("[$tag] $message")
    }
}
