# 留白专注前端交接

应用包名是 `com.focusgarden.app`，版本以 `version.properties` 为准。代码采用 Java 17 和 Android 原生 View，最低 API 27。[README](README.md) 提供构建、规则验证和发布说明。[设计参照](design/README.md) 提供界面截图。

## 界面入口

| 界面 | 代码 |
| --- | --- |
| 首页（顶部肉色动态小猪储蓄罐支持点击吸入/弹出 `vaultCardCollapsed` 2h 储蓄卡、右上角标签 ⓘ 查看规则与自律指数及躺平模式紫色冻结卡、可用时长 / 躺平重置 HH:MM:SS 倒计时、靶心目的模式卡、其下方无折叠无兑换按钮的自律模式直达卡、奖励兑换快捷卡）、独立兑换页（顶部浅蓝时间兑换券、金/蓝 `4dp` 3D 立体兑换按钮、持久化紧凑折叠卡片与 `全部展开/收起`、粒子飞券动效及完整双模式自律/待办/步数/早睡/戒断/应用入口）、续步风格戒断主页（深松绿环形进度 Hero、条件式「预计节省」、我的戒断与每日一句）、洞察页（顶部「自律统计 · 本机永久保留」+ 7 日堆叠图与收支时间线）、应用规则、全 4 页共享右上角悬浮设置按钮、全屏早睡锁机调度及轻/深/跟随系统动态主题 | `app/src/main/java/com/focusgarden/app/MainActivity.java`、`PiggyBankView.java`、`RedeemUi.java` |
| 全屏自律双模式计时（`正计时` 可停 / `倒计时` 不可停 + 学习应用直达）、首次修改免费提醒与付费修改确认（`confirmPaidChange()`）、6 种时间获取模式（加大型「切换 / 当前」按钮）与每日 30 分调整上限（`showRateSettings()`）、通用设置含外观与夜间模式 3 档切换（`showGeneralSettings()`）、奖励列表（躺平仅扣储蓄）与左上角 `+` 新增二级弹窗、强力守护与系统聚合面板（`showKeepAliveSheet()`）、开源与反馈面板（`showFeedbackSheet()`，位于设置倒数第二项）、开发阶段调试面板（`showDebugSheet()`）、设置二级页关闭后返回设置、`pickClock()`、待办、早睡挑战（`showEarlySleep()`）、戒断编辑器（`showAbstinenceEditor()`）、步数与复选框多选应用选择器（隐藏包名） | 同级 `FocusSheets.java` |
| 续步风格戒断全屏详情（`总览 / 趋势 / 奖励`、记录复发、想改变的理由、位于里程碑之前的月度「复发日记」红叉日历与日期日记弹窗、2×5 里程碑网格）、时间兑换券动效/立体按键组件（`TicketCardLayout` / `Pressable3DButton` / `RedeemCoinView` / `AccordionHandle` / `RedeemAnimOverlay`）、游戏化放纵/躺平卡牌组件与合成/开箱层（`MilestoneTrackView` 3 节点成就轨与 `TreasureChestView` 宝箱、`CraftingBenchView` 2→1 可视化合成台、`ItemCardView`、开箱展示 `showChestRewardModal()` 与融合展示 `showCraftFusionModal()`）及早睡挑战全屏不可跳转夜览锁机弹窗（顶栏语录、7 段数字时钟、护眼暗度、35 分钟付费「修改延后 / 主动退出」） | 同级 `AbstinenceUi.java`、`RedeemUi.java`、`CardCraftUi.java`、`SleepLockUi.java` |
| 动态浅色 / 深色设计系统与系统栏适配（`applyTheme()` / `isDark()` / `applySystemBars()`）、颜色、按钮、定制卡片弹窗（`cardDialog` / `infoDialog` / `confirmDialog` / `loadingDialog`）、单次测量限高弹窗（`MaxHeightScrollView`）、支持左上角动作槽的底部弹窗（`bottomSheet()`）、轻量规则提示按钮（`infoIcon` / `titleWithInfo`）、统一防抖触感通道（`HapticKind` / `tapHaptic()`）、开关、进度条与 24h 心跳条（`HeartbeatStripView`） | 同级 `GardenUi.java`、`GardenIcon.java` |
| 倒计时/正计时表盘与 7 日堆叠统计柱（自律与专注合并展示） | 同级 `TimerDial.java`、`InsightChart.java` |
| 保活与防关闭异步快照检测（`StatusSnapshot` / `refreshStatusAsync()`）、权限跳转、UsageStats 异步（04:00 逻辑日起点）与手动校准、自选壁纸持久化及服务同步 | 同级 `KeepAliveManager.java` |
| 后台常驻保活前台服务（心跳记录、后台计步监听；维持 `24dp`/`0.16` 视觉透明但窗口 `alpha=1.0` + `48dp` 触控区及 Android 14+ `PendingIntent` BAL 兼容的可拖动边缘吸附悬浮球） | 同级 `KeepAliveService.java`、`KeepAliveWallpaperService.java`、`FocusDeviceAdmin.java` |
| 语录加载、Gitee 优先与 GitHub 回退的更新检测、下载及校验 | 同级 `QuoteBank.java`、`GitHubUpdates.java` |
| 前台应用窗口识别（过滤小尺寸悬浮球/侧边栏与 OEM 安全弹窗、目的模式期间优先保底已授权应用窗口防误拦）、早睡全屏锁机全应用及桌面持续拦截（「返回早睡锁机」）、多应用目的模式与桌面全非目标应用拦截、可拖拽半透明灰色悬浮计时药丸、躺平模式全免限、专注防关闭系统设置保护与顶部时间感知条 | 同级 `FocusService.java` |
| 全屏图片与方图拦截界面、字号与图片尺寸、居中小胶囊主按钮、预览、多套界面顺序轮换 | 同级 `BlockAppearanceSettings.java`、`BlockScreen.java`、`BlockImage.java` |
| 目的模式多应用复选框选择（隐藏包名）、全屏倒计时、目的应用直达与暂停操作 | 同级 `SoberModeUi.java` |
| 启动更新检查、手动检查更新及下载安装弹窗 | 同级 `UpdatePanel.java` |
| 桌面小组件与快捷设置磁贴（自律模式控制，禁止提前停倒计时） | 同级 `FocusWidget.java`、`FocusTile.java` 与 `app/src/main/res/layout/focus_widget.xml` |

