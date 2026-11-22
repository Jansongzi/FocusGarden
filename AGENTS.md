# 留白专注前端交接

当前版本为 1.2.0。应用包名是 `com.focusgarden.app`，代码采用 Java 17 和 Android 原生 View，最低 API 27。项目根目录的 [README](README.md) 提供构建、规则验证和发布说明。模拟器截图见 [设计参照](design/README.md)。

## 界面入口

| 界面 | 代码 |
| --- | --- |
| 首页、应用规则、习惯、洞察（统计与时间线）、底部导航 | `app/src/main/java/com/focusgarden/app/MainActivity.java` |
| 全屏专注计时、闪卡、待办、早睡、清醒模式、步数、应用选择器与设置面板 | 同级 `FocusSheets.java` |
| 设计系统、颜色、按钮、开关、进度条、BottomSheet 与圆角容器 | 同级 `GardenUi.java`、`GardenIcon.java` |
| 倒计时表盘与每日统计柱 | 同级 `TimerDial.java`、`InsightChart.java` |
| 语录与词库加载（CET-4 / CET-6 / 高中词汇） | 同级 `QuoteBank.java` |
| 应用拦截页与顶部时间感知条 | 同级 `FocusService.java` 的 `showOverlay()`、`updateAwarenessBar()` |
| GitHub 更新弹窗 | 同级 `UpdatePanel.java` |
| 桌面小组件 | 同级 `FocusWidget.java` 和 `app/src/main/res/layout/focus_widget.xml` |

矢量图标和种子角色在 `res/drawable/`，植物插画在 `res/drawable-nodpi/pond_at_dusk.png`，词库与语录在 `res/raw/`。

## 联调

计时、兑换和日规则集中在 `FocusState`，持久化由 `FocusStore` 管理。前端改动可沿用现有回调并保留旧数据兼容性。GitHub 更新的下载、校验与安装授权入口分别位于 `GitHubUpdates`、`UpdateProvider` 和 `UpdatePanel`。

界面联调可以覆盖倒计时与番茄钟切换、暂停续计、学习和限制规则、编辑后保留打卡记录、统计日期切换、拦截页返回桌面，以及默认和自定义更新源。现有 JVM 检查位于 `checks/`。

使用 JDK 17 或 21、Android SDK Platform 35 和 Build Tools 35.0.0。设置 `JAVA_HOME` 与 `ANDROID_SDK_ROOT` 后，运行 `sh ./build-local-apk.sh` 可直接生成预览 APK。覆盖安装要沿用原签名，新签名的测试包可安装到独立模拟器。
