package com.gitpilot.agent.config;

import com.gitpilot.agent.tools.GitTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the annotated git tools with Spring AI's MCP server. */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider gitToolCallbacks(GitTools gitTools) {
        return MethodToolCallbackProvider.builder().toolObjects(gitTools).build();
    }
}
