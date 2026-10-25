package com.xiaoyu.lorenotifymcpserver;

import com.xiaoyu.lorenotifymcpserver.tool.EmailTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LoreNotifyMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoreNotifyMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider notifyTools(EmailTool emailTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(emailTool)
                .build();
    }
}
