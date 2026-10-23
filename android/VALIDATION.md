# v1.0 正式版构建与发布校验

校验日期：2026-10-23。应用版本 `1.0`，versionCode `2`，应用 ID `cn.xingsilu.app`。

## 构建与功能验证

从受版本控制的 39 个文件导出独立源码目录，未复制本机 SDK 配置、认证缓存或签名文件。SDK、依赖缓存和签名参数通过本机构建环境提供；使用缓存的公开 Maven 依赖完成离线构建：

```text
:app:testDebugUnitTest :app:assembleRelease :app:lintRelease --offline
```

结果：**BUILD SUCCESSFUL**。构建输入中的 28 个 Android 非文档文件与本地发布源码逐字节一致。

| 检查 | 结果 |
| --- | --- |
| BackupCodecTest | 5 项通过 |
| RepositoryTest | 7 项通过 |
| AppUiTest | 3 项通过 |
| 失败、错误、跳过测试 | 均为 0 |
| Release Android Lint | 0 错误，1 条 GradleDependency 更新提示 |
| APK 版本 | versionName 1.0，versionCode 2 |
| Release 调试开关 | 关闭 |
| APK 签名 | apksigner v2 验证通过，证书与此前安装包一致 |
| APK 对齐 | zipalign 校验通过 |
| 系统要求 | minSdk 26、targetSdk 35 |
| 应用权限 | 无网络权限、无公共存储读写权限 |
| 网页原型 | JavaScript 语法检查通过 |

Lint 提示来自显式 Kotlin JDK 兼容包 1.9.24 的可用更新，不影响本次构建。测试覆盖数据编解码、数据库与设置持久化、自动保存、草稿、搜索、删除恢复及主要 Compose 界面流程。

此前使用者已试用同一套首版功能并反馈主要功能正常。v1.0 本次验证为构建、自动化测试及安装包静态检查；未在用户实体手机上重新安装。功能代码和数据库结构未改动，沿用原有本地测试证书并提高 versionCode，以支持覆盖升级。

## 文件完整性与敏感信息

- 既有远端版本的 39 个文件与本地提交的路径、文件模式和 Git blob 哈希逐一相符，无缺失或额外文件。
- 检查所有可达 Git 提交、当前工作区受控文件以及 APK 解压后的文件名和内容，未发现个人绝对路径、常见服务令牌、私钥、认证配置、个人数据库或私人方案文档。
- Git 提交使用 GitHub noreply 邮箱。签名私钥、本机 SDK 配置、构建缓存、授权缓存及私人方案文档均被忽略，不进入源码或 Release 附件。
- 上传后再次核对远端文件清单和内容哈希；只有对应源码提交一致、Release 附件哈希与本地一致时才发布正式版。
- `SOURCE_SHA256SUMS.txt` 保存该版本 Git 源文件的逐文件校验值；按 Git 中存储的字节计算，适用于 GitHub 源码压缩包。Windows 检出时的换行转换可能导致本地文件哈希不同。

以上为本次明确范围内的文件核对、规则扫描和人工复核结果。

## 正式安装包

文件：`XingSiLu-1.0.apk`，大小：12,125,301 字节。

SHA-256：

```text
2596fc1fb8eb196c7fc00bcfa8c2c0feaf1811faa98447a2b4a5e2b3a723fab4
```

安装说明、发布附件校验文件与源码清单随 [v1.0 Release](https://github.com/FeiYang20816/TAF-XingSiLu/releases/tag/v1.0) 提供。原始测试报告、构建日志和详细本机检查结果仅保存在本机，不作为附件上传。
