# 留白专注前端交接

应用包名是 `com.focusgarden.app`，版本以 `version.properties` 为准。代码采用 Java 17 和 Android 原生 View，最低 API 27。[README](README.md) 提供构建、规则验证和发布说明。[设计参照](design/README.md) 提供界面截图。

## 界面入口

| 界面 | 代码 |
| --- | --- |
| 首页（顶部肉色动态小猪储蓄罐、2h 上限储蓄卡、可用时长与每日 15m 基础免费提示、靶心目的模式卡且无黑点徽章、奖励兑换快捷卡）、独立兑换页（6 项时间赚取与自律双模式卡片 + 对齐卡片管理）、极简戒断页（自定义戒断事件/目标时长/起始时间/奖励、秒级实时计时与点卡编辑）、洞察页（顶部「自律统计 · 本机永久保留」+ 7 日堆叠图与收支时间线）、应用规则、全 4 页共享右上角悬浮设置按钮与滑动切页 | `app/src/main/java/com/focusgarden/app/MainActivity.java`、`PiggyBankView.java` |
| 全屏自律双模式计时（`正计时` 可停 / `倒计时` 不可停 + 学习应用直达）、付费修改确认（`confirmPaidChange()`）、6 种时间获取模式（含放纵卡与 2 放纵兑 1 躺平卡）与每日上限（`showRateSettings()`）、通用设置（`showGeneralSettings()`）、奖励列表与左上角 `+` 新增二级弹窗（`showRewardsSheet()` / `showAddRewardSheet()`）、强力守护与系统聚合面板（`showKeepAliveSheet()`）、开发阶段调试面板（`showDebugSheet()`）、定制时间选择器（`pickClock()`）、待办、早睡挑战（`showEarlySleep()`）、戒断编辑器（`showAbstinenceEditor()`）、步数、复选框多选应用选择器（隐藏包名）与设置面板 | 同级 `FocusSheets.java` |
| 设计系统、颜色、按钮、定制卡片弹窗（`cardDialog` / `infoDialog` / `confirmDialog` / `loadingDialog`）、单次测量限高弹窗（`MaxHeightScrollView`）、支持左上角动作槽的底部弹窗（`bottomSheet()`）、轻量规则提示按钮（`infoIcon` / `titleWithInfo`）、统一防抖触感通道（`HapticKind` / `tapHaptic()`）、开关、进度条与 24h 心跳条（`HeartbeatStripView`） | 同级 `GardenUi.java`、`GardenIcon.java` |
| 倒计时/正计时表盘与 7 日堆叠统计柱（自律与专注合并展示） | 同级 `TimerDial.java`、`InsightChart.java` |
| 保活与防关闭异步快照检测（`StatusSnapshot` / `refreshStatusAsync()`）、权限跳转、UsageStats 异步（04:00 逻辑日起点）与手动校准、自选壁纸持久化及服务同步 | 同级 `KeepAliveManager.java` |
| 后台常驻保活前台服务（心跳记录、后台计步监听与维持原 `24dp`/`0.16` 透明度的可拖拽、带位置持久化及系统手势排除区边缘吸附悬浮球） | 同级 `KeepAliveService.java`、`KeepAliveWallpaperService.java`、`FocusDeviceAdmin.java` |
| 语录加载与无 API 速率限制阻断的网页优先更新检测/校验逻辑 | 同级 `QuoteBank.java`、`GitHubUpdates.java` |
| 前台应用窗口识别（过滤小尺寸悬浮球/侧边栏与 OEM 安全弹窗、目的模式期间优先保底已授权应用窗口防误拦）、多应用目的模式与桌面全非目标应用拦截、半透明灰色悬浮计时药丸、躺平模式全免限、专注防关闭系统设置保护与顶部时间感知条 | 同级 `FocusService.java` |
| 全屏图片与方图拦截界面、字号与图片尺寸、居中小胶囊主按钮、预览、多套界面顺序轮换 | 同级 `BlockAppearanceSettings.java`、`BlockScreen.java`、`BlockImage.java` |
| 目的模式多应用复选框选择（隐藏包名）、全屏倒计时、目的应用直达与暂停操作 | 同级 `SoberModeUi.java` |
| 启动更新检查、手动检查更新及下载安装弹窗 | 同级 `UpdatePanel.java` |
| 桌面小组件与快捷设置磁贴（自律模式控制，禁止提前停倒计时） | 同级 `FocusWidget.java`、`FocusTile.java` 与 `app/src/main/res/layout/focus_widget.xml` |

矢量图标（含 `ic_gift.xml`、`ic_target.xml`、`ic_nav_redeem.xml`、`ic_nav_abstain.xml`）与小芽状态插画（`res/drawable/`）、池塘横幅（`res/drawable-nodpi/pond_at_dusk.png`）、语录库（`res/raw/quotes.json`）、壁纸/设备管理器配置（`res/xml/`）及其他资源均位于 `app/src/main/res/`。

## 联调

时间经济体系（每日凌晨 `04:00` 重置逻辑日/每日可用与 15m 基础免费已用额度、全部受限应用每日 `15 分钟` 共享基础免费上限、未自定义免费额度默认 `0 分钟` 完全由可用时长决定、储蓄罐 `2 小时` 上限不清零、6 种时间获取模式 `松弛 1:1 / 简单 2:1 / 适中 3:1 / 学霸 4:1 / 放纵 1:2 / 躺平全免限`、常规模式每日 1 次 `30 分钟` 切换限制、正常模式累计 `3 天` 自律兑 1 张放纵卡最多 `5` 张、2 张放纵卡兑 1 张躺平卡最多 `2` 张及次日 04:00 自动跨过放纵恢复正常模式、睡眠时段强制锁定全部非豁免应用与正常锁机自动完成/改期失败与 `35 分钟` 修改成本、冷静期、自律起兑门槛、配置修改成本公式、主动奖励兑换、本机永久保留历史统计 `FocusStore` 仅裁剪明细 `events`、`Abstinence` 戒断模型与 `spendTime()` 优先扣可用再扣储蓄罐）与开发测试调试接口集中在 `FocusState`，向下兼容持久化由 `FocusStore` 管理。更新的下载、SHA-256 校验与安装授权入口位于 `GitHubUpdates`、`UpdateProvider` 和 `UpdatePanel`。

界面联调可覆盖零余额/每日次数拦截切换模式、累计 5 日解锁放纵模式及 04:00 重置恢复、储蓄罐 2 小时上限提示、每日 15 分钟基础免费上限与 0 分钟默认受限应用行为、复选框多选应用且无包名展示、首页自律双模式无闪烁切换与启动直达全屏（正计时可停 / 倒计时不可停）、睡眠全锁与 35 分钟放弃消耗、定制弹窗与定制时间选择器、零余额/付费修改规则、奖励创建/编辑与主动消耗、待办/习惯冷静期、系统计步与兑换、UsageStats 校准、「设置 → 强力守护与系统 / 通用设置 / 调试模式」，以及直接点击「检查更新」验证最新正式版结果。现有 JVM 检查位于 `checks/`。

使用 JDK 17 或 21、Android SDK Platform 35 和 Build Tools 35.0.0。设置 `JAVA_HOME` 与 `ANDROID_SDK_ROOT` 后，运行 `sh ./build-local-apk.sh` 可直接生成预览 APK。
