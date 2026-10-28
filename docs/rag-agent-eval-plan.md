# LoreKeeper · RAG 智能体评测方案与排期

> 参考《RAG 智能体评测：从检索、归因、答案质量到 Agent 调用全流程》（CSDN 159681615），
> 结合本项目现状（Java 21 + Spring AI + DashScope qwen-max + pgvector + ReAct Agent + 现有 `eval/` 三层架构）制定。

---

## 1. 现状诊断：项目 vs 文章四层框架

项目已有评测底子（`纯函数评测器 + 真连运行器 + JSON golden 集`，对应文章"数据集是地基，指标只是尺子"），但对照四层还缺一半：

| 文章四层 | 项目现状 | 需要补什么 |
|---|---|---|
| **检索层** | ✅ `RagEvaluator`（`Recall@K` / `MRR`） | 缺 `Context Precision`（纯度）、`Context Relevance`（语义相关度） |
| **归因层** | ❌ 完全没有 | 新增 **`Faithfulness`**（最重要，直接量化"答案有没有依据"） |
| **回答层** | ❌ 完全没有 | 新增 `Answer Relevancy` / `Answer Accuracy` |
| **Agent 层** | ⚠️ 有机制（防死循环、意图路由）但**无评测** | 新增 `Intent Accuracy` / `Tool Call Accuracy` / `Agent Goal Accuracy` |

一句话：**现有评测覆盖了"检索"和"抽取/归并"两个确定性环节，漏掉了"最终答案质量"和"Agent 行为"这两个最贴近用户、也最易被幻觉钻空子的环节。**

---

## 2. 目标指标体系（映射到项目）

**确定性环节走纯函数（进 CI），依赖 LLM 的走运行器 + `@Disabled` 隔离**（与现有 `ExtractionEvaluator` 同套路）：

| 层 | 指标 | 项目落点 | 实现方式 | 数据来源 |
|---|---|---|---|---|
| 检索 | `Recall@K` / `MRR` | ✅ 已有 | 纯函数 | `metadata.entityId` |
| 检索 | `Context Precision@K` | 新增 | 纯函数 | 前 K 命中 golden 比例 |
| 归因 | `Faithfulness` | **新增（核心）** | LLM-as-judge | claim ↔ 检索上下文 |
| 回答 | `Answer Relevancy` | 新增 | LLM-as-judge | 回答 ↔ question |
| 回答 | `Answer Accuracy` | 新增（有 reference 才上） | LLM-as-judge | 回答 ↔ reference_answer |
| Agent | `Intent Accuracy` | 新增 | 运行器 | `RouteResult.intent` ↔ 标注 |
| Agent | `Tool Call Accuracy` | 新增 | 运行器 | 实际工具序列 ↔ `expectedTools` |
| Agent | `Agent Goal Accuracy` | 新增 | 运行器 | 最终回答 ↔ `successCriteria` |
| 工程 | 延迟 / loopGuard 触发率 / maxSteps 耗尽率 | 新增（运营侧） | 日志埋点 | `ToolCallAgent` 已有状态 |

综合评分（工程配置权重，非固定标准）：

```
总分 = 0.35*contextRecall + 0.25*faithfulness + 0.25*answerAccuracy + 0.15*answerRelevancy
```

---

## 3. 测试集 schema 扩展

**增量扩展** `EvalDataset` record，不动已有三类：

```java
public record EvalDataset(
        Seed seed,
        List<RagCase> rag,
        List<ConsistencyCase> consistency,
        List<ExtractionCase> extraction,
        List<QaCase> qa,          // 新增：端到端问答（归因层 + 回答层）
        List<IntentCase> intent,  // 新增：意图路由
        List<AgentCase> agent     // 新增：Agent 工具调用
) {
    /** 端到端问答样本：question/reference/golden_facts/bad_cases */
    public record QaCase(
            String question,
            String referenceAnswer,          // 可为 null → 跳过 Accuracy
            List<String> goldenContextIds,   // 理想命中设定条目 id
            List<String> goldenFacts,        // 必须覆盖的关键事实
            List<String> badCases,           // 不该出现的幻觉点
            String difficulty               // easy|medium|hard
    ) {}

    /** 意图路由样本 */
    public record IntentCase(String message, String expectedIntent) {}

    /** Agent 样本：expected_tools + success_criteria */
    public record AgentCase(
            String question,
            List<String> expectedTools,
            String successCriteria
    ) {}
}
```

`dataset.json` 新增示例：

