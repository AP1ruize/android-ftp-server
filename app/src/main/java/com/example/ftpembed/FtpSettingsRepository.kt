package com.example.ftpembed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * 统一管理 FTP 相关配置（SharedPreferences + SAF 目录校验与路径映射）。
 * UI 与 FtpForegroundService 均通过此类读写配置，避免重复逻辑。
 */
class FtpSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class FtpCredentials(
        val username: String,
        val password: String,
        val allowAnonymous: Boolean,
    )

    data class RootDisplayInfo(
        val label: String,
        val absolutePath: String,
    )

    sealed class RootResolveResult {
        data class Success(
            val dir: File,
            val displayLabel: String,
            val fromSaf: Boolean,
        ) : RootResolveResult()

        data class Fallback(
            val dir: File,
            val displayLabel: String,
            val reason: String,
        ) : RootResolveResult()

        data class Failure(val message: String) : RootResolveResult()
    }

    fun getRootUri(): String? = prefs.getString(KEY_ROOT_URI, null)

    fun getRootDisplayName(): String? = prefs.getString(KEY_ROOT_DISPLAY_NAME, null)

    fun hasSafRoot(): Boolean = !getRootUri().isNullOrBlank()

    /** SAF 有效或默认 File 目录可写时返回 true。 */
    fun canStartFtp(): Boolean = resolveRootDirectory(requireSaf = false) !is RootResolveResult.Failure

    fun getDefaultRootLabel(): String {
        val dir = defaultRootDir()
        return "默认：${BuildConfig.DEFAULT_ROOT_RELATIVE}\n${dir.absolutePath}"
    }

    /** UI 在未启动 FTP 时展示的根目录说明。 */
    fun getConfiguredRootLabel(): String {
        val uriString = getRootUri()
        if (!uriString.isNullOrBlank() && isRootUriValid(uriString)) {
            val name = getRootDisplayName() ?: "已选目录"
            when (val resolved = resolveRootDirectory(requireSaf = false)) {
                is RootResolveResult.Success -> {
                    return "根目录：$name\n${resolved.dir.absolutePath}"
                }
                is RootResolveResult.Fallback -> {
                    return "根目录：$name\n${resolved.dir.absolutePath}"
                }
                is RootResolveResult.Failure -> Unit
            }
        }
        return getDefaultRootLabel()
    }

    /** Service 广播 / 通知使用的当前有效根目录信息。 */
    fun getEffectiveRootInfo(): RootDisplayInfo {
        return when (val resolved = resolveRootDirectory(requireSaf = false)) {
            is RootResolveResult.Success -> RootDisplayInfo(resolved.displayLabel, resolved.dir.absolutePath)
            is RootResolveResult.Fallback -> RootDisplayInfo(resolved.displayLabel, resolved.dir.absolutePath)
            is RootResolveResult.Failure -> {
                val dir = defaultRootDir()
                RootDisplayInfo("默认：${BuildConfig.DEFAULT_ROOT_RELATIVE}", dir.absolutePath)
            }
        }
    }

    fun getPort(): Int = prefs.getInt(KEY_PORT, BuildConfig.DEFAULT_FTP_PORT)

    fun setPort(port: Int) {
        prefs.edit().putInt(KEY_PORT, port).apply()
    }

    fun getCredentials(): FtpCredentials = FtpCredentials(
        username = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME,
        password = prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD,
        allowAnonymous = prefs.getBoolean(KEY_ALLOW_ANON, true),
    )

    fun setUsername(value: String) {
        prefs.edit().putString(KEY_USERNAME, value).apply()
    }

    fun setPassword(value: String) {
        prefs.edit().putString(KEY_PASSWORD, value).apply()
    }

    fun setAllowAnonymous(value: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_ANON, value).apply()
    }

    fun saveRootDirectory(uri: Uri) {
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val displayName = DocumentFile.fromTreeUri(appContext, uri)?.name
            ?: uri.lastPathSegment
            ?: uri.toString()
        prefs.edit()
            .putString(KEY_ROOT_URI, uri.toString())
            .putString(KEY_ROOT_DISPLAY_NAME, displayName)
            .apply()
    }

    fun clearRootDirectory() {
        prefs.edit()
            .remove(KEY_ROOT_URI)
            .remove(KEY_ROOT_DISPLAY_NAME)
            .apply()
    }

    /** 启动时校验 SAF 目录；失效则清除并回退默认路径。 */
    fun validateAndRepairSavedRoot(): Boolean {
        val uriString = getRootUri() ?: return true
        if (isRootUriValid(uriString)) return true
        clearRootDirectory()
        return false
    }

    fun isRootUriValid(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val hasPermission = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!hasPermission) return false
        val doc = DocumentFile.fromTreeUri(appContext, uri) ?: return false
        return doc.exists() && doc.canWrite()
    }

    /**
     * 解析 FTP 写入根目录：优先 SAF URI → File 映射（路线 A），否则默认 File 目录（路线 C）。
     */
    fun resolveRootDirectory(requireSaf: Boolean = false): RootResolveResult {
        val uriString = getRootUri()
        if (!uriString.isNullOrBlank()) {
            if (!isRootUriValid(uriString)) {
                return RootResolveResult.Failure("目录权限已失效，请重新选择 FTP 根目录")
            }
            val uri = Uri.parse(uriString)
            val mapped = mapSafTreeUriToFile(uri)
            if (mapped != null) {
                if (!mapped.exists()) mapped.mkdirs()
                if (mapped.isDirectory && mapped.canWrite()) {
                    val label = getRootDisplayName() ?: mapped.absolutePath
                    return RootResolveResult.Success(mapped, label, fromSaf = true)
                }
                return RootResolveResult.Failure(
                    "无法写入所选目录：${getRootDisplayName() ?: mapped.absolutePath}。" +
                        "请改选主存储（内部存储）下的文件夹。",
                )
            }
            return RootResolveResult.Failure(
                "无法将所选 SAF 目录映射为文件路径。" +
                    "请改选内部存储下的文件夹（如 ${BuildConfig.DEFAULT_ROOT_RELATIVE}）。",
            )
        }

        if (requireSaf) {
            return RootResolveResult.Failure("请先选择 FTP 根目录")
        }

        val fallback = defaultRootDir().apply { if (!exists()) mkdirs() }
        if (!fallback.isDirectory || !fallback.canWrite()) {
            return RootResolveResult.Failure("默认目录不可写：${fallback.absolutePath}")
        }
        return RootResolveResult.Fallback(
            fallback,
            "默认：${BuildConfig.DEFAULT_ROOT_RELATIVE}",
            "使用默认路径：${fallback.absolutePath}",
        )
    }

    fun defaultRootDir(): File =
        File(Environment.getExternalStorageDirectory(), BuildConfig.DEFAULT_ROOT_RELATIVE)

    private fun mapSafTreeUriToFile(treeUri: Uri): File? {
        if (!DocumentsContract.isTreeUri(treeUri)) return null
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return mapDocumentIdToFile(documentId)
    }

    private fun mapDocumentIdToFile(documentId: String): File? {
        val parts = documentId.split(":", limit = 2)
        if (parts.size != 2) return null
        val (volume, relativePath) = parts
        return when (volume) {
            "primary" -> File(Environment.getExternalStorageDirectory(), relativePath)
            else -> {
                val volumeRoot = File("/storage/$volume")
                if (volumeRoot.isDirectory) File(volumeRoot, relativePath) else null
            }
        }
    }

    companion object {
        const val PREFS_NAME = "ftp_prefs"
        const val KEY_ROOT_URI = "rootUri"
        const val KEY_ROOT_DISPLAY_NAME = "rootDisplayName"
        const val KEY_USERNAME = "ftp_username"
        const val KEY_PASSWORD = "ftp_password"
        const val KEY_ALLOW_ANON = "ftp_allow_anon"
        const val KEY_PORT = "ftp_port"

        const val DEFAULT_USERNAME = "user"
        const val DEFAULT_PASSWORD = "1234"
    }
}
