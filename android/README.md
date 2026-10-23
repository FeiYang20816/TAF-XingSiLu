# 行思录 Android

原生 Android 正式版，版本 `1.0`，versionCode `2`，应用 ID `cn.xingsilu.app`。支持 Android 8.0（API 26）及以上。

## 已实现

- 经验：一句话总结必填，详情可选；首次明确保存，之后自动保存。
- 想法：无标题自由文本，输入有效内容后自动保存。
- 编辑草稿：不完整的内容单独保存在本地，列表可继续编辑；无效修改不会清空已经保存的记录。
- 列表：经验只显示总结；想法最多显示两行；白色卡片边缘渐变融入浅色背景；时间默认隐藏，可在设置中开启。
- 搜索总结、详情、正文和标签；标签筛选；按创建或更新时间排序；置顶。
- 删除确认、回收站恢复、永久删除确认。
- 全部经验、全部想法和单条记录的 UTF-8 Markdown 导出。
- 带版本号的 JSON 完整备份与合并恢复，包含回收站及草稿。冲突记录作为新记录导入，已有内容不被覆盖。
- 浅色、深色、跟随系统主题。

首次运行没有演示数据。应用不申请网络或公共存储读写权限。导出与恢复使用系统文件选择器。

## 安装

从 [v1.0 发布页](https://github.com/FeiYang20816/TAF-XingSiLu/releases/tag/v1.0) 的 Assets 下载 `XingSiLu-1.0.apk`，交付副本位于仓库 `dist/`。
将 APK 传到 Android 手机，点击安装；需要时允许当前文件管理器安装应用。

正式版为关闭调试功能的 Release 构建，沿用个人测试阶段的本地签名证书，支持直接覆盖已安装版本。签名密钥不随源码发布。卸载会删除本机记录；升级前也建议先在设置中导出 JSON 完整备份。

## 构建

需要 JDK 17 或 21、Android SDK Platform 35、Build Tools 35.0.0。用 Android Studio 打开本目录，或使用 Gradle Wrapper 8.11.1。

首次用 Android Studio 打开本目录时，通过 SDK 设置选择本机 SDK，IDE 会生成 `local.properties`。命令行构建也可以通过标准环境变量 `ANDROID_HOME` 指定 SDK。这个位置由每台电脑自行配置，`local.properties` 不提交 Git。

```powershell
# 在 android/ 目录执行：运行测试、构建 APK、运行 Android Lint
.\tools\build.ps1

# 只构建 APK
.\tools\build.ps1 -Tasks @(':app:assembleDebug')
```

macOS / Linux 可以直接运行：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

默认使用仓库内的 Gradle Wrapper，首次构建会下载 Gradle 和 Maven 依赖。`build.ps1` 不依赖其他项目；其路径均相对于脚本自身解析，因此从其他工作目录调用也有效。JDK 可通过 `JAVA_HOME` 或命令搜索路径配置。

如需复用已安装工具，可在本机自行设置以下可选环境变量。仓库不包含这些变量的个人取值：

| 环境变量 | 用途 |
| --- | --- |
| `XINGSILU_GRADLE_HOME` | 仅 Windows 构建脚本使用，指定已有的 Gradle 8.11.1 安装目录，替代 Wrapper |
| `GRADLE_USER_HOME` | 标准 Gradle 缓存位置，不设置时使用 Gradle 默认位置 |
| `XINGSILU_ROBOLECTRIC_DIR` | 可选的本地 Robolectric Android 运行时缓存；不设置时测试自动下载所需运行时 |

Windows 脚本首次运行会创建 `.local/debug.keystore`。项目检测到此密钥时复用它；没有该密钥时，直接通过 Android Studio 或 Wrapper 构建会使用 Android 的默认调试签名，无需先运行脚本。所有密钥均留在本机。后续要覆盖安装同一个应用，必须保留原来的签名证书。

### Release 构建与签名

```powershell
.\tools\build.ps1 -Tasks @(':app:assembleRelease', ':app:lintRelease')
```

Release 构建始终关闭调试功能。未提供发布签名时生成 `app/build/outputs/apk/release/app-release-unsigned.apk`；提供以下全部环境变量时，生成已签名的 `app-release.apk`：

| 环境变量 | 用途 |
| --- | --- |
| `XINGSILU_RELEASE_STORE_FILE` | 本机签名密钥库位置，由当前电脑配置 |
| `XINGSILU_RELEASE_STORE_PASSWORD` | 密钥库密码 |
| `XINGSILU_RELEASE_KEY_ALIAS` | 签名密钥别名 |
| `XINGSILU_RELEASE_KEY_PASSWORD` | 签名密钥密码 |

这些值只通过本机构建进程注入，不写入源码或版本控制。签名文件放在被忽略的本地目录中；独立构建者使用自己的密钥。缺少部分签名变量时构建会明确报错，避免意外使用其他证书。

若本机 Java 无法访问 Maven、但 PowerShell 可以下载，`tools/cache-datastore.ps1` 可从 Google Maven 缓存 DataStore 的原始发布文件，并从 Maven Central 缓存所需的 Kotlin Parcelize 运行时。缓存位于 `.local/maven/`，构建会自动识别；DataStore 二进制根据发布元数据验证 SHA-256。此脚本只用于开发环境，不打包进应用。

## 源码结构

| 文件/目录 | 作用 |
| --- | --- |
| `app/src/main/java/cn/xingsilu/app/data/` | Room 数据库、记录仓库、DataStore 设置、备份和 Markdown 编解码 |
| `AppViewModel.kt` | 屏幕状态、顺序保存、导出和导入协调 |
| `ui/XingSiLuApp.kt` | Compose 界面、主题与交互 |
| `app/schemas/` | Room 数据库版本快照；后续升级需提供迁移，禁止破坏性重建 |
| `app/src/test/` | 数据规则、持久化、备份冲突、Compose 流程测试 |

## 备份说明

JSON `schemaVersion: 1` 的记录字段与网页预览兼容，因此可以导入网页预览导出的完整备份。原生应用另外保存 `drafts`；网页预览不识别这些草稿。原生恢复界面明确保留当前主题、日期显示和排序偏好。

恢复前先验证整个文件，再在单个数据库事务中导入。相同记录跳过；同 ID 不同内容重新分配 ID，导入草稿与对应记录一起重新关联。格式错误、未知版本、重复 ID 或无效日期会拒绝导入，不改变现有数据库。

当前单个 JSON 备份上限 20 MB。Markdown 仅用于阅读与整理，不作为可恢复备份。系统自动云备份关闭，用户自行选择的导出文件是迁移数据的方式。

## 测试与验收

`testDebugUnitTest` 包含纯逻辑、Room/Robolectric 持久化和 Compose 界面流程测试。界面测试截图输出至 `.local/screenshots/`，测试报告位于 `app/build/reports/tests/testDebugUnitTest/`，Lint 报告位于 `app/build/reports/lint-results-debug.html`。

首版已经过使用者的手机日常试用，各项主要功能运行正常。其他设备的输入法、系统文件选择器、厂商后台管理和不同字体缩放仍可继续验收。
