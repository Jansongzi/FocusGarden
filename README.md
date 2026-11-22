# 留白专注

Android 本地专注工具，支持 Android 8.1（API 27）及更高版本，当前版本为 `v1.3.0`。产品定位聚焦**正向行为打卡 + 离线锁机兑换时长**。

前端交接从 [界面入口与联调说明](AGENTS.md) 开始。[界面参照](design/README.md) 提供 Android 15 模拟器界面截图。

## 核心功能

1. **离线锁机与专注计时（首页 / 全屏表盘）**：默认提供「离线锁机」累计正向离屏时间，并支持「倒计时」与「番茄钟」，可暂停续计、实时或结束结算兑换为可用娱乐时长。默认比例为离线 `50%`、学习 `50%`、专注 `20%`（可在设置中调整）。
2. **正向行为兑换**：支持正向学习应用（按比例自动累积并兑换）、待办清单（支持清理已领取项）、早睡挑战（跨午夜作息与违约标记）、习惯连续打卡，以及步数兑换（支持自定义步数门槛与奖励分钟数，手动补录或硬件计步传感器同步）。
3. **时间储蓄罐与清醒模式**：可将今日可用时长存入或提取出长期保留的时间储蓄罐（单次步长可调）；「清醒模式」可在明确目的后限时暂时放行受限应用。
4. **应用规则与使用统计校准**：在「规则」配置学习应用、受限应用及每日免费额度，配置跨午夜睡眠时段；授权「使用量数据访问权限」后，应用会同步校准每日应用前台统计并联动「洞察」7 日柱状图与时间线。
5. **强力防关闭与后台保活**：在「设置 → 强力防关闭与后台保活」和首页保活卡片中集中管理使用量数据访问权限、悬浮窗、24 小时 `App 后台运行状态` 心跳条、通知权限、无障碍服务、相册自选动态「保活壁纸」、关闭省电优化、桌面边缘「悬浮小球」、从最近使用中隐藏、系统自启动跳转、设备管理器「防冲动卸载」以及「专注中防关闭设置」系统设置拦截保护。
6. **多页切换与触感反馈**：底栏四个主页面（首页 / 规则 / 习惯 / 洞察）支持横向滑动手势与点击切换，带滑入淡入转场动画及可开关的震动触感反馈。

部分 Android 版本会限制侧载应用启用无障碍服务。如果系统显示「受限设置」，请从留白专注的系统应用详情按提示确认，再返回无障碍页面开启。[Android 受限设置说明](https://support.google.com/android/answer/12623953)

右上角「设置」还可以管理计时完成通知、添加桌面小组件（支持离线计时实时正计时）、添加快捷设置磁贴（一键启停离线锁机）、配置实验室功能（时间感知条与拦截页样式）及显示/隐藏首页卡片。设置和统计全部保存在手机本地，仅在手动检查更新时请求 GitHub API。

## GitHub 更新

1. 从右上角「设置」进入「GitHub 更新」。默认使用 [Jansongzi/FocusGarden](https://github.com/Jansongzi/FocusGarden/releases)，也可以填写其他公开仓库名 `owner/repo` 或 GitHub 仓库链接。
2. 点击「检查更新」。应用读取最近一个正式 Release，显示版本和发布说明。
3. 选择 APK 附件。应用检查字节数、GitHub 提供的 SHA-256、应用包名、签名和构建号。首次需要在系统设置中允许留白专注请求安装，再返回软件更新页发起安装。

发布新版时，在 `version.properties` 同时提升 `versionName` 和 `versionCode`，上传 APK 到正式 Release。标签使用 `v1.3.0` 这类语义化版本，与 APK 版本一致。覆盖更新须使用同一签名密钥，签名文件应保存在发布者本地。

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
javac --release 17 -d "$classes" \
  app/src/main/java/com/focusgarden/app/FocusState.java \
  app/src/main/java/com/focusgarden/app/UpdateRules.java \
  checks/FocusStateCheck.java checks/UpdateRulesCheck.java
java -cp "$classes" com.focusgarden.app.FocusStateCheck
java -cp "$classes" com.focusgarden.app.UpdateRulesCheck
```

规则检查覆盖学习兑换、日限额与扣款、跨午夜、睡眠限制、番茄阶段、暂停续计、每天打卡一次、连续天数、离线锁机结算与兑换、UsageStats 同步、24 小时心跳记录及步数统计。更新规则检查验证 GitHub 仓库格式、版本号顺序及摘要格式。

## 插画来源

首页与拦截面板使用内置原创插画 `app/src/main/res/drawable-nodpi/pond_at_dusk.png`，小芽吉祥物与状态图标为本项目的 Android 矢量资源。

> Use case: illustration-story. Create a single original editorial illustration for the full-bleed landscape header of a Chinese mobile focus and digital wellbeing app named Focus Garden. Premium restrained contemporary Japanese botanical editorial style, tactile layered cut-paper with fine silkscreen grain, an intimate quiet pond at dusk, broad rounded lily leaves, a few softly lit fern tips and one very small abstract amber firefly. Composition: wide 3:2 landscape, concentrated distinctive artwork at far right and right lower corner; the left 52% is pure dark evergreen negative space with faint grain and no objects so live white app title fits with good contrast. Deep pine and midnight teal palette (#10352C to #173B31), sage foliage, muted pale chartreuse and amber accents. Elegant fundamentally flat lighting with gentle depth. No lettering, no text, no icons, no borders, no device mockup, no person, no stone mascot, no stock collage. The image is an actual artwork asset, not a screenshot. Ensure the right side artwork still reads in a crop to aspect 2.6:1.
