# 简松时前端交接

应用包名是 `com.focusgarden.app`，版本以 `version.properties` 为准。代码采用 Java 17 和 Android 原生 View，最低 API 27。[README](README.md) 提供构建、规则验证和发布说明。[设计参照](design/README.md) 提供界面截图。

## 界面入口

| 界面 | 代码 |
| --- | --- |
| 首页（顶部支持主题换肤与液位显示的动态小猪储蓄罐、支持点击吸入/弹出 `vaultCardCollapsed` 动态上限储蓄卡、右上角签到、商店和设置入口、躺平模式紫色冻结卡、可用时长 / 躺平重置 HH:MM:SS 倒计时、靶心目的模式卡、其下方无折叠无兑换按钮的自律模式直达卡）、独立兑换页（顶部蓝色渐变时间兑换券与时间商店入口卡、金/蓝 `4dp` 3D 立体兑换按钮、持久化紧凑折叠卡片与 `全部展开/收起`、支持专属兑换动效的粒子飞券动画及完整双模式自律/待办/步数/早睡/戒断/应用入口）、统一品牌蓝渐变的治愈戒断主页（5 阶成长萌芽画布 `SproutStageView`、渐进阶段无需清零、条件式「预计节省」、冲动救急条、我的戒断与每日一句）、洞察页（顶部「自律统计及自定义分类时长分布」+ 7 日堆叠图与收支时间线）、带页面过渡动画的应用规则、首页右上角悬浮设置按钮、其余主页面随页头滚动的设置按钮和右侧滑入面板、全屏早睡锁机调度及轻/深/跟随系统动态主题 | `app/src/main/java/com/focusgarden/app/MainActivity.java`、`PiggyBankView.java`、`RedeemUi.java` |
| 全屏自律双模式计时（`正计时` 可停 / `倒计时` 不可停 + 学习应用直达）、首次修改免费提醒与付费修改确认（`confirmPaidChange()`）、精简后的「模式切换」（加大型「切换 / 当前 / 今日已切换」按钮，放纵/躺平提供持有张数与前往时间商店入口）与每日 30 分调整上限（`showRateSettings()`）、通用设置含外观与夜间模式 3 档切换（`showGeneralSettings()`）、`showRewardsSheet()` 转发至时间商店、强力守护与系统聚合面板（`showKeepAliveSheet()`）、「反馈与交流」（`showFeedbackSheet()`，联系作者含 GitHub / Gitee / 作者 B 站主页与邮箱）、开发阶段调试面板（`showDebugSheet()`）、设置二级页关闭后返回设置、`pickClock()`、待办、早睡挑战（`showEarlySleep()`）、戒断编辑器（`showAbstinenceEditor()`）、步数与复选框多选应用选择器（隐藏包名） | 同级 `FocusSheets.java` |
| 时间商店「时光杂货铺 · LITTLE TIME STORE」（奶油白浅色 `#FAF7F1`/`#FFFCF6` 与暖夜灯深色双主题、雾蓝条纹波浪遮阳棚、固定顶栏防滚动遮挡、平衡栏与背包角标、分类胶囊导航 `全部 / 道具 / 外观 / 心愿`、原木隔板开放式货架、`打卡保护卡`/`储蓄扩容卡`/20h `放纵卡` 分期兑换带积攒进度条与「继续积攒」按钮/`星空小猪` 等主题与液体/动效收藏、迁移后的 3 天自律宝箱与 2→1 躺平合成区、淡雅纸质壁挂心愿卡片与自定义编辑器、礼物飞入背包动效）及「我的背包仓库」（`全部 / 道具 / 卡牌 / 外观 / 心愿券` 实际使用、装备切换与心愿券 `核销使用`） | 同级 `TimeShopUi.java`、`ShopCatalog.java` |
| 全屏戒断详情与冲动救急（平滑进出过渡动画、`总览 / 趋势 / 成就与奖励` 3 标签页去重设计、60s 呼吸引导 + 一键开启 5 分钟自律锁机、复发诱因标签与月度角标红叉日记日历 + 同日撤回恢复、多轮趋势柱状图与诱因归因、渐进阶段推进与 2×5 里程碑勋章墙）、时间兑换券动效/立体按键组件（`TicketCardLayout` / `Pressable3DButton` / `RedeemCoinView` / `AccordionHandle` / `RedeemAnimOverlay`）、游戏化放纵/躺平卡牌组件与合成/开箱层（`MilestoneTrackView` 3 节点成就轨与 `TreasureChestView` 宝箱、`CraftingBenchView` 2→1 可视化合成台、`ItemCardView`、开箱展示 `showChestRewardModal()` 与融合展示 `showCraftFusionModal()`）及早睡挑战全屏不可跳转夜览锁机弹窗（顶栏语录、7 段数字时钟、护眼暗度、35 分钟付费「修改延后 / 主动退出」） | 同级 `AbstinenceUi.java`、`RedeemUi.java`、`CardCraftUi.java`、`SleepLockUi.java` |
| 动态浅色 / 深色设计系统与系统栏适配（`applyTheme()` / `isDark()` / `applySystemBars()`）、颜色、按钮、定制卡片弹窗（`cardDialog` / `infoDialog` / `confirmDialog` / `loadingDialog`）、单次测量限高弹窗（`MaxHeightScrollView`）、支持左上角动作槽的底部弹窗（`bottomSheet()`）、轻量规则提示按钮（`infoIcon` / `titleWithInfo`）、统一防抖触感通道（`HapticKind` / `tapHaptic()`）、开关、进度条与 24h 心跳条（`HeartbeatStripView`） | 同级 `GardenUi.java`、`GardenIcon.java` |
| 自律与目的模式的全屏计时布局、内置全屏素材/自选背景选择器（含早睡锁机背景与睡意提示）及 7 日堆叠统计柱（自律与专注合并展示） | 同级 `TimerScene.java`、`TimerAppearance.java`、`InsightChart.java` |
| 保活与防关闭异步快照检测（`StatusSnapshot` / `refreshStatusAsync()`）、权限跳转、UsageStats 异步（04:00 逻辑日起点）与手动校准、自选壁纸持久化及服务同步 | 同级 `KeepAliveManager.java` |
| 后台常驻保活前台服务（心跳记录、后台计步监听；维持 `24dp`/`0.16` 视觉透明但窗口 `alpha=1.0` + `48dp` 触控区及 Android 14+ `PendingIntent` BAL 兼容的可拖动边缘吸附悬浮球） | 同级 `KeepAliveService.java`、`KeepAliveWallpaperService.java`、`FocusDeviceAdmin.java` |
| 语录加载、Gitee 优先与 GitHub 回退的更新检测、下载及校验 | 同级 `QuoteBank.java`、`GitHubUpdates.java` |
| 前台应用窗口识别（过滤小尺寸悬浮球/侧边栏与 OEM 安全弹窗、目的模式期间优先保底已授权应用窗口防误拦）、早睡全屏锁机全应用及桌面持续拦截（「返回早睡锁机」）、多应用目的模式与桌面全非目标应用拦截、可拖拽半透明灰色悬浮计时药丸、躺平模式全免限、专注防关闭系统设置保护与顶部时间感知条 | 同级 `FocusService.java` |
| 拦截素材库（4 张系统自带全屏 JPG + 5 张系统自带方图含 2 张动态 GIF，方图 55% / 30sp / 不可编辑可切换启用与日常/晚睡双态预览）与自定义界面轮换 | 同级 `BlockAppearanceSettings.java`、`BlockScreen.java`、`BlockAssets.java`、`BlockImage.java` |
| 目的模式多应用复选框选择（隐藏包名）、全屏倒计时、目的应用直达与暂停操作 | 同级 `SoberModeUi.java` |
| 启动更新检查、手动检查更新及下载安装弹窗 | 同级 `UpdatePanel.java` |
| 桌面小组件与快捷设置磁贴（自律模式控制，禁止提前停倒计时） | 同级 `FocusWidget.java`、`FocusTile.java` 与 `app/src/main/res/layout/focus_widget.xml` |

