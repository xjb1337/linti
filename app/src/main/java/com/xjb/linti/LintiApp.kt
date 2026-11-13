package com.xjb.linti

import android.app.Application
import com.xjb.linti.data.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LintiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
        container.start()
    }
}
