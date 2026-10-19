package cn.miao.parttime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import cn.miao.parttime.theme.Theme
import cn.miao.parttime.ui.AppViewModel
import cn.miao.parttime.ui.PartTimeApp
import cn.miao.parttime.export.ReportExporter

class MainActivity : ComponentActivity() {
  private val model: AppViewModel by viewModels {
    object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AppViewModel((application as PartTimeApplication).repository, ReportExporter(applicationContext)) as T
    }
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      Theme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { PartTimeApp(model) } }
    }
  }
}
