# 首版构建验证

版本：1.0.0-preview.1，versionCode 1。

最终执行：

```powershell
.\tools\build.ps1 -Tasks @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', '--offline')
```

结果：BUILD SUCCESSFUL。

| 检查 | 结果 |
| --- | --- |
| BackupCodecTest | 5 项通过 |
| RepositoryTest | 7 项通过 |
| AppUiTest | 3 项通过 |
| Android Lint | 0 错误，1 条 GradleDependency 版本提示 |
| APK 签名 | apksigner 验证通过，v2 |
| 清单 | minSdk 26、targetSdk 35、启动入口 MainActivity |
| 应用权限 | 仅 AndroidX 声明的本应用签名级动态广播权限；无网络/公共存储权限 |
| 页面截图 | 7 张，已检查经验/想法主内容、两行截断、可选时间及主题 |

Lint 提示来自显式 Kotlin JDK 兼容包 1.9.24；Kotlin 编译插件与主标准库使用 2.1.20。该提示不阻止构建。

Robolectric 临时目录设在 `.local/test-temp/`，避免 Windows 沙箱临时目录对原子替换的限制。DataStore 由 Application 单例持有，测试结束时关闭所属协程作用域，防止跨测试共享文件。

完整报告在 `app/build/reports/`；数据库 v1 架构快照在 `app/schemas/`。交付安装包、校验值与使用说明在仓库 `dist/`。

本表记录开发阶段的自动化验证。后续使用者已完成手机试用并反馈各项主要功能正常；其他设备的输入法、系统文件选取/导出、后台切换和字体缩放可继续验收。
