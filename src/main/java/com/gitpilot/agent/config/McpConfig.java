package com.gitpilot.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP server configuration.
 * <p>
 * The {@code spring-ai-mcp-server-spring-boot-starter} auto-configures the
 * stdio transport and registers {@code @Tool} beans. This class is a hook
 * for any additional MCP plumbing (custom converters, interceptors, etc.)
 * you may need later.
 * <p>
 * For now it's intentionally empty — the auto-configuration handles everything.
 */
@Configuration
public class McpConfig {

    // Future: add McpServerFeatures.SyncServerSpec customizations here.
    // Spring AI auto-config creates the server from all @Tool beans.
}
