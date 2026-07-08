# Implementation Notes - 2026-07-08

> **鉴权编码指南**：[`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md)  
> **全量计划**：[`DDNS_INTEGRATION_PLAN.md`](./DDNS_INTEGRATION_PLAN.md)

## 当前状态（2026-07-08 晚）

**阶段 0–6 代码已实现**；阶段 7 整合完成；**阶段 8 真机/MVP 联调待人工验收**。

## 已实现

### 阶段 0：MVP 配置
- `applicationId = com.ah.ddns"`、BuildConfig OIDC/DDNS 常量、`AuthConfig.kt`
- OAuth deep link、`manifestPlaceholders`

### 阶段 1：Rauthy PKCE 鉴权
- `TokenStore`（EncryptedSharedPreferences）
- `OidcAuthManager`（AppAuth + Custom Tabs）
- `AuthRepository`（实现 `AccessTokenProvider`，遵守 refresh `nbf`）
- `MainActivity`：`onNewIntent`、`LoginSection` UI

### 阶段 2：ah_ddns API
- `DdnsApi` / `DdnsApiClient` / `DdnsRepository`
- Bearer 拦截器 + 401 Authenticator 重试

### 阶段 3：IP 上报
- `LocalIpProvider` 已接入 `FtpForegroundService`
- `NetworkMonitor`、`DdnsUpdateScheduler`（60s 周期 + 2s 防抖 + 429 冷却）

### 阶段 4–5：UI
- `FqdnSection`（label、shard/zone、复制、同步状态）
- `NetworkBanner`（Wi-Fi / 热点设置跳转）

### 阶段 6：FTP 连接状态
- `FtpClientStateMachine` + `FtpConnectionStatusChip`（红/绿/浅蓝）

### 整合
- `AppServices`、`MainViewModel`、更新后的 `MainActivity`

### 修复
- `LabelValidator` 补全 `ns`/`admin`/`smtp` 保留词
- `AppFtplet` 恢复中文日志

### 测试
- JVM 单元测试全部通过（`testDebugUnitTest`）

## 待人工验收（需 MVP 环境与真机）

- [ ] MVP Rauthy PKCE 登录（邮箱+密码）
- [ ] Token 持久化与 refresh（杀进程恢复）
- [ ] `GET /v1/my-shard` + 创建 FQDN
- [ ] PATCH 局域网 IP + DNS 解析验证
- [ ] 相机/FileZilla 通过 `ftp://{fqdn}:port` 连接
- [ ] 无网络时 NetworkBanner 与设置按钮

## Rauthy 连通性规则

若联调时以下端点不可达，**停止并通知项目负责人**：

- `https://mvp.auth.alphahalf.cc/auth/v1/.well-known/openid-configuration`
- `https://mvp.auth.alphahalf.cc/auth/v1/oidc/authorize`
- `https://mvp.auth.alphahalf.cc/auth/v1/oidc/token`

## 验证命令

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```
