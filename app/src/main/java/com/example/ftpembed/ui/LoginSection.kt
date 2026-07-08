package com.example.ftpembed.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ftpembed.auth.AuthState

@Composable
fun LoginSection(
    authState: AuthState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (authState) {
        AuthState.Loading -> {
            Text("账户：登录中…（等待浏览器返回）", modifier = modifier.fillMaxWidth())
        }
        is AuthState.LoggedIn -> {
            val email = authState.email ?: "已登录用户"
            Text("账户：$email", modifier = modifier.fillMaxWidth())
            OutlinedButton(
                onClick = onLogout,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("退出登录")
            }
        }
        is AuthState.Error -> {
            Text(
                "登录失败：${authState.message}",
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.fillMaxWidth(),
            )
            Button(
                onClick = onLogin,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("重新登录")
            }
        }
        AuthState.LoggedOut -> {
            Text(
                "使用 AlphaHalf 账号登录（邮箱+密码）",
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier.fillMaxWidth(),
            )
            Button(
                onClick = onLogin,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("登录")
            }
        }
    }
}
