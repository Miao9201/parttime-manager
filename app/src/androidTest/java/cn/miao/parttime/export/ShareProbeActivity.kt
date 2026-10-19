package cn.miao.parttime.export

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log

/** Test-APK activity: verifies the receiving UID can read only the granted report URI. */
class ShareProbeActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("ReportShareProbe", "receiver-created uid=${android.os.Process.myUid()}")
        val receiver = intent.getParcelableExtra<ResultReceiver>("probeResult")
        val result = Bundle().apply { putInt("uid", android.os.Process.myUid()) }
        try {
            val uri = requireNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            require(uri.scheme == "content")
            val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("Recipient could not decode PNG")
            result.putInt("width", bitmap.width)
            result.putInt("height", bitmap.height)
            Log.i("ReportShareProbe", "receiver-read-completed width=${bitmap.width} height=${bitmap.height} uri=$uri")
            bitmap.recycle()
            receiver?.send(1, result)
        } catch (error: Exception) {
            result.putString("error", error.toString())
            receiver?.send(0, result)
        } finally { finish() }
    }
}
