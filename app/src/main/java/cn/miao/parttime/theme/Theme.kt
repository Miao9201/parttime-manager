package cn.miao.parttime.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppColors = lightColorScheme(
    primary = Color(0xFF0879CE), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F2FF), onPrimaryContainer = Color(0xFF125184),
    secondary = Color(0xFF596A7C), background = Color(0xFFF3F5F7),
    surface = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F4F8), surfaceContainerHigh = Color(0xFFEDF1F5),
    outline = Color(0xFFC9D1DB), outlineVariant = Color(0xFFDCE1E7),
    onBackground = Color(0xFF202B38), onSurface = Color(0xFF202B38),
)

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(16.dp)),
        content = content)
}
