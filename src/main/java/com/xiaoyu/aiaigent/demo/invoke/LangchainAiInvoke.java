package com.xiaoyu.aiaigent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

public class LangchainAiInvoke {

    public static void main(String[] args){
        ChatLanguageModel qwenModel = QwenChatModel.builder()
                .apiKey(TestApiKey.API_KEY)
                .modelName("qwen-max")
                .build();
        String answer = qwenModel.chat("我是一个计算机专业的大学生，我要做AI编程项目");
        System.out.println(answer);

    }
}
