package com.example.ftpembed.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ftpembed.FqdnUiState
import com.example.ftpembed.auth.AuthState

@Composable
fun FqdnSection(
    authState: AuthState,
    fqdnState: FqdnUiState,
    previewFqdn: String?,
    syncStatusText: String,
    ftpUrl: String?,
    onLabelChange: (String) -> Unit,
    onSaveLabel: () -> Unit,
    onCopyFqdn: () -> Unit,
    onCopyFtpUrl: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (authState !is AuthState.LoggedIn) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text("我的 FQDN", style = MaterialTheme.typography.titleSmall)
        val shard = fqdnState.userShard
        val zone = fqdnState.zone
        if (!shard.isNullOrBlank() && !zone.isNullOrBlank()) {
            Text(
                "Shard：$shard · Zone：$zone",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        OutlinedTextField(
            value = fqdnState.labelInput,
            onValueChange = onLabelChange,
            label = { Text("Label（4 位）") },
            singleLine = true,
            isError = fqdnState.labelError != null,
            supportingText = fqdnState.labelError?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        val displayFqdn = fqdnState.activeRecord?.fqdn ?: previewFqdn
        if (!displayFqdn.isNullOrBlank()) {
            Text(
                "完整地址：$displayFqdn",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            syncStatusText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (!ftpUrl.isNullOrBlank()) {
            Text(
                "相机请使用：$ftpUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Button(
            onClick = onSaveLabel,
            enabled = !fqdnState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(if (fqdnState.isSaving) "保存中…" else "保存 FQDN")
        }

        OutlinedButton(
            onClick = onSyncNow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("立即同步 IP")
        }

        RowButtons(
            onCopyFqdn = onCopyFqdn,
            onCopyFtpUrl = onCopyFtpUrl,
            fqdnEnabled = !displayFqdn.isNullOrBlank(),
            ftpEnabled = !ftpUrl.isNullOrBlank(),
        )
    }
}

@Composable
private fun RowButtons(
    onCopyFqdn: () -> Unit,
    onCopyFtpUrl: () -> Unit,
    fqdnEnabled: Boolean,
    ftpEnabled: Boolean,
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        OutlinedButton(
            onClick = onCopyFqdn,
            enabled = fqdnEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text("复制 FQDN")
        }
        OutlinedButton(
            onClick = onCopyFtpUrl,
            enabled = ftpEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text("复制 FTP 地址")
        }
    }
}
