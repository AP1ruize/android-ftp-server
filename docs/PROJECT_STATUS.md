# Android FTP Server — 项目现状与已实现功能

> 文档更新日期：2026-07-08  
> 应用包名：`com.ah.ddns`（`namespace` 仍为 `com.example.ftpembed`）  
> 项目根目录：`android-ftp-server`（Gradle 工程名 `ftpembed`）  
> 技术栈：Kotlin · Jetpack Compose · Material3 · Apache FTPServer · Apache MINA · DocumentFile

本文档供后续开发者与 Coding Agent 快速了解本仓库的**目标、完成度、代码结构、已知缺口与扩展方向**。README 侧重使用说明；本文档侧重**实现现状与待办**。

---

## 目录

- [项目背景与定位](#项目背景与定位)
- [与主工程 ah_kotlin 的关系](#与主工程-ah_kotlin-的关系)
- [架构概览](#架构概览)
- [已实现功能清单](#已实现功能清单)
- [完成度评估](#完成度评估)
- [源码文件索引](#源码文件索引)
- [数据流与通信机制](#数据流与通信机制)
- [配置与默认值](#配置与默认值)
- [已知问题与未完成项](#已知问题与未完成项)
- [ah_ddns / Rauthy 集成](#ah_ddns--rauthy-集成)
- [构建与验证](#构建与验证)

---

## 项目背景与定位

本仓库是一个**独立的 Android 本地 FTP 服务端 Demo**，用于验证相机/PC 通过 Wi-Fi 或热点向手机传图的场景。

当前状态：**FTP 收发、默认根目录、SAF 可选目录、连接/断开事件、滚动事件日志** 均已实现，可作为迁入 `ah_kotlin` 的验证基准。

---

## 与主工程 ah_kotlin 的关系

| 对比项 | `android-ftp-server` | `ah_kotlin` 主工程 |
|--------|------------------------|-------------------|
| `FtpSettingsRepository` | ✅ | ❌ 尚未迁移 |
| 默认根目录（BuildConfig） | ✅ | ❌ |
| 连接/断开 + 事件日志 UI | ✅ | ❌ |
| ConnectMgmt 接线 | N/A | ❌ 占位 |

迁入时优先复制：`FtpSettingsRepository`、`FtpLogFormatter`、`AppFtplet`（含 onConnect/onDisconnect）、更新后的 Service 与 UI/ViewModel。

---

## 架构概览

```
MainActivity
  ├── FtpSettingsRepository（配置 + 默认/SAF 根目录）
  ├── LazyColumn 事件日志（新→旧，带时间戳）
  └── BroadcastReceiver ← FtpForegroundService

FtpForegroundService
  ├── AppFtplet（连接/断开/上传）
  ├── FileObserver（新文件）
  └── sendStatus(message) → UI 追加日志
```

---

## 已实现功能清单

### 1. 默认根目录（BuildConfig）✅

- `BuildConfig.DEFAULT_ROOT_RELATIVE = "Pictures/ftptest"`
- 首次安装**无需 SAF 选目录**即可启动 FTP
- `FtpSettingsRepository.defaultRootDir()` 由 `Environment.getExternalStorageDirectory()` + BuildConfig 拼接
- 用户仍可通过「选择 FTP 根目录」改为 SAF 路径（优先于默认）

### 2. FTP 连接 / 断开事件 ✅

- `AppFtplet.onConnect` → `客户端已连接：{ip}`
- `AppFtplet.onDisconnect` → `客户端已断开：{ip}`
- 经 `sendStatus(message)` 进入 UI 事件日志

### 3. 可滚动事件日志 ✅

- `LazyColumn`，最高约 200dp，可上下滚动
- 新事件插入列表顶部（新→旧）
- 格式：`[yyyy-MM-dd HH:mm:ss] 消息`
- 最多保留 200 条
- 涵盖：连接、断开、上传、新文件、FTP 启停

### 4. 根目录显示修复 ✅

- FTP **未运行**时：`rootLabel` 来自 `getConfiguredRootLabel()`，不被 Service PING 广播覆盖
- FTP **运行中**：显示实际生效目录（label + 绝对路径）
- 首次启动显示「默认：Pictures/ftptest」而非误导性的「未选择」+ 隐藏 ftptest

### 5. 其余已有能力

- FtpSettingsRepository 统一配置
- SAF 选目录 + 持久化 + URI→File 映射
- 前台通知 toggle（从 Repository 读配置）
- Ftplet 上传回调 + FileObserver

---

## 完成度评估

| 模块 | 完成度 |
|------|--------|
| FTP 启停与文件写入 | ★★★★★ |
| 默认根目录 + SAF | ★★★★★ |
| 连接/断开 + 事件日志 | ★★★★☆ |
| 相机 FTP 状态 Chip | ★★★☆☆ |
| MVP OAuth 配置（无 PKCE） | ★★☆☆☆ |
| Rauthy PKCE 登录 | ☆☆☆☆☆ |
| ah_ddns API / FQDN / IP 上报 | ☆☆☆☆☆ |
| 迁入 ah_kotlin | ☆☆☆☆☆ |

---

## 源码文件索引

| 文件 | 职责 |
|------|------|
| `FtpSettingsRepository.kt` | 配置、默认/SAF 根目录、路径映射 |
| `FtpLogFormatter.kt` | 日志时间戳格式化 |
| `AppFtplet.kt` | 连接/断开/上传事件 |
| `MainActivity.kt` | UI、滚动日志列表 |
| `FtpForegroundService.kt` | FTP 服务、广播、通知 |
| `auth/AuthConfig.kt` | MVP OIDC / API 常量 |
| `ddns/LabelValidator.kt` 等 | DDNS 校验与模型（无 API 客户端） |
| `ftp/FtpClientStateMachine.kt` | 相机 FTP 连接状态机 |
| `app/build.gradle.kts` | FTP 默认值 + MVP OIDC BuildConfig |

---

## 数据流与通信机制

### 事件日志

```
AppFtplet / FileObserver / 启停 FTP
  → sendStatus(message=...)
  → MainActivity BroadcastReceiver
  → appendLog() 插入列表 index 0，前缀 [时间戳]
```

### 启动 FTP（无 SAF）

```
resolveRootDirectory(requireSaf=false)
  → 无 rootUri → Fallback 到 BuildConfig 默认目录
  → 启动 FtpServer
```

---

## 配置与默认值

| 配置项 | 来源 |
|--------|------|
| FTP 端口 | `BuildConfig.DEFAULT_FTP_PORT` = 2121 |
| 默认根目录相对路径 | `BuildConfig.DEFAULT_ROOT_RELATIVE` = Pictures/ftptest |
| 用户名 / 密码 | user / 1234（prefs） |
| 允许匿名 | true（prefs） |

---

## 已知问题与未完成项

1. **SAF 映射局限**：非 primary 卷可能失败（路线 A 固有限制）
2. **重复事件**：上传时 Ftplet 与 FileObserver 可能各报一条
3. **README 未同步**：仍描述旧行为
4. **无自动化测试**
5. **安全**：明文 FTP、弱默认密码

---

## ah_ddns / Rauthy 集成

MVP 环境（`mvp.api.alphahalf.cc` + `mvp.auth.alphahalf.cc`）已部署；本 App 鉴权应对齐 ah_ddns 规范 **[`mobile-auth-rauthy.md`](../../../ah_ddns/docs/spec/mobile-auth-rauthy.md)**。

| 文档 | 说明 |
|------|------|
| [`RAUTHY_MVP_AUTH.md`](./RAUTHY_MVP_AUTH.md) | MVP Rauthy PKCE 接入指南（**阶段 1 编码主文档**） |
| [`DDNS_INTEGRATION_PLAN.md`](./DDNS_INTEGRATION_PLAN.md) | 全量 DDNS + FTP 分阶段计划 |
| [`IMPLEMENTATION_NOTES_2026_07_08.md`](./IMPLEMENTATION_NOTES_2026_07_08.md) | 2026-07-08 编码进度 |

**阶段 0（配置对齐）已完成**：`applicationId=com.ah.ddns`、`AuthConfig`、OAuth deep link。  
**下一步**：AppAuth + `OidcAuthManager` / `TokenStore` / `AuthRepository`（见 RAUTHY_MVP_AUTH §8）。

---

## 构建与验证

```powershell
cd D:\alpha-half\android-kotlin\ah_kotlin\android-ftp-server
.\gradlew.bat assembleDebug
```

### 手动验证清单

- [ ] 全新安装：显示默认根目录，可直接启动 FTP
- [ ] 客户端连接/断开：日志顶部出现带时间戳的记录
- [ ] 上传文件：日志追加，旧记录不被覆盖，列表可滚动
- [ ] SAF 选其他目录后优先使用 SAF；重启仍记住
- [ ] 通知栏 toggle 启停正常

---

## 给 Coding Agent 的快速上下文

1. 配置唯一入口：`FtpSettingsRepository`
2. 默认根目录在 `BuildConfig.DEFAULT_ROOT_RELATIVE`，无需 SAF 即可启动
3. 事件日志在 `MainActivity` 的 `mutableStateListOf<FtpLogEntry>()`，勿改回单行 message
4. `AppFtplet` 已含 onConnect/onDisconnect
5. 迁入 ah_kotlin 时对齐图库扫描路径

---

*文档维护：功能变更后请同步更新本文档与 README。*
