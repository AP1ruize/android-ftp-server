# Android FTP Server — 项目现状与已实现功能

> 文档更新日期：2026-06-19  
> 应用包名：`com.example.ftpembed`  
> 项目根目录：`android-ftp-server`（Gradle 工程名 `ftpembed`）  
> 技术栈：Kotlin · Jetpack Compose · Material3 · Apache FTPServer · Apache MINA · DocumentFile

本文档供后续开发者与 Coding Agent 快速了解本仓库的**目标、完成度、代码结构、已知缺口与扩展方向**。README 侧重使用说明；本文档侧重**实现现状与待办**。

---

## 目录

- [项目背景与定位](#项目背景与定位)
- [与主工程 ah_kotlin 的关系](#与主工程-ah_kotlin-的关系)
- [Git 与版本信息](#git-与版本信息)
- [架构概览](#架构概览)
- [已实现功能清单](#已实现功能清单)
- [完成度评估](#完成度评估)
- [源码文件索引](#源码文件索引)
- [数据流与通信机制](#数据流与通信机制)
- [配置与默认值](#配置与默认值)
- [权限与 Android 清单](#权限与-android-清单)
- [已知问题与未完成项](#已知问题与未完成项)
- [建议的后续开发方向](#建议的后续开发方向)
- [构建与验证](#构建与验证)

---

## 项目背景与定位

本仓库是一个**独立的 Android 本地 FTP 服务端 Demo**，最初设计场景是：通过 Wi-Fi 或手机热点，让支持 FTP 的设备（例如 Sony 相机）将 JPG/RAW 图片上传到 Android 手机。

特点：

- 在手机上直接运行 FTP Server（非 FTP 客户端）
- 使用 **Apache FTPServer** 嵌入 Android 前台服务
- Compose 控制台 + 常驻通知栏，便于后台收图
- 上传事件回调 + 文件系统监听，便于后续接入图库刷新等业务
- **SAF 目录选择 + 本地持久化 + 路径映射** 已通过 `FtpSettingsRepository` 接通

当前状态：**核心 FTP 收发链路可用，SAF 根目录已接通（路线 A + 回退 C）**，可作为迁入 `ah_kotlin` 的验证基准。

---

## 与主工程 ah_kotlin 的关系

同工作区内的 `ah_kotlin/ah_kotlin`（Alpha Half Demo）也有一份 FTP 相关代码，但**集成不完整**：

| 对比项 | `android-ftp-server`（本仓库） | `ah_kotlin` 主工程 |
|--------|-------------------------------|-------------------|
| 定位 | 独立可运行的 FTP Demo | 相机伴侣 App 的子模块（未接线） |
| `AppFtplet` | ✅ 已实现 | ❌ 曾缺失（主工程 docs 记录为待补） |
| `FtpSettingsRepository` | ✅ 已实现 | ❌ 尚未迁移 |
| SAF 根目录 | ✅ UI + 持久化 + Service 映射 | ❌ 未接通 |
| UI 与 FTP 打通 | ✅ 本 App 内完整 | ❌ ConnectMgmt 页仅为占位按钮 |

**结论：** 本仓库是 FTP 功能的**完整参考实现**；迁入主工程时，优先迁移 `FtpSettingsRepository` + 更新后的 `FtpForegroundService` / `MainActivity`（或对应 ViewModel 层）。

主工程相关文档：`ah_kotlin/docs/project-overview.md`（「连接管理 / FTP 连接」章节）。

---

## Git 与版本信息

| 项 | 值 |
|----|-----|
| 当前分支 | `master` |
| 版本 | `versionName=1.0`, `versionCode=1` |
| 提交历史 | `7067c7d` initial commit → README 更新 |
| 工作区 | 含 SAF / Repository 改造（待提交） |

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│  MainActivity (Compose UI)                                   │
│  - 配置端口 / 用户名 / 密码 / 匿名                           │
│  - SAF 选择根目录                                            │
│  - 启动 / 停止 FTP                                           │
│  - 监听 BroadcastReceiver ← ACTION_STATUS                    │
└──────────────────────────┬──────────────────────────────────┘
                           │ 读写配置 / 启动 Service
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  FtpSettingsRepository                                       │
│  - SharedPreferences (ftp_prefs)                             │
│  - rootUri + rootDisplayName 持久化                          │
│  - takePersistableUriPermission                              │
│  - 启动校验 validateAndRepairSavedRoot()                     │
│  - SAF URI → File 路径映射 resolveRootDirectory()            │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  FtpForegroundService (前台 Service)                         │
│  - 从 Repository 读配置（含 toggle 启动）                    │
│  - Apache FtpServerFactory + AppFtplet                       │
│  - FileObserver → 新文件事件                                 │
│  - 常驻 Notification                                         │
└──────────────────────────┬──────────────────────────────────┘
                           │ 写入
                           ▼
              用户 SAF 所选目录映射后的 File 路径
              （内部存储 primary 卷；失败则报错提示重选）
              无 SAF 时回退：/storage/emulated/0/Pictures/ftptest
```

依赖关系（`app/build.gradle.kts`）：

- Compose：`activity-compose`、`material3`、`ui`
- SAF：`androidx.documentfile:documentfile`
- FTP：`org.apache.ftpserver:ftpserver-core:1.1.1`
- 网络 IO：`org.apache.mina:mina-core:2.0.16`

---

## 已实现功能清单

### 1. FTP 服务端核心 ✅

| 能力 | 说明 |
|------|------|
| 启动 / 停止 | `ACTION_START` / `ACTION_STOP` / `ACTION_TOGGLE` |
| 监听端口 | UI 可配置，持久化到 `ftp_port`，默认 `2121` |
| 写入权限 | 用户对根目录具备 `WritePermission` |
| 多用户 | 主账号 + 可选 `anonymous` |
| 根目录 | 从 Repository 解析 SAF 映射路径 |

实现位置：`FtpForegroundService.startServer()`

### 2. FtpSettingsRepository ✅（P0 + P1）

| 能力 | 说明 |
|------|------|
| 配置读写 | 用户名、密码、匿名、端口、rootUri、rootDisplayName |
| SAF 保存 | `saveRootDirectory()`：权限 + prefs |
| 启动校验 | `validateAndRepairSavedRoot()`：权限失效则清除 |
| 路径映射 | `resolveRootDirectory()`：SAF → File（路线 A） |
| 回退 | 无 SAF 且 `requireSaf=false` 时用默认 `Pictures/ftptest`（路线 C） |
| 统一入口 | UI、Service、通知 toggle 均通过 Repository |

实现位置：`FtpSettingsRepository.kt`

### 3. Jetpack Compose 控制台 UI ✅

| 能力 | 说明 |
|------|------|
| SAF 选目录 | `OpenDocumentTree` → Repository 保存 |
| 启动前校验 | 无目录 / 权限失效 → Toast + 阻止启动 |
| App 启动校验 | `LaunchedEffect` 恢复已保存目录或提示重选 |
| 配置项 | 端口、用户名、密码、匿名（经 Repository 持久化） |
| 状态展示 | FTP URL、用户、根目录 displayName + 绝对路径 |

实现位置：`MainActivity.AppUI()`

### 4. 前台服务与常驻通知 ✅

- 通知栏 toggle 启动时从 Repository 读端口与 SAF 根目录（**已修复此前 rootUri=null 问题**）
- 通知 BigText 展示 displayName + 绝对路径

### 5. 上传事件回调（Ftplet）✅

- `onUploadStart` / `onUploadEnd` → 广播到 UI

### 6. 文件系统监听（FileObserver）✅

- 监听映射后的 `File` 路径（与 FTP 写入目录一致）

### 7. UI ↔ Service 状态同步 ✅

| Extra 键 | 含义 |
|----------|------|
| `running` | FTP 是否在运行 |
| `ip` | 本机 IPv4 |
| `port` | 当前端口 |
| `root` | 根目录绝对路径 |
| `root_label` | 根目录 displayName（新增） |
| `error` | 启动失败原因 |
| `message` | 文件/上传事件 |

### 8. 配置持久化 ✅

`SharedPreferences` 文件：`ftp_prefs`（由 Repository 统一管理）

| Key | 默认值 | 用途 |
|-----|--------|------|
| `rootUri` | `null` | SAF tree URI 字符串 |
| `rootDisplayName` | `null` | UI 展示名 |
| `ftp_username` | `user` | FTP 主用户名 |
| `ftp_password` | `1234` | FTP 主用户密码 |
| `ftp_allow_anon` | `true` | 是否允许 anonymous |
| `ftp_port` | `2121` | 上次使用的端口 |

---

## 完成度评估

| 模块 | 完成度 | 说明 |
|------|--------|------|
| FTP 启停与文件写入 | ★★★★★ | SAF 映射已接通 |
| FtpSettingsRepository | ★★★★☆ | 主存储 primary 卷可靠；SD 卡等待验证 |
| Compose 控制台 | ★★★★☆ | 功能完整，端口校验可加强 |
| 前台通知 | ★★★★★ | toggle 已与 Repository 对齐 |
| 上传/新文件事件 | ★★★☆☆ | 双通道仍可能重复 |
| SAF 自定义根目录 | ★★★★☆ | 路线 A 已接通；非 primary 卷可能失败 |
| 单元 / 仪器测试 | ★☆☆☆☆ | 仅模板测试 |
| 与 Alpha Half 集成 | ☆☆☆☆☆ | 待迁移 |

**总体结论：** 主链路约 **90%**，可作为 ah_kotlin FTP 验证基准；迁入主工程前建议真机验证所选目录与图库路径对齐。

---

## 源码文件索引

| 文件 | 职责 |
|------|------|
| `FtpSettingsRepository.kt` | **配置持久化、SAF 校验、URI→File 映射** |
| `MainActivity.kt` | Compose UI、广播接收、启动 Service |
| `FtpForegroundService.kt` | FTP 服务、通知、FileObserver、状态广播 |
| `AppFtplet.kt` | 上传开始/结束回调 |
| `AndroidManifest.xml` | Activity、Service、权限 |
| `app/build.gradle.kts` | 依赖与 BuildConfig |

---

## 数据流与通信机制

### 选择并记住目录

```
用户点击「选择 FTP 根目录」
  → OpenDocumentTree
  → FtpSettingsRepository.saveRootDirectory(uri)
      → takePersistableUriPermission
      → prefs: rootUri + rootDisplayName
```

### 启动 FTP

```
用户点击「启动 FTP」
  → validateAndRepairSavedRoot()
  → Intent(ACTION_START) + port
  → FtpForegroundService.startServer(port)
      → settings.resolveRootDirectory(requireSaf=true)
      → 映射 File → 启动 FtpServer
      → sendStatus(running=true, root, root_label)
```

### 通知栏 Toggle 启动

```
ACTION_TOGGLE（未运行）
  → startServer(settings.getPort())
  → 同样从 Repository 读 SAF 根目录（不再传 null）
```

---

## 配置与默认值

| 配置项 | 默认值 |
|--------|--------|
| FTP 端口 | `2121`（`BuildConfig.DEFAULT_FTP_PORT` / `ftp_port`） |
| 用户名 / 密码 | `user` / `1234` |
| 允许匿名 | `true` |
| FTP 根目录 | 用户 SAF 所选（映射为 File）；无配置时回退 `Pictures/ftptest` |
| minSdk / targetSdk | 24 / 34 |

### SAF 路径映射规则（路线 A）

- `DocumentsContract.getTreeDocumentId` → `primary:Pictures/xxx`
- 映射为 `Environment.getExternalStorageDirectory()/Pictures/xxx`
- 非 `primary` 卷尝试 `/storage/{volumeId}/...`（SD 卡等，设备差异大）

---

## 权限与 Android 清单

与改造前相同：`INTERNET`、前台服务、`POST_NOTIFICATIONS`、存储权限等。SAF 目录**不依赖**运行时存储权限（用户授权 URI 即可），但映射到 File 路径写入时仍受系统分区存储限制。

---

## 已知问题与未完成项

### 1. SAF 映射局限（路线 A 固有限制）

Apache FTPServer 使用 `java.io.File`，当前通过 URI→路径映射实现。以下场景可能**映射失败或无法写入**：

- 部分 SD 卡 / OEM 自定义存储卷
- Android 11+ 对某些路径的严格限制

失败时 Service 返回明确错误，提示用户改选**内部存储**下的文件夹。长期方案：自定义 FTPServer 写入层，直接用 `ContentResolver`（路线 B）。

### 2. README 尚未同步

README 仍写「固定根目录、SAF 可后续扩展」，需更新为「已通过 SAF 选择并记住目录」。

### 3. `@RequiresApi(O)` 与 minSdk 24

Activity 仍标注 API 26+，低版本设备可能无法启动。

### 4. 重复的文件事件

Ftplet 与 FileObserver 仍可能重复推送。

### 5. `FileObserver(String)` 已废弃

minSdk 24 下仍用字符串构造；API 29+ 可改用 `FileObserver(File)`。

### 6. 无自动化测试

Repository 的路径映射与校验逻辑适合补单元测试。

### 7. 安全与生产化

明文 FTP、默认弱密码、匿名默认开启等 Demo 级配置未加固。

---

## 建议的后续开发方向

1. **迁入 ah_kotlin**
   - 复制 `FtpSettingsRepository` + 更新 Service
   - `FtpViewModel` 封装 Repository，`ConnectMgmt` 接线
   - FTP 根目录与图库扫描路径（`Pictures/AlphaHalf/jpg/`）对齐或可配置

2. **路线 B（可选）**
   - ContentResolver 直写 SAF，摆脱 File 映射

3. **测试**
   - `FtpSettingsRepository` 单元测试（documentId 映射、校验逻辑）
   - 仪器测试：选目录 → 启停 → 广播断言

4. **文档**
   - 同步更新 `README.md`

---

## 构建与验证

```powershell
cd D:\alpha-half\android-kotlin\ah_kotlin\android-ftp-server
.\gradlew.bat assembleDebug
```

### 手动验证清单

- [ ] 首次启动：未选目录时无法启动 FTP
- [ ] 选择内部存储下目录（如 `Pictures/ftptest`），重启 App 后目录仍 remembered
- [ ] 启动 FTP 后，文件上传到**所选目录**（非硬编码路径，除非未选目录走回退）
- [ ] 撤销 SAF 权限或清除数据后，App 提示重新选择
- [ ] 通知栏 toggle 启停正常，且使用同一 SAF 目录
- [ ] FTP 客户端连接并上传成功
- [ ] UI 显示 displayName + 绝对路径

---

## 给 Coding Agent 的快速上下文

1. **配置唯一入口**：`FtpSettingsRepository` — 不要在新代码里直接读写 `ftp_prefs`
2. **Service 不再接收** `EXTRA_ROOT_URI` / 用户名 / 密码 Intent；仅 `EXTRA_PORT`（可选），其余读 Repository
3. **启动 FTP 必须**先通过 SAF 选目录（`requireSaf=true`）；默认 `ftptest` 仅作映射失败外的内部回退
4. **迁入 ah_kotlin** 时保持 Repository API 稳定，便于 ViewModel 封装
5. **改根目录** 时考虑与主工程图库路径一致

---

*文档维护：功能或架构变更后请同步更新本文档与 README。*
