# 酒店监控项目协作规则

## 开始工作前

修改代码前必须按顺序阅读：

1. `docs/CURRENT_STATUS.md`
2. `docs/PROJECT_KNOWLEDGE_BASE.md`
3. `docs/DEVELOPMENT_LOG.md` 最新日期
4. 当前任务相关源码和测试

需要测试、安装或诊断时再读 `docs/RUNBOOK.md`。

资料权威边界：

- 当前产品规则：`PROJECT_KNOWLEDGE_BASE.md`
- 最新构建、设备和待办：`CURRENT_STATUS.md`
- 时间线：`DEVELOPMENT_LOG.md`
- `docs/archive/`：仅供追溯，不能作为当前需求

## 用户协作

- 用户不编写代码；沟通使用清晰中文，需要人工操作时给出短步骤。
- 用户说“暂停”时，立即停止工具调用、手机操作、编译和安装。
- 用户报告自动化异常后，先只读取截图、前台 Activity、UI 树和 `HotelAutomation` 日志，不要先点击、返回或改变手机现场。

## 不可误改规则

- 不得把相似酒店、附近酒店、优惠金额或其他数字当作目标酒店价格。
- 无法可信确认时记录“错误”或“待核验”；满房记录“满”；不得猜测。
- 酒店的平台名称为空时必须跳过该平台。
- 验证码必须由用户人工完成，禁止自动破解。
- 按锁屏键或无障碍服务 `onInterrupt()` 时必须立即结束整次采集，不恢复步骤，不继续队列。

## 修改与验证

- 修改自动化流程后至少运行 `flutter test` 和 `flutter build apk --debug`。
- 安装后检查无障碍服务，并提示用户重新允许屏幕采集。
- 产品决定、流程变更或重要修复：更新主资料库。
- 测试数、设备、APK 或下一步变化：更新当前状态。
- 每次重要修改和真机结论：追加开发日志。

## 备份

- 当前目录已建立 Git 仓库，远端为 `https://github.com/304939905/hotel-monitor-app`；重要版本仍必须保留外部冻结备份，不能只依赖 Git。
- 用户提到“上个版本、历史版本、旧版本、之前版本、问小团之前版”等相近表述时，必须先读取 `docs/BACKUP_INDEX.md` 并提示匹配备份。
- 当前主要恢复点：`G:\hotel_monitor_app_backups\7.27问小团之前版`。
- 文字识别版冻结备份：`G:\hotel_monitor_app_backups\酒店监控文字版26-7-18`
