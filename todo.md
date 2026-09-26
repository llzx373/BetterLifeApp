# 待办

面向继续开发这个 App 的人。先记两条**已定的边界**,免得后来者反复纠结:

- **签名**:开发期就用 debug 签名(`assembleRelease` 在缺 `keystore.properties` 时回退到 debug 签名),
  **正式签名在上架前单独处理**。在那之前 release 产物不能对外分发。
- **语言**:当前**只支持简体中文**;多语言(含本地化)列入中期规划,现在不做。

各条目的详细背景在 [`docs/DESIGN.md`](docs/DESIGN.md) 与 [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md),
下面只写「做什么、为什么、怎么验」。

---

## A · 发布前必须

现在不做可以,但对外分发前必须做完。

| # | 事项 | 为什么 | 怎么验 |
|---|---|---|---|
| A1 | **补 Room 迁移** | `AppDatabase` 现在是 `version 3` + `fallbackToDestructiveMigration(dropAllTables = true)`,且 `exportSchema = false`。**未发布时这样最省事;一旦发布,任何 schema 变更都会静默删掉用户数据**。发布前必须补真实 `Migration`,并把 `exportSchema` 打开(目录里没有 schema 就写不了迁移测试) | 加 `MigrationTestHelper` 用例 + `exportSchema = true`,跑 `testDebugUnitTest` |
| A2 | **正式签名密钥库** | 见文件开头。密钥一旦丢失不可恢复,保管方式需由项目所有者决定 | `assembleRelease` 产出已签名包;`README` 的「发布构建与签名」有生成命令 |
| A3 | **CI 首次跑通** | `.github/workflows/ci.yml` 已写好但**从未跑过**。最容易出问题的是 `screenshot` 作业:仓库里的基线是在 **Windows** 上渲染的,runner 是 Linux | 推一次看两个作业是否都绿;若截图报 diff,按 `docs/DESIGN_SYSTEM.md` §8 P3-A2 的三步处理(先看图区分真回归与亚像素差异) |
| A4 | **真机走查 4 项** | 这几项按定义截图拍不出来,`docs/DESIGN_SYSTEM.md` §11 里以 `[~]` 挂着 | ① 预测性返回动画在条目详情/聊天页可见;② TalkBack 走完 归档→打卡→待办→搜索→详情→聊天→设置;③ 200% 字体下引导页/设置页/详情页不破版;④ 动效手感与时长(打卡三拍、气泡入场、卡片下沉是否互相打架) |

---

## B · 已识别的功能缺口

已全部修复(2026-09):提醒权限被拒/被收回的界面反馈(设置页 snackbar + 警告行)、全局离线提示(网络监听 + 壳层横幅)、
引导页保存中/失败状态、今日页骨架屏与平板 hover、搜索历史记录、单任务独立提醒时间。
原条目见 git 历史。

---

## C · 有意没做的设计系统项

都做过权衡,不是遗漏。延后没问题,但**别当成是漏配**。

| 事项 | 为什么没做 | 补的话要动什么 |
|---|---|---|
| ≥840dp 的条目库第三栏 | 要在详情栏里再嵌一层 list-detail,还要为内层设计返回行为(内层折叠成单栏时,系统返回该退回条目列表) | 与预测性返回是同一块地,建议一起做 |
| 口径分组展开动效 | 今日页的口径分组**只有标题、不可展开**,没有可加的交互 | 等于新增一个交互,超出「加动效」的范围 |
| FAB → 对话页 container transform | 要么用实验性的 `SharedTransitionLayout`,要么手写一套锚点传递;收益不抵复杂度 | 见 §10 的范围纪律 |

---

## D · 产品功能(中期)

来自 `docs/DESIGN.md` §8 的「第二阶段计划」。

| # | 事项 | 说明 |
|---|---|---|
| D1 | **Health Connect 接入** | 步数/睡眠/运动自动核销每日任务。接入点已明确:`TaskManager.completeTask` 与 `DailyTaskPlanner`。注意需要 Google Play 的健康数据申报 |
| D2 | 数据图表 | 连续天数、完成率趋势。§10 禁止引入图表库 → 手写 `Canvas` |
| D3 | 成就系统 | ⚠️ **需要先做设计决定**:与 §1 第 4 条「不贩卖焦虑」正面冲突(徽章/等级/排行榜和「你落后了」是同一类东西)。§10 写明「真要做,单独设计」 |

---

## E · 等你决定

暂无。(E1 日期语言一致性已按建议落地:今日页日期锁中文 locale,见 `TodayScreen.kt` 的 `formatFullDateZh`。)

---

## 不要做的事

记录两条「看起来像问题、其实不用动」的,免得后来者白费功夫。

- **不要为了消除 lint 的 `GradleDependency` 告警而降级依赖。**
  它报 `benchmark-macro-junit4` 当前是 `1.4.1`、`uiautomator` 当前是 `2.3.0`,但
  `gradle/libs.versions.toml` 里已经是 `1.5.0` / `2.4.0`,且 `:baselineprofile:dependencies`
  确认实际就解析到这两个新版本。停 daemon、关 configuration cache、清 lint 中间产物都无效,
  且 lint worker 每次都抛一条 AGP 内部异常(`AndroidLintWorkAction` 的
  `ReplaceStringBuilder#range()`)。判断为 **AGP 9.4.1 的 lint 报告问题**,不是配置问题。
  0 error 的门禁不受影响,这几条 warning 在 AGP 修好前会一直挂着。
- **不要去「优化」APK 里那几个 `META-INF/**/LICENSE.txt`。**
  共 9 个、压缩后 29 KB(占包体 1.0%)。库的许可声明要不要留在包里是法务选择;
  真要排除,应该先在「关于」页补一个开源许可声明,那是另一件事。
