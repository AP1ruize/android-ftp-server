package com.example.ftpembed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FtpConnectionStatusChip(state: String, modifier: Modifier = Modifier) {
    val (label, background, foreground) = when (state) {
        "Connected" -> Triple("相机已连接", Color(0xFF66BB6A), Color(0xFF1B5E20))
        "Transferring" -> Triple("传输中", Color(0xFF4FC3F7), Color(0xFF01579B))
        else -> Triple("相机未连接", Color(0xFFEF5350), Color(0xFFB71C1C))
    }

    Text(
        text = label,
        color = foreground,
        modifier = modifier
            .background(background, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
