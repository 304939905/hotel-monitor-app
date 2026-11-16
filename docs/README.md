# 项目资料索引

## 阅读顺序

1. [`CURRENT_STATUS.md`](CURRENT_STATUS.md)
   最新构建、当前手机、待验证事项和推荐下一步。
2. [`PROJECT_KNOWLEDGE_BASE.md`](PROJECT_KNOWLEDGE_BASE.md)
   当前有效的产品规则、采集流程、状态定义和架构。
3. [`DEVELOPMENT_LOG.md`](DEVELOPMENT_LOG.md) 最新日期
   最近修改、测试和真机结论。
4. [`RUNBOOK.md`](RUNBOOK.md)
   测试、编译、ADB、安装、诊断和分享 APK 的操作步骤。
5. [`BACKUP_INDEX.md`](BACKUP_INDEX.md)
   可恢复版本、备份路径和历史版本提示规则。
6. [`RELEASE_POLICY.md`](RELEASE_POLICY.md)
   GitHub Release、APK 拆分和附件流量规则。

根目录 [`AGENTS.md`](../AGENTS.md) 是必须遵守的协作与安全规则。

## 权威边界

| 内容 | 权威文件 |
|---|---|
| 当前产品行为与不可误改规则 | `PROJECT_KNOWLEDGE_BASE.md` |
| 最新测试数、手机、APK、待办 | `CURRENT_STATUS.md` |
| 操作命令与诊断步骤 | `RUNBOOK.md` |
| 修改和测试时间线 | `DEVELOPMENT_LOG.md` |
| 可恢复的历史版本 | `BACKUP_INDEX.md` |
| GitHub 发布与附件安排 | `RELEASE_POLICY.md` |
| 旧需求与历史现场 | `archive/`，仅供追溯 |

发生冲突时：

1. 当前产品规则以主资料库为准。
2. 最新构建状态以当前状态文件为准。
3. 历史归档永远不能覆盖当前资料。

## 更新规则

- 产品决定、流程规则和稳定架构变化：更新主资料库。
- 测试数、设备、APK、待验证项和下一步：更新当前状态。
- 每次重要修改和真机结论：追加开发日志。
- 命令、安装或诊断步骤变化：更新操作手册。
- 新增冻结版本：更新备份索引。
- 失效资料移入 `archive/`，不要与当前资料并列。

## 历史归档

见 [`archive/README.md`](archive/README.md)。
