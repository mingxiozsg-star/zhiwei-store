package com.zhiwei.store.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import com.zhiwei.store.core.Http
import com.zhiwei.store.core.formatSize
import com.zhiwei.store.data.StorePrefs
import com.zhiwei.store.model.DownloadStatus
import com.zhiwei.store.model.DownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 应用内下载器：直接拉 APK 文件，进度可观察、可取消，完成后自动唤起安装器。
 */
class Downloader(private val context: Context, private val prefs: StorePrefs) {

    val tasks = mutableStateListOf<DownloadTask>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    fun downloadDir(): File {
        val dir = context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun download(
        name: String,
        packageName: String?,
        sourceName: String,
        url: String,
        versionName: String?
    ): DownloadTask {
        val dir = downloadDir()
        val fileName = uniqueFileName(dir, sanitize("${name}_${versionName ?: "latest"}.apk"))
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            name = name,
            packageName = packageName,
            sourceName = sourceName,
            url = url,
            fileName = fileName
        )
        tasks.add(0, task)
        if (prefs.wifiOnly() && !onWifi()) {
            update(task.id) { it.copy(status = DownloadStatus.PAUSED, error = "仅 Wi-Fi 下载已开启，等待 Wi-Fi") }
            return tasks.first { it.id == task.id }
        }
        start(task.id)
        return tasks.first { it.id == task.id }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        val file = tasks.firstOrNull { it.id == id }?.let { File(downloadDir(), it.fileName) }
        file?.let { runCatching { it.delete() } }
        update(id) { it.copy(status = DownloadStatus.CANCELED, error = null) }
        notifier.cancel()
    }

    fun retry(id: String) {
        val task = tasks.firstOrNull { it.id == id } ?: return
        update(id) { it.copy(status = DownloadStatus.WAITING, doneBytes = 0, error = null) }
        start(id)
    }

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        tasks.removeAll { it.id == id }
    }

    fun completedFile(task: DownloadTask): File = File(downloadDir(), task.fileName)

    fun install(task: DownloadTask) {
        val file = completedFile(task)
        if (!file.exists()) return
        if (!AppInstaller.canInstall(context)) {
            AppInstaller.requestInstallPermission(context)
            return
        }
        AppInstaller.install(context, file)
    }

    private fun start(id: String) {
        val task = tasks.firstOrNull { it.id == id } ?: return
        jobs[id]?.cancel()
        val job = scope.launch {
            update(id) { it.copy(status = DownloadStatus.RUNNING, error = null) }
            val file = File(downloadDir(), task.fileName)
            if (file.exists()) file.delete()
            var lastNotifyAt = 0L
            try {
                java.io.FileOutputStream(file).use { out ->
                    Http.download(
                        url = task.url,
                        out = out,
                        timeoutMs = 30000
                    ) { done, total ->
                        update(id) {
                            it.copy(
                                doneBytes = done,
                                totalBytes = if (total > 0) total else it.totalBytes
                            )
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyAt > 600) {
                            lastNotifyAt = now
                            notifier.progress(task.name, done, total)
                        }
                        isActive
                    }
                }
                if (!isActive) {
                    runCatching { file.delete() }
                    update(id) { it.copy(status = DownloadStatus.CANCELED) }
                    notifier.cancel()
                    return@launch
                }
                val size = file.length()
                update(id) {
                    it.copy(
                        status = DownloadStatus.DONE,
                        doneBytes = size,
                        totalBytes = if (it.totalBytes > 0) it.totalBytes else size
                    )
                }
                notifier.done(task.name, size)
                if (prefs.autoInstall()) {
                    withContext(Dispatchers.Main) {
                        install(tasks.firstOrNull { it.id == id } ?: task)
                    }
                }
            } catch (e: CancellationException) {
                runCatching { file.delete() }
                update(id) { it.copy(status = DownloadStatus.CANCELED) }
                throw e
            } catch (e: Exception) {
                runCatching { file.delete() }
                update(id) { it.copy(status = DownloadStatus.FAILED, error = e.message ?: "下载失败") }
                notifier.failed(task.name, e.message ?: "下载失败")
            } finally {
                jobs.remove(id)
            }
        }
        jobs[id] = job
    }

    private fun update(id: String, block: (DownloadTask) -> DownloadTask) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index >= 0) tasks[index] = block(tasks[index])
    }

    private fun onWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(80)

    private fun uniqueFileName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.removeSuffix(".apk")
        var i = 1
        while (true) {
            val candidate = "${base}_$i.apk"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    private val notifier = DownloadNotifier(context)
}

/** 下载通知：进度、完成、失败 */
class DownloadNotifier(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "zhiwei_store_download"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "应用下载", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    private fun builder(): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

    private fun contentIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun progress(name: String, done: Long, total: Long) {
        runCatching {
            val b = builder()
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载 $name")
                .setContentText(if (total > 0) "${formatSize(done)} / ${formatSize(total)}" else formatSize(done))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(if (total > 0) 100 else 0, if (total > 0) ((done * 100) / total).toInt() else 0, total <= 0)
            contentIntent()?.let { b.setContentIntent(it) }
            manager.notify(1001, b.build())
        }
    }

    fun done(name: String, size: Long) {
        runCatching {
            val b = builder()
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("下载完成 $name")
                .setContentText(formatSize(size))
                .setAutoCancel(true)
            contentIntent()?.let { b.setContentIntent(it) }
            manager.notify(1001, b.build())
        }
    }

    fun failed(name: String, reason: String) {
        runCatching {
            val b = builder()
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("下载失败 $name")
                .setContentText(reason)
                .setAutoCancel(true)
            contentIntent()?.let { b.setContentIntent(it) }
            manager.notify(1001, b.build())
        }
    }

    fun cancel() {
        runCatching { manager.cancel(1001) }
    }
}

/** APK 安装：FileProvider + 未知来源权限绕行 */
object AppInstaller {

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    fun install(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}