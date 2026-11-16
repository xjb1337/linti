package com.xjb.linti.data.net

import okhttp3.OkHttpClient

/** AI/OCR/更新 client 共享连接池与调度器；各自 newBuilder() 定制超时与重定向策略。 */
object SharedHttp {
    val base: OkHttpClient by lazy { OkHttpClient() }
}