```json
"qa": [
  {
    "question": "主角惯用哪只手拿剑？",
    "referenceAnswer": "主角林玄天生左撇子，惯用左手持剑。",
    "goldenContextIds": ["9003"],
    "goldenFacts": ["左撇子", "左手持剑"],
    "badCases": ["右手", "剑修"],
    "difficulty": "easy"
  }
],
"intent": [
  { "message": "主角的师父是谁", "expectedIntent": "QUERY" },
  { "message": "帮我写第 50 章", "expectedIntent": "WRITE" }
],
"agent": [
  { "question": "女二的佩剑叫什么", "expectedTools": ["settingSearch"], "successCriteria": "答案含「青霜」" }
]
```

---

## 4. 新增评测器设计

### 4.1 纯函数评测器（进 CI，零 LLM 依赖）

**`RagEvaluator` 扩展 `Context Precision@K`**：

```java
public double contextPrecisionAtK(List<String> retrievedIds, List<String> expectedIds, int k) {
    var topK = retrievedIds.stream().limit(k).toList();
    if (topK.isEmpty()) return 0.0;
    return (double) topK.stream().filter(expectedIds::contains).count() / topK.size();
}
```

**新增 `AgentEvaluator`（纯函数）**：

```java
public record AgentEvaluator() {
    public record ToolMetrics(double precision, double recall, double f1) {}
    public ToolMetrics toolCallF1(Set<String> expected, Set<String> actual) { /* ... */ }
    public double intentAccuracy(List<String> predicted, List<String> expected) { /* ... */ }
}
```

### 4.2 LLM-as-judge（归因层 + 回答层核心）

项目已有 DashScope `qwen-max`，`IntentRouter` 已示范 `ChatClient.entity(Xxx.class)` 结构化输出，照抄该模式：

```java
@Component
public class LlmJudgeService {
    private final ChatClient chatClient;
    public LlmJudgeService(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    public record JudgeVerdict(
            double faithfulness,      // claim 能从上下文推出的比例
            double answerRelevancy,   // 回答是否切题
            double answerAccuracy,    // 与 reference 一致程度（无 reference 返回 -1）
            List<String> unsupportedClaims
    ) {}

    public JudgeVerdict judge(String question, String answer,
                              String retrievedContext, String referenceAnswer) {
        return chatClient.prompt()
            .system("""
                你是 RAG 评测裁判。基于「检索上下文」与「参考答案」给最终回答打分：
                1. faithfulness：把回答拆成若干 claim，逐个判断能否从「检索上下文」推出，能推出的比例（0~1）。
                2. answerRelevancy：回答是否直接、恰当地回应原问题（0~1）。
                3. answerAccuracy：与参考答案一致性（0~1）；无参考答案时填 -1。
                4. unsupportedClaims：列出无法被上下文支撑的 claim（幻觉点）。
                只输出 JSON：{"faithfulness":..,"answerRelevancy":..,"answerAccuracy":..,"unsupportedClaims":[...]}
                """)
            .user("问题：%s\n检索上下文：%s\n最终回答：%s\n参考答案：%s"
                    .formatted(question, retrievedContext, answer, referenceAnswer))
            .call().entity(JudgeVerdict.class);
    }
}
```

### 4.3 运行器（真连 DB/LLM，`@Disabled` 隔离）

新增 `QaEvalRunnerTest`：灌种子 → 每 `QaCase` 走真实链路（`IntentRouter` → `LoreAgentFactory` → `ToolCallAgent`）拿最终回答 + 检索上下文 → 调 `LlmJudgeService` 打分 → 汇总 F1/平均分。默认 `@Disabled`，不进 CI。

---

## 5. 落地排期

按文章三阶段拆分，以周为单位（1 人全职口径，可并行调整）。

### 阶段一：检索 + 归因 + 回答三件套（核心，第 1 周）✅ 已完成

| 编号 | 任务 | 交付物 | 优先级 | 工作量 | 验收标准 |
|---|---|---|---|---|---|
| 1.1 | `RagEvaluator` 扩展 `ContextPrecision@K` | 纯函数 + 单测 | P0 | 0.5 天 | `mvn test` 通过，输出 `ContextPrecision@5` |
| 1.2 | 扩展 `EvalDataset` record + `dataset.json` 新增 `qa` 样本 | 数据模型 + golden 集 | P0 | 1 天 | 至少 5 条 `QaCase`，覆盖 easy/medium/hard |
| 1.3 | 新增 `LlmJudgeService`（Faithfulness/Relevancy/Accuracy） | Judge 服务 + 结构化输出 | P0 | 1 天 | `temperature=0`，返回合法 `JudgeVerdict` |
| 1.4 | 新增 `QaEvalRunnerTest` | 运行器（`@Disabled`） | P0 | 1 天 | 手动删 `@Disabled` 后能跑通并输出三指标 |

