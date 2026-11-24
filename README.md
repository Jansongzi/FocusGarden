# 留白专注

Android 本地专注工具，支持 Android 8.1（API 27）及更高版本。产品定位聚焦**自律模式（正计时/倒计时）+ 正向行为克制兑换 + 主动奖励消耗**。构建版本以 `version.properties` 为准。

前端交接从 [界面入口与联调说明](AGENTS.md) 开始。[界面参照](design/README.md) 提供 Android 15 模拟器界面截图。

## 核心机制

1. **六档时间获取模式与卡片进阶体制**：
   - **六种模式**：提供**松弛模式（1:1）**、**简单模式（2:1）**、**适中模式（3:1，默认）**、**学霸模式（4:1）**，以及卡片解锁的**放纵模式（1:2）**和**躺平模式（全天免限）**；「时间获取模式与上限」页的「切换 / 当前」按钮加大加高。
   - **卡片兑换、躺平冻结果实与回退机制**：四档正常模式之间**每天限切换 1 次**且常规消耗 **30 分钟**自由时间；**正常模式**下累计达成 **3 个自律日**（当日评分 ≥ 80% 且有正向自律记录、未破早睡戒）自动获赠 **1 张放纵卡**（最多囤积 **5 张**）；每 **2 张放纵卡**可兑换 **1 张躺平卡**（最多囤积 **2 张**）。使用放纵卡（1:2）后于次日凌晨 04:00 自动恢复正常模式；开启**躺平模式**后**当日无法再兑换或领取任何时间**、首页时间储蓄罐保持原时长冻结（不可存取）且卡片变为**紫色渐变卡**、可用时长实时显示**距离明日凌晨 04:00 重置倒计时（HH:MM:SS）**，次日 04:00 跨过放纵自动恢复上一正常模式。
   - **独立「兑换」标签页与简洁折叠卡片**：「兑换」标签页 6 项卡片（应用、自律模式、早睡挑战、待办、戒断挑战、步数）默认折叠为单行摘要卡，点击展开明细与操作控件，再点收起；自律双模式（正计时可随时停、倒计时结束前不可停）确认后进入全屏计时界面并锁定除已配置「正向学习应用」外的全部应用。
   - **每日 15 分钟基础免费上限**：全部受限应用每日共享最多 `15 分钟` 基础免费额度；通过搜索多选新增受限应用时（不显示包名），默认每日免费 `0 分钟`（完全由可用时长决定）。
   - **续步风格戒断与复发日历日记**：独立「戒断」标签页采用深松绿 Hero 环形进度卡与「我的戒断」列表，「预计节省」仅在配置每日节省金额后动态显示；点击卡片或「详情」进入全屏详情页（`总览 / 趋势 / 奖励`），依次提供「记录复发」、「想改变的理由」、位于里程碑之前的**月度「复发日记」日历**（复发日期带红色划叉 `✕` 标识，点击任意 ≤ 今日的日期可补记复发与写日记）以及 2×5「里程碑」网格；新建待办后需经历 `10 分钟` 冷静期方可领取，每日待办/戒断/步数兑换受各自每日上限约束。
   - **步数兑换限制**：通过系统计步传感器获取步数，默认 `2000 步` 兑换 `5 分钟`，每日步数兑换上限为 `30 分钟`。
   - **全屏早睡锁机与次日手动领奖**：开启「睡眠时段强制锁定」后到点进入极光夜空全屏锁机界面（[SleepLockUi](app/src/main/java/com/focusgarden/app/SleepLockUi.java)），锁定期间跳转桌面或其他应用会持续触发 `FocusService` 全屏拦截（「返回早睡锁机」）；在锁机界面可通过「主动退出 (35分)」或「修改延后 (35分)」付费解锁或改期，操作将判本轮早睡挑战失败且次日不可领奖；完整经历整夜锁机醒来后，早睡奖励（默认 `10 分钟`）改为**次日手动点击领取**（不再自动发放）。
   - **浅色 / 深色 / 跟随系统夜间模式与全页悬浮设置**：「设置 → 通用设置」新增「外观与夜间模式」（**跟随系统 / 浅色 / 深色**），动态重绘全部页面、弹窗、自定义视图及系统状态栏/导航栏并配置 `values-night` 冷启动深色背景；所有 4 个主标签页（`首页 / 兑换 / 戒断 / 洞察`）均统一固定右上角悬浮设置按钮与顶部渐隐遮罩；`KeepAliveService` 悬浮球保持 `24dp` / `0.16` 透明度与可拖拽边缘吸附；每日 **04:00** 重置可用时长与限额，储蓄罐最长累计 **2 小时（120 分钟）** 不清零。
   - **目的模式（原清醒模式）**：更换靶心矢量标识并去除状态黑点前缀，支持多选目的应用（不显示包名）；过滤系统/OEM 小尺寸悬浮窗（含本应用可拖动悬浮球）与权限弹窗，修复受限应用与悬浮窗并存时进入目的应用被误拦的冲突，离开目的应用（含返回桌面或打开其他应用）持续触发全屏拦截，支持半透明灰色悬浮计时药丸与暂停退回未用时长。
   - **永久保留自律统计**：「洞察 → 数据统计」顶部新增「自律统计 · 本机永久保留」看板，长期记录累计自律总时长、累计完成次数、本月自律时长与连续/历史最高达标天数，不随每日重置或 90 天时间线明细清理而删除。
