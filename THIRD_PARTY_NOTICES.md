# 第三方组件与许可

项目自有代码使用 MIT；以下依赖及工具保留其上游许可与版权。项目中的 MIT 许可不会替代第三方组件的许可。

| 组件 | 主要版权方 | 许可 | 上游 |
| --- | --- | --- | --- |
| AndroidX、Jetpack Compose、Room、Lifecycle、Activity、Core、Material3 | The Android Open Source Project / Google 及贡献者 | Apache-2.0 | https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt |
| Kotlin 标准库与编译工具 | JetBrains 及贡献者 | Apache-2.0 | https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt |
| Kotlin Coroutines | JetBrains 及贡献者 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt |
| Gradle Wrapper 与 Gradle 构建工具 | Gradle 及贡献者 | Apache-2.0 | https://github.com/gradle/gradle/blob/master/LICENSE |
| Android Gradle Plugin 与 KSP（构建期） | The Android Open Source Project / Google 及贡献者 | Apache-2.0 | https://android.googlesource.com/platform/tools/base/ ; https://github.com/google/ksp |

Apache License 2.0 全文见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)。精确依赖版本以项目 Gradle 文件及上游发行元数据为准。JVM / Android 测试依赖只用于开发，不包含在正式 APK 中。
