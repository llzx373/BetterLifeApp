# BetterLifeApp 高性价比人生助手

一款 Android 原生 APP,基于《高性价比人生指南》(github.com/eternity4719/HowToLiveBetter)的 601 条建议,回答一个问题:**你现在该把精力放在什么事情上。**

- 填写一份个人档案(年龄段、吸烟/饮酒、运动睡眠、慢性病、职业、家庭、目标)
- APP 按书中条目的性价比档和证据等级,结合档案给出**该做什么、不该做什么**的推荐
- 运动、饮食、睡眠这类每天都要做的事,自动生成**今日任务**,打卡记连续天数
- 一次性的行动项(换低钠盐、约体检)进**待办列表**
- 内置 AI 问答(自带 API Key,Kimi/DeepSeek),答案基于书中条目并注明出处

> 内容给的是通用口径,不替代医生、律师、会计。

## 功能一览

| 页面 | 说明 |
| --- | --- |
| 档案向导 | 四步填写:基本情况 → 健康习惯 → 家庭与计划 → 目标,除年龄段外都可跳过 |
| 今日 | 每日任务打卡(含连续天数)+ 按口径分组的推荐(先保命/守住钱/省精力/别踩线) |
| 待办 | 每日习惯 + 一次性待办,勾选完成 |
| 条目库 | 33 章 601 条完整浏览、搜索、六栏详情(成本/说人话/收益/证据/来源/备注) |
| AI 问答 | 本地检索书中条目 + 大模型回答,注明「第 X 节第 Y 条」;无 Key 时降级为规则回答 |
| 设置 | API Key、每日提醒时间(本地通知)、档案修改、关于 |

## 构建

环境:JDK 17、Android SDK(platform-34 + build-tools 34.0.0)。`local.properties` 里配置 `sdk.dir`。

```bash
./gradlew testDebugUnitTest   # 24 个单元测试
./gradlew assembleDebug       # 产出 app/build/outputs/apk/debug/app-debug.apk
```

## 内容管线

书中条目不手抄,由脚本从源仓库解析生成:

```bash
python tools/build_content.py                 # book/*.md → app/src/main/assets/entries.json
python tools/gen_rules.py                     # 重新生成 tools/relevance_rules.json,需手动拷到 app/src/main/assets/
```

- `entries.json`:601 条结构化条目,含机器可读的成本标签(钱/时间/毅力/收益/口径)、性价比档(极高/高/一般)、证据等级(A/B/C)
- `relevance_rules.json`:55 条档案 → 条目的加权/排除规则 + 18 条每日习惯白名单

## 技术栈

Kotlin + Jetpack Compose(Material 3)· Room · DataStore · WorkManager · Navigation Compose · OkHttp · Kotlinx Serialization。最低 Android 8.0(API 26),目标 Android 14(API 34)。

详细架构与二次开发指引见 [docs/DESIGN.md](docs/DESIGN.md)。

## 内容出处与许可

条目内容来自《高性价比人生指南》,版权归原作者所有,按其仓库 LICENSE 使用;APP 关于页已注明出处。本仓库代码与书中内容各自遵循其许可。
