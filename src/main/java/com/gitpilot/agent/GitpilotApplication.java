package com.gitpilot.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * gitpilot: a model-agnostic Java MCP server providing git workflow tools.
 * <p>
 * Exposes {@link org.springframework.ai.tool.annotation.Tool @Tool}-annotated
 * beans as MCP tools over stdio:
 * <ul>
 *   <li>Tier 1 (pure git): status, create-branch, commit</li>
 *   <li>Tier 2 (AI-assisted): generate-commit-message, generate-pr-description
 *       (ChatClient, provider from application.yml)</li>
 * </ul>
 * Any MCP client — Claude Code, Claude Desktop — discovers and invokes them.
 * <p>
 * No LLM calls happen unless you inject a
 * {@link org.springframework.ai.chat.client.ChatClient} into a tool.
 */
@SpringBootApplication
public class GitpilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitpilotApplication.class, args);
    }
}
