# android-ftp-server × ah_ddns 集成实现计划

> 文档版本：2026-07-08（rev.2）  
> 状态：**阶段 0 已完成；阶段 1（Rauthy PKCE）待编码**  
> 关联仓库：`android-ftp-server`（本仓库）、`ah_ddns`（MVP 已上线）  
> 鉴权规范（权威）：[`ah_ddns/docs/spec/mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)  
> 本仓库鉴权落地：[`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md)  
> API 规范：[`api.openapi.yaml`](../../../ah_ddns/docs/spec/api.openapi.yaml)、[`api-responses.md`](../../../ah_ddns/docs/spec/api-responses.md)

---

## 目录

- [1. 总目标](#1-总目标)
- [2. 业务场景与用户故事](#2-业务场景与用户故事)
- [3. 现状与差距分析](#3-现状与差距分析)
- [4. 架构设计](#4-架构设计)
- [5. 分阶段实现计划](#5-分阶段实现计划)
- [6. UI/UX 规格](#6-uiux-规格)
- [7. 数据模型与持久化](#7-数据模型与持久化)
- [8. 网络与 IP 上报策略](#8-网络与-ip-上报策略)
- [9. 错误处理与边界情况](#9-错误处理与边界情况)
- [10. 依赖与配置变更](#10-依赖与配置变更)
- [11. 测试计划](#11-测试计划)
- [12. 风险与待决事项](#12-风险与待决事项)
- [13. 验收标准](#13-验收标准)
- [14. 文档索引](#14-文档索引)

---

## 1. 总目标

将 **ah_ddns** 动态 DNS 能力接入 **android-ftp-server**，使用户完成以下闭环：

```
邮箱+密码登录（Rauthy）
  → 获取 user_shard 与 zone
  → 自定义 4 位 label，形成 FQDN（如 ab12.k3m9x2.alsh.cc）
  → App 自动检测本机在当前局域网（Wi-Fi 或热点）内的 IPv4
  → 通过 ah_ddns API 上报 IPv4
  → 相机通过用户自定义 FQDN 做 DNS 查询，得到 App FTP 服务的局域网地址
  → App 界面展示 FQDN、网络状态、DDNS 同步状态、相机 FTP 连接状态
```

### 1.1 成功标准（一句话）

用户在 Wi-Fi/热点环境下启动 App 后，能在界面上看到自己的 **FQDN**；相机配置该 FQDN 作为 FTP 主机名即可连上手机 FTP 服务；App 能实时反映 **DDNS 同步** 与 **相机连接/传输** 状态。

### 1.2 非目标（本期不做）

| 项 | 说明 |
|----|------|
| 公网穿透 / NAT 打洞 | 相机与手机须在同一局域网（同一 Wi-Fi 或手机热点） |
| App 内注册/改密 API | 账号生命周期由 Rauthy 托管 Web 完成 |
| IPv6 | ah_ddns 一期仅 IPv4 |
| 多 FQDN 管理 UI | API 支持最多 5 条，App 一期聚焦 **单条主 FQDN**（可预留扩展） |
| 迁入 ah_kotlin 主工程 | 本计划仅在 `android-ftp-server` 内实现；迁入另开任务 |
| FTPS / 加密 FTP | 沿用现有明文 FTP |

---

## 2. 业务场景与用户故事

### 2.1 典型场景

1. 摄影师开启手机 **Wi-Fi 热点** 或连接 **同一 Wi-Fi**
2. 打开 App → 邮箱+密码登录
3. 设置 label（如 `cam1` → 实际 FQDN `cam1.k3m9x2.alsh.cc`）
4. 启动 FTP 服务
5. 在相机 FTP 设置中填写 FQDN + 端口 + 凭据
6. 相机 DNS 查询 FQDN → 得到手机局域网 IP → 建立 FTP 连接
7. App 显示「已连接（绿色）」；传图时显示「传输中（浅蓝）」

### 2.2 用户故事

| ID | 作为… | 我希望… | 以便… |
|----|-------|---------|-------|
| US-01 | 新用户 | 用邮箱+密码登录 | 绑定我的 DDNS 账号 |
| US-02 | 登录用户 | 看到我的 shard 前缀（如 `k3m9x2.alsh.cc`） | 理解 FQDN 组成 |
| US-03 | 登录用户 | 自定义 4 位 label 并保存 | 得到专属 FQDN |
| US-04 | 登录用户 | App 自动上报当前局域网 IP | 相机能通过 FQDN 找到我的手机 |
| US-05 | 未联网用户 | 看到明确提示和两个快捷按钮（Wi-Fi / 热点设置） | 快速恢复可用网络 |
| US-06 | 使用者 | 在界面上看到 FQDN 与 `ftp://FQDN:port` | 方便配置相机 |
| US-07 | 使用者 | 看到相机 FTP 连接状态（颜色区分） | 知道相机是否连上、是否在传图 |
| US-08 | 使用者 | token 过期后自动刷新 | 无需频繁重新登录 |

---

## 3. 现状与差距分析

### 3.1 android-ftp-server 已有能力

| 能力 | 现状 | 文件 |
|------|------|------|
| FTP 启停 | ✅ Apache FTPServer + 前台 Service | `FtpForegroundService.kt` |
| 本地 IPv4 检测 | ✅ `NetworkInterface` 优先非 loopback IPv4 | `network/LocalIpProvider.kt`、`FtpForegroundService.kt` |
| 连接/断开/上传事件 | ✅ 日志 + 结构化状态 | `AppFtplet.kt`、`ftp/FtpClientStateMachine.kt` |
| 相机 FTP 状态 Chip | ✅ 未连接/已连接/传输中（颜色） | `MainActivity.kt` |
| 配置持久化 | ✅ SharedPreferences | `FtpSettingsRepository.kt` |
| UI | ✅ 单屏 Compose | `MainActivity.kt` |
| MVP OAuth 配置 | ✅ BuildConfig + `AuthConfig` | `app/build.gradle.kts`、`auth/AuthConfig.kt` |
| OAuth Deep Link | ✅ `com.ah.ddns:/oauth2redirect` | `AndroidManifest.xml` |
| DDNS 辅助类 | ✅ Label 校验、ApiError 解析、模型 | `ddns/LabelValidator.kt` 等 |

### 3.2 缺失能力

| 能力 | 差距 |
|------|------|
| OIDC 登录 | 无 AppAuth、无 PKCE 流程、无 Token 加密存储（见 [`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md) 阶段 1） |
| ah_ddns API | 无 OkHttp/Retrofit、无 `/v1/*` 调用 |
| FQDN 管理 UI | 无 label 输入/展示/复制 |
| 网络状态监听 | 无 `ConnectivityManager`；无 Wi-Fi/热点/无网判断与设置跳转 |
| IP 变化上报 | 无 PATCH 调度与 60s 节流 |

### 3.3 ah_ddns MVP 环境参数

| 项 | 值 |
|----|-----|
| API Base | `https://mvp.api.alphahalf.cc` |
| IdP Issuer | `https://mvp.auth.alphahalf.cc/auth/v1/` |
| OIDC Client ID | `ah-mobile` |
| DNS Zone | `alsh.cc`（以 `GET /v1/my-shard` 返回为准） |
| Redirect URI（已登记且已对齐） | `com.ah.ddns:/oauth2redirect` |
| applicationId | `com.ah.ddns` |
| Scopes | `openid profile email offline_access dns:records:read dns:records:write` |

MVP 鉴权接入细节、PKCE 流程、refresh `nbf` 约束与阶段 1 实现清单见 **[`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md)**（映射 [`mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)）。

### 3.4 关于「邮箱+密码登录」的说明

ah_ddns **不提供**登录 API。用户感知的「邮箱+密码」发生在 **Rauthy MVP 托管登录页**（Chrome Custom Tabs），App 侧实现 **Authorization Code + PKCE (S256)**，规范见 [`mobile-auth-rauthy.md` §5–§6](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)。  
Dev 环境的 `password` grant 仅用于 ah_ddns 本地 curl 联调，**禁止**作为本 App 生产登录方式。

---

## 4. 架构设计

### 4.1 目标分层

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (Compose)                                      │
│  MainActivity / 各 Composable 区块                        │
│  - 登录区、FQDN 区、网络提示区、FTP 状态区、事件日志       │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  Presentation / State                                    │
│  MainViewModel（新建，逐步替代 Activity 内 mutableState）   │
│  - AuthState, DdnsState, NetworkState, FtpClientState    │
└───────────────────────────┬─────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐  ┌────────────────┐  ┌──────────────────┐
│ AuthRepository│  │ DdnsRepository │  │ NetworkMonitor   │
│ TokenStore    │  │ DdnsApiClient  │  │ LocalIpProvider  │
└───────┬───────┘  └───────┬────────┘  └────────┬─────────┘
        │                  │                     │
        ▼                  ▼                     ▼
   Rauthy OIDC      ah_ddns REST API      ConnectivityManager
                    PATCH /v1/records      NetworkInterface
```

### 4.2 建议新增包结构

```
com.example.ftpembed/
├── auth/
│   ├── AuthConfig.kt           # issuer、client_id、redirect_uri（BuildConfig）
│   ├── AuthRepository.kt       # login / logout / refresh / session 状态
│   ├── TokenStore.kt           # EncryptedSharedPreferences 或 DataStore
│   └── OidcAuthManager.kt      # AppAuth 封装 PKCE 流程
├── ddns/
│   ├── DdnsApi.kt              # Retrofit interface
│   ├── DdnsApiClient.kt        # 带 Bearer 拦截器
│   ├── DdnsRepository.kt       # my-shard、records CRUD、IP 上报
│   ├── DdnsUpdateScheduler.kt  # 定时 + 网络变化触发 PATCH
│   └── model/                  # Record, MyShard, ApiError
├── network/
│   ├── NetworkMonitor.kt       # Wi-Fi / Hotspot / None 检测
│   ├── LocalIpProvider.kt      # 增强版 IPv4 检测（绑定活跃网络）
│   └── NetworkSettingsNavigator.kt  # 跳转系统 Wi-Fi / 热点设置
├── ftp/
│   ├── FtpClientState.kt       # 状态枚举 + 颜色映射
│   └── FtpStateHolder.kt       # 由 AppFtplet 回调驱动（或 Service 内维护）
├── ui/
│   ├── LoginSection.kt
│   ├── FqdnSection.kt
│   ├── NetworkBanner.kt
│   └── FtpConnectionStatusChip.kt
└── （现有文件保持不变或小幅扩展）
```

### 4.3 核心数据流

#### 登录 + 首次 DDNS 初始化

```
用户点击「登录」
  → OidcAuthManager 打开 Custom Tabs（Rauthy authorize URL + PKCE）
  → Deep Link 回调携带 code
  → POST {issuer}oidc/token → access_token + refresh_token
  → TokenStore 持久化
  → GET /v1/my-shard → 缓存 user_shard + zone
  → GET /v1/records → 若已有记录则加载；否则等待用户设置 label
```

#### IP 上报（DDNS 心跳）

```
NetworkMonitor 检测到 Wi-Fi/Hotspot 且 IP 可用
  → LocalIpProvider.getLanIpv4()
  → 若 IP 变化 或 距上次成功 PATCH ≥ 60s
      → PATCH /v1/records/{label} {"ipv4": "192.168.x.x"}
  → 更新 UI：上次同步时间、同步结果
```

#### FTP 连接状态

```
AppFtplet.onConnect     → FtpClientState.Connected
AppFtplet.onUploadStart → FtpClientState.Transferring
AppFtplet.onUploadEnd   → FtpClientState.Connected（若仍有连接）或 Idle
AppFtplet.onDisconnect  → FtpClientState.Disconnected（若无其他 session）
  → 经 Broadcast / StateFlow 通知 UI 更新颜色
```

> Apache FTPServer 可能同时存在多个 FTP session；状态机需维护 **活跃连接数** 与 **传输中计数**，避免单连接断开时误报「未连接」。

---

## 5. 分阶段实现计划

每个阶段列出：**目标**、**交付物**、**依赖**、**完成判据**。

---

### 阶段 0：工程准备与配置对齐 ✅ 已完成

**目标**：MVP OAuth/DDNS 配置与 Deep Link 基础设施。

| 交付物 | 说明 | 状态 |
|--------|------|------|
| `BuildConfig` MVP 字段 | `DDNS_API_BASE`、`OIDC_*`、`OIDC_SCOPE` | ✅ |
| `applicationId` | `com.ah.ddns` 与 redirect 一致 | ✅ |
| `auth/AuthConfig.kt` | discovery / authorize / token URL 封装 | ✅ |
| Manifest Deep Link | `com.ah.ddns:/oauth2redirect` | ✅ |
| Manifest queries | Custom Tabs / 浏览器 | ✅ |
| Gradle 依赖（AppAuth 等） | 阶段 1 编码时添加 | ☐ |

**完成判据**：✅ App 编译安装；配置与 MVP Rauthy bootstrap 一致。

**下一步**：按 [`RAUTHY_MVP_AUTH.md` §8](./RAUTHY_MVP_AUTH.md#8-android-实现清单阶段-1) 实施阶段 1。

---

### 阶段 1：OIDC 登录与会话管理（Rauthy MVP）

**目标**：用户通过 **已部署 MVP Rauthy** 完成邮箱+密码登录；App 持久化 token 并维持会话。

**规范依据**：[`mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md) 全文；本仓库 **[`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md)**。

| 任务 | 细节 |
|------|------|
| 添加依赖 | AppAuth、`androidx.security:security-crypto`；`manifestPlaceholders["appAuthRedirectScheme"]` |
| 实现 `OidcAuthManager` | Discovery → Custom Tabs authorize → code 换 token；PKCE S256 |
| 实现 `TokenStore` | EncryptedSharedPreferences；存 access/refresh/exp |
| 实现 `AuthRepository` | `startLogin`、`handleRedirectIntent`、`getValidAccessToken`、`logout` |
| Refresh 流程 | 仅对 `{issuer}oidc/token`；**遵守 Rauthy `nbf`**（§7.1）：勿在 access 有效时过早 refresh |
| `MainActivity` | `onNewIntent` 处理 OAuth redirect；恢复 session |
| UI：登录区 | 未登录：「登录」；已登录：email +「退出」 |

**关键约束**（摘自 `mobile-auth-rauthy.md`）：

- scope 必须含 **`offline_access`** 才有 `refresh_token`
- **只用 `access_token` 调 API**；refresh_token **不得**发给 ah_ddns
- API 返回 **401** → refresh **一次** → 仍 401 → 重新 PKCE
- **禁止** WebView 长期托管登录页；**禁止**生产 password grant

**完成判据**：见 [`RAUTHY_MVP_AUTH.md` §11](./RAUTHY_MVP_AUTH.md#11-联调验收)。

**预估工作量**：2–3 天

---

### 阶段 2：ah_ddns API 客户端与 FQDN 数据层

**目标**：登录后可获取 shard、管理 DNS 记录。

| 任务 | 细节 |
|------|------|
| `DdnsApi` Retrofit 定义 | `GET /v1/my-shard`、`GET/POST/PATCH/DELETE /v1/records` |
| Bearer 拦截器 | 自动附加 token；401 交 AuthRepository 处理 |
| 错误解析 | 统一解析 `{"code","message"}` → sealed class |
| `DdnsRepository` | `fetchShard()`、`listRecords()`、`createRecord(label, ipv4)`、`updateIp(label, ipv4)` |
| Label 校验 | 客户端预校验：4 位 `[a-z0-9]`、reserved 列表、转小写 |
| 首次流程顺序 | **严格顺序**：`my-shard` → `POST /records`（禁止并发首次 shard 分配） |

**API 响应处理要点**：

| 场景 | 处理 |
|------|------|
| PATCH IP 未变 | 200 + `no_change: true`，视为成功 |
| PATCH 过于频繁 | 429 `throttled`，退避 ≥60s |
| POST 重复 label | 409 `conflict`，提示用户更换 |
| DELETE 成功 | 204 无 body，禁止 JSON 解析 |

**完成判据**：登录后手动触发 API 调用（可用调试 UI），能在 ah_ddns 后台看到记录创建与 IP 更新。

**预估工作量**：2 天

---

### 阶段 3：局域网 IP 检测与 DDNS 自动上报

**目标**：App 自动检测本机局域网 IPv4，并在网络变化时上报 ah_ddns。

| 任务 | 细节 |
|------|------|
| `LocalIpProvider` | 绑定当前活跃网络的 IPv4；排除 loopback、link-local |
| 增强策略 | 优先 Wi-Fi / Hotspot 对应 interface；与 `ConnectivityManager` 联动 |
| `NetworkMonitor` | 监听 `onAvailable` / `onLost` / `onCapabilitiesChanged` |
| 网络分类 | `WifiConnected`、`HotspotActive`、`NoUsableNetwork` |
| `DdnsUpdateScheduler` | 前台：FTP Service 运行时每 60s 检查；网络变化立即尝试 |
| 上报内容 | PATCH body 仅 `{"ipv4": "<LAN_IP>"}` |
| 与 FTP Service 协作 | FTP 启动时启动 DDNS 心跳；停止时可停止或降频（待定） |

**完成判据**：

- 连接 Wi-Fi 后 60s 内 FQDN 解析到正确局域网 IP（`nslookup` / 相机实测）
- 切换 Wi-Fi 后 IP 更新（受 60s API 节流限制）
- 无网络时不发 PATCH，UI 显示待上报

**预估工作量**：2–3 天

---

### 阶段 4：FQDN 设置与展示 UI

**目标**：用户可自定义 label，界面清晰展示完整 FQDN 与 FTP 连接串。

| 任务 | 细节 |
|------|------|
| FQDN 输入 | 4 位 label 输入框；实时校验；展示完整 `{label}.{shard}.{zone}` |
| Shard 展示 | 登录后只读显示 `user_shard` 与 `zone` |
| 创建/更新 | 首次：`POST /v1/records`；已有记录：加载并允许换 label（DELETE + POST 或 PATCH 仅 IP） |
| 复制功能 | 一键复制 FQDN、`ftp://fqdn:port` |
| 同步状态 | 上次成功时间、失败原因（throttled / unauthorized / no network） |
| 与 FTP 区块联动 | FTP 运行中显示「相机请使用：ftp://{fqdn}:{port}」 |

**完成判据**：用户从零完成 label 设置 → 看到 FQDN → 复制到相机可连。

**预估工作量**：1.5–2 天

---

### 阶段 5：无网络提醒与系统设置快捷入口

**目标**：未连接 Wi-Fi 且未开热点时，明确提醒并提供一键跳转。

| 任务 | 细节 |
|------|------|
| `NetworkBanner` 组件 | 顶部/中部醒目横幅：「需要连接 Wi-Fi 或开启热点才能使用 DDNS 与 FTP」 |
| 按钮 A：Wi-Fi 设置 | `Settings.ACTION_WIFI_SETTINGS` |
| 按钮 B：热点设置 | Android 版本差异大：优先 `Settings.ACTION_WIRELESS_SETTINGS` / `TetherSettings` intent；失败则 Toast 引导 |
| 显示条件 | `NetworkMonitor` 为 `NoUsableNetwork` 时显示；恢复后自动隐藏 |
| 与 FTP 启停关系 | 无网络时允许启动 FTP（局域网内仍可能有用），但 **DDNS 上报暂停** 并提示 |

**完成判据**：关闭 Wi-Fi 与热点 → 横幅出现 → 按钮可打开对应系统设置页。

**预估工作量**：1 天

---

### 阶段 6：相机 FTP 连接状态显示（颜色区分）

**目标**：独立于事件日志，提供结构化、带颜色的连接状态指示。

#### 6.1 状态定义

| 状态 | 含义 | 建议颜色 | 触发 |
|------|------|----------|------|
| `Disconnected` | 无相机连接 | **Red** `#EF5350` | 最后 session 断开 |
| `Connected` | 已连接，空闲 | **Green** `#66BB6A` | `onConnect` 且非传输中 |
| `Transferring` | 正在传文件 | **Light Blue** `#4FC3F7` | `onUploadStart`；`onUploadEnd` 后若仍连接 → Connected |

可选扩展（二期）：`Connecting`（Yellow）、多客户端计数。

#### 6.2 实现任务

| 任务 | 细节 |
|------|------|
| `FtpClientState` 枚举 + 主题色 | 统一 UI 引用 |
| Service 内状态机 | 维护 `activeSessions`、`activeTransfers` 计数 |
| 扩展广播协议 | 新增 `EXTRA_FTP_CLIENT_STATE` 或改用 `StateFlow` + `collectAsState` |
| `FtpConnectionStatusChip` | 大号状态胶囊/卡片：图标 + 文案 + 颜色 |
| 保留事件日志 | 现有 `LazyColumn` 日志继续保留，与状态 Chip 互补 |

**完成判据**：相机连接/断开/传图时，状态 Chip 颜色与文案正确切换；多文件连续传输不闪烁回 Disconnected。

**预估工作量**：1.5–2 天

---

### 阶段 7：整合、生命周期与体验打磨

**目标**：各模块联调，形成可交付 MVP。

| 任务 | 说明 |
|------|------|
| `MainViewModel` 收敛状态 | 减少 `MainActivity` 内散落的 `mutableState` |
| 启动恢复 | 已登录 + 已配置 label → 自动拉 records、恢复 DDNS 调度 |
| FTP 与 DDNS 联动 | 启动 FTP → 立即 PATCH 一次；停止 → 可选最后一次上报或停止 |
| 前台通知增强 | 通知栏展示 FQDN + FTP 状态（可选） |
| 日志与调试 | Debug 构建可开关 API 日志；Release 关闭 |
| 更新 `PROJECT_STATUS.md` | 同步实现现状 |

**完成判据**：端到端演示路径全部走通（见 [§13](#13-验收标准)）。

**预估工作量**：2 天

---

### 阶段 8：测试与文档

**目标**：保证回归质量，便于后续迁入主工程。

| 类型 | 内容 |
|------|------|
| 单元测试 | Label 校验、ApiError 解析、IP 选择逻辑、状态机转换 |
| 集成测试 | MockWebServer 测 Retrofit；Token refresh 流程 |
| 手动测试 | MVP 环境真机 + 相机或 FileZilla 通过 FQDN 连接 |
| 文档 | 更新 README；本计划标记「已实现」章节 |

**预估工作量**：2–3 天

---

### 阶段总览与时间估算

| 阶段 | 名称 | 状态 | 预估 |
|------|------|------|------|
| 0 | 工程准备（MVP 配置对齐） | ✅ 已完成 | — |
| 1 | Rauthy PKCE 登录 | ☐ 待编码 | 2–3 天 |
| 2 | DDNS API | 2 天 |
| 3 | IP 检测与上报 | 2–3 天 |
| 4 | FQDN UI | 1.5–2 天 |
| 5 | 网络提醒 | 1 天 |
| 6 | FTP 连接状态 UI | 1.5–2 天 |
| 7 | 整合打磨 | 2 天 |
| 8 | 测试文档 | 2–3 天 |
| **合计** | | **约 15–19 个工作日** |

建议实施顺序：**1 → 2 → 4 → 3 → 5 → 6 → 7 → 8**（阶段 0 已完成；先打通鉴权与 FQDN API，再做自动 IP 上报）。

> 阶段 1 详细步骤见 [`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md)，不要偏离 [`mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)。

---

## 6. UI/UX 规格

### 6.1 建议屏幕布局（自上而下）

```
┌──────────────────────────────────────┐
│  FTP Server 控制台                    │
├──────────────────────────────────────┤
│  [网络横幅 - 仅无网络时显示]          │
│  ⚠ 请连接 Wi-Fi 或开启热点            │
│  [打开 Wi-Fi 设置] [打开热点设置]     │
├──────────────────────────────────────┤
│  账户                                 │
│  已登录：user@example.com  [退出]     │
│  Shard：k3m9x2 · Zone：alsh.cc       │
├──────────────────────────────────────┤
│  我的 FQDN                            │
│  Label: [ab12]  (4位)                 │
│  完整地址: ab12.k3m9x2.alsh.cc  [复制] │
│  DDNS: 已同步 · 192.168.1.23 · 12:01  │
├──────────────────────────────────────┤
│  相机连接状态 ●已连接/未连接/传输中     │  ← 颜色 Chip
├──────────────────────────────────────┤
│  （现有 FTP 配置与启停控件）           │
├──────────────────────────────────────┤
│  事件日志（可滚动）                   │
└──────────────────────────────────────┘
```

### 6.2 颜色与无障碍

| 状态 | 颜色 | Compose 建议 |
|------|------|--------------|
| 未连接 | Red | `Color(0xFFEF5350)` |
| 已连接 | Green | `Color(0xFF66BB6A)` |
| 传输中 | Light Blue | `Color(0xFF4FC3F7)` |

- 文案勿仅依赖颜色（同时显示「已连接」等文字）
- 对比度满足 Material3 可读性

### 6.3 登录 UX

- 使用 **Custom Tabs** 打开 Rauthy（优于 WebView，符合 OAuth 安全实践）
- 登录失败：Toast + 可重试
- 首次登录后引导用户设置 label（Inline 提示）

---

## 7. 数据模型与持久化

### 7.1 本地存储分区

| 存储 | 内容 | 方式 |
|------|------|------|
| `TokenStore` | access_token、refresh_token、expires_at | EncryptedSharedPreferences |
| `DdnsPrefs` | user_shard、zone、selected_label、last_synced_ip、last_sync_at | DataStore 或 SharedPreferences |
| 现有 `ftp_prefs` | FTP 端口/凭据/根目录 | 不变 |

### 7.2 关键模型（Kotlin）

```kotlin
// 示例结构，实施时可微调
data class MyShard(val userShard: String, val zone: String)

data class DdnsRecord(
    val label: String,
    val fqdn: String,
    val ipv4: String,
    val ttl: Int,
)

sealed class DdnsSyncStatus {
    data object Idle : DdnsSyncStatus()
    data object Syncing : DdnsSyncStatus()
    data class Success(val ip: String, val at: Instant) : DdnsSyncStatus()
    data class Failed(val code: String, val message: String) : DdnsSyncStatus()
}

enum class FtpClientState { Disconnected, Connected, Transferring }

enum class NetworkKind { Wifi, Hotspot, None }
```

---

## 8. 网络与 IP 上报策略

### 8.1 局域网 IP 检测规则

1. 若当前活跃网络为 Wi-Fi → 取该网络对应 interface 的 IPv4
2. 若手机作为热点 AP → 取 AP interface（通常为 `192.168.43.x` 段）
3. 排除：`127.0.0.1`、`169.254.x.x`、蜂窝移动数据 IP（除非明确要报公网，**本期不报**）
4. 多 IP 时：优先与 `ConnectivityManager` 绑定 network 的地址

### 8.2 上报时机

| 触发 | 行为 |
|------|------|
| FTP 服务启动 | 立即尝试 PATCH |
| 网络切换 / IP 变化 | 防抖 2s 后 PATCH |
| 周期性 | 每 60s 检查；仅当 IP 变或到期需心跳时 PATCH |
| 用户手动 | 「立即同步」按钮（仍受 API 429 约束） |
| 无 label | 跳过 PATCH，UI 提示先配置 FQDN |
| 无网络 | 跳过 PATCH，显示网络横幅 |

### 8.3 与 ah_ddns 节流对齐

- API 最小间隔 **60s**（`min_update_interval_sec`）
- DNS TTL **15s**（相机测解析时可等待 ≤30s）
- 收到 `no_change: true` 时不视为错误

---

## 9. 错误处理与边界情况

| 场景 | App 行为 |
|------|----------|
| Token 过期 | Silent refresh → 失败则跳转登录 |
| 429 throttled | 显示「同步过于频繁，稍后再试」；定时 60s 后重试 |
| 401 unauthorized | 清除 token，回登录 |
| 403 quota_exceeded | 提示已达 5 条上限（一期 UI 只管理 1 条时可简化） |
| 400 bad_request | label 格式错误，inline 提示 |
| 409 conflict | label 已被占用，提示更换 |
| DNS 未生效 | UI 说明 TTL 15s；提供「上次上报 IP」供核对 |
| 相机与手机不在同一网段 | 连接失败；FAQ 提示检查 Wi-Fi/热点 |
| 杀进程重启 | 恢复 token、label、FTP 配置；重新注册 NetworkCallback |
| 多 FTP 客户端 | 状态机按 session 计数处理 |

---

## 10. 依赖与配置变更

### 10.1 建议新增 Gradle 依赖

```kotlin
// 版本以 libs.versions.toml 为准，实施时锁定
implementation("net.openid:appauth:<version>")
implementation("com.squareup.okhttp3:okhttp:<version>")
implementation("com.squareup.retrofit2:retrofit:<version>")
implementation("com.squareup.retrofit2:converter-moshi:<version>") // 或 kotlinx-serialization
implementation("androidx.security:security-crypto:<version>")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<version>")
implementation("androidx.work:work-runtime-ktx:<version>") // 可选，用于后台 PATCH
```

### 10.2 BuildConfig 示例

```kotlin
buildConfigField("String", "DDNS_API_BASE", "\"https://mvp.api.alphahalf.cc\"")
buildConfigField("String", "OIDC_ISSUER", "\"https://mvp.auth.alphahalf.cc/auth/v1/\"")
buildConfigField("String", "OIDC_CLIENT_ID", "\"ah-mobile\"")
buildConfigField("String", "OIDC_REDIRECT_SCHEME", "\"com.ah.ddns\"")
buildConfigField("String", "OIDC_REDIRECT_HOST", "\"oauth2redirect\"")
```

### 10.3 Manifest 要点

```xml
<!-- Deep Link for OAuth redirect -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="com.ah.ddns" android:host="oauth2redirect" />
</intent-filter>
```

---

## 11. 测试计划

### 11.1 单元测试

- [ ] `LabelValidator`：长度、字符集、reserved、大小写
- [ ] `ApiErrorParser`：各 code 映射
- [ ] `FtpClientStateMachine`：connect / disconnect / upload 序列
- [ ] `LocalIpProvider`：mock NetworkInterface 选择逻辑

### 11.2 集成 / 手动测试

- [ ] MVP Rauthy 登录 + refresh
- [ ] `GET /v1/my-shard` 首次分配
- [ ] `POST /v1/records` 创建 FQDN
- [ ] `PATCH` 上报局域网 IP，`no_change` 与成功更新
- [ ] 关闭 Wi-Fi → 横幅与按钮
- [ ] 相机通过 FQDN 连接 FTP（或 PC `ftp://fqdn:2121`）
- [ ] 传图时状态为「传输中 / 浅蓝」
- [ ] 杀进程恢复会话

### 11.3 联调命令参考

```powershell
# 验证 DNS 解析（相机同网段）
nslookup ab12.k3m9x2.alsh.cc ns1.alsh.cc
```

---

## 12. 风险与待决事项

| ID | 项 | 说明 | 建议 |
|----|-----|------|------|
| R-02 | 热点设置 Intent 碎片化 | 各 OEM/Android 版本差异大 | 多 intent fallback + 文案引导 |
| R-03 | 局域网 IP vs 公网 | 相机在同网段依赖 LAN IP | 本期只报 LAN IP；文档明确限制 |
| R-04 | API 60s 节流 | 网络频繁切换可能 429 | 退避 + UI 提示；避免无限重试 |
| R-05 | 明文 FTP | 局域网内明文传输 | 本期接受；安全加固另立项 |
| R-06 | 单模块无 DI | 复杂度上升后测试性变差 | 先用构造函数注入；必要时引入 Hilt（非本期必须） |
| D-01 | 是否支持多 FQDN | API 最多 5 条 | 一期 UI 仅 1 条；Repository 设计预留 list |
| D-02 | FTP 停止后是否继续 DDNS | 产品选择 | 建议：停止 FTP 仍保留 DNS 记录，但停止自动 PATCH |
| R-07 | Rauthy refresh `nbf` | 过早 refresh 导致 session 失效 | 严格按 [`RAUTHY_MVP_AUTH.md` §5.2](./RAUTHY_MVP_AUTH.md#52-rauthy-refresh-的-nbf-约束重要) 实现 |

---

## 13. 验收标准

以下全部通过即视为本期 **Done**：

1. **登录**：用户通过 Rauthy 邮箱+密码登录，App 展示邮箱与 shard/zone
2. **FQDN**：用户可设置 4 位 label，界面展示完整 FQDN，且与 ah_ddns 控制台一致
3. **IP 上报**：Wi-Fi 或热点下，App 自动 PATCH 当前局域网 IPv4；DNS 查询 FQDN 返回该 IP
4. **相机连接**：相机使用 `ftp://{fqdn}:{port}` 可连接并传图
5. **网络提醒**：无 Wi-Fi/热点时显示横幅 + 两个设置快捷按钮
6. **FTP 状态**：未连接（红）/ 已连接（绿）/ 传输中（浅蓝）显示正确
7. **会话**：token 过期自动 refresh；refresh 失败引导重新登录
8. **文档**：`PROJECT_STATUS.md` 与 README 已更新

---

## 附录 A：ah_ddns API 快速参考

| 方法 | 路径 | Scope | 用途 |
|------|------|-------|------|
| GET | `/v1/my-shard` | read | 获取 shard + zone |
| GET | `/v1/records` | read | 列表 |
| POST | `/v1/records` | write | 创建（body: `label`, `ipv4`） |
| PATCH | `/v1/records/{label}` | write | 更新 IP（body: `ipv4`） |
| DELETE | `/v1/records/{label}` | write | 删除（204） |

FQDN 格式：`{label}.{user_shard}.{zone}`，**客户端不得提交 fqdn 字段**。

---

## 附录 B：与现有文件的关系

| 现有文件 | 计划变更 |
|----------|----------|
| `MainActivity.kt` | 拆分 Composable；引入 ViewModel；增加 DDNS/网络/状态区块 |
| `FtpForegroundService.kt` | 集成 DdnsUpdateScheduler；扩展状态广播 |
| `AppFtplet.kt` | 增加传输状态回调（或合并到状态机） |
| `FtpSettingsRepository.kt` | 保持不变；或仅抽取 FTP 相关 |
| `AndroidManifest.xml` | Deep Link、queries |
| `app/build.gradle.kts` | 依赖 + BuildConfig |

---

*实施过程中若 ah_ddns spec 或 MVP 环境 URL 变更，请先更新 [`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md) 与本计划再编码。*

---

## 14. 文档索引

| 文档 | 用途 |
|------|------|
| [`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md) | **MVP Rauthy 鉴权接入（阶段 1 主文档）** |
| [`ah_ddns/docs/spec/mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md) | OIDC 规范原文（权威） |
| [`IMPLEMENTATION_NOTES_2026_07_08.md`](./IMPLEMENTATION_NOTES_2026_07_08.md) | 编码进度快照 |
| [`PROJECT_STATUS.md`](./PROJECT_STATUS.md) | 项目整体现状 |
