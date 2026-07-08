# Implementation Notes - 2026-07-08

> **鉴权编码指南**：[`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md)（映射 ah_ddns [`mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)）  
> **全量计划**：[`DDNS_INTEGRATION_PLAN.md`](./DDNS_INTEGRATION_PLAN.md)

## Readiness Assessment

The existing Rauthy and DDNS documents are clear enough to start implementation, but they are not enough to declare every goal complete from this repo alone. Several acceptance criteria require live MVP services, a browser-based Rauthy login, account credentials, an Android device or emulator, and DNS/camera validation.

This pass implements the locally verifiable foundation and documents the remaining live-integration boundary.

## Implemented

- Stage 0 OAuth/DDNS configuration:
  - `applicationId = "com.ah.ddns"`
  - `DDNS_API_BASE = "https://mvp.api.alphahalf.cc"`
  - `OIDC_ISSUER = "https://mvp.auth.alphahalf.cc/auth/v1/"`
  - `OIDC_CLIENT_ID = "ah-mobile"`
  - `OIDC_REDIRECT_URI = "com.ah.ddns:/oauth2redirect"`
  - full OIDC scope including `offline_access`
- Android manifest support:
  - OAuth redirect deep link for `com.ah.ddns:/oauth2redirect`
  - browser query declaration for Custom Tabs/browser launch
- Auth/DDNS helper foundation:
  - `auth/AuthConfig.kt`
  - `ddns/LabelValidator.kt`
  - `ddns/ApiErrorParser.kt`
  - `ddns/DdnsModels.kt`
  - `network/LocalIpProvider.kt`
- FTP client state foundation:
  - `ftp/FtpClientStateMachine.kt`
  - `AppFtplet` now reports structured client state alongside event text
  - `FtpForegroundService` broadcasts structured client state
  - `MainActivity` displays a camera state chip
- Independent JVM unit tests under `app/src/test/java`.

## Not Yet Implemented

- AppAuth dependency and Authorization Code + PKCE flow.
- Encrypted token persistence.
- Refresh-token lifecycle handling and logout.
- OkHttp/Retrofit ah_ddns API client.
- Authenticated `GET /v1/my-shard`.
- DNS record list/create/update/delete.
- Automatic LAN IP PATCH scheduling and 60-second throttle handling.
- Full FQDN management UI and copy actions.
- Real-device Rauthy, ah_ddns, DNS, and camera/FTP acceptance tests.

## Rauthy Connectivity Rule

No Rauthy endpoint was contacted during this pass. If a future implementation step fails while connecting to any of these Rauthy endpoints, stop and notify the project owner immediately:

- `https://mvp.auth.alphahalf.cc/auth/v1/.well-known/openid-configuration`
- `https://mvp.auth.alphahalf.cc/auth/v1/oidc/authorize`
- `https://mvp.auth.alphahalf.cc/auth/v1/oidc/token`
- `https://mvp.auth.alphahalf.cc/auth/v1/oidc/logout`

## Verification

Command run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Result:

- Build successful.
- JVM unit tests passed.

Gradle needed network approval to download its wrapper distribution. That connectivity issue was unrelated to Rauthy.

## Recommended Next Slice

按 [`RAUTHY_MVP_AUTH.md` §8](./RAUTHY_MVP_AUTH.md#8-android-实现清单阶段-1) 实施 **阶段 1（Rauthy MVP PKCE）**：

1. Add AppAuth and AndroidX Security dependencies; `manifestPlaceholders["appAuthRedirectScheme"] = "com.ah.ddns"`.
2. Implement `TokenStore` → `OidcAuthManager` → `AuthRepository`（遵守 refresh `nbf`，见 RAUTHY_MVP_AUTH §5.2）.
3. Wire `MainActivity.onCreate` / `onNewIntent` for OAuth redirect.
4. Add login/logout UI; validate PKCE against **MVP** Rauthy on device.
5. After auth succeeds, proceed to **阶段 2**：Retrofit + `GET /v1/my-shard`（见 DDNS_INTEGRATION_PLAN）.
