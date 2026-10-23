# 行思录

把经历沉淀为经验，把想法留给未来。

行思录是一款离线 Android 记录应用，以“一句话总结 + 自由详情”积累经验，也可以随时记下没有标题的想法。无需登录或联网，支持搜索、标签、置顶、回收站、Markdown 导出和 JSON 备份恢复。

| 路径 | 内容 |
| --- | --- |
| `web/` | 独立网页原型，包含页面、样式、脚本、启动配置和说明 |
| `android/` | Kotlin / Jetpack Compose 原生 Android 应用、数据库、测试与构建脚本 |
| `dist/` | 本机生成的 APK 安装包与校验信息（不纳入 Git） |

支持 Android 8.0 及以上。使用 Android Studio 打开 `android/`，配置本机 Android SDK 后即可同步和构建。Android 开发与安装说明见 [android/README.md](android/README.md)。Windows 也可在项目根目录运行：

```powershell
.\android\tools\build.ps1
```

构建使用随仓库提供的 Gradle Wrapper，不依赖相邻项目或固定磁盘位置。本机 SDK 配置、签名密钥、构建缓存、安装包和私人方案文档不纳入版本控制。

## 网页预览

双击 [web/index.html](web/index.html) 打开预览；也可在项目根目录执行：

```powershell
npm --prefix web start
```

然后访问 <http://127.0.0.1:4173>。详细功能与使用说明见 [web/README.md](web/README.md)。
