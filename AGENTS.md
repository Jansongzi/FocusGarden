# 留白专注前端交接

当前版本为 `1.4.0`（`versionCode=5`）。应用包名是 `com.focusgarden.app`，代码采用 Java 17 和 Android 原生 View，最低 API 27。项目根目录的 [README](README.md) 提供构建、规则验证和发布说明。界面截图及参照见 [设计参照](design/README.md)。

## 界面入口

| 界面 | 代码 |
| --- | --- |
| 首页（储蓄罐、可用时长、清醒模式、奖励兑换快捷卡、离线锁机与兑换卡片、24h 保活入口）、应用规则、习惯、洞察（统计与收支时间线）、滑动/触感切页与底部导航 | `app/src/main/java/com/focusgarden/app/MainActivity.java` |
| 全屏三模式计时、付费修改确认（`confirmPaidChange()`）、奖励兑换商店（`showRewardsSheet()`）、强力防关闭与保活面板、可用时长明细、待办、早睡、清醒模式、步数、应用选择器与异步设置面板 | 同级 `FocusSheets.java` |
| 设计系统、颜色、按钮、单次测量限高弹窗（`MaxHeightScrollView`）、单一通道防抖触感（`HapticKind`）、开关、进度条与 24h 心跳条（`HeartbeatStripView`） | 同级 `GardenUi.java`、`GardenIcon.java` |
| 倒计时/正计时表盘与 7 日堆叠统计柱（专注与离线合并展示） | 同级 `TimerDial.java`、`InsightChart.java` |
| 保活与防关闭异步快照检测（`StatusSnapshot` / `refreshStatusAsync()`）、权限跳转、UsageStats 异步与手动校准、自选壁纸持久化及服务同步 | 同级 `KeepAliveManager.java` |
| 后台常驻保活前台服务（心跳记录、后台计步监听与可拖拽边缘吸附悬浮球） | 同级 `KeepAliveService.java`、`KeepAliveWallpaperService.java`、`FocusDeviceAdmin.java` |
| 语录加载与版本元数据缓存 | 同级 `QuoteBank.java`、`GitHubUpdates.java` |
| 应用拦截页、专注期间系统设置保护拦截及顶部时间感知条 | 同级 `FocusService.java` 的 `showOverlay()`、`showSettingsGuardOverlay()`、`updateAwarenessBar()` |
| GitHub 更新弹窗 | 同级 `UpdatePanel.java` |
| 桌面小组件与快捷设置磁贴（一键启停离线锁机） | 同级 `FocusWidget.java`、`FocusTile.java` 与 `app/src/main/res/layout/focus_widget.xml` |

矢量图标（含 `ic_gift.xml`）与小芽状态插画（`res/drawable/`）、池塘横幅（`res/drawable-nodpi/pond_at_dusk.png`）、语录库（`res/raw/quotes.json`）、壁纸/设备管理器配置（`res/xml/`）及其他资源均位于 `app/src/main/res/`。

## 联调

时间经济体系（获取上限、冷静期、离线起兑门槛、配置修改成本公式、主动奖励兑换、`spendTime()` 优先扣可用再扣储蓄罐）与 24h 心跳记录集中在 `FocusState`，向下兼容持久化由 `FocusStore` 管理。GitHub 更新的下载、校验与安装授权入口分别位于 `GitHubUpdates`、`UpdateProvider` 和 `UpdatePanel`。

界面联调可覆盖零余额修改规则拦截、付费修改规则扣时、奖励创建/编辑与主动消耗、待办/习惯冷静期、手动步数日上限、早睡时间窗口校验、离线锁机/倒计时/番茄钟、UsageStats 校准、设置页零卡顿打开、强力防关闭与悬浮球窗口、统计日期切换以及更新检查流程。现有 JVM 检查位于 `checks/`。

使用 JDK 17 或 21、Android SDK Platform 35 和 Build Tools 35.0.0。设置 `JAVA_HOME` 与 `ANDROID_SDK_ROOT` 后，运行 `sh ./build-local-apk.sh` 可直接生成预览 APK。
