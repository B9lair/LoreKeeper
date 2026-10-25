package com.xiaoyu.loredatabasemcpserver;

import com.xiaoyu.loredatabasemcpserver.tool.DatabaseQueryTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LoreDatabaseMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoreDatabaseMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider databaseTools(DatabaseQueryTool databaseQueryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(databaseQueryTool)
                .build();
    }
}
