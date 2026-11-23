# 留白专注

Android 本地专注工具，支持 Android 8.1（API 27）及更高版本。产品定位聚焦**自律模式（正计时/倒计时）+ 正向行为克制兑换 + 主动奖励消耗**。构建版本以 `version.properties` 为准。

前端交接从 [界面入口与联调说明](AGENTS.md) 开始。[界面参照](design/README.md) 提供 Android 15 模拟器界面截图。

## 核心机制

1. **五档时间获取模式与防廉价兑换**：
   - **五种兑换模式**：提供**松弛模式（1:1）**、**简单模式（2:1）**、**适中模式（3:1，默认）**、**学霸模式（4:1）**与**放纵模式（1:2）**。
   - **切换限制与放纵解锁**：四档常规模式之间**每天限切换 1 次**且每次**消耗 30 分钟**自由时间；**放纵模式（1:2）**不可手动切换，累计达成 **5 个自律日**（当日评分 ≥ 80% 且有正向自律记录、未破早睡戒）自动获得 1 次开启机会，开启后在**次日凌晨 04:00 重置点自动恢复为上一次模式**。
   - **自律模式（正计时 / 倒计时）**：确认开始后进入全屏计时界面并锁定除已配置「正向学习应用」外的全部应用。开始按钮下方会提示停止规则。正计时可随时停止并结算（需累计满 `5 分钟` 可兑换），倒计时结束前不可提前停止。
   - **每日 15 分钟基础免费上限**：全部受限应用每日共享最多 `15 分钟` 基础免费额度；通过搜索多选新增受限应用时（不显示包名），默认每日免费 `0 分钟`（完全由可用时长决定）。
   - **待办清单与习惯打卡冷静期**：新建待办与新建习惯后需经历 `10 分钟` 冷静期方可领取或首次打卡，已领取的待办不可取消勾选；每日兑换上限默认为待办 `30 分钟`、习惯 `25 分钟`。
   - **步数兑换限制**：通过系统计步传感器获取步数，默认 `2000 步` 兑换 `5 分钟`，每日步数兑换上限为 `30 分钟`。
   - **睡眠时段全应用强制锁定与早睡打卡**：开启「睡眠时段强制锁定所有应用」后将强制锁定全部非系统豁免应用（不因清醒模式放行）；放弃关闭或修改睡眠设置均需消耗 `35 分钟`；早睡打卡默认 `10 分钟` 且限入睡前 `1 小时` 或起床后 `4 小时` 内完成。
   - **清醒模式**：支持搜索多选一个或多个目的应用（不显示包名）并设置时长，开启后扣除等额时长，进入全屏倒计时。期间可使用所选目标应用及必要的系统核心能力（如系统通知、输入法、权限确认），离开目标应用（含返回桌面或切换其他应用）均会触发拦截并可一键返回清醒模式；通过半透明灰色跨应用悬浮计时药丸可随时回到倒计时页。暂停或结束会退回未用时间，继续时扣除剩余时长。睡眠锁定开始时会暂停清醒模式。
2. **凌晨 04:00 重置点与 2 小时储蓄罐上限**：
   - 每日 **04:00** 作为逻辑日重置边界：清零上一周期剩余可用时长、每日限额与每日基础免费已用额度、刷新每日模式切换次数，并自动将已到期的「放纵模式」恢复为上一次模式；存入**时间储蓄罐**的时长保留不清零，储蓄罐最大累计容量为 **2 小时（120 分钟）**。
3. **修改时间获取规则与主动奖励消耗**：
   - 调整模式、每日获取上限、步数门槛/单次奖励、放弃或调整睡眠时段强制锁、增加额外学习应用或习惯，以及放宽或移除受限应用额度，均需消耗可支配时间（优先扣除今日可用，不足从储蓄罐扣除）。
   - 首页「奖励兑换」位于「兑换」分区上方，也可从「设置 → 奖励兑换商店」进入，设置现实奖励并主动消耗时长。
4. **设置聚合、统一弹窗与克制触感**：
   - 首页移除后台保活卡与通用体验卡，全部收口至右上角「设置」：**强力守护与系统**（聚合后台保活权限、24h 心跳条、无障碍监测、通知权限、悬浮球、隐藏最近任务、自启动、设备管理器防卸载、自律防关闭、桌面小组件与快捷设置磁贴）、**通用设置**（震动反馈开关、时间感知条、应用拦截页风格、储蓄罐步长、管理首页卡片）、**时间获取模式与上限**、**奖励兑换商店**、**调试模式**（开发测试阶段用于自由增减时间与强制解锁，后续版本移除）与**检查更新**。
   - 全应用弹窗与作息时间选择器均采用统一圆角卡片弹窗与底部表单设计，全面替换原生系统 `AlertDialog` 与 `TimePickerDialog`；切页手势与交互控件采用统一防抖震动反馈通道，拓展核心按钮触感响应。
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

规则检查覆盖兑换与每日重置、系统计步兑换、早睡窗口、清醒模式的目标应用限制与退时、其他时间收支、UsageStats 校准、心跳记录，以及 Release 网页资产与摘要解析。

## 插画来源

首页与拦截面板使用内置原创插画 `app/src/main/res/drawable-nodpi/pond_at_dusk.png`，小芽吉祥物与状态图标为本项目的 Android 矢量资源。

> Use case: illustration-story. Create a single original editorial illustration for the full-bleed landscape header of a Chinese mobile focus and digital wellbeing app named Focus Garden. Premium restrained contemporary Japanese botanical editorial style, tactile layered cut-paper with fine silkscreen grain, an intimate quiet pond at dusk, broad rounded lily leaves, a few softly lit fern tips and one very small abstract amber firefly. Composition: wide 3:2 landscape, concentrated distinctive artwork at far right and right lower corner; the left 52% is pure dark evergreen negative space with faint grain and no objects so live white app title fits with good contrast. Deep pine and midnight teal palette (#10352C to #173B31), sage foliage, muted pale chartreuse and amber accents. Elegant fundamentally flat lighting with gentle depth. No lettering, no text, no icons, no borders, no device mockup, no person, no stone mascot, no stock collage. The image is an actual artwork asset, not a screenshot. Ensure the right side artwork still reads in a crop to aspect 2.6:1.
