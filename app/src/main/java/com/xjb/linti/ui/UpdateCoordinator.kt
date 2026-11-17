package com.xjb.linti.ui

import com.xjb.linti.BuildConfig
import com.xjb.linti.data.update.UpdateCheck
import com.xjb.linti.data.update.UpdateClient
import com.xjb.linti.data.update.UpdateException
import com.xjb.linti.data.update.UpdateFailure
import com.xjb.linti.data.update.UpdateInstaller
import com.xjb.linti.data.update.UpdateProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** 应用内版本检查、APK 下载与安装权限流程协调器。 */
internal class UpdateCoordinator(
    private val scope: CoroutineScope,
    private val updateClient: UpdateClient,
    private val updateInstaller: UpdateInstaller,
    private val onMessage: (String) -> Unit,
) {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var verifiedFile: File? = null
    private var awaitingInstallPermission = false

    fun checkForUpdates(silent: Boolean = false) {
        if (checkJob?.isActive == true || _state.value.downloading) return
        _state.update {
            it.copy(
                checking = true,
                release = null,
                dialogVisible = false,
                message = null,
                messageIsError = false,
                permissionRequired = false,
            )
        }
        verifiedFile = null
        checkJob = scope.launch {
            try {
                when (val result = updateClient.check(BuildConfig.VERSION_NAME)) {
                    is UpdateCheck.Available -> {
                        verifiedFile = null
                        _state.update {
                            it.copy(
                                checking = false,
                                release = result.release,
                                dialogVisible = true,
                                message = null,
                                messageIsError = false,
                                permissionRequired = false,
                            )
                        }
                    }
                    UpdateCheck.Latest -> {
                        if (!silent) {
                            _state.update {
                                it.copy(
                                    checking = false,
                                    release = null,
                                    dialogVisible = true,
                                    message = "已是最新版本 v${BuildConfig.VERSION_NAME}",
                                    messageIsError = false,
                                )
                            }
                        } else {
                            _state.update { it.copy(checking = false) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        dialogVisible = !silent,
                        message = if (silent) null else UpdateProtocol.message(error),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        if (downloadJob?.isActive == true) return
        val release = _state.value.release ?: return
        val existing = verifiedFile?.takeIf { it.isFile }
        if (existing != null) {
            installDownloadedUpdate(existing)
            return
        }
        downloadJob = scope.launch {
            _state.update {
                it.copy(
                    downloading = true,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSize ?: -1L,
                    dialogVisible = true,
                    message = null,
                    messageIsError = false,
                    permissionRequired = false,
                )
            }
            try {
                val file = updateClient.download(release) { downloaded, total ->
                    _state.update {
                        it.copy(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                        )
                    }
                }
                verifiedFile = file
                _state.update { it.copy(downloading = false) }
                installDownloadedUpdate(file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        downloading = false,
                        dialogVisible = true,
                        message = UpdateProtocol.message(error),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        verifiedFile = null
        _state.update {
            it.copy(
                downloading = false,
                downloadedBytes = 0L,
                totalBytes = -1L,
                dialogVisible = false,
                message = null,
                messageIsError = false,
            )
        }
        onMessage("下载已取消")
    }

    fun dismissUpdateDialog() {
        if (_state.value.downloading) return
        _state.update {
            it.copy(
                dialogVisible = false,
                message = null,
                messageIsError = false,
                permissionRequired = false,
            )
        }
    }

    fun openInstallPermissionSettings() {
        awaitingInstallPermission = true
        try {
            updateInstaller.openInstallPermissionSettings()
        } catch (error: Exception) {
            awaitingInstallPermission = false
            _state.update {
                it.copy(
                    dialogVisible = true,
                    message = UpdateProtocol.message(error),
                    messageIsError = true,
                )
            }
        }
    }

    fun onAppResumed() {
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        if (updateInstaller.canInstallPackages()) {
            verifiedFile?.takeIf { it.isFile }?.let(::installDownloadedUpdate)
        } else {
            _state.update {
                it.copy(
                    dialogVisible = true,
                    permissionRequired = true,
                    message = "安装权限未开启",
                    messageIsError = true,
                )
            }
        }
    }

    private fun installDownloadedUpdate(file: File) {
        if (!updateInstaller.canInstallPackages()) {
            awaitingInstallPermission = true
            _state.update {
                it.copy(
                    dialogVisible = true,
                    permissionRequired = true,
                    message = null,
                    messageIsError = false,
                )
            }
            return
        }
        try {
            updateInstaller.install(file)
            _state.update {
                it.copy(
                    dialogVisible = false,
                    permissionRequired = false,
                    message = null,
                    messageIsError = false,
                )
            }
        } catch (error: Exception) {
            val message = when (error) {
                is UpdateException -> when (error.failure) {
                    UpdateFailure.INSTALL -> error.message.orEmpty()
                    else -> UpdateProtocol.message(error)
                }
                else -> UpdateProtocol.message(error)
            }
            _state.update {
                it.copy(
                    dialogVisible = true,
                    message = message.ifBlank { "无法打开系统安装器" },
                    messageIsError = true,
                )
            }
        }
    }
}