**阶段一里程碑 ✅ 达成**：能对任意问答输出 `ContextRecall / Faithfulness / AnswerRelevancy` 三件套。实测（10 样本，含 3 条无答案负样本）：Faithfulness=1.0 / AnswerRelevancy=1.0 / AnswerAccuracy=1.0；负样本还暴露并修复了「无答案场景 LLM 空回答」的 bug（`QUERY_SOP` 强化 + `ToolCallAgent.act` 兜底）。

### 阶段二：补准确/覆盖（第 2 周）✅ 已完成

| 编号 | 任务 | 交付物 | 优先级 | 工作量 | 验收标准 |
|---|---|---|---|---|---|
| 2.1 | `dataset.json` 补全 `referenceAnswer` | golden 集扩充 | P1 | 0.5 天 | 每条 `QaCase` 有参考答案 |
| 2.2 | `Answer Accuracy` 接入并打通 | Judge 输出 Accuracy | P1 | 0.5 天 | 无 reference 样本正确跳过 |
| 2.3 | 加权综合评分落地 | 评分公式 + 汇总输出 | P1 | 0.5 天 | 输出单样本/整体加权总分 |
| 2.4 | 失败样本回放：输出 `unsupportedClaims` 与低分 query | 可定位报告 | P1 | 0.5 天 | 能定位"检索掉分 vs 生成掉分" |

**阶段二里程碑 ✅ 达成**：新增 `ScoreAggregator`（纯函数）落地加权总分 `0.35*recall + 0.25*faithfulness + 0.25*accuracy + 0.15*relevancy`（缺失指标按剩余权重归一化），并落地失败样本回放（`diagnose()` 定位「检索掉分 vs 生成掉分 vs 双重」）。

### 阶段三：Agent 层 + 运营指标（第 3 周）✅ 已完成（线上回灌为人工抽检流程）

| 编号 | 任务 | 交付物 | 优先级 | 工作量 | 验收标准 |
|---|---|---|---|---|---|
| 3.1 | `dataset.json` 新增 `intent` / `agent` 样本 | golden 集扩充 | P2 | 0.5 天 | 各 ≥ 5 条样本 |
| 3.2 | 新增 `AgentEvaluator`（工具 F1 + 意图准确率） | 纯函数 + 单测 | P2 | 0.5 天 | `mvn test` 通过 |
| 3.3 | 新增 `IntentEvalRunnerTest` / `AgentEvalRunnerTest` | 运行器 | P2 | 1 天 | 输出 Intent Accuracy、Tool Call F1、Goal Accuracy |
| 3.4 | `ToolCallAgent` 埋点：loopGuard 触发/步数/耗时 | 日志埋点 | P2 | 0.5 天 | 可统计 loopGuard 触发率、maxSteps 耗尽率 |
| 3.5 | `eval_trace` 落库 + 线上失败样本回灌 | 采集 + 回灌机制 | P2 | 1 天 | 真实问答落表，坏样本回灌成 `badCases` |

**阶段三里程碑 ✅ 达成**：新增 `AgentEvaluator`（工具 F1 + 意图准确率）、`IntentEvalRunnerTest` / `AgentEvalRunnerTest`、`ToolCallAgent` 埋点（工具序列/loopGuard/步数/耗时）、`EvalTraceRepository` + `onFinish` 落表 hook。评测从"模型评测"升级到"产品质量评测"；`eval_trace` 落表 + `findRecent` 支撑人工抽检回灌（自动化回灌工具未做，属运营流程）。

### 排期总览

```
第 1 周：阶段一（P0，核心）—— Faithfulness 是重中之重
第 2 周：阶段二（P1）       —— 补 Accuracy + 加权总分
第 3 周：阶段三（P2）       —— Agent 层 + 运营指标 + 线上回灌
```

**依赖关系**：1.2 → 1.4；1.3 → 1.4；2.1 → 2.2；3.1 → 3.3；3.4 → 3.5。阶段二依赖阶段一，阶段三依赖阶段一（可并行启动 3.4 埋点）。

---

## 6. 注意事项（结合项目既有约束）

