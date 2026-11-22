# 留白专注

Android 本地专注工具，支持 Android 8.1 及更高版本。

前端交接从 [界面入口与联调说明](AGENTS.md) 开始。[界面参照](design/README.md) 提供模拟器截图。

## 开始使用

1. 安装 APK，在「规则」中添加学习应用和限制应用，并设置各应用的每日免费分钟数。
2. 从「规则」进入无障碍设置，开启「留白专注 · 应用限制」。随时可从系统设置关闭。
3. 在「首页」选择倒计时或番茄钟，按需调整专注时长、休息时长和轮数。暂停专注时保留应用限制。
4. 在「习惯」添加、编辑和打卡。「首页」还提供待办、闪卡、早睡、步数和时间储蓄罐。清醒模式可以按设定时长暂时放行受限应用。
5. 在「洞察」按天查看应用用量、兑换收支及新增记录的时间线。

部分 Android 版本会限制侧载应用启用无障碍服务。如果系统显示「受限设置」，请从留白专注的系统应用详情按提示确认，再返回无障碍页面开启。 [Android 受限设置说明](https://support.google.com/android/answer/12623953)

限制应用先使用自己的每日免费时长，然后扣除共享的可兑换时间。按默认比例，学习 10 分钟赚 5 分钟，完成 25 分钟专注赚 5 分钟。专注进行中和睡眠时段内，限制应用会被挡住。番茄休息时段恢复按日限额判断。结余跨天保留，免费时长按本地日期重置。

右上角「设置」可以开启计时通知、添加桌面小组件和快捷设置磁贴。计时通知受系统后台调度影响，省电状态下可能延迟。

应用通过系统的窗口状态事件跟踪已配置应用的前台时长。设置和统计保存在手机本地。网络仅用于手动触发的 GitHub 更新请求。步数可以手动补录，也可在配有计步传感器的设备上授权读取系统计数。

## GitHub 更新

1. 从右上角「设置」进入「GitHub 更新」，填写公开仓库名 `owner/repo` 或 GitHub 仓库链接。出厂时更新源为空。
2. 点击「检查更新」。应用读取最近一个正式 Release，显示版本和发布说明。
3. 选择 APK 附件。应用检查字节数、GitHub 提供的 SHA-256、应用包名、签名和构建号。首次需要在系统设置中允许留白专注请求安装，再返回软件更新页发起安装。

发布新版时，在 `version.properties` 同时提升 `versionName` 和 `versionCode`，上传 APK 到正式 Release。标签使用 `v1.2.3`，与 APK 版本一致。覆盖更新须使用同一签名密钥，签名文件应保存在发布者本地。

## 构建

用 JDK 17 或 21、Android SDK Platform 35 和 Build Tools 35.0.0，在项目目录执行：

```sh
./gradlew :app:assembleDebug
```

构建结果位于 `app/build/outputs/apk/debug/app-debug.apk`。也可直接用 Android Studio 打开本目录。

只安装了 SDK 时，可设好 `ANDROID_SDK_ROOT` 和 `JAVA_HOME` 后运行
`sh ./build-local-apk.sh`。它直接调用 SDK 工具，输出
`app/build/outputs/apk/local/focus-garden.apk`。本聊天交付的试用包走这条构建路径。
用同一个本地测试密钥重建可以覆盖安装并保留数据。Gradle 调试包使用 Gradle 的调试签名。

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

规则检查覆盖学习兑换、日限额与扣款、跨午夜、睡眠限制、番茄阶段、暂停续计、每天打卡一次和连续天数。更新规则检查验证 GitHub 仓库格式、版本号顺序及摘要格式。

## 插画来源

首页与拦截面板使用内置 ImageGen 生成的原创插画 `app/src/main/res/drawable-nodpi/pond_at_dusk.png`。

仓库中的种子角色和卡片背景为本项目的 Android 矢量及形状资源。[词库来源与许可证](licenses/README.md)。

> Use case: illustration-story. Create a single original editorial illustration for the full-bleed landscape header of a Chinese mobile focus and digital wellbeing app named Focus Garden. Premium restrained contemporary Japanese botanical editorial style, tactile layered cut-paper with fine silkscreen grain, an intimate quiet pond at dusk, broad rounded lily leaves, a few softly lit fern tips and one very small abstract amber firefly. Composition: wide 3:2 landscape, concentrated distinctive artwork at far right and right lower corner; the left 52% is pure dark evergreen negative space with faint grain and no objects so live white app title fits with good contrast. Deep pine and midnight teal palette (#10352C to #173B31), sage foliage, muted pale chartreuse and amber accents. Elegant fundamentally flat lighting with gentle depth. No lettering, no text, no icons, no borders, no device mockup, no person, no stone mascot, no stock collage. The image is an actual artwork asset, not a screenshot. Ensure the right side artwork still reads in a crop to aspect 2.6:1.
