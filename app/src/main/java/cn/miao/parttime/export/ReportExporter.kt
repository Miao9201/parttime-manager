package cn.miao.parttime.export

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import androidx.core.content.FileProvider
import cn.miao.parttime.data.DayBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class ReportArtifact(val file: File, val uri: Uri, val width: Int, val height: Int)

class ReportExporter(context: Context) {
    private val context = context.applicationContext

    suspend fun create(day: DayBundle, scope: ReportScope): ReportArtifact = withContext(Dispatchers.IO) {
        val report = buildReport(day, scope)
        val prepared = prepare(reportLines(report))
        currentCoroutineContext().ensureActive()
        val directory = File(context.cacheDir, "exports")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建导出缓存目录")
        val location = day.day.locationName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(28).ifBlank { "费用" }
        val file = File.createTempFile("费用-$location-${day.day.localDate}-${scope.label}-", ".png", directory)
        var bitmap: Bitmap? = null
        try {
            bitmap = draw(prepared)
            currentCoroutineContext().ensureActive()
            file.outputStream().buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("生成PNG失败，请重试")
            }
            check(file.length() > 0) { "生成的图片为空，请重试" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            ReportArtifact(file, uri, WIDTH, prepared.height)
        } catch (error: OutOfMemoryError) {
            file.delete()
            throw IllegalArgumentException(TOO_LARGE, error)
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            bitmap?.recycle()
        }
    }

    suspend fun save(artifact: ReportArtifact): Uri = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= 29) { "此系统请通过系统文件选择器保存图片" }
        requireArtifact(artifact)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${artifact.file.nameWithoutExtension}-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/兼职管理器")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("无法在相册创建图片")
        try {
            copyTo(artifact, uri)
            currentCoroutineContext().ensureActive()
            val published = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            if (published != 1) throw IOException("图片写入完成，但未能发布到相册")
            uri
        } catch (error: Throwable) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) { /* Keep the original failure. */ }
            throw error
        }
    }

    suspend fun saveTo(artifact: ReportArtifact, destination: Uri): Unit = withContext(Dispatchers.IO) {
        require(destination.scheme == "content") { "请选择系统提供的文件保存位置" }
        requireArtifact(artifact)
        copyTo(artifact, destination)
    }

    fun shareIntent(artifact: ReportArtifact): Intent {
        requireArtifact(artifact)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, artifact.uri)
            clipData = ClipData.newRawUri("费用明细", artifact.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun copyTo(artifact: ReportArtifact, destination: Uri) {
        val copied = context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            artifact.file.inputStream().use { input -> input.copyTo(output) }.also { output.flush() }
        } ?: throw IOException("无法打开图片保存位置")
        if (copied != artifact.file.length()) throw IOException("图片未完整写入，请重试")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val saved = context.contentResolver.openInputStream(destination)
            ?: throw IOException("保存后无法读取图片，请检查保存位置")
        saved.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth != artifact.width || bounds.outHeight != artifact.height || bounds.outMimeType != "image/png") {
            throw IOException("保存后的PNG校验失败，请重试")
        }
    }

    private fun requireArtifact(artifact: ReportArtifact) {
        val directory = File(context.cacheDir, "exports").canonicalFile
        require(artifact.file.canonicalFile.parentFile == directory && artifact.file.isFile && artifact.file.length() > 0) { "导出预览已失效，请重新生成" }
        require(artifact.uri == FileProvider.getUriForFile(context, "${context.packageName}.files", artifact.file)) { "导出图片地址无效，请重新生成" }
    }
}

private const val WIDTH = 1080
private const val TOO_LARGE = "内容过长，无法安全生成完整单张图片，请选择中午或晚上单班导出"
private data class MeasuredLine(val line: ReportLine, val layout: StaticLayout, val top: Int, val padding: Int, val left: Int)
private data class PreparedReport(val height: Int, val lines: List<MeasuredLine>)

private fun prepare(lines: List<ReportLine>): PreparedReport {
    // Keep fixed, readable type; reject oversized reports instead of shrinking or silently splitting.
    require(lines.fold(0L) { count, line -> count + line.text.length } <= 120_000L) { TOO_LARGE }
    val memoryBudget = minOf(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4)
    val maxHeight = minOf(30_000L, memoryBudget / (WIDTH * 4L))
    var y = 36L
    val measured = ArrayList<MeasuredLine>(lines.size)
    for (line in lines) {
        val bold = line.kind != ReportLineKind.BODY && line.kind != ReportLineKind.DATE
        val size = when (line.kind) {
            ReportLineKind.TITLE -> 56f
            ReportLineKind.DATE -> 36f
            ReportLineKind.SHIFT, ReportLineKind.SUMMARY, ReportLineKind.TOTAL -> 46f
            ReportLineKind.SUBTOTAL -> 44f
            ReportLineKind.BODY -> 40f
        }
        val padding = when (line.kind) {
            ReportLineKind.DATE -> 18
            ReportLineKind.SHIFT, ReportLineKind.SUMMARY -> 14
            else -> 8
        }
        val left = if (line.kind == ReportLineKind.BODY) 76 else 48
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(line.text, 0, line.text.length, paint, WIDTH - left - 48)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setLineSpacing(6f, 1f)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        measured += MeasuredLine(line, layout, y.toInt(), padding, left)
        y += layout.height.toLong() + padding * 2L
        require(y + 40 <= maxHeight) { TOO_LARGE }
    }
    return PreparedReport((y + 40).toInt(), measured)
}

private fun draw(report: PreparedReport): Bitmap {
    val bitmap = Bitmap.createBitmap(WIDTH, report.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val yellow = Paint().apply { color = Color.rgb(255, 230, 143) }
        for (measured in report.lines) {
            if (measured.line.kind == ReportLineKind.SHIFT || measured.line.kind == ReportLineKind.SUMMARY) {
                canvas.drawRect(36f, measured.top.toFloat(), WIDTH - 36f, (measured.top + measured.layout.height + measured.padding * 2).toFloat(), yellow)
            }
            canvas.save()
            canvas.translate(measured.left.toFloat(), (measured.top + measured.padding).toFloat())
            measured.layout.draw(canvas)
            canvas.restore()
        }
        return bitmap
    } catch (error: Throwable) {
        bitmap.recycle()
        throw error
    }
}
