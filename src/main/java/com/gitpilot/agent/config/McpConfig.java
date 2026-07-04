package com.gitpilot.agent.config;

import org.springframework.context.annotation.Configuration;

/**
 * MCP server configuration hook.
 * <p>
 * The {@code spring-ai-mcp-server-spring-boot-starter} auto-configures
 * the stdio transport and discovers all {@code @Tool} beans. This class
 * exists as a hook for manual MCP plumbing — custom tool call interceptors,
 * error handlers, or transport-level settings — when the defaults are not
 * sufficient.
 * <p>
 * Intentionally empty at present. The auto-configuration handles tool
 * registration, JSON-RPC framing, and stdio transport automatically.
 *
 * @author bufferbrew
 * @since 0.2.0
 */
@Configuration
public class McpConfig {
}
