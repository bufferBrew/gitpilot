package com.gitpilot.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * gitpilot: a model-agnostic Java MCP server providing git workflow tools.
 * <p>
 * Exposes {@link org.springframework.ai.tool.annotation.Tool @Tool}-annotated
 * beans as MCP tools over stdio:
 * <ul>
 *   <li><b>Tier 1 (pure git)</b> — status, create-branch, commit, show-staged.
 *       Zero API keys needed. Uses JGit for all operations.</li>
 *   <li><b>Tier 2 (AI-assisted)</b> — generate-commit-message,
 *       generate-pr-description. Uses Spring AI {@code ChatClient}
 *       (provider configured in {@code application.yml}).</li>
 * </ul>
 * <p>
 * Any MCP client — Claude Code, Claude Desktop, or a custom agent —
 * discovers tools at startup via {@code tools/list} and invokes them
 * via {@code tools/call} over stdio JSON-RPC.
 * <p>
 * No LLM calls happen unless a {@link org.springframework.ai.chat.client.ChatClient}
 * is explicitly injected into a tool. The server is useful with zero AI dependencies.
 *
 * @author bufferbrew
 * @since 0.2.0
 */
@SpringBootApplication
public class GitpilotApplication {

    /**
     * Boots the Spring context and starts the MCP server on stdio.
     * <p>
     * Requires {@code spring.ai.mcp.server.transport=stdio} in config.
     * No HTTP port is bound — all communication is over stdin/stdout.
     *
     * @param args command-line arguments (passed to Spring Boot)
     */
    public static void main(String[] args) {
        SpringApplication.run(GitpilotApplication.class, args);
    }
}
