# GitHub 发布与附件规范

最后更新：2026-11-16

## 目标

源码仓库保持可快速克隆，APK 独立发布，用户只下载所需版本和 CPU 架构，避免 Git 历史被大二进制持续撑大。

## 文件边界

进入 Git：

- Flutter、Dart、Kotlin、Android 配置和测试源码。
- 当前有效的产品、开发、运行和发布文档。
- 小型文本配置（`.gitignore`、`pubspec.yaml`、`pubspec.lock` 等）。

不进入 Git：

- APK、AAB、签名文件、密钥库和 `key.properties`。
- `build/`、`.dart_tool/`、`android/.gradle/`。
- ADB 日志、UI XML、调试截图和临时现场文件。
- `dist/` 中的历史分享包。
- `lib/新建 文本文档.txt` 等旧代码副本。

## Release 规则

1. Git tag 与 APP 版本保持一致，例如源码 `1.0.0+1` 对应 tag `v1.0.0`。
2. Android 安装包使用 `flutter build apk --release --split-per-abi` 生成。
3. Release 至少提供 `arm64-v8a`；存在 32 位设备需求时再提供 `armeabi-v7a`。
4. 没有明确需求时不提供通用 APK，避免用户下载包含多套原生库的大文件。
5. 不把同一版本的 APK 同时放入 Git、Release 和市场渠道。
6. 每个 Release 附带 `SHA256SUMS.txt`。
7. GitHub 自动生成的 Source code ZIP/TAR 已足够，不重复上传源码压缩包。
8. Release 说明写清版本状态、已知风险和适用设备，不用“稳定版”描述未完成真机回归的包。

## 文件名

```text
hotel-monitor-app-arm64-v8a-v1.0.0.apk
hotel-monitor-app-armeabi-v7a-v1.0.0.apk
SHA256SUMS.txt
```

## 下载流量原则

- 普通用户只下载一个与设备架构匹配的 APK。
- `arm64-v8a` 是近年主流安卓设备优先选项。
- `armeabi-v7a` 只给较老的 32 位设备。
- 不为每个 Release 附带源码 ZIP、截图包、日志包和重复安装包。
- 大文件升级时创建新 Release；不要用 force push 改写已有版本。

## 签名边界

当前 Android `release` 构建仍使用调试签名，只适合本机测试和侧载。若后续需要长期分发，应另行建立受保护的正式签名配置；签名文件不得提交到 Git。
