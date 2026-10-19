package cn.miao.parttime.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.miao.parttime.export.ReportArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReportPreviewDialog(artifact: ReportArtifact, saving: Boolean,
    onDismiss: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    var bitmap by remember(artifact.file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(artifact.file) { mutableStateOf(false) }
    LaunchedEffect(artifact.file) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                var sample = 1
                while (artifact.width.toLong() * artifact.height / sample / sample > 8_000_000L) sample *= 2
                BitmapFactory.decodeFile(artifact.file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (_: OutOfMemoryError) { null }
        }
        failed = bitmap == null
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f).testTag("report_preview")) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("费用明细预览", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag("close_report")) { Text("关闭") }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFEDEDED)), contentAlignment = Alignment.Center) {
                    val current = bitmap
                    when {
                        current != null -> BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Image(current.asImageBitmap(), contentDescription = "完整费用明细，可上下滚动查看",
                                modifier = Modifier.fillMaxWidth().height(maxWidth * current.height.toFloat() / current.width))
                        }
                        failed -> Text("预览加载失败，仍可保存或分享原图", Modifier.padding(24.dp))
                        else -> CircularProgressIndicator()
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onShare, enabled = !saving, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("share_report")) { Text("分享图片") }
                    Button(onClick = onSave, enabled = !saving, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_report")) { Text(if (saving) "保存中…" else "保存图片") }
                }
            }
        }
    }
}