1. **LLM-as-judge 随机性**：`qwen-max` 做 judge 也随机，`temperature=0` 且每样本跑 2~3 次取均值，或对 judge 结果人工抽检校准（离线 + 线上 + 人工抽检三者结合）。
2. **保持"确定性环节不进 LLM"既有原则**：`Intent Accuracy` 的 `expectedIntent` 确定，但 `IntentRouter.route()` 依赖 LLM → 属运行器，不塞进 CI。
3. **`goldenContextIds` 与 `goldenFacts` 不混用**：前者归检索层（`Recall@K`），后者归归因层（判 claim 覆盖）——混用即失去定位能力。
4. **失败样本回灌**：现有 SSE 事件流（`intent`/`tool`/`answer`/`error`）可支撑 `eval_trace` 落库，线上坏样本回灌 `badCases`。
5. **复用 `evidence` 字段**：设定四元组本有 `evidence` 原文依据，judge 时一并喂入，显著提高归因判定准确性，天然支撑后续 `Citation Precision/Coverage`。

---

## 7. 附：`eval_trace` 建表 DDL（已执行 ✅ 2026-10-28）

```sql
CREATE TABLE IF NOT EXISTS eval_trace (
    id            bigserial PRIMARY KEY,
    work_id       bigint,
    user_message  text,
    intent        text,
    tool_sequence text,          -- JSON 数组：["settingSearch","doTerminate"]
    answer        text,
    loop_guard    boolean,        -- 是否触发防死循环
    step_count    int,            -- 实际步数
    cost_ms       bigint,         -- 端到端耗时
    created_at    timestamptz DEFAULT now()
);
```

---

## 8. 下一步

阶段一、二、三已全部落地（截至 2026-10-28）。剩余可选事项：

- **真连复测**：手动删 `@Disabled` 跑 `QaEvalRunnerTest` / `IntentEvalRunnerTest` / `AgentEvalRunnerTest`，固化三套指标实测值进 README（当前纯函数单测全绿，真连指标仅阶段一 QA 有实测值）。
- **评测集扩充**：补难样本（语义等价、多跳、跨章矛盾）。现有 10 条 QA 偏浅，首次全满分已暴露「设定直接命中、无幻觉样本」的区分度不足。
- **线上回灌闭环**：`eval_trace` 已落表，运营侧人工抽检坏样本 → 回灌 `dataset.json` 的 `badCases`；可进一步做「自动回灌工具」或「评测报告面板」。
- **Citation Precision/Coverage**：复用设定四元组的 `evidence` 字段，给回答加出处标注后评测引用质量（第 6 节第 5 条）。

---

## 9. 实测结果汇总（截至 2026-10-28）

### 9.1 检索层（`RagEvaluator`，真连 PG + embedding）

| 指标 | 结果 | 说明 |
| --- | --- | --- |
| `Recall@5` | **1.0** | 8 条 rag 样本 |
| `MRR` | **1.0** | 同上 |
| `ContextPrecision@K` | 纯函数单测通过 | 阶段一新增，暂未接运行器输出真连值 |

### 9.2 归因 + 回答层（`LlmJudgeService`，真连 LLM，10 样本）

| 指标 | 结果 |
| --- | --- |
| `ContextRecall` | **1.0000**（7/10 有 goldenContextIds） |
| `Faithfulness` | **1.00** |
| `AnswerRelevancy` | **1.00** |
| `AnswerAccuracy` | **1.00**（10/10 有参考答案） |
| 加权总分（整体均值） | **1.0000** |

> 10 样本 = 7 正 + 3 负（无答案场景）。负样本暴露并修复「无答案空回答」bug（`QUERY_SOP` 强化 + `ToolCallAgent.act` 兜底）后复测全满分；失败样本回放 = 无失败样本。首次 7 样本全满分暴露评测集偏简单（设定直接命中、无幻觉样本），需后续补难样本增强区分度。

### 9.3 归并判定（`ConsistencyEvaluator`，真连 PG，14 样本）

| 指标 | 别名归一前 | 归一后 |
| --- | --- | --- |
| `F1` | 0.625 | **0.875** |
| `Recall` | 0.714 | **1.000**（冲突零漏检） |

### 9.4 阶段二加权总分 + 失败回放（`ScoreAggregator`）

- 纯函数单测通过（10 用例，进 CI）；
- 真连（`QaEvalRunnerTest`）：加权总分（整体均值）= **1.0000**，失败样本回放 = **无失败样本**（10/10 满分）。

### 9.5 阶段三 Agent 层（`AgentEvaluator` / 运行器）

- `AgentEvaluator`（工具 F1 + 意图准确率）纯函数单测通过（10 用例，进 CI）；
- 真连：**Intent Accuracy = 1.0000（6/6）**、**Tool Call F1 = 1.0000（平均）**、**Goal Accuracy = 1.0000（5/5）**。
