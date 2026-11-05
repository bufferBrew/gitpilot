package com.gitpilot.agent.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.mcp.server.stdio=false")
class McpConfigTest {
    @Autowired
    private ToolCallbackProvider callbacks;

    @Test
    void exposesAllGitToolsToTheMcpServer() {
        var names = Arrays.stream(callbacks.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertThat(names).isEqualTo(Set.of(
                "gitStatus", "gitCreateFeatureBranch", "gitCommit",
                "gitShowStagedChanges", "gitGenerateCommitMessage",
                "gitGeneratePrDescription"));
    }
}