矢量图标（含 `ic_gift.xml`、`ic_target.xml`、`ic_feedback.xml`、`ic_mail.xml`、`ic_nav_redeem.xml`、`ic_nav_abstain.xml`）与小芽状态插画（`res/drawable/`）、池塘横幅（`res/drawable-nodpi/pond_at_dusk.png`）、语录库（`res/raw/quotes.json`）、壁纸/设备管理器配置（`res/xml/`）及其他资源均位于 `app/src/main/res/`。

## 联调

时间经济体系（每日凌晨 `04:00` 重置逻辑日/每日可用与 15m 基础免费已用额度、全部受限应用每日 `15 分钟` 共享基础免费上限、未自定义免费额度默认 `0 分钟` 完全由可用时长决定、储蓄罐 `2 小时` 上限不清零与 `vaultCardCollapsed` 折叠态、6 种时间获取模式 `松弛 1:1 / 简单 2:1 / 适中 3:1 / 学霸 4:1 / 放纵 1:2 / 躺平全免限`、兑换券多源汇总 `redeemSummary()` 与原子幂等一键领取 `claimAllRedeemable()`、持久化折叠状态 `expandedRedeemCards`、躺平生效当日冻结赚取与储蓄罐存取且主动兑换奖励 `redeemReward()` / `spendVaultOnly()` 仅消耗储蓄时间并展示紫色卡与重置倒计时、常规模式每日 1 次 `30 分钟` 切换限制、每日获取上限调整成本 `30 分钟`、首次修改设置免费提醒 `isFirstConfigModifyFree` / `freeConfigUsedKeys`、正常模式累计 `3 天` 自律解锁待领放纵卡宝箱（`pendingIndulgentRewards` 持久化保存，用户经 `claimPendingIndulgentReward()` 主动开箱领取，最多囤 `5` 张）、2 张放纵卡经可视化合成台兑 1 张躺平卡（最多 `2` 张，含跳过动效与系统减少动态效果支持）及次日 04:00 自动跨过放纵恢复正常模式、睡眠时段强制锁定全部非豁免应用含桌面与 `exitActiveSleepLock` / `delayActiveSleepLock` `35 分钟` 解锁成本及次日 `claimPendingEarlySleep` 手动领奖、冷静期、自律起兑门槛、配置修改成本公式、主动奖励兑换、本机永久保留历史统计 `FocusStore` 仅裁剪明细 `events`、`Abstinence` 戒断模型含 `relapseDates` / `diaryNotes` / `reason` / `dailySavingYuan` / `iconIndex` 与 `spendTime()` 优先扣可用再扣储蓄罐）与开发测试调试接口集中在 `FocusState`，向下兼容持久化由 `FocusStore` 管理。更新的下载、SHA-256 校验与安装授权入口位于 `GitHubUpdates`、`UpdateProvider` 和 `UpdatePanel`。

界面联调可覆盖浅色 `#F3F5F9` / 深色 / 跟随系统三档主题即时切换、`values-night` 冷启动背景、兑换时间兑换券与一键兑换 3D 金按钮 / 卡片 3D 蓝按钮及粒子飞券动效、6 张紧凑折叠卡片与持久化 `全部展开/收起`、首次免费修改提醒与次改 30 分钟扣除、零余额/每日次数拦截切换模式、躺平紫色储蓄卡冻结与次日重置倒计时、早睡全屏夜览锁机与跳转桌面持续拦截及次日手动领奖、续步风格戒断页（条件节省与复发日记红叉日历）、储蓄罐 2 小时上限提示、每日 15 分钟基础免费上限与 0 分钟默认受限应用行为、复选框多选应用且无包名展示、首页/兑换自律双模式无闪烁切换与启动直达全屏（正计时可停 / 倒计时不可停）、定制弹窗与定制时间选择器、奖励创建/编辑与主动消耗、待办冷静期、系统计步与兑换、UsageStats 校准、「设置 → 强力守护与系统 / 通用设置 / 调试模式（含 20 分钟多源奖励模拟）」，以及直接点击「检查更新」验证最新正式版结果。现有 JVM 检查位于 `checks/`。

使用 JDK 17 或 21、Android SDK Platform 35 和 Build Tools 35.0.0。设置 `JAVA_HOME` 与 `ANDROID_SDK_ROOT` 后，运行 `sh ./build-local-apk.sh` 可直接生成预览 APK。