矢量图标（含 `ic_gift.xml`、`ic_target.xml`、`ic_feedback.xml`、`ic_mail.xml`、`ic_nav_redeem.xml`、`ic_nav_abstain.xml` 与 `bg_sleep_stars.xml` 渐变夜空）、内置全屏/方图拦截图片与小猪/社交图标（`res/drawable-nodpi/block_*.jpg`）、动态方图 GIF 与语录库（`res/raw/block_square_*.gif`、`res/raw/quotes.json`）、壁纸/设备管理器配置（`res/xml/`）及其他资源均位于 `app/src/main/res/`。

## 联调

时间经济体系（每日凌晨 `04:00` 重置逻辑日和今日可用时长、所有受限应用共用今日可用时长、储蓄罐初始 `15 分钟` 上限不清零，可通过时间商店购买 `储蓄扩容卡` 60m/张永久每次 `+15 分钟` 扩容至上限 `120 分钟 / 2 小时` 并向下兼容自动迁移老版本存量、`vaultCardCollapsed` 折叠态、6 种时间获取模式 `松弛 1:1 / 简单 2:1 / 适中 3:1 / 学霸 4:1 / 放纵 1:2 / 躺平全免限`、兑换券多源汇总 `redeemSummary()` 与原子幂等一键领取 `claimAllRedeemable()`、持久化折叠状态 `expandedRedeemCards`、躺平生效当日冻结赚取与储蓄罐存取且时间商店所有消费 `redeemReward()` / 道具 / 分期 / 外观仅消耗 `vaultMs` 储蓄时间并展示紫色卡与重置倒计时、常规模式每日 1 次 `30 分钟` 切换限制、每日获取上限调整成本 `30 分钟`、首次修改设置免费提醒 `isFirstConfigModifyFree` / `freeConfigUsedKeys`、放纵卡双轨获取：正常模式累计 `3 天` 自律解锁待领宝箱 `claimPendingIndulgentReward()` 或在时间商店通过 `contributeIndulgentInstallment()` 20h/`1200 分钟` 分期累计兑换，最多囤 `5` 张、2 张放纵卡经时间商店内可视化合成台兑 1 张躺平卡（最多 `2` 张，含跳过动效与系统减少动态效果支持）及次日 04:00 自动跨过放纵恢复正常模式、`打卡保护卡`（10m/张，最多囤 10 张，用于早睡或连续自律补护且不刷取 3 天宝箱进度）、永久外观收藏 `equippedPiggySkin` / `equippedLiquidTheme` / `equippedRedeemEffect`、心愿兑换 `WishVoucher` 生成与背包核销 `useWishVoucher()`、睡眠时段强制锁定全部非豁免应用含桌面与 `exitActiveSleepLock` / `delayActiveSleepLock` `35 分钟` 解锁成本及次日 `claimPendingEarlySleep` 手动领奖、冷静期、自律起兑门槛、配置修改成本公式、本机永久保留历史统计 `FocusStore` 仅裁剪明细 `events`、`Abstinence` 戒断模型与防通胀规则〔`habitDailyCapMinutes` 每日戒断上限硬约束、24h 防回溯护盾 `rewardGuardUntilMs`、目标缩减/提奖 `15m` 改配消耗、`advanceAbstinenceStage()` 不清零渐进升阶、精确 `totalEarnedMinutes`、`AbstinenceRun` 与诱因标签、关联拦截包名 `linkedPackages`〕与 `spendTime()` 优先扣可用再扣储蓄罐）与开发测试调试接口集中在 `FocusState`，向下兼容持久化由 `FocusStore` 管理。更新的下载、SHA-256 校验与安装授权入口位于 `GitHubUpdates`、`UpdateProvider` 和 `UpdatePanel`。

