# 开发与真机操作手册

## 1. 开始工作

按顺序阅读：

1. [`CURRENT_STATUS.md`](CURRENT_STATUS.md)
2. [`PROJECT_KNOWLEDGE_BASE.md`](PROJECT_KNOWLEDGE_BASE.md)
3. [`DEVELOPMENT_LOG.md`](DEVELOPMENT_LOG.md) 最新日期
4. 当前任务相关源码和测试

## 2. 测试与编译

```powershell
cd G:\hotel_monitor_app
flutter test
flutter build apk --debug
```

最新 Debug APK：

`G:\hotel_monitor_app\build\app\outputs\flutter-apk\app-debug.apk`

## 3. ADB 连接

```powershell
$adb='C:\Users\30493\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb start-server
& $adb devices -l
```

读取设备信息：

```powershell
& $adb shell getprop ro.product.model
& $adb shell getprop ro.product.device
& $adb shell getprop ro.build.version.release
& $adb shell getprop ro.build.version.sdk
```

状态含义：

- `device`：连接并授权。
- `unauthorized`：解锁手机，确认“允许 USB 调试”。
- 无设备：检查数据线、USB 模式、驱动和开发者选项。

## 4. 安装与启动

覆盖安装并保留现有数据：

```powershell
& $adb install -r 'G:\hotel_monitor_app\build\app\outputs\flutter-apk\app-debug.apk'
& $adb shell am start -n com.hotelmonitor.hotel_monitor_app/.MainActivity
```

若出现 `INSTALL_FAILED_USER_RESTRICTED`：

1. 保持手机解锁。
2. 开启“USB 安装”。
3. 必要时重新开启“USB 调试（安全设置）”。
4. 再执行安装。

安装后检查：

- 酒店监控 APP 无障碍服务是否启用。
- 首次采集时是否重新出现屏幕采集授权。
- 项目数据和历史截图是否仍存在。

## 5. 自动化异常诊断

用户报告“异常、卡住、停住”后，先保留现场。禁止先点击、返回、重启 APP 或锁屏。

只读检查：

```powershell
& $adb shell dumpsys activity activities |
  Select-String 'mResumedActivity|topResumedActivity'

& $adb logcat -d -v time -s HotelAutomation

& $adb shell uiautomator dump /sdcard/current_ui.xml
& $adb pull /sdcard/current_ui.xml G:\hotel_monitor_app\diagnostic_ui.xml

& $adb shell screencap -p /sdcard/diagnostic_current.png
& $adb pull /sdcard/diagnostic_current.png G:\hotel_monitor_app\diagnostic_current.png
```

先根据截图、前台 Activity、UI 树和日志判断原因，再决定是否修改手机现场。

## 6. 自动化修改后的最低验证

每次修改携程、美团、截图、OCR、队列、终止或验证码流程后：

1. 运行 `flutter test`。
2. 运行 `flutter build apk --debug`。
3. 覆盖安装 APK。
4. 检查无障碍和屏幕采集授权。
5. 先单酒店，再单项目，最后主页“全”。
6. 将产品决定、修复和真机结论写入主资料库、当前状态和开发日志。

## 7. 紧急停止

- 用户按锁屏键后，整次采集必须结束。
- 无障碍服务 `onInterrupt()` 后，整次采集必须结束。
- 中断后禁止恢复步骤或继续剩余队列。

## 8. 分享 APK

不要直接分享 `dist/酒店监控截图版_2026-07-18.apk`，它是历史版本。

生成并验证当前 Debug APK 后，再复制到 `dist`，文件名应包含日期和功能版本；同时在 [`CURRENT_STATUS.md`](CURRENT_STATUS.md) 记录路径、测试状态和是否已真机验证。
