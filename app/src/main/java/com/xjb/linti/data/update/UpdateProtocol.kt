package com.xjb.linti.data.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

data class UpdateRelease(
    val version: SemanticVersion,
    val notes: String,
    val apkUrl: String,
    val checksumUrl: String,
    val releaseUrl: String,
    val apkSize: Long?,
)

sealed interface UpdateCheck {
    data object Latest : UpdateCheck
    data class Available(val release: UpdateRelease) : UpdateCheck
}

enum class UpdateFailure {
    NETWORK,
    TIMEOUT,
    HTTP,
    INVALID_RELEASE,
    MISSING_ASSET,
    CHECKSUM,
    STORAGE,
    INSTALL,
}

class UpdateException(
    val failure: UpdateFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object UpdateProtocol {
    const val API_URL = "https://api.github.com/repos/xjb1337/linti/releases/latest"
    const val RELEASE_URL = "https://github.com/xjb1337/linti/releases/latest"

    private val versionPattern = Regex("""^(\d{1,9})\.(\d{1,9})\.(\d{1,9})$""")
    private val tagPattern = Regex("""^v(\d{1,9})\.(\d{1,9})\.(\d{1,9})$""")

    fun parseVersion(value: String): SemanticVersion? {
        val match = versionPattern.matchEntire(value.trim()) ?: return null
        return SemanticVersion(
            major = match.groupValues[1].toIntOrNull() ?: return null,
            minor = match.groupValues[2].toIntOrNull() ?: return null,
            patch = match.groupValues[3].toIntOrNull() ?: return null,
        )
    }

    fun parseTag(tag: String): SemanticVersion? {
        val match = tagPattern.matchEntire(tag.trim()) ?: return null
        return SemanticVersion(
            major = match.groupValues[1].toIntOrNull() ?: return null,
            minor = match.groupValues[2].toIntOrNull() ?: return null,
            patch = match.groupValues[3].toIntOrNull() ?: return null,
        )
    }

    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remote = parseVersion(remoteVersion)
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE, "远端版本号无效")
        val current = parseVersion(currentVersion)
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE, "当前版本号无效")
        return remote > current
    }

    fun parseRelease(raw: String): UpdateRelease {
        val root = try {
            JSONObject(raw)
        } catch (error: JSONException) {
            throw UpdateException(UpdateFailure.INVALID_RELEASE, "更新信息不是有效的 JSON", error)
        }
        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
            throw UpdateException(UpdateFailure.INVALID_RELEASE, "最新 Release 仍是草稿或预发布版本")
        }
        val tag = root.optString("tag_name").trim()
        val version = parseTag(tag)
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE, "Release tag 不是 vX.Y.Z")
        val assets = root.optJSONArray("assets")
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE, "Release 缺少资产列表")
        val apkName = "linti-$version.apk"
        val checksumName = "$apkName.sha256"
        val apk = findAsset(assets, apkName)
        val checksum = findAsset(assets, checksumName)
        if (apk == null || checksum == null) {
            throw UpdateException(
                UpdateFailure.MISSING_ASSET,
                "Release 缺少 $apkName 或 $checksumName",
            )
        }
        val apkUrl = secureUrl(apk.optString("browser_download_url"))
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE, "APK 下载地址无效")
        val checksumUrl = secureUrl(checksum.optString("browser_download_url"))
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE, "SHA-256 下载地址无效")
        val releaseUrl = secureUrl(root.optString("html_url")) ?: RELEASE_URL
        val size = apk.optLong("size", -1L).takeIf { it > 0L }
        return UpdateRelease(
            version = version,
            notes = plainTextNotes(root.optString("body")),
            apkUrl = apkUrl,
            checksumUrl = checksumUrl,
            releaseUrl = releaseUrl,
            apkSize = size,
        )
    }

    fun parseChecksum(value: String): String? {
        val first = value.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.split(Regex("""\s+"""), limit = 2)
            ?.firstOrNull()
            ?: return null
        return normalizeSha256(first)
    }

    fun normalizeSha256(value: String): String? {
        val normalized = value
            .filterNot { it == ':' || it == '-' || it.isWhitespace() }
            .lowercase()
        return normalized.takeIf {
            it.length == 64 && it.all { character -> character in '0'..'9' || character in 'a'..'f' }
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun checksumMatches(expected: String, actual: String): Boolean =
        normalizeSha256(expected)?.let { it == normalizeSha256(actual) } ?: false

    fun plainTextNotes(markdown: String): String {
        if (markdown.isBlank()) return ""
        var inCodeBlock = false
        val output = mutableListOf<String>()
        for (rawLine in markdown.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            var line = rawLine.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (inCodeBlock) {
                if (line.isNotEmpty()) output += line
                continue
            }
            if (line.isEmpty()) {
                output += ""
                continue
            }
            line = line.replace(Regex("""<!--.*?-->"""), "").trim()
            if (line.isEmpty()) continue
            if (Regex("""^([-*_])(?:\s*\1){2,}$""").matches(line)) continue
            line = line.replace(Regex("""^#{1,6}\s*"""), "")
            line = line.replace(Regex("""^>\s?"""), "")
            line = line.replace(Regex("""^[-*+]\s+\[([ xX])]\s+""")) { match ->
                if (match.groupValues[1].isBlank()) "☐ " else "☑ "
            }
            line = line.replace(Regex("""^[-*+]\s+"""), "• ")
            line = line.replace(Regex("""!\[([^]]*)]\([^)]*\)""")) { match ->
                match.groupValues[1]
            }
            line = line.replace(Regex("""\[([^]]+)]\([^)]*\)""")) { match ->
                match.groupValues[1]
            }
            line = line.replace(Regex("""<((?:https?://)[^>]+)>""")) { match ->
                match.groupValues[1]
            }
            line = line.replace("`", "")
            line = line.replace(Regex("""(\*\*|__)(.*?)\1""")) { match ->
                match.groupValues[2]
            }
            line = line.replace(Regex("""(?<!\w)[*_]([^*_\n]+)[*_](?!\w)""")) { match ->
                match.groupValues[1]
            }
            line = line.replace("~~", "")
            line = line.replace(Regex("""<[^>]+>"""), "")
            line = line.replace(Regex("""\\([\\`*{}\[\]()#+.!_>\-])""")) { match ->
                match.groupValues[1]
            }
            output += line.replace(Regex("""[ \t]+"""), " ").trimEnd()
        }
        return output.joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .take(30_000)
    }

    fun classify(error: Throwable): UpdateFailure = when (error) {
        is UpdateException -> error.failure
        is SocketTimeoutException -> UpdateFailure.TIMEOUT
        is UnknownHostException, is ConnectException -> UpdateFailure.NETWORK
        is IOException -> UpdateFailure.NETWORK
        else -> UpdateFailure.INVALID_RELEASE
    }

    fun message(error: Throwable): String = when (classify(error)) {
        UpdateFailure.NETWORK -> "网络连接失败，请检查网络后重试"
        UpdateFailure.TIMEOUT -> "连接 GitHub 超时，请稍后重试"
        UpdateFailure.HTTP -> error.message ?: "GitHub 返回错误，暂时无法获取更新"
        UpdateFailure.INVALID_RELEASE -> "更新信息无效或已损坏"
        UpdateFailure.MISSING_ASSET -> error.message ?: "Release 缺少 APK 或校验文件"
        UpdateFailure.CHECKSUM -> "安装包 SHA-256 校验失败，安装已停止"
        UpdateFailure.STORAGE -> "无法保存安装包，请检查存储空间"
        UpdateFailure.INSTALL -> "无法打开系统安装器，请检查安装权限"
    }

    private fun findAsset(assets: JSONArray, name: String): JSONObject? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name") == name) return asset
        }
        return null
    }

    private fun secureUrl(value: String): String? {
        if (value.isBlank()) return null
        return try {
            val uri = URI(value)
            value.takeIf {
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }
        } catch (_: Exception) {
            null
        }
    }
}
