package com.xiaoyu.loredocparsermcpserver;

import com.xiaoyu.loredocparsermcpserver.tool.DocumentParserTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LoreDocParserMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoreDocParserMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider docParserTools(DocumentParserTool documentParserTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(documentParserTool)
                .build();
    }

}
