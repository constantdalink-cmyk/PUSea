package org.example.project

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 安卓侧「项目 → zip → 直接分享」实现（ProjectZipExporter）。
 *
 * 交互契约：长按竖块一下 → 本类自动打包 zip → 立即调用 ACTION_SEND 拉起安卓原生分享面板，
 * 用户在面板上点目标应用直接发送——不需要自己去任何地方找 zip 文件。
 *
 * URI 通道选择（FileProvider 为主通道的原因 = 修复"打包出一个数字"的 BUG）：
 *  · 主通道 FileProvider：URI 末段就是真实文件名（xxx.zip），接收方无论按 DISPLAY_NAME
 *    还是直接解析 URI 末段取名，拿到的都是"正确文件名 + .zip 扩展名"；
 *  · 回退通道 MediaStore.Downloads（manifest 未配 FileProvider 时兜底）：分享功能本身
 *    仍可用，但 URI 形如 content://media/…/downloads/{行号}——末段是媒体行号，
 *    蓝牙/部分应用按 URI 段取名时，对方收到的文件会显示成 "52" 这种纯数字、无扩展名。
 *    想让所有接收方都拿到正确文件名，把下面的一次性配置加上即可。
 *
 * 一次性配置（主通道所需；不配则自动走回退通道）：
 * AndroidManifest.xml 的 <application> 内声明——
 *
 *   <provider
 *       android:name="androidx.core.content.FileProvider"
 *       android:authorities="${applicationId}.fileprovider"
 *       android:exported="false"
 *       android:grantUriPermissions="true">
 *       <meta-data
 *           android:name="android.support.FILE_PROVIDER_PATHS"
 *           android:resource="@xml/project_share_paths" />
 *   </provider>
 *
 * 并新建 res/xml/project_share_paths.xml——
 *
 *   <paths>
 *       <cache-path name="cache" path="." />
 *   </paths>
 *
 * 两条通道都失败 → Toast 报错，绝不静默无反应。
 */
class AndroidProjectZipExporter(private val context: Context) : ProjectZipExporter {

    override suspend fun exportAndShare(zipName: String, files: List<ProjectFilePayload>) {
        if (files.isEmpty()) {
            toast("Empty project")
            return
        }
        val safeName = zipName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trim('.')
            .ifBlank { "project" }
        try {
            val zipBytes = withContext(Dispatchers.IO) { buildZip(files) }
            val uri: Uri? = withContext(Dispatchers.IO) {
                val viaFileProvider =
                    runCatching { writeToCacheAndExpose(safeName, zipBytes) }.getOrNull()
                viaFileProvider ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { insertToDownloads(safeName, zipBytes) }.getOrNull()
                } else {
                    null
                }
            }
            if (uri == null) {
                toast("分享失败：请在 Manifest 配置 FileProvider（见本文件头说明）")
                return
            }
            launchSystemShare(uri, "$safeName.zip")
        } catch (t: Throwable) {
            toast("分享失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun buildZip(files: List<ProjectFilePayload>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            files.forEach { f ->
                zos.putNextEntry(ZipEntry(f.path))
                if (!f.isDirectory) {
                    zos.write(f.content.toByteArray(Charsets.UTF_8))
                }
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertToDownloads(name: String, bytes: ByteArray): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.zip")
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun writeToCacheAndExpose(name: String, bytes: ByteArray): Uri {
        val out = File(context.cacheDir, "$name.zip")
        FileOutputStream(out).use { it.write(bytes) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            out
        )
    }

    private fun launchSystemShare(uri: Uri, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