界面联调可覆盖浅色 `#F3F5F9` / 深色 / 跟随系统三档主题即时切换、`values-night` 冷启动背景、时间商店「时光杂货铺」浅/深色开放货架、购买 `星空小猪` 等外观并即时换肤、使用 `储蓄扩容卡` 将储蓄罐上限从 `15 分钟` 逐步扩至 `120 分钟`、放纵卡 20h 分期投入与 3 天宝箱 + 2→1 合成、心愿创建/编辑/兑换并在「背包 → 心愿券」核销使用、兑换时间兑换券与一键兑换 3D 金按钮 / 卡片 3D 蓝按钮及装备后的专属粒子飞券动效、6 张紧凑折叠卡片与持久化 `全部展开/收起`、精简版「时间获取模式与上限」、首次免费修改提醒与次改 30 分钟扣除、零余额/每日次数拦截切换模式、躺平紫色储蓄卡冻结与次日重置倒计时、早睡全屏夜览锁机与跳转桌面持续拦截及次日手动领奖、续步风格戒断页（条件节省与复发日记红叉日历）、新旧受限应用共用可用时长及全局时间感知条、复选框多选应用且无包名展示、首页/兑换自律双模式无闪烁切换与启动直达全屏（正计时可停 / 倒计时不可停）、定制弹窗与定制时间选择器、待办冷静期、系统计步与兑换、UsageStats 校准、「设置 → 强力守护与系统 / 通用设置 / 调试模式（含 20 分钟多源奖励模拟）」，以及直接点击「检查更新」验证最新正式版结果。现有 JVM 检查位于 `checks/`。

使用 JDK 17 或 21、Android SDK Platform 35 和 Build Tools 35.0.0。设置 `JAVA_HOME` 与 `ANDROID_SDK_ROOT` 后，运行 `sh ./build-local-apk.sh` 可直接生成预览 APK。
