package com.xiaoyu.aiaigent.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM-as-Judge：对 RAG 最终回答做归因层（Faithfulness）与回答层（Relevancy/Accuracy）打分。
 * <p>
 * 复用项目「结构化输出」模式（同 IntentRouter / EntityExtractor 的 .entity(Xxx.class)）。
 * 评测结果随机、慢、费 key，仅由 QaEvalRunnerTest（@Disabled）真连调用，不进常规 CI。
 */
@Slf4j
@Component
public class LlmJudgeService {

    private final ChatClient chatClient;

    public LlmJudgeService(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /** 结构化判定结果 */
    public record JudgeVerdict(
            double faithfulness,      // 0~1：回答中的 claim 能从上下文推出的比例（归因层核心，直接量化幻觉）
            double answerRelevancy,   // 0~1：回答是否切题（回答层）
            double answerAccuracy,    // 0~1：与参考答案一致；无 reference 返回 -1（回答层）
            List<String> unsupportedClaims,  // 无法被上下文支撑的 claim（幻觉点，用于失败样本回放）
            List<String> badCaseViolations   // 回答中「错误地把 badCases 当成答案」的部分（澄清式提及不算）
    ) {}

    /**
     * 打分。judge 自身随机，失败时返回零分兜底而非中断评测（answerAccuracy 无 reference 时置 -1）。
     *
     * @param question         用户问题
     * @param answer           最终回答
     * @param retrievedContext 检索上下文（供 faithfulness 判定 claim 是否有据）
     * @param referenceAnswer  参考答案，可为 null（此时跳过 Accuracy）
     */
    public JudgeVerdict judge(String question, String answer, String retrievedContext, String referenceAnswer) {
        return judge(question, answer, retrievedContext, referenceAnswer, List.of());
    }

    /**
     * 打分（带幻觉点清单）。badCases 是「回答不应错误地当成答案」的幻觉点，judge 语义判断是否违规，
     * 以区分「错误断言」与「澄清式提及」（纯字符串匹配会误判后者）。
     */
    public JudgeVerdict judge(String question, String answer, String retrievedContext,
                              String referenceAnswer, List<String> badCases) {
        boolean hasRef = referenceAnswer != null && !referenceAnswer.isBlank();
        String ref = hasRef ? referenceAnswer : "（无参考答案，answerAccuracy 填 -1）";
        String badCaseDesc = (badCases == null || badCases.isEmpty()) ? "（无）" : String.join("、", badCases);
        try {
            JudgeVerdict verdict = chatClient.prompt()
                    .system("""
                            你是 RAG 评测裁判。基于「检索上下文」与「参考答案」给最终回答打分：
                            1. faithfulness：把回答拆成若干 claim，逐个判断能否从「检索上下文」推出，能推出的比例（0~1）。
                            2. answerRelevancy：回答是否直接、恰当地回应原问题（0~1），只判是否切题，不判事实对错。
                            3. answerAccuracy：与参考答案一致性（0~1）；无参考答案时填 -1。
                            4. unsupportedClaims：列出无法被上下文支撑的 claim（幻觉点）；没有则空数组。
                            5. badCaseViolations：幻觉点清单——%s。列出回答中「错误地把这些幻觉点当成答案」的部分；仅澄清式提及（如说明"那是别的角色的，不是本问题所问"）不算违规；没有则空数组。
                            只输出 JSON：{"faithfulness":0.0,"answerRelevancy":0.0,"answerAccuracy":0.0,"unsupportedClaims":[],"badCaseViolations":[]}，不要解释。"""
                            .formatted(badCaseDesc))
                    .user("问题：%s\n检索上下文：%s\n最终回答：%s\n参考答案：%s"
                            .formatted(question, retrievedContext, answer, ref))
                    .call()
                    .entity(JudgeVerdict.class);
            return normalize(verdict, hasRef);
        } catch (Exception e) {
            log.warn("Judge 打分失败，返回零分兜底：{}", e.getMessage());
            return zeroVerdict(hasRef);
        }
    }

    /** 目标达成判定结果（Agent 层 Goal Accuracy 用） */
    public record GoalVerdict(boolean satisfied) {
    }

    /**
     * 判定最终回答是否满足 successCriteria（Agent 层 Goal Accuracy）。
     * judge 自身随机，失败时返回 false 兜底（不中断评测）。
     */
    public boolean judgeGoal(String question, String answer, String successCriteria) {
        try {
            GoalVerdict v = chatClient.prompt()
                    .system("""
                            你是 Agent 目标达成裁判。判断「最终回答」是否满足「成功判据 successCriteria」。
                            满足即 satisfied=true（回答确实达成了判据描述的目标），否则 false。
                            只输出 JSON：{"satisfied":true} 或 {"satisfied":false}，不要解释。""")
                    .user("问题：%s\n最终回答：%s\n成功判据：%s"
                            .formatted(question, answer, successCriteria))
                    .call()
                    .entity(GoalVerdict.class);
            return v != null && v.satisfied();
        } catch (Exception e) {
            log.warn("Goal 判定失败，返回 false 兜底：{}", e.getMessage());
            return false;
        }
    }

    /** null 字段兜底（LLM 结构化输出不稳定时，List 字段可能为 null） */
    private static JudgeVerdict normalize(JudgeVerdict v, boolean hasRef) {
        if (v == null) {
            return zeroVerdict(hasRef);
        }
        return new JudgeVerdict(
                v.faithfulness(), v.answerRelevancy(), v.answerAccuracy(),
                v.unsupportedClaims() == null ? List.of() : v.unsupportedClaims(),
                v.badCaseViolations() == null ? List.of() : v.badCaseViolations());
    }

    private static JudgeVerdict zeroVerdict(boolean hasRef) {
        return new JudgeVerdict(0.0, 0.0, hasRef ? 0.0 : -1.0, List.of(), List.of());
    }
}
