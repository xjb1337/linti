package com.xjb.linti.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateClient(
    private val cacheDirectory: File,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .followRedirects(true)
        .build(),
) {
    suspend fun check(currentVersion: String): UpdateCheck = withContext(Dispatchers.IO) {
        val release = UpdateProtocol.parseRelease(readText(UpdateProtocol.API_URL, 1_048_576))
        if (UpdateProtocol.isNewer(release.version.toString(), currentVersion)) {
            UpdateCheck.Available(release)
        } else {
            UpdateCheck.Latest
        }
    }

    suspend fun download(
        release: UpdateRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(cacheDirectory, "updates")
        if (!directory.exists() && !directory.mkdirs()) {
            throw UpdateException(UpdateFailure.STORAGE, "无法创建更新缓存目录")
        }
        val target = File(directory, "linti-${release.version}.apk")
        val partial = File(directory, "linti-${release.version}.apk.part")
        if (target.exists() && !target.delete()) {
            throw UpdateException(UpdateFailure.STORAGE, "无法删除旧安装包")
        }
        if (partial.exists() && !partial.delete()) {
            throw UpdateException(UpdateFailure.STORAGE, "无法清理旧下载")
        }
        val expected = UpdateProtocol.parseChecksum(readText(release.checksumUrl, 8_192))
            ?: throw UpdateException(UpdateFailure.INVALID_RELEASE, "SHA-256 校验文件格式无效")
        val call = httpClient.newCall(request(release.apkUrl))
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (error: IOException) {
                throw UpdateException(UpdateProtocol.classify(error), "无法连接下载服务器", error)
            }
            response.use { result ->
                if (!result.isSuccessful) {
                    throw UpdateException(
                        UpdateFailure.HTTP,
                        "GitHub 返回 HTTP ${result.code}，无法下载安装包",
                    )
                }
                if (!result.request.url.isHttps) {
                    throw UpdateException(UpdateFailure.INVALID_RELEASE, "安装包地址不是 HTTPS")
                }
                val body = result.body ?: throw UpdateException(
                    UpdateFailure.INVALID_RELEASE,
                    "下载响应为空",
                )
                val total = body.contentLength()
                if (total > MAX_APK_BYTES) {
                    throw UpdateException(UpdateFailure.STORAGE, "安装包超过大小限制")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastProgressAt = 0L
                try {
                    partial.outputStream().buffered().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                if (downloaded > MAX_APK_BYTES) {
                                    throw UpdateException(UpdateFailure.STORAGE, "安装包超过大小限制")
                                }
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                val now = System.nanoTime()
                                if (now - lastProgressAt >= PROGRESS_INTERVAL_NANOS) {
                                    onProgress(downloaded, total)
                                    lastProgressAt = now
                                }
                            }
                        }
                    }
                } catch (error: IOException) {
                    throw UpdateException(UpdateFailure.STORAGE, "无法写入安装包", error)
                }
                onProgress(downloaded, total)
                if (downloaded <= 0L) {
                    throw UpdateException(UpdateFailure.INVALID_RELEASE, "下载的安装包为空")
                }
                if (total >= 0L && downloaded != total) {
                    throw UpdateException(UpdateFailure.NETWORK, "安装包下载不完整")
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!UpdateProtocol.checksumMatches(expected, actual)) {
                    throw UpdateException(UpdateFailure.CHECKSUM, "SHA-256 与校验文件不一致")
                }
                if (!partial.renameTo(target)) {
                    throw UpdateException(UpdateFailure.STORAGE, "无法保存安装包")
                }
                target
            }
        } finally {
            cancellation?.dispose()
            partial.delete()
        }
    }

    private suspend fun readText(url: String, maxBytes: Int): String {
        val uri = try {
            URI(url)
        } catch (error: Exception) {
            throw UpdateException(UpdateFailure.INVALID_RELEASE, "下载地址无效", error)
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw UpdateException(UpdateFailure.INVALID_RELEASE, "更新只允许 HTTPS 地址")
        }
        val call = httpClient.newCall(request(url))
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (error: IOException) {
                throw UpdateException(UpdateProtocol.classify(error), "无法连接 GitHub", error)
            }
            response.use { result ->
                if (!result.isSuccessful) {
                    throw UpdateException(
                        UpdateFailure.HTTP,
                        "GitHub 返回 HTTP ${result.code}，请稍后重试",
                    )
                }
                if (!result.request.url.isHttps) {
                    throw UpdateException(UpdateFailure.INVALID_RELEASE, "响应地址不是 HTTPS")
                }
                val body = result.body ?: throw UpdateException(
                    UpdateFailure.INVALID_RELEASE,
                    "GitHub 响应为空",
                )
                if (body.contentLength() > maxBytes) {
                    throw UpdateException(UpdateFailure.INVALID_RELEASE, "更新响应过大")
                }
                val bytes = try {
                    body.bytes()
                } catch (error: IOException) {
                    throw UpdateException(UpdateProtocol.classify(error), "读取更新信息失败", error)
                }
                if (bytes.size > maxBytes) {
                    throw UpdateException(UpdateFailure.INVALID_RELEASE, "更新响应过大")
                }
                return bytes.toString(Charsets.UTF_8)
            }
        } finally {
            cancellation?.dispose()
        }
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json, application/json, application/octet-stream")
        .header("User-Agent", "Linti-Android")
        .build()

    private companion object {
        const val MAX_APK_BYTES = 200L * 1024L * 1024L
        const val PROGRESS_INTERVAL_NANOS = 180_000_000L
    }
}
