package com.xjb.linti.data.ocr

import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

/** OCR 失败类型；VM 据此给简短文案并决定重试前是否提示可能重复计费。 */
enum class OcrFailureKind {
    NO_KEY,
    AUTH,
    BALANCE,
    TOO_LARGE,
    TOO_MANY_PAGES,
    RATE_LIMIT,
    BUSY,
    UPLOAD_TIMEOUT,
    TIMEOUT,
    NETWORK,
    SERVER,
    BAD_REQUEST,
    UNSUPPORTED,
    REDIRECT,
    PROTOCOL,
    TRUNCATED,
    PAGE_MISMATCH,
    RECOGNITION_FAILED,
}

/** OCR 请求失败；message 为面向用户的简短可操作文案，不含密钥/附件内容/识别正文。 */
class OcrException(
    val kind: OcrFailureKind,
    message: String,
) : Exception(message)

data class OcrPageData(
    val index: Int,
    val text: String,
    val finishReason: String,
)

data class OcrSuccess(
    val purpose: String,
    val pages: List<OcrPageData>,
    val pageCount: Int,
)

/**
 * DeepSeek 官方在线 OCR 独立客户端。
 *
 * - 仅 POST 官方 https://api.deepseek.com/v1/tools/ocr（硬编码，不吃自定义 Base URL）；
 * - multipart/form-data 只有 purpose 与带文件名的 file 两个字段，不发 model、不走 /files 或 chat；
 * - 不跟随重定向（官方端点不该 3xx，出现即报错，防止密钥被带往其他主机）；
 * - 响应为普通 JSON（非 SSE），服务端可能先发送换行保活，前导空白按 JSON 前置空白忽略；
 * - 全程不打印密钥、附件与识别正文。
 */
class DeepSeekOcrClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    // 仅测试注入 MockWebServer 地址；生产代码一律使用默认官方端点，无 release 开关
    private val endpoint: String = ENDPOINT,
) {
    init {
        if (apiKey.isBlank()) {
            throw OcrException(OcrFailureKind.NO_KEY, "尚未配置 DeepSeek OCR Key，请先在设置中配置")
        }
        // 默认端点为硬编码官方 HTTPS 地址；endpoint 参数仅供测试注入 MockWebServer，
        // 生产代码从不传参，不存在 release 侧绕过主机/认证的开关。
    }

    /**
     * 上传文件并识别。
     * @param file 待上传的本地临时副本（上传前已按 [OcrLimits.MAX_FILE_BYTES] 检查）。
     * @param knownPageCount 调用方已知的页数（PDF 页数/图片固定 1），与响应 page_count 核对；null 跳过该项。
     */
    suspend fun recognizeFile(
        file: File,
        fileName: String,
        knownPageCount: Int?,
        purpose: String = PURPOSE_TEXT_RECOGNITION,
    ): OcrSuccess {
        if (!file.exists() || file.length() <= 0L) {
            throw OcrException(OcrFailureKind.BAD_REQUEST, "上传文件为空或不可读，请重新选择")
        }
        if (file.length() > OcrLimits.MAX_FILE_BYTES) {
            throw OcrException(
                OcrFailureKind.TOO_LARGE,
                "文件超过 ${OcrLimits.MAX_FILE_BYTES / (1024 * 1024)} MB 上限，请压缩或拆分后再试",
            )
        }
        val request = buildRequest(file, fileName, purpose)
        val response = execute(request)
        return try {
            response.use { parseResponse(it, purpose, knownPageCount) }
        } catch (error: IOException) {
            // 响应头之后的读取中断（超时/断流）：同样按可操作文案给出，并提示重试可能重复计费
            throw mapNetworkError(error)
        }
    }

    private fun buildRequest(file: File, fileName: String, purpose: String): Request {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(FIELD_PURPOSE, purpose)
            .addFormDataPart(FIELD_FILE, fileName, file.asRequestBody(mediaTypeFor(fileName)))
            .build()
        return Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
    }

    /**
     * enqueue + invokeOnCancellation：协程取消时调用 call.cancel()，
     * 立即中断上传/下载并释放连接与缓冲，不留后台请求。
     */
    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!continuation.isActive) return
                    if (call.isCanceled()) {
                        continuation.cancel(IOException("OCR 请求已取消", error))
                    } else {
                        continuation.resumeWith(Result.failure(mapNetworkError(error)))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }
                    continuation.resumeWith(Result.success(response))
                }
            })
        }

    private fun mapNetworkError(error: IOException): Exception {
        val message = error.message.orEmpty().lowercase()
        // SocketTimeout 与超时看门狗触发的取消（Canceled/timeout）都按超时处理：
        // 请求可能已到达服务端并继续识别，重试前需提示可能重复计费
        if (error is SocketTimeoutException ||
            message.contains("timeout") ||
            message.contains("cancel")
        ) {
            return OcrException(
                OcrFailureKind.TIMEOUT,
                "连接超时或识别超过总时长上限，请检查网络后重试（重试可能重复计费）",
            )
        }
        return OcrException(OcrFailureKind.NETWORK, "网络连接失败，请检查网络后重试")
    }

    private fun parseResponse(
        response: Response,
        purpose: String,
        knownPageCount: Int?,
    ): OcrSuccess {
        if (response.isRedirect) {
            throw OcrException(OcrFailureKind.REDIRECT, "OCR 端点返回重定向，已终止（不跟随跨域跳转）")
        }
        val code = response.code
        if (code != 200) {
            val detail = readBodyBounded(response, SERVER_ERROR_BODY_LIMIT)
            throw httpError(code, detail)
        }
        val raw = readBodyBounded(response, OcrLimits.MAX_RESPONSE_BYTES)
        val json = parseJsonObject(raw) ?: throw OcrException(
            OcrFailureKind.PROTOCOL,
            "OCR 响应不是有效的 JSON，本次结果不可用",
        )
        json.optJSONObject("error")?.let { error ->
            throw OcrException(
                OcrFailureKind.RECOGNITION_FAILED,
                "识别未完成：${shortServerMessage(error)}。文件可能已按部分页计费，请重试",
            )
        }
        if (json.optString("object") != RESULT_OBJECT) {
            throw OcrException(
                OcrFailureKind.PROTOCOL,
                "OCR 响应类型异常（object 非 ocr.result），本次结果不可用",
            )
        }
        val responsePurpose = json.optString("purpose")
        if (responsePurpose != purpose) {
            throw OcrException(
                OcrFailureKind.PROTOCOL,
                "OCR 响应 purpose 与请求不符，本次结果不可用",
            )
        }
        val pages = parsePages(json)
        val pageCount = json.optInt("page_count", -1)
        if (pageCount != pages.size) {
            throw OcrException(
                OcrFailureKind.PROTOCOL,
                "OCR 响应 page_count（$pageCount）与返回页数（${pages.size}）不一致，本次结果不可用",
            )
        }
        pages.forEachIndexed { position, page ->
            if (page.index != position) {
                throw OcrException(
                    OcrFailureKind.PROTOCOL,
                    "OCR 返回页序异常（第 ${position + 1} 个页面的序号为 ${page.index}，应为 $position），本次结果不可用",
                )
            }
            if (page.finishReason != FINISH_STOP && page.finishReason != FINISH_LENGTH) {
                throw OcrException(
                    OcrFailureKind.PROTOCOL,
                    "OCR 返回第 ${position + 1} 页 finish_reason 无法识别，本次结果不可用",
                )
            }
        }
        if (knownPageCount != null && pages.size != knownPageCount) {
            throw OcrException(
                OcrFailureKind.PAGE_MISMATCH,
                "OCR 返回页数（${pages.size}）与文件实际页数（$knownPageCount）不符，本次结果不可用；请重试或拆分文件",
            )
        }
        val truncatedPages = pages.filter { it.finishReason == FINISH_LENGTH }
        if (truncatedPages.isNotEmpty()) {
            val labels = truncatedPages.joinToString("、") { "第 ${it.index + 1} 页" }
            throw OcrException(
                OcrFailureKind.TRUNCATED,
                "$labels 识别被截断（达到输出上限），内容不完整，本次不入库；请重试或拆分文件",
            )
        }
        return OcrSuccess(
            purpose = responsePurpose,
            pages = pages,
            pageCount = pageCount,
        )
    }

    private fun parsePages(json: JSONObject): List<OcrPageData> {
        val rawPages = json.optJSONArray("pages") ?: throw OcrException(
            OcrFailureKind.PROTOCOL,
            "OCR 响应缺少 pages 数组，本次结果不可用",
        )
        if (rawPages.length() > OcrLimits.MAX_PAGES) {
            throw OcrException(
                OcrFailureKind.TOO_MANY_PAGES,
                "OCR 返回页数超过 ${OcrLimits.MAX_PAGES} 页上限",
            )
        }
        val seenIndexes = mutableSetOf<Int>()
        val pages = ArrayList<OcrPageData>(rawPages.length())
        for (position in 0 until rawPages.length()) {
            val page = rawPages.optJSONObject(position) ?: throw OcrException(
                OcrFailureKind.PROTOCOL,
                "OCR 返回第 ${position + 1} 个页面不是对象，本次结果不可用",
            )
            val index = page.optInt("index", Int.MIN_VALUE)
            if (index == Int.MIN_VALUE || index < 0 || !seenIndexes.add(index)) {
                throw OcrException(
                    OcrFailureKind.PROTOCOL,
                    "OCR 返回页序号缺失、为负或重复（${if (index == Int.MIN_VALUE) "缺失" else index}），本次结果不可用",
                )
            }
            if (!page.has("text") || page.isNull("text")) {
                throw OcrException(
                    OcrFailureKind.PROTOCOL,
                    "OCR 返回第 ${index + 1} 页缺少文本，本次结果不可用",
                )
            }
            val text = page.getString("text")
            val finishReason = page.optString("finish_reason", "")
            pages += OcrPageData(index = index, text = text, finishReason = finishReason)
        }
        return pages
    }

    private fun httpError(code: Int, detail: String): OcrException {
        val serverMessage = runCatching { JSONObject(detail) }.getOrNull()
            ?.optJSONObject("error")
            ?.let { shortServerMessage(it) }
        val message = when (code) {
            400 -> "请求被拒绝（400）${serverMessage?.let { "：$it" } ?: "，文件可能损坏或格式不受支持"}"
            401, 403 -> "OCR 认证失败（$code），请检查 DeepSeek OCR Key 或重新配置"
            402 -> "DeepSeek 账户余额不足（402），请充值后重试"
            408 -> "上传超时（408），请检查网络后重试（重试可能重复计费）"
            413 -> "文件超过 ${OcrLimits.MAX_FILE_BYTES / (1024 * 1024)} MB 上限（413），请压缩或拆分后再试"
            429 -> "请求过于频繁（429），请稍后重试"
            500 -> "DeepSeek 服务器错误（500），请稍后重试"
            503 -> "DeepSeek 服务繁忙（503），请稍后重试"
            504 -> "识别总时长超限（504），请重试或缩小文件（重试可能重复计费）"
            else -> "OCR 请求失败（HTTP $code）${serverMessage?.let { "：$it" } ?: ""}"
        }
        val kind = when (code) {
            400 -> OcrFailureKind.BAD_REQUEST
            401, 403 -> OcrFailureKind.AUTH
            402 -> OcrFailureKind.BALANCE
            408 -> OcrFailureKind.UPLOAD_TIMEOUT
            413 -> OcrFailureKind.TOO_LARGE
            429 -> OcrFailureKind.RATE_LIMIT
            500, 503 -> OcrFailureKind.BUSY
            504 -> OcrFailureKind.TIMEOUT
            else -> OcrFailureKind.SERVER
        }
        return OcrException(kind, message)
    }

    // 服务器错误信息可能很长，只取首段给用户提示；不落日志
    private fun shortServerMessage(error: JSONObject): String {
        val message = error.optString("message", "").trim()
        if (message.isEmpty()) return "服务返回未说明原因"
        return message.take(SERVER_MESSAGE_LIMIT)
    }

    private fun readBodyBounded(response: Response, maxBytes: Long): String {
        val body = response.body ?: throw OcrException(OcrFailureKind.PROTOCOL, "OCR 响应缺少内容")
        val source = body.source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val read = source.read(buffer, READ_CHUNK_BYTES)
            if (read == -1L) break
            total += read
            if (total > maxBytes) {
                throw OcrException(OcrFailureKind.PROTOCOL, "OCR 响应超出大小上限，已终止读取")
            }
        }
        return buffer.readUtf8()
    }

    // 服务端先发换行保活，前导空白（含 \n）是 JSON 的合法前置空白
    private fun parseJsonObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/v1/tools/ocr"
        const val PURPOSE_TEXT_RECOGNITION = "text_recognition"
        const val PURPOSE_RICH_LAYOUT = "rich_layout_recognition"
        const val RESULT_OBJECT = "ocr.result"
        const val FINISH_STOP = "stop"
        const val FINISH_LENGTH = "length"

        private const val FIELD_PURPOSE = "purpose"
        private const val FIELD_FILE = "file"
        private const val READ_CHUNK_BYTES = 64L * 1024
        private const val SERVER_ERROR_BODY_LIMIT = 64L * 1024
        private const val SERVER_MESSAGE_LIMIT = 120

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(OcrLimits.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OcrLimits.TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(OcrLimits.TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // 官方语义：不做自动重试（取消/失败后重试由用户确认，避免重复计费）
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        private fun mediaTypeFor(fileName: String): MediaType {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mime = when (extension) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "gif" -> "image/gif"
                "tif", "tiff" -> "image/tiff"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "ppt" -> "application/vnd.ms-powerpoint"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "application/octet-stream"
            }
            return mime.toMediaType()
        }
    }
}
