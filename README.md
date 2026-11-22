# 留白专注

Android 本地专注工具，支持 Android 8.1（API 27）及更高版本，当前版本为 `v1.4.0`。产品定位聚焦**离线锁机 + 正向行为克制兑换 + 主动奖励消耗**。

前端交接从 [界面入口与联调说明](AGENTS.md) 开始。[界面参照](design/README.md) 提供 Android 15 模拟器界面截图。

## 核心机制

1. **防廉价的时间获取（首页 / 规则 / 习惯）**：
   - **离线锁机与专注计时**：提供「离线锁机 / 倒计时 / 番茄钟」三模式，默认转化比例为离线 `35%`、学习 `30%`、专注 `25%`。离线锁机需累计满 `5 分钟` 后才可结算兑换。
   - **待办清单与习惯打卡冷静期**：新建待办与新建习惯后需经历 `10 分钟` 冷静期方可领取或首次打卡，已领取的待办不可取消勾选，防止随手新建秒刷奖励；每日兑换上限默认为待办 `30 分钟`、习惯 `25 分钟`（均可在设置中调整）。
   - **步数兑换限制**：默认 `2000 步` 兑换 `5 分钟`，支持计步传感器同步与每日最多 `4000 步` 的手动补录限制，每日步数兑换上限为 `30 分钟`。
   - **早睡挑战窗口与夜间锁**：需开启「睡眠时段自动拦截」方可参与早睡打卡（默认 `10 分钟`），且仅可在入睡前 `1 小时` 或起床后 `4 小时` 内完成打卡。
   - **清醒模式**：有目的、有限时地暂时放行受限应用，开启时需实时扣除等额自由时间（余额不足时无法开启）。
2. **修改时间获取规则需付出代价**：
   - 调整兑换比例、修改每日获取上限、改动步数门槛/单次奖励、关闭夜间早睡锁、调整作息时间与早睡奖励、增加额外学习应用或习惯，以及放宽或移除受限应用额度，均需消耗已赚取的可支配时间（优先扣除今日可用时长，不足部分自动从储蓄罐扣除），并在「洞察 → 时间线」中生成 `CONFIG` 支出记录。收紧受限应用额度或添加首个受限应用保持免费；删除已有学习应用或习惯后不会重置首个免费名额。
3. **主动奖励兑换（时间价值消费）**：
   - 首页「奖励兑换」卡片与「设置 → 奖励兑换商店」支持新增、点击编辑与删除现实生活奖励及消耗分钟数（内置默认示例），确认兑换后主动扣除可用/储蓄罐时间并在时间线记录 `REWARD` 消费。
4. **强力防关闭与后台保活**：
   - 集中提供使用量数据访问权限、悬浮窗、24 小时 `App 后台运行状态` 心跳条、通知权限、无障碍服务、相册自选动态「保活壁纸」、关闭省电优化、桌面边缘吸附「悬浮小球」、从最近使用中隐藏、系统自启动跳转、设备管理器「防冲动卸载」以及「专注中防关闭设置」拦截保护。
   - 设置与保活状态检测全部通过后台线程池异步刷新并缓存，结合单次测量的底部弹层容器实现零卡顿展开。
5. **切页动画与克制触感**：
   - 底部四栏支持横向滑动手势与点击切页，搭配滑入淡入与胶囊回弹动画；触感反馈改为单一震动通道 + `45ms` 防抖，清晰区分 `NAV`（导航/待办开关）、`SWITCH`（开关控件）、`CONFIRM`（真正扣款/兑换/启动成功）与 `REJECT`（余额不足/冷静期/上限拦截），移除冗余杂震。

部分 Android 版本会限制侧载应用启用无障碍服务。如果系统显示「受限设置」，请从留白专注的系统应用详情按提示确认，再返回无障碍页面开启。[Android 受限设置说明](https://support.google.com/android/answer/12623953)

右上角「设置」还可以管理计时完成通知、添加桌面小组件（支持离线正计时）、添加快捷设置磁贴（一键启停离线锁机）、配置实验室功能（时间感知条与拦截页样式）及显示/隐藏首页卡片。设置与统计全部保存在手机本地，仅在手动检查更新时请求 GitHub API。

## GitHub 更新

1. 从右上角「设置」进入「GitHub 更新」。默认使用 [Jansongzi/FocusGarden](https://github.com/Jansongzi/FocusGarden/releases)，也可以填写其他公开仓库名 `owner/repo` 或 GitHub 仓库链接。
2. 点击「检查更新」。应用读取最近一个正式 Release，显示版本和发布说明。
3. 选择 APK 附件。应用检查字节数、GitHub 提供的 SHA-256、应用包名、签名和构建号。首次需要在系统设置中允许留白专注请求安装，再返回软件更新页发起安装。

发布新版时，在 `version.properties` 同时提升 `versionName` 和 `versionCode`，上传 APK 到正式 Release。标签使用 `v1.4.0` 这类语义化版本，与 APK 版本一致。覆盖更新须使用同一签名密钥，签名文件应保存在发布者本地。

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

规则检查覆盖严格版默认比例、离线 `5 分钟` 起兑、待办/习惯 `10 分钟` 冷静期与每日上限、手动步数 `4000 步` 日限、早睡窗口与夜间锁前置要求、清醒模式扣时、全部配置修改成本计算、主动奖励兑换、UsageStats 校准以及 24 小时心跳记录。更新规则检查验证 GitHub 仓库格式、版本号顺序及摘要格式。

## 插画来源

首页与拦截面板使用内置原创插画 `app/src/main/res/drawable-nodpi/pond_at_dusk.png`，小芽吉祥物与状态图标为本项目的 Android 矢量资源。

> Use case: illustration-story. Create a single original editorial illustration for the full-bleed landscape header of a Chinese mobile focus and digital wellbeing app named Focus Garden. Premium restrained contemporary Japanese botanical editorial style, tactile layered cut-paper with fine silkscreen grain, an intimate quiet pond at dusk, broad rounded lily leaves, a few softly lit fern tips and one very small abstract amber firefly. Composition: wide 3:2 landscape, concentrated distinctive artwork at far right and right lower corner; the left 52% is pure dark evergreen negative space with faint grain and no objects so live white app title fits with good contrast. Deep pine and midnight teal palette (#10352C to #173B31), sage foliage, muted pale chartreuse and amber accents. Elegant fundamentally flat lighting with gentle depth. No lettering, no text, no icons, no borders, no device mockup, no person, no stone mascot, no stock collage. The image is an actual artwork asset, not a screenshot. Ensure the right side artwork still reads in a crop to aspect 2.6:1.
