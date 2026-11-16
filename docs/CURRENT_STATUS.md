# 当前开发状态

最后核对：2026-11-16

本文记录会频繁变化的事实。产品规则以 [`PROJECT_KNOWLEDGE_BASE.md`](PROJECT_KNOWLEDGE_BASE.md) 为准。

## 当前版本

- 工作版本：截图版。
- GitHub 仓库：`https://github.com/304939905/hotel-monitor-app`
- 发布方式：源码进 Git，APK 按 CPU 架构发布到 GitHub Releases；当前版本 `v1.0.0`。
- 当前恢复点：`G:\hotel_monitor_app_backups\7.27问小团之前版`，定位为“问小团”功能开发前版本。
- 最新功能：新增/编辑项目支持全屏“批量添加酒店”。
- 批量格式支持可选主体酒店、A/B/C 竞对分组、`1` 转空平台名称。
- 最新 Debug APK：`G:\hotel_monitor_app\build\app\outputs\flutter-apk\app-debug.apk`
- 最新 Release APK：`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`，不提供通用大包。
- `dist/酒店监控截图版_2026-07-18.apk` 是旧分享包，不包含最新批量添加功能。

## 最近验证

- `flutter test`：21 项通过。
- `flutter analyze`：无问题。
- `flutter build apk --debug`：通过。
- `flutter build apk --release --split-per-abi`：通过；`arm64-v8a` 约 29.7 MB，`armeabi-v7a` 约 23.1 MB。
- “7.27问小团之前版”已生成 APK、源码 ZIP、版本说明、开发交接说明和 SHA256。
- 最新 Debug APK 已于 2026-07-27 覆盖安装并成功启动。
- 批量解析、全屏入口和回填表单已有自动测试。

## 当前连接手机

- 型号：小米 MI 8
- 设备代号：`dipper`
- Android：10（SDK 29）
- ADB 序列号：`faaca45b`
- ADB 状态：已授权并可安装 APK

历史主测试设备为小米 8 SE、Android 9；不要把两台设备的真机结论混写。

## 待人工验证

1. 全屏批量文本框能完整显示精简模板。
2. 中文粘贴或输入后，默认提示会消失。
3. 带主体酒店的批量内容能正确回填主体与竞对。
4. 不带主体酒店的内容只追加竞对，不覆盖原主体。
5. `1` 能正确显示为未填写，并在采集时跳过对应平台。
6. 点击项目窗口“保存”后，重启 APP 数据仍存在。

## 待回归风险

- 更新弹窗守卫需要等待携程/美团真实更新弹窗再次出现。
- 美团验证码保护及人工验证后的剩余任务续跑需要真实场景回归。
- 当前连接的 Android 10 手机应完整跑一次单项目与“全”采集。
- Android 11 以上尚未系统测试。
- Release APK 当前仍使用 Android Debug 签名，只适合测试和侧载，不适合上架应用商店。
- GitHub 仓库已排除调试截图、ADB 日志、构建目录、旧代码副本和 APK；仓库下载体积不等于安装包体积。

## 推荐下一步

先完成上述批量添加 6 项人工验证。通过后：

1. 通过 GitHub Releases 发布新的分架构 APK，不把 APK 提交进 Git。
2. 在 Android 10 上执行一次单项目携程+美团采集。
3. 再执行一次主页“全”多项目采集。
4. 把真机结论更新到本文和开发日志。
