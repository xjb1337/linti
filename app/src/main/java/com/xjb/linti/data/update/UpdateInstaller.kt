package com.xjb.linti.data.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstaller(private val context: Context) {
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            throw UpdateException(UpdateFailure.INSTALL, "无法打开安装权限设置", error)
        }
    }

    fun install(file: File) {
        if (!canInstallPackages()) {
            throw UpdateException(UpdateFailure.INSTALL, "尚未允许安装未知应用")
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("Linti APK", uri)
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            throw UpdateException(UpdateFailure.INSTALL, "无法打开系统安装器", error)
        }
    }
}
