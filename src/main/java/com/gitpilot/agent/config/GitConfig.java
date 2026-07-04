package com.gitpilot.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Git workspace configuration.
 * <p>
 * The repo path defaults to the current working directory (where the MCP
 * client spawns the process). Override with GIT_REPO_PATH env var to pin
 * to a specific repo.
 */
@Configuration
@ConfigurationProperties(prefix = "git")
public class GitConfig {

    /**
     * Path to the git repository. Defaults to user.dir (CWD).
     */
    private String repoPath = System.getProperty("user.dir");

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }
}
