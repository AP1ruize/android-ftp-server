package com.example.ftpembed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ftpembed.network.NetworkKind

@Composable
fun NetworkBanner(
    networkKind: NetworkKind,
    onOpenWifiSettings: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (networkKind != NetworkKind.None) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "需要连接 Wi-Fi 或开启热点",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "DDNS 与相机 FTP 需要手机处于可用局域网环境",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Button(
                    onClick = onOpenWifiSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Wi-Fi 设置")
                }
                Button(
                    onClick = onOpenHotspotSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("热点设置")
                }
            }
        }
    }
}
