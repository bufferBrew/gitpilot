package com.gitpilot.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Git workspace configuration bound from {@code application.yml} and env vars.
 * <p>
 * The repo path defaults to the current working directory — whatever directory
 * the MCP client spawns the process in. Override with the {@code GIT_REPO_PATH}
 * env var or {@code git.repo-path} in yml to pin to a specific repository.
 *
 * @author bufferbrew
 * @since 0.2.0
 */
@Configuration
@ConfigurationProperties(prefix = "git")
public class GitConfig {

    /**
     * Absolute path to the git repository to operate on.
     * Defaults to {@code user.dir} (the JVM's working directory).
     */
    private String repoPath = System.getProperty("user.dir");

    /**
     * @return the configured git repository path
     */
    public String getRepoPath() {
        return repoPath;
    }

    /**
     * @param repoPath absolute or relative path to a git repository
     */
    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }
}
