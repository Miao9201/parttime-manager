# 兼职管理器

一个离线使用的 Android 兼职排班与费用管理工具。以“地点 + 日期”管理中午、晚上两班的人员、工资和加班，自动计算人费与当天总额，支持导出完整费用图片。

当前正式版：**1.2.2** · Android **8.0 / API 26 及以上** · Kotlin / Jetpack Compose / Room · MIT 开源。

[下载正式版 APK](https://github.com/Miao9201/parttime-manager/releases/latest) · [版本记录](CHANGELOG.md) · [第三方许可](THIRD_PARTY_NOTICES.md)

## 功能

- 创建、修改、删除地点与日期记录，支持“今天 / 明天”和日历选择。
- 香樟、旺辉快捷地点，支持自由输入；同地点同日期可进入已有记录。
- 固定中午、晚上两班，分别登记人员并汇总当天费用。
- 任务快速选择：服务员、传菜、洗碗、打荷；支持自定义任务。
- 工资按元输入，最多两位小数；同班同姓名提醒确认。
- 在人员卡片内直接新增、编辑、删除加班，支持多条加班。
- 加班时长快捷填入“加班1小时 / 加班2小时 / 加班3小时”，也可手动输入。
- 当天人头费单价两班共用，填 `0` 表示不收人费，留空表示未设置。
- 中午、晚上或整天费用独立导出为 PNG，支持预览、保存、系统分享。
- 导出包含每种任务的人数、单价、工资合计、加班、人费、两班小计和当天汇总；三段标题使用黄色条分隔。
- 未保存的修改有返回保护；演示入口和开发提示已从正式版移除。

## 安装

从 [Releases](https://github.com/Miao9201/parttime-manager/releases) 下载 `parttime-manager-1.2.2.apk` 安装。系统可能需要你允许本次来源或进行指纹验证。

升级官方版本时直接覆盖安装，保留已保存记录。自行编译的包使用不同签名时不能覆盖官方 APK。

## 使用

1. 点击首页“创建”，填写地点和日期并保存。
2. 设置当天人头费单价；不收人费填写 `0`。
3. 进入中午或晚上，添加人员、任务和工资。
4. 在人员卡片点击“加班”，填写时长和该条加班总金额并保存。
5. 返回当天详情，在右上角选择“导出”，再选择中午、晚上或整天。
6. 预览后保存图片，或通过系统分享面板选择接收应用。

金额以整数分保存和计算，界面按元输入与显示。加班金额是该条加班总额。同一人参加两班时分别计入两班的人费。

不同工资分别列行；同任务、同加班时长、同金额的加班合并显示，数量单位为“人”。导出只包含费用汇总，不包含人员姓名或历史电话。

Android 10 及以上将图片保存到相册 `Pictures/兼职管理器`；Android 8–9 使用系统文件选择器。长图会完整生成，超过安全单图尺寸时会提示选择单班导出。

## 数据与隐私

应用本机离线保存，没有服务器、账号系统或网络权限。业务数据保存在应用私有数据库；只有主动保存或分享费用图片时才写入公共图片目录或交给接收应用。

本仓库没有真实业务数据库、私人截图、手机信息、签名私钥、密码或本机配置。测试目录中的人员与地点为合成数据，不会随正式应用初始化。

卸载应用或清除应用数据会删除本地记录；费用图片不是数据库备份。本版没有独立备份恢复功能。

## 构建

建议使用 Android Studio 打开项目，让其下载 Gradle 与 Android SDK，然后进行 Gradle Sync。命令行构建需配置 `JAVA_HOME` 和 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`），或者由 Android Studio 生成本机 `local.properties`。

锁定配置：Gradle 9.1.0、AGP 9.0.1、Kotlin / Compose Compiler 2.2.10、KSP 2.3.10、Compile SDK 36、Build Tools 37.0.0、最低 API 26。应用主要依赖 Compose、Room 和 Coroutines；精确版本见 Gradle 文件。

```sh
# macOS / Linux
chmod +x gradlew
./gradlew assembleDebug
```

```powershell
# Windows
.\gradlew.bat assembleDebug
# 或使用环境变量中的 JDK / SDK
.\build.ps1
```

Debug APK 在 `app/build/outputs/apk/debug/`。首次构建需要下载依赖。项目不包含官方签名：Release 未配置自己的签名时会生成未签名 APK，不能直接作为官方覆盖更新。

需要自行签名时，可在本机创建已忽略的 `.signing/parttime-local.jks`，密钥别名使用 `parttime`；在 `.signing/local.properties` 中设置对应的 `storePassword`。两个文件始终保留在本机，不要提交。

1.2.2 仅更换为蓝色清单、人员和人民币图案的圆角启动图标，去掉外侧白底，同时更新普通及自适应图标资源。业务功能与数据库结构保持完整。Android 自适应图标的最终外轮廓也受桌面启动器遮罩影响。

本版完成 Release 构建及版本、图标资源、签名的静态核对；按发布要求未运行功能或设备测试。源码中保留 `test` / `androidTest` 供后续开发；这些目录和 Debug 工具不包含在正式 APK 内。本次没有配置会自动运行测试的 GitHub Actions。

## 目录

```text
app/src/main/java/cn/miao/parttime/
  data/       Room 实体、数据库与事务保存
  domain/     金额解析与费用计算
  export/     费用分组、PNG 绘制、保存与分享
  ui/         页面、表单、原位加班与图片预览
  theme/      界面主题
app/src/test/         JVM 测试（仅开发）
app/src/androidTest/  设备测试（仅开发）
app/schemas/          Room 数据结构快照
gradle/               Gradle Wrapper 与版本目录
```

## 常见问题

**为什么总额显示待设置？**
当天人头费尚未设置；不收费也需要明确填写 `0`。

**加班金额需要乘时长吗？**
输入该条加班总额，时长字段用于说明和导出分组，应用不再次乘时长。

**找不到导出图片？**
先确认保存成功提示，再查看系统相册或 `Pictures/兼职管理器`。Android 8–9 以文件选择器中选定的位置为准。

**新安装能看到以前的数据吗？**
使用相同应用签名覆盖升级才保留原应用记录。卸载后重新安装不会恢复本地数据。

**源码里为什么保留旧字段？**
旧电话与标记字段保留用于兼容既有数据库，正式界面不提供电话或演示功能，不会执行破坏性数据库迁移。

## 贡献与许可

欢迎提交 Issue 和 Pull Request，参见 [CONTRIBUTING.md](CONTRIBUTING.md)。请使用脱敏的合成数据描述问题。

本项目自有代码使用 [MIT License](LICENSE)。依赖及工具保留各自版权和许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
