# Rauthy MVP 鉴权接入指南（android-ftp-server）

> 文档版本：2026-07-08  
> 状态：**阶段 0 已完成配置对齐；阶段 1（PKCE 登录）待编码**  
> **规范来源（必读）**：[`ah_ddns/docs/spec/mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)  
> 本文档是该规范在本仓库的 **MVP 落地映射与实现清单**，不重复 ah_ddns 的全局约定。

---

## 目录

- [1. 职责与边界](#1-职责与边界)
- [2. MVP 环境参数（已对齐）](#2-mvp-环境参数已对齐)
- [3. 与本仓库代码的映射](#3-与本仓库代码的映射)
- [4. PKCE 登录流程（实现规格）](#4-pkce-登录流程实现规格)
- [5. Token 生命周期与 refresh 约束](#5-token-生命周期与-refresh-约束)
- [6. 账号相关用户操作](#6-账号相关用户操作)
- [7. 调用 ah_ddns API](#7-调用-ah_ddns-api)
- [8. Android 实现清单（阶段 1）](#8-android-实现清单阶段-1)
- [9. 文件与类设计](#9-文件与类设计)
- [10. UI 行为](#10-ui-行为)
- [11. 联调验收](#11-联调验收)
- [12. 禁止事项与常见错误](#12-禁止事项与常见错误)
- [13. 联调故障排查](#13-联调故障排查)

---

## 1. 职责与边界

与 [`mobile-auth-rauthy.md` §1](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md) 一致：

| 组件 | 职责 |
|------|------|
| **本 App** | Authorization Code + PKCE；加密持久化 token；`Authorization: Bearer` 调 ah_ddns |
| **Rauthy（MVP 已部署）** | 注册、邮箱验证、忘记密码、邮箱+密码登录（托管 Web） |
| **ah_ddns API** | 无登录端点；仅验签 JWT |

**本 App 不要实现**：注册 API、改密 API、验证码 API、Resource Owner Password Grant（生产路径）。

用户感知的「邮箱+密码登录」发生在 **Rauthy 托管登录页**（Chrome Custom Tabs），App 只负责发起 PKCE 并接收 deep link 回调。

---

## 2. MVP 环境参数（已对齐）

以下值已写入 `app/build.gradle.kts` 与 `auth/AuthConfig.kt`，**直接对接已部署 MVP**，无需本地 Rauthy。

| 项 | MVP 值 | 说明 |
|----|--------|------|
| **API Base** | `https://mvp.api.alphahalf.cc` | ah_ddns 控制面 |
| **Issuer** | `https://mvp.auth.alphahalf.cc/auth/v1/` | **尾斜杠必填**，与 JWT `iss` 一致 |
| **Discovery** | `{issuer}.well-known/openid-configuration` | AppAuth 自动发现 |
| **Authorization** | `{issuer}oidc/authorize` | PKCE 起点 |
| **Token** | `{issuer}oidc/token` | code 换 token / refresh |
| **Logout** | `{issuer}oidc/logout` | RP 发起登出（可选） |
| **client_id** | `ah-mobile` | Public client，无 secret |
| **redirect_uri** | `com.ah.ddns:/oauth2redirect` | 与 Rauthy bootstrap 一致 |
| **applicationId** | `com.ah.ddns` | 与 redirect scheme 一致 |
| **scope** | `openid profile email offline_access dns:records:read dns:records:write` | 须含 `offline_access` |
| **DNS zone** | `alsh.cc` | 以 `GET /v1/my-shard` 返回为准，勿硬编码展示逻辑 |

### 2.1 OIDC Client 固定参数

```
client_id:              ah-mobile
response_type:          code
code_challenge_method:  S256
scope:                  openid profile email offline_access dns:records:read dns:records:write
redirect_uri:           com.ah.ddns:/oauth2redirect
```

要点（摘自规范 §3）：

- **只用 `access_token` 调 API**；不要用 `id_token` 或 `refresh_token` 作为 API 凭证。
- Public client：**禁止** `client_secret`。
- **生产禁止** password grant；本地 curl 联调见 ah_ddns `local-dev.md` §9.4，与本 App 无关。

---

## 3. 与本仓库代码的映射

### 3.1 已完成（阶段 0）

| 交付物 | 位置 | 状态 |
|--------|------|------|
| MVP BuildConfig | `app/build.gradle.kts` | ✅ |
| 配置常量封装 | `auth/AuthConfig.kt` | ✅ |
| OAuth Deep Link | `AndroidManifest.xml` intent-filter | ✅ |
| 浏览器 queries | `AndroidManifest.xml` `<queries>` | ✅ |
| applicationId | `com.ah.ddns` | ✅ |

`AuthConfig` 已暴露 discovery / authorize / token / logout URL，实施阶段 1 时直接引用，勿在业务代码硬编码 MVP 域名。

### 3.2 待实现（阶段 1）

| 交付物 | 目标文件 | 状态 |
|--------|----------|------|
| AppAuth 依赖 | `app/build.gradle.kts`、`libs.versions.toml` | ☐ |
| Security Crypto | 同上 | ☐ |
| `OidcAuthManager` | `auth/OidcAuthManager.kt` | ☐ |
| `TokenStore` | `auth/TokenStore.kt` | ☐ |
| `AuthRepository` | `auth/AuthRepository.kt` | ☐ |
| `MainActivity` redirect 处理 | `onNewIntent` + AppAuth 回调 | ☐ |
| 登录/登出 UI | `ui/LoginSection.kt` 或 MainActivity 区块 | ☐ |

---

## 4. PKCE 登录流程（实现规格）

与 [`mobile-auth-rauthy.md` §5](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md) 一致，时序如下：

```
App 生成 code_verifier、code_challenge(S256)、state
  → Custom Tabs 打开 oidc/authorize
  → 用户在 Rauthy 页输入邮箱+密码（或注册）
  → Rauthy redirect: com.ah.ddns:/oauth2redirect?code=...&state=...
  → App 校验 state
  → POST oidc/token（authorization_code + code_verifier + client_id + redirect_uri）
  → 得到 access_token + refresh_token
  → TokenStore 加密持久化
  → 可选：解析 id_token 取 email 用于 UI 展示
```

### 4.1 授权请求（AppAuth 等价参数）

```
GET https://mvp.auth.alphahalf.cc/auth/v1/oidc/authorize
  ?client_id=ah-mobile
  &response_type=code
  &scope=openid%20profile%20email%20offline_access%20dns:records:read%20dns:records:write
  &redirect_uri=com.ah.ddns%3A%2Foauth2redirect
  &code_challenge={S256_challenge}
  &code_challenge_method=S256
  &state={random_state}
```

可选：`ui_locales=zh` 或 `ui_locales=zh en`（Rauthy 默认法语 UI，可按产品需求追加）。

### 4.2 Code 换 Token

```
POST https://mvp.auth.alphahalf.cc/auth/v1/oidc/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&client_id=ah-mobile
&code={authorization_code}
&redirect_uri=com.ah.ddns:/oauth2redirect
&code_verifier={code_verifier}
```

成功响应须含 **`access_token`** 与 **`refresh_token`**。若缺少 `refresh_token`，检查 scope 是否包含 `offline_access`。

### 4.3 Deep Link 与 Manifest

已实现：

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="com.ah.ddns" android:host="oauth2redirect" />
</intent-filter>
```

AppAuth 还需在 `build.gradle.kts` 配置：

```kotlin
manifestPlaceholders["appAuthRedirectScheme"] = "com.ah.ddns"
```

`MainActivity` 须：

1. `launchMode` 建议使用 `singleTask` 或确保 `onNewIntent` 处理 redirect；
2. `onCreate` / `onNewIntent` 中将 intent 交给 `AuthorizationManagementActivity` 或 AppAuth 的 `AuthorizationResponse.fromIntent()`；
3. **禁止**在 WebView 内嵌长期托管登录页。

---

## 5. Token 生命周期与 refresh 约束

摘自 [`mobile-auth-rauthy.md` §7](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)。

### 5.1 有效期（bootstrap 默认）

| Token | 有效期 | 用途 |
|-------|--------|------|
| `access_token` | 86400s（1 天） | 调 ah_ddns API |
| `refresh_token` | 72h（Rauthy 全局） | 仅向 Rauthy 换新 access |
| Authorization code | 300s | 一次性换 token |

MVP Admin 可能将 access 调整为 7 天；App 应以 JWT `exp` 为准，勿写死时长。

### 5.2 Rauthy refresh 的 `nbf` 约束（重要）

Rauthy 在 `refresh_token` 上默认设置 **`nbf`**：仅当 access **临近过期（约提前 60s）** 时 refresh 才有效。

**过早 refresh 会导致该 refresh 及关联 session 全部失效。**

App 必须：

- 以 access JWT 的 `exp` 判断是否该 refresh；
- **不要**在 access 仍有效时 proactively 频繁 refresh；
- **推荐触发时机**：ah_ddns API 返回 **401** → refresh 一次 → 重试原请求；仍 401 → 清 token，重新 PKCE。

若使用 AppAuth 的 `performActionWithFreshTokens`，须确认其行为不会在 access 有效时提前 refresh；必要时自行封装 refresh 逻辑并检查 `exp`。

### 5.3 Refresh 请求

```
POST https://mvp.auth.alphahalf.cc/auth/v1/oidc/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&client_id=ah-mobile
&refresh_token={stored_refresh_token}
```

- 成功：更新 access；若响应含新 `refresh_token`（rotation），**覆盖**旧值。
- 失败（`invalid_grant`、过期、过早 refresh）：清除本地 token → 引导重新 PKCE。
- **`refresh_token` 不得发送给 ah_ddns API**。

### 5.4 存储

- 使用 `EncryptedSharedPreferences` + Android Keystore。
- 存储：`access_token`、`refresh_token`、`access_expires_at`（从 JWT `exp` 解析）、可选 `email`（从 id_token `email` claim）。
- 日志 **禁止** 打印完整 token。

### 5.5 登出

1. 可选：打开 `{issuer}oidc/logout?client_id=ah-mobile&post_logout_redirect_uri=com.ah.ddns%3A%2Foauth2redirect`
2. 清除本地 token。
3. UI 回到未登录态。

---

## 6. 账号相关用户操作

App **不实现**账号 API，仅通过浏览器打开 Rauthy 托管页（与规范 §6 一致）：

| 用户操作 | App 行为 |
|----------|----------|
| 登录 | 点击「登录」→ 发起 PKCE → Custom Tabs 打开 authorize URL |
| 注册 | 同上；用户在 Rauthy 页点 **Register** |
| 忘记密码 | 登录页点 **Forgot password**；或引导用户点邮件重置链接 |
| 邮箱未验证 | 登录失败时提示查收邮件；验证完成后重新 PKCE |

邮件内为 **链接**（非 6 位验证码）。改密或验证完成后，用户再次点击 App 内「登录」。

---

## 7. 调用 ah_ddns API

登录成功后，所有 `/v1/*` 请求：

```http
GET /v1/my-shard HTTP/1.1
Host: mvp.api.alphahalf.cc
Authorization: Bearer {access_token}
```

### 7.1 建议首次调用顺序

1. `GET /v1/my-shard` — 触发 `EnsureUser(sub)`，获取 `user_shard` + `zone`
2. `GET /v1/records` — 加载已有记录
3. 后续 CRUD / PATCH IP（见 [`DDNS_INTEGRATION_PLAN.md`](./DDNS_INTEGRATION_PLAN.md) 阶段 2+）

**禁止**新用户首次并发 `my-shard` 与 `POST /records`（shard 分配竞态，见 ah_ddns AGENTS.md）。

### 7.2 HTTP 状态与 App 行为

| HTTP | 含义 | App 建议 |
|------|------|----------|
| 401 | access 无效/过期 | **先 refresh**（遵守 §5.2 `nbf`）；仍 401 → 重新 PKCE |
| 403 | scope 不足 | 检查 scope 含 `dns:records:read` / `write` |
| 429 | PATCH 节流 | 展示 `retry_after`，退避 ≥60s |

Retrofit 建议：`Interceptor` 附加 Bearer；`Authenticator` 在 401 时调用 `AuthRepository.refresh()` 并重试 **一次**。

---

## 8. Android 实现清单（阶段 1）

按 [`mobile-auth-rauthy.md` §9](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md) 与本仓库现状，推荐顺序：

### Step 1：依赖

```kotlin
// app/build.gradle.kts — 实施时锁定版本
implementation("net.openid:appauth:<version>")
implementation("androidx.security:security-crypto:<version>")
```

### Step 2：`TokenStore`

- `save(tokens)` / `load()` / `clear()`
- 从 access JWT 解析 `exp`（Base64 payload，不验签即可读 exp）
- 可选：从 id_token 解析 `email`、`sub`

### Step 3：`OidcAuthManager`

- `fetchConfiguration()`：`AuthorizationServiceConfiguration.fetchFromIssuer(issuer, ...)`
- `buildAuthorizationRequest()`：`AuthorizationRequest.Builder` + PKCE + scope + redirect
- `performAuthorization(activity)`：`AuthorizationService.createCustomTabsIntentBuilder()` 打开 Custom Tabs
- `exchangeCode(response)`：`AuthorizationService.performTokenRequest`
- `refresh(refreshToken)`：`performTokenRequest` with `RefreshTokenRequest`
- `logout()`：可选 `EndSessionRequest`

Issuer 尾斜杠须与 `AuthConfig.issuer` 一致，避免 discovery URL 拼接错误。

### Step 4：`AuthRepository`

对外 API 建议：

```kotlin
sealed class AuthState {
    data object LoggedOut : AuthState()
    data object Loading : AuthState()
    data class LoggedIn(val email: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}

interface AuthRepository {
    val state: StateFlow<AuthState>
    suspend fun startLogin(activity: Activity)
    suspend fun handleRedirectIntent(intent: Intent): Boolean
    suspend fun getValidAccessToken(): String?  // 401 路径才 refresh
    suspend fun logout()
}
```

`getValidAccessToken()` 逻辑：

1. 无 token → `null`
2. access 未过期 → 直接返回
3. access 已过期或 API 401 → 尝试 refresh（检查 `exp` 临近过期窗口）
4. refresh 失败 → `clear()` + `LoggedOut`

### Step 5：`MainActivity` 接线

- `onCreate`：恢复 session（`TokenStore.load()` → 若有效则 `LoggedIn`）
- `onNewIntent`：OAuth redirect
- 登录按钮：`authRepository.startLogin(this)`
- 退出按钮：`authRepository.logout()`

### Step 6：验证 MVP 连通性

在真机/模拟器上完成 §11 验收项。若连接 Rauthy 端点失败，按 [`IMPLEMENTATION_NOTES_2026_07_08.md`](./IMPLEMENTATION_NOTES_2026_07_08.md) 规则 **停止并通知项目负责人**。

---

## 9. 文件与类设计

```
app/src/main/java/com/example/ftpembed/auth/
├── AuthConfig.kt          ✅ 已有
├── TokenStore.kt          ☐ EncryptedSharedPreferences
├── OidcAuthManager.kt     ☐ AppAuth 封装
└── AuthRepository.kt      ☐ 会话状态 + 对外 API

app/src/main/java/com/example/ftpembed/ui/
└── LoginSection.kt        ☐ Compose 登录/登出区块（可后于 Repository 完成）
```

与 DDNS 层的边界：

- `AuthRepository` 只负责 token 与登录态；
- `DdnsApiClient`（阶段 2）通过 `AuthRepository.getValidAccessToken()` 取 Bearer，**不**直接读 TokenStore。

---

## 10. UI 行为

### 未登录

- 显示「登录」主按钮
- 简短说明：「使用 AlphaHalf 账号登录（邮箱+密码）」
- 可选链接文案：「没有账号？在登录页注册」——仍走同一 PKCE 入口

### 已登录

- 显示邮箱（来自 id_token `email` claim）
- 「退出登录」按钮
- 登录成功后自动触发（或提示）`GET /v1/my-shard`（阶段 2 接入）

### 错误态

| 场景 | UI |
|------|-----|
| 用户取消浏览器 | Toast「已取消登录」 |
| redirect state 不匹配 | Toast「登录异常，请重试」 |
| 无 refresh_token | Toast「登录失败：缺少离线授权，请联系支持」 |
| refresh 失效 | 清 session，提示重新登录 |
| 邮箱未验证 | 「请先验证邮箱后再登录」 |

---

## 11. 联调验收

与 [`mobile-auth-rauthy.md` §11](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md) 对齐，本 App 阶段 1 Done 标准：

- [ ] Custom Tabs 打开 MVP Rauthy 登录页
- [ ] 邮箱+密码登录成功，token 响应含 **access_token + refresh_token**
- [ ] JWT payload 含 **sub**（Rauthy opaque sub，API 侧会映射 UUID）
- [ ] 杀进程重启后仍保持登录（EncryptedSharedPreferences）
- [ ] `GET https://mvp.api.alphahalf.cc/v1/records` 带 Bearer → **200**（阶段 2 自动化；阶段 1 可用临时调试按钮或单元/integration 测试）
- [ ] 模拟 API **401** 后 refresh 成功并重试 → **200**（无需重新打开浏览器）
- [ ] refresh 失效或过早 refresh 后 → 清 token → 重新 PKCE 可恢复
- [ ] redirect_uri 与 Rauthy Client 完全一致（否则 authorize/token 报错）

阶段 1 **不要求**：FQDN 创建、IP PATCH、相机 FTP 联调（属阶段 2+）。

---

## 12. 禁止事项与常见错误

| 禁止 | 原因 |
|------|------|
| App 内 WebView 长期托管登录 | 规范 §2.3；钓鱼与 cookie 风险 |
| 生产使用 password grant | 规范 §3；仅 dev curl |
| 用 id_token / refresh_token 调 ah_ddns | 规范 §3 |
| refresh_token 发给 ah_ddns | 规范 §7.3 |
| access 有效时频繁 proactive refresh | Rauthy `nbf` 级联失效 §7.1 |
| 硬编码 zone / fqdn | zone 来自 `GET /v1/my-shard` |
| 日志打印完整 token | 安全 |

---

## 13. 联调故障排查

| 现象 | 可能原因 | 排查 |
|------|----------|------|
| authorize 页报错 redirect_uri | App 与 Rauthy 登记不一致 | 核对 `AuthConfig.redirectUri` 与 Admin |
| token 响应无 refresh_token | scope 缺 `offline_access` | 检查 `AuthConfig.scope` |
| discovery 失败 | issuer 无尾斜杠 / 网络 | 核对 `AuthConfig.issuer`；浏览器打开 discovery URL |
| API 401 且 refresh 也失败 | 过早 refresh 导致 session 失效 | 清 token 重新 PKCE；检查 refresh 触发时机 |
| API 403 forbidden | scope 不足 | 确认 authorize 请求 scope 完整 |
| Deep link 未回到 App | intent-filter / launchMode | 检查 Manifest；`adb shell am start -W -a android.intent.action.VIEW -d "com.ah.ddns:/oauth2redirect?code=test"` |

### MVP Rauthy 端点（连通性检查）

联调前可在浏览器或 curl 验证（无需鉴权）：

```
https://mvp.auth.alphahalf.cc/auth/v1/.well-known/openid-configuration
```

若上述 URL 不可达，**停止实现并通知项目负责人**（见 IMPLEMENTATION_NOTES）。

---

## 相关文档

| 文档 | 用途 |
|------|------|
| [`ah_ddns/docs/spec/mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md) | **OIDC 规范原文（权威）** |
| [`DDNS_INTEGRATION_PLAN.md`](./DDNS_INTEGRATION_PLAN.md) | 全量 DDNS + FTP 集成阶段计划 |
| [`IMPLEMENTATION_NOTES_2026_07_08.md`](./IMPLEMENTATION_NOTES_2026_07_08.md) | 当前编码进度快照 |
| [`PROJECT_STATUS.md`](./PROJECT_STATUS.md) | 项目整体现状 |

---

*阶段 1 编码完成后，请更新本文档状态、IMPLEMENTATION_NOTES 与 PROJECT_STATUS。*
