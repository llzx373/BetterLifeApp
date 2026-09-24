# BetterLifeApp 设计指导说明书

面向继续开发这个 APP 的人。读完应能:改推荐规则、加档案字段、接新页面、换 AI 服务商、做对拍验证。

## 1. 产品模型

一切围绕书中「条目」展开。一条条目是一个建议(如「把家里的食盐换成低钠盐」),带:

- **成本标签**(机器可读):钱(0/少/多)、时间(少/中/多)、毅力(否/些/是)、收益(大/中/小)、口径(死亡率/金钱/时间/自由)
- **性价比档 ratio**:极高/高/一般,由成本分 `cs = 钱 + 时间 + 毅力`(各 0~2)与收益档推出,算法必须与源仓库 `index.html` 一致(收益=大:cs==0→极高,cs≤2→高,否则一般;收益=中:cs==0→高,否则一般;收益=小→一般)
- **证据等级** A/B/C 与 **dispute(争议)/ todo(待核实)** 标记

两条铁律(来自原书,全 App 遵守):

1. **口径之间不排序**。换寿命的和换钱的分开列,各排各的;UI 上就是「先保命/守住钱/省精力/别踩线」四个分组。
2. **待核实条目不进推荐**,只在条目库可见并标注;争议条目可见但要标「争议」。

## 2. 模块结构(包 com.betterlife.app)

```
data/        内容与持久层
  EntryModels.kt        entries.json / relevance_rules.json 的 DTO
  EntryRepository.kt    assets → 内存索引(byId/bySection/sections/dailyEntryIds)
  db/                   Room:ProfileEntity(单行档案)、TaskEntity(任务)、EntryStateEntity(条目状态)
  SettingsStore.kt      DataStore:API 配置、提醒时间、onboardingDone
  ProfileRepository.kt  Profile 领域模型 ↔ ProfileEntity;Profile.matches() 规则匹配
recommend/   纯 Kotlin,不依赖 Android,全部可单测
  RecommendationEngine.kt  规则打分 + 按口径分组排序
  DailyTaskPlanner.kt      每日任务挑选(确定性轮换)
tasks/
  TaskManager.kt           任务读写、打卡、streak、加待办
  ReminderScheduler.kt     WorkManager 周期任务 + 本地通知(渠道 "daily")
ai/
  LlmClient.kt             OpenAI 兼容 chat/completions(预设 Kimi/DeepSeek)
  EntryRetriever.kt        本地检索:bigram 重叠 + 标题加权 + 档案加分
  SystemPrompts.kt         安全护栏与回答格式(改不得,见 §6)
  AiAdvisor.kt             检索 + 调模型;无 Key 时降级
viewmodel/   六个 ViewModel,各带 Factory,手动 DI 在 BetterLifeApp.AppContainer
ui/          Compose 页面:onboarding / today / todo / library / chat / settings / mine
```

依赖方向:ui → viewmodel → (recommend / tasks / ai) → data。recommend 和 EntryRetriever 不许 import Android 类,保持可单测。

## 3. 内容管线(tools/)

- `build_content.py`:解析源仓库 `book/*.md`,输出 `app/src/main/assets/entries.json`。解析正则复刻自源仓库 index.html,**源书格式变了先改这里**,改完跑脚本看自检输出(601 条、六栏完整率、档位分布)。
- `gen_rules.py` → `tools/relevance_rules.json` → 手动拷贝到 `app/src/main/assets/`。脚本自带 id 存在性校验。

entries.json 单条字段:`id`(节号-条号)、`sec/n/title`、`cost/human/gain/grade/src/note`、`money/time/will/level/lens`、`cs/ratio/dispute/todo`、`hay`(检索用小写拼接)。

## 4. 推荐引擎语义(relevance_rules.json)

```json
{"when": {"smoking": ["yes"], "chronic": ["kidney"]},
 "boostEntryIds": [...], "boostSections": [2], "excludeEntryIds": [...],
 "weight": 100, "reason": "..."}
```

- `when` 全部字段匹配才生效(档案值 ∈ 数组);`{}` = 所有人;**布尔一律写字符串 `"true"/"false"`**(写成 JSON 布尔会在反序列化时崩溃,已踩过)
- 打分:命中规则的 weight 累加(boostEntryIds 直接加;boostSections 加给该节每条);命中规则的 excludeEntryIds **无条件剔除**;todo=true 剔除
- 排序:按 lens 分组(固定序 死亡率→金钱→时间→自由),组内 score desc → ratio → grade → cs asc,每组 topN(默认 5)

**改规则的方法论**:加分规则回答「这条建议为谁而写」,排除规则回答「备注里写了谁不能用」。新加档案字段时,先在 `Profile.fieldValues()` 注册,再补规则,最后加引擎单测。

### 每日任务

`dailyEntryIds` 白名单(18 条,全是「今天做一次、明天还要做」的习惯)∩ 档案命中条目中,剔除 todo/exclude 后,按 ratio/grade/cs 稳定排序,用 `floorMod(日期hash, 条数)` 做起点轮换,取前 3 条。同一天结果必然可复现。

## 5. 数据流

```
onboarding 填档案 → ProfileEntity
  → TodayViewModel:TaskManager.ensureTodayTasks(profile)(幂等,按日期)
                   RecommendationEngine 出分组推荐
  → 打卡 → TaskEntity.done → streak 从昨天往前数(今天未打卡不清零)
  → 加入待办 → TaskEntity(ONCE) + EntryStateEntity(TODO)
ReminderScheduler:WorkManager 每天 ensureTodayTasks + 通知未完成数
```

## 6. AI 模块与护栏

`SystemPrompts.kt` 的护栏是产品安全底线,**改动需要明确理由**:

- 正在发生的急症 → 先说 120/119 和第一个现场动作(出处第 13 节)
- 自杀念头/活不下去 → 先给心理援助热线 12356,不劝导不评价
- 已被传唤/拘留/起诉 → 指向第 8 节,建议找律师
- 回答必须基于检索到的条目,注明「第 X 节第 Y 条」;书里没写的直说;数字照抄不改;末尾带「通用口径,不替代医生/律师」

检索是本地的(EntryRetriever),只有 top6 条目内容进 prompt,档案只以摘要形式进 prompt。换服务商:设置页选预设或自定义 baseUrl/model,OpenAI 兼容即可;baseUrl 已含 `/v1` 时客户端不会重复拼接。

## 7. 构建与验证

```bash
python tools/build_content.py      # 内容变更后重跑,看自检统计
./gradlew testDebugUnitTest        # 24 例:引擎 8 / 规划器 7 / 检索器 4 / 档案映射 5
./gradlew assembleDebug
```

冒烟路径(新机器或改动后必走):onboarding → 今日页出任务和推荐 → 打卡出 streak → 待办页两分区 → 条目库搜索 → (配 Key)AI 问答 → 设置开提醒。

## 8. 已知取舍与路线

- `collectAsStateWithLifecycle` 已改用官方 `androidx.lifecycle.compose` 实现(此前是本仓库 `ui/util/StateFlowExt.kt` 的本地替代品,引入 `lifecycle-runtime-compose` 后已删除)
- 单任务无独立提醒时间(只有全局提醒),TodoScreen 未展示提醒时间
- 第二阶段计划:Health Connect 接入(步数/睡眠/运动自动核销每日任务)、数据图表、成就系统;接入点在 `TaskManager.completeTask` 与 `DailyTaskPlanner`
- 条目内容的 LICENSE 归原书仓库,分发 APK 即分发其内容,关于页须保留出处