2. **凌晨 04:00 重置点与 2 小时储蓄罐上限**：
   - 每日 **04:00** 作为逻辑日重置边界：清零上一周期剩余可用时长、每日限额与每日基础免费已用额度、刷新每日模式切换次数，并自动将到期的「放纵模式 / 躺平模式」恢复为上一正常模式；存入**时间储蓄罐**的时长保留不清零，储蓄罐最大累计容量为 **2 小时（120 分钟）**。
3. **首次修改免费提醒与配置修改消耗**：
   - **首次免费提醒**：除夜间锁机期间退出/延后外，首次修改各类设置（如切换模式、调整每日上限、调节早睡配置/奖励、修改步数或应用规则）均为 **0 分钟（首次免费，仅提醒）**，后续再次修改时按规则扣除可支配时间；**每日获取上限修改成本提升至 `30 分钟`**（首改免费后每次 `30 分钟`），优先扣除今日可用，不足从储蓄罐扣除。
   - 「奖励兑换」打开即直达「我的奖励列表」，点按左上角 `+` 可进入「添加自定义奖励」二级弹窗并灵活管理奖励项目。
4. **设置聚合、统一弹窗与克制触感**：
   - 原后台保活卡与通用体验卡均收口至全页右上角悬浮「设置」：**强力守护与系统**（聚合后台保活权限、24h 心跳条、无障碍监测、通知权限、可拖动悬浮球、隐藏最近任务、自启动、设备管理器防卸载、自律防关闭、桌面小组件与快捷设置磁贴）、**通用设置**（震动反馈开关、时间感知条、应用拦截页风格、储蓄罐步长、管理兑换卡片）、**时间获取模式与上限**、**奖励兑换商店**、**调试模式**（开发测试阶段用于自由增减时间、发放放纵卡/躺平卡与强制解锁，后续版本移除）与**检查更新**。
   - 全应用弹窗与作息时间选择器均采用统一圆角卡片弹窗与底部表单设计；切页手势与交互控件采用统一防抖震动反馈通道。
   - 底部弹层约占屏幕高度的 80%。可向下拖动顶部把手、点按遮罩或用系统返回关闭。
   - 「设置 → 通用设置 → 自定义拦截提示」支持保存多套界面。每套可选全屏图片或正方形大图，调整缩放、字号和文字，并预览。启用「轮换显示」后，每次新拦截按列表顺序切换一套界面。

