package cn.miao.parttime.export

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.miao.parttime.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReportExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exporter = ReportExporter(context)

    @Test fun createsIndependentReadableFullHeightPngForEveryScope() = runBlocking {
        val files = mutableListOf<File>()
        try {
            for (scope in ReportScope.entries) {
                val artifact = exporter.create(fixture(), scope)
                files += artifact.file
                assertEquals("content", artifact.uri.scheme)
                val bitmap = context.contentResolver.openInputStream(artifact.uri).use { BitmapFactory.decodeStream(it) }!!
                assertEquals(1080, bitmap.width)
                assertEquals(artifact.height, bitmap.height)
                assertTrue(bitmap.height > 400)
                bitmap.recycle()
            }
            val long = exporter.create(fixture("需要完整换行的中文地点".repeat(12), "复杂任务说明".repeat(70), "完整加班说明".repeat(100)), ReportScope.DAY)
            files += long.file
            assertTrue(long.height > 3000)
            val decoded = BitmapFactory.decodeFile(long.file.absolutePath)!!
            var bottomInk = false
            for (y in (decoded.height - 180).coerceAtLeast(0) until decoded.height - 30) {
                for (x in 48 until decoded.width - 48) {
                    if ((decoded.getPixel(x, y) and 0x00ffffff) < 0x00808080) { bottomInk = true; break }
                }
                if (bottomInk) break
            }
            assertTrue("Final totals must be drawn near the bottom, not cropped", bottomInk)
            decoded.recycle()
        } finally { files.forEach { it.delete() } }
    }

    @Test fun rejectsExcessiveImageBeforeAllocatingAndNeverOverwritesCache() = runBlocking {
        try {
            exporter.create(fixture(task = "非常长的任务".repeat(40000)), ReportScope.DAY)
            fail("An image exceeding the safe single-image budget must fail explicitly")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("单班"))
        }
        val first = exporter.create(fixture(location = "../坏:/文件名"), ReportScope.NOON)
        val second = exporter.create(fixture(location = "../坏:/文件名"), ReportScope.NOON)
        try {
            assertNotEquals(first.file, second.file)
            assertEquals(File(context.cacheDir, "exports").canonicalFile, first.file.parentFile!!.canonicalFile)
            assertTrue(first.file.isFile)
            assertTrue(second.file.isFile)
        } finally { first.file.delete(); second.file.delete() }
    }

    @Test fun savesActualPngToMediaStoreAndClearsPending() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val artifact = exporter.create(fixture(), ReportScope.DAY)
        var saved: Uri? = null
        try {
            saved = exporter.save(artifact)
            val savedDigest = context.contentResolver.openInputStream(saved).use { digest(it!!.readBytes()) }
            assertArrayEquals(digest(artifact.file.readBytes()), savedDigest)
            context.contentResolver.query(saved, arrayOf(MediaStore.Images.Media.IS_PENDING, MediaStore.Images.Media.MIME_TYPE), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
                assertEquals("image/png", it.getString(1))
            }
            val bitmap = context.contentResolver.openInputStream(saved).use { BitmapFactory.decodeStream(it) }!!
            assertEquals(artifact.width, bitmap.width)
            assertEquals(artifact.height, bitmap.height)
            bitmap.recycle()
        } finally {
            saved?.let { context.contentResolver.delete(it, null, null) }
            artifact.file.delete()
        }
    }

    @Test fun destinationWriterCopiesExactPngBytes() = runBlocking {
        val artifact = exporter.create(fixture(), ReportScope.EVENING)
        val destination = File(context.cacheDir, "exports/test-destination-${UUID.randomUUID()}.png")
        destination.createNewFile()
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", destination)
            exporter.saveTo(artifact, uri)
            assertArrayEquals(artifact.file.readBytes(), destination.readBytes())
        } finally { destination.delete(); artifact.file.delete() }
    }

    @Suppress("DEPRECATION")
    @Test fun shareIntentCarriesOnlyTemporaryReadAccessToPng() = runBlocking {
        val artifact = exporter.create(fixture(), ReportScope.DAY)
        try {
            val intent = exporter.shareIntent(artifact)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertEquals(artifact.uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("${context.packageName}.files", artifact.uri.authority)
            assertEquals(1, intent.clipData!!.itemCount)
            assertEquals(artifact.uri, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        } finally { artifact.file.delete() }
    }

    private fun fixture(location: String = "导出测试（独立数据）", task: String = "洗碗", overtime: String = "加班2小时") = DayBundle(
        DayRecord(991, location, location, "2026-10-06", 500),
        listOf(ShiftKind.NOON, ShiftKind.EVENING).mapIndexed { i, shift -> PersonBundle(
            Assignment(i + 1L, 991, shift.name, "不应导出的姓名", "PHONE_TEST_ONLY", task, 10000, i),
            listOf(OvertimeItem(i + 1L, i + 1L, overtime, 4000, 0)),
        ) },
    )
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
