# CR-1：相机 FTP 连接/传文件时应用崩溃

**日期**：2026-07-08  
**应用**：`com.ah.ddns`（android-ftp-server）  
**设备场景**：Sony 手机，相机通过局域网 FTP 连接本机 FTP 服务

---

## 现象摘要

| # | 触发场景 | 崩溃线程 | 异常类型 |
|---|----------|----------|----------|
| A | 相机断开 FTP 连接；或连接后 UI 刷新事件日志 | `main` | `IllegalArgumentException: Key "...客户端已断开/已连接..." was already used` |
| B | 相机开始传文件（FTP `TYPE` 命令阶段） | `pool-5-thread-5`（MINA 工作线程） | `CoderMalfunctionError` → `newPosition > limit: (30 > 24)` |

两类崩溃彼此独立，但都在「相机使用 FTP」的高频交互下暴露。

---

## 崩溃 A：Compose LazyColumn 重复 key

### Logcat 关键栈

```
java.lang.IllegalArgumentException: Key "2026-07-08 22:39:51_客户端已断开：10.84.68.172_-1723643457" was already used.
    at androidx.compose.ui.layout.LayoutNodeSubcompositionsState.subcompose(...)
    at androidx.compose.foundation.lazy.LazyListKt$rememberLazyListMeasurePolicy$1$1.invoke(...)
```

### 根因

主界面「事件日志」使用 `LazyColumn`，`items()` 的 `key` 原先为：

```kotlin
key = { "${it.timestamp}_${it.text}_${it.hashCode()}" }
```

`FtpLogEntry` 是 `data class`，`hashCode()` 仅由 `timestamp + text` 决定。当同一秒内出现**相同文案**的日志（例如同一 IP 快速重连/断开，或 `BroadcastReceiver` 与 `AppEventLog` 几乎同时写入同类消息），则：

- `timestamp` 相同（格式只到秒：`yyyy-MM-dd HH:mm:ss`）
- `text` 相同（如 `客户端已断开：10.84.68.172`）
- `hashCode()` 也相同

Compose 要求 `LazyColumn` 每项 key 全局唯一，重复 key 会在 measure/scroll 阶段直接抛异常并杀死进程。

### 修复（已实施）

1. 为每条 `FtpLogEntry` 增加单调递增的 `id: Long`（`AtomicLong`）。
2. `LazyColumn` 使用 `key = { it.id }`。
3. 时间戳格式改为 `yyyy-MM-dd HH:mm:ss.SSS`，便于人工区分，但**不依赖**时间戳做唯一性。

### 如何避免复发

- **规则**：凡 `LazyColumn` / `LazyRow` 的 `key`，必须使用稳定且唯一的标识（自增 ID、UUID、数据库主键等），不要用「时间 + 文案」拼接。
- **Code Review 检查点**：列表数据来自实时事件流时，默认同秒内可产生重复内容。
- **测试建议**：模拟同一秒内连续 `appendLog("客户端已断开：x.x.x.x")` 两次，确认 UI 不崩溃。

---

## 崩溃 B：Apache MINA / FtpServer 字符编码

### Logcat 关键栈

```
java.nio.charset.CoderMalfunctionError: java.lang.IllegalArgumentException: newPosition > limit: (30 > 24)
    at java.nio.charset.CharsetEncoder.encode(...)
    at org.apache.mina.core.buffer.AbstractIoBuffer.putString(...)
    at org.apache.ftpserver.listener.nio.FtpResponseEncoder.encode(...)
    at org.apache.ftpserver.command.impl.TYPE.execute(TYPE.java:77)
```

### 根因

项目原先依赖 `ftpserver-core:1.1.1`。该版本的 `FtpResponseEncoder` 将 `CharsetEncoder` 作为**静态共享实例**使用（[FTPSERVER-499](https://issues.apache.org/jira/browse/FTPSERVER-499)），在多线程 MINA 工作池中并发编码 FTP 响应时不安全。

在 Android 上，默认字符集走 `com.android.icu.charset.CharsetEncoderICU`，共享 encoder 的状态错乱会表现为 `CoderMalfunctionError`、`newPosition > limit` 或 `U_ILLEGAL_ARGUMENT_ERROR`。相机传文件时会连续发送 `TYPE`、`PASV`、`STOR` 等命令，多线程并发写响应，极易触发。

同类问题见 [Amaze File Manager #4399](https://github.com/TeamAmaze/AmazeFileManager/issues/4399)。

### 修复（已实施）

1. 升级 `ftpserver-core`：`1.1.1` → `1.2.1`（含 FTPSERVER-499 修复，见 [1.2.1 发布说明](https://mina.apache.org/ftpserver-project/download_1_2.html)）。
2. 将 `mina-core` 固定为 `2.1.3`：`ftpserver 1.2.1` 默认依赖 MINA 2.2.x，在 `minSdk 24` 上可能因 `StandardSocketOptions` 等 API 不兼容出问题（参考 [MaterialFiles 做法](https://github.com/zhanghai/MaterialFiles/blob/master/app/build.gradle)）。

### 如何避免复发

- 保持 `ftpserver-core >= 1.2.1`，升级依赖时核对 [FTPSERVER 发行说明](https://mina.apache.org/downloads-ftpserver_1_2.html)。
- FTP 协议响应应为 ASCII；避免在 FTP 控制通道发送中文（当前 `AppFtplet` 事件文案仅用于 App 内 UI，不写入 FTP 响应，这是正确做法）。
- **长期改进**：
  - 为 FTP 工作线程设置 `UncaughtExceptionHandler`，记录日志并尝试重启服务，避免整个进程被 MINA 线程异常拖死。
  - 增加仪器化测试：模拟客户端 `TYPE I` / 上传小文件。
  - 考虑引入 `slf4j-android` 消除 SLF4J NOP 警告（非崩溃，但干扰 logcat）。

---

## 非崩溃日志（可忽略）

以下条目与本次崩溃无关，记录备查：

| 日志 | 说明 |
|------|------|
| `SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` | 未绑定 SLF4J 实现，回退 NOP；不影响功能 |
| `OpenGLRenderer: Unable to match the desired swap behavior` | 设备图形栈常见警告 |
| `OnBackInvokedCallback is not enabled` | Android 13+ 预测性返回手势未在 Manifest 启用 |

---

## 改动文件

| 文件 | 变更 |
|------|------|
| `MainActivity.kt` | `FtpLogEntry.id` + `LazyColumn` key 修复 |
| `FtpLogFormatter.kt` | 时间戳精度到毫秒 |
| `app/build.gradle.kts` | `ftpserver-core 1.2.1`，`mina-core 2.1.3` |

---

## 验证清单

- [ ] 启动 FTP，相机连接 → 断开 → 再连接，事件日志滚动/快速刷新不崩溃
- [ ] 相机向 FTP 根目录传照片/视频，`TYPE` 阶段不崩溃，文件落盘成功
- [ ] 同一秒内多次连接/断开，日志列表正常显示多条记录
- [ ] `assembleDebug` 构建通过