拦截服务通过 Android 无障碍接口确认获得焦点的应用窗口，只读取窗口或事件所属应用的包名。拦截图片与设置保存在本机。

部分 Android 版本会限制侧载应用启用无障碍服务。如果系统显示「受限设置」，请从留白专注的系统应用详情按提示确认，再返回无障碍页面开启。[Android 受限设置说明](https://support.google.com/android/answer/12623953)

## 软件更新

1. 打开应用时会在后台检查 GitHub 正式版本，发现更新后弹出安装包窗口。「设置 → 检查更新」可手动检查。
2. 确认发现新版本后，应用会校验字节数、SHA-256 摘要、应用包名、签名证书与版本号；首次安装需在系统设置允许留白专注请求安装后即可覆盖升级并保留数据。

发布新版时，在 `version.properties` 同时提升 `versionName` 和 `versionCode`，上传 APK 到正式 Release（如 `v1.5.6`），并使用同一签名密钥签名。

## 构建

用 JDK 17 或 21、Android SDK Platform 35 和 Build Tools 35.0.0，在项目目录执行：

```sh
./gradlew :app:assembleDebug
```

构建结果位于 `app/build/outputs/apk/debug/app-debug.apk`。也可直接用 Android Studio 打开本目录。

只安装了 SDK 时，可设好 `ANDROID_SDK_ROOT` 和 `JAVA_HOME` 后运行
`sh ./build-local-apk.sh`。它直接调用 SDK 工具，输出
`app/build/outputs/apk/local/focus-garden.apk`。本项目发布的安装包使用该路径构建并用固定本地测试密钥签名，可直接覆盖升级并保留数据。

## 验证规则

```sh
classes="$(mktemp -d)"
javac --release 17 -cp "$ANDROID_SDK_ROOT/platforms/android-35/android.jar" -d "$classes" \
  app/src/main/java/com/focusgarden/app/FocusState.java \
  app/src/main/java/com/focusgarden/app/UpdateRules.java \
  app/src/main/java/com/focusgarden/app/GitHubUpdates.java \
  checks/FocusStateCheck.java checks/UpdateRulesCheck.java
java -cp "$classes:$ANDROID_SDK_ROOT/platforms/android-35/android.jar" com.focusgarden.app.FocusStateCheck
java -cp "$classes:$ANDROID_SDK_ROOT/platforms/android-35/android.jar" com.focusgarden.app.UpdateRulesCheck
```

规则检查覆盖兑换与每日重置、放纵卡/躺平卡与躺平模式冻结及重置倒计时、早睡锁机次日手动领奖与 35 分钟解锁、首次免费修改与 30 分钟上限调整、戒断复发日记与里程碑、永久自律统计、系统计步兑换、目的模式的目标应用限制与退时、其他时间收支、UsageStats 校准、心跳记录，以及 Release 网页资产与摘要解析。

## 插画来源

首页与拦截面板使用内置原创插画 `app/src/main/res/drawable-nodpi/pond_at_dusk.png`，小芽吉祥物与状态图标为本项目的 Android 矢量资源。

> Use case: illustration-story. Create a single original editorial illustration for the full-bleed landscape header of a Chinese mobile focus and digital wellbeing app named Focus Garden. Premium restrained contemporary Japanese botanical editorial style, tactile layered cut-paper with fine silkscreen grain, an intimate quiet pond at dusk, broad rounded lily leaves, a few softly lit fern tips and one very small abstract amber firefly. Composition: wide 3:2 landscape, concentrated distinctive artwork at far right and right lower corner; the left 52% is pure dark evergreen negative space with faint grain and no objects so live white app title fits with good contrast. Deep pine and midnight teal palette (#10352C to #173B31), sage foliage, muted pale chartreuse and amber accents. Elegant fundamentally flat lighting with gentle depth. No lettering, no text, no icons, no borders, no device mockup, no person, no stone mascot, no stock collage. The image is an actual artwork asset, not a screenshot. Ensure the right side artwork still reads in a crop to aspect 2.6:1.
