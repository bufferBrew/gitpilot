package com.gitpilot.agent.tools;

import com.gitpilot.agent.repository.GitRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for git workflow — branches, commits, commit messages, and
 * PR descriptions.
 * <p>
 * Two tiers of tools:
 * <ol>
 *   <li><b>Tier 1 — pure git</b> — {@code gitStatus}, {@code gitCreateFeatureBranch},
 *       {@code gitCommit}, {@code gitShowStagedChanges}. Zero API keys needed.
 *       All operations via JGit.</li>
 *   <li><b>Tier 2 — AI-assisted</b> — {@code gitGenerateCommitMessage},
 *       {@code gitGeneratePrDescription}. Uses Spring AI {@code ChatClient}
 *       (provider configured in {@code application.yml}, model-agnostic).</li>
 * </ol>
 * <p>
 * Every tool catches exceptions and returns a clear error string — never
 * a raw stack trace. Output is formatted as Markdown for LLM readability.
 * <p>
 * Tier 2 tools are currently scaffold stubs. They return context + a
 * placeholder note. Wire {@code ChatClient} to activate AI generation.
 *
 * @author bufferbrew
 * @since 0.2.0
 */
@Component
public class GitTools {

    private final GitRepository git;

    public GitTools(GitRepository git) {
        this.git = git;
    }

    // =========================================================================
    // Tier 1 — pure git operations (no AI)
    // =========================================================================

    /**
     * Show the current branch, staged/unstaged files, and untracked files.
     */
    @Tool(description = "Show current git status — branch name, staged files, modified files, and untracked files. Use when the user asks 'what's the status' or 'what branch am I on'.")
    public String gitStatus() {
        try {
            return git.shortStatus();
        } catch (Exception e) {
            return "Error reading git status: " + e.getMessage();
        }
    }

    /**
     * Create a feature branch from the current HEAD and switch to it.
     */
    @Tool(description = "Create a new git branch and switch to it. Use when the user asks to start a feature or create a branch. Branch name should follow the format 'feat/<description>' or 'fix/<description>'.")
    public String gitCreateFeatureBranch(
            @ToolParam(description = "Branch name, e.g. 'feat/add-login' or 'fix/null-pointer'") String branchName) {

        if (branchName == null || branchName.isBlank()) {
            return "Error: branch name is required.";
        }

        try {
            return git.createBranch(branchName);
        } catch (Exception e) {
            return "Error creating branch '" + branchName + "': " + e.getMessage();
        }
    }

    /**
     * Stage all changes and commit with a user-provided message.
     */
    @Tool(description = "Stage all changes (git add -A) and commit with the given message. Use when the user provides an explicit commit message. For generating a message from the diff, use gitGenerateCommitMessage instead.")
    public String gitCommit(
            @ToolParam(description = "The commit message. Follow conventional commits: 'feat:', 'fix:', 'docs:', 'chore:', 'refactor:'") String message) {

        if (message == null || message.isBlank()) {
            return "Error: commit message is required.";
        }

        try {
            return git.commit(message);
        } catch (Exception e) {
            return "Error committing: " + e.getMessage();
        }
    }

    // =========================================================================
    // Tier 2 — AI-assisted tools (ChatClient, provider from application.yml)
    // =========================================================================

    /**
     * Show what would go into a commit: staged diff + recent messages for context.
     * This is the input the LLM would use to generate a commit message.
     */
    @Tool(description = "Show the staged diff and recent commit history. Use this to see what a generated commit message would be based on, or before manually writing a commit message.")
    public String gitShowStagedChanges() {
        try {
            var sb = new StringBuilder();

            sb.append("## Staged Changes\n\n");
            try {
                sb.append("```diff\n");
                sb.append(git.stagedDiff());
                sb.append("\n```\n");
            } catch (Exception e) {
                sb.append("(could not read staged diff: ").append(e.getMessage()).append(")\n");
            }

            sb.append("\n## Recent Commits (for style context)\n\n");
            for (var msg : git.recentCommits(10)) {
                sb.append("- ").append(msg).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ---- AI-scaffold tools: ChatClient wiring goes here ----
    // Uncomment and inject ChatClient when ready for the AI tier.

    /**
     * Generate a conventional commit message from staged changes.
     * <p>
     * TODO: inject ChatClient, pass stagedDiff() + recentCommits() as context,
     * return the LLM's suggested commit message.
     */
    @Tool(description = "Generate a conventional commit message from the currently staged changes. Reads the staged diff and recent commit history, then uses AI to suggest a message matching the repo's style. Use when the user says 'write a commit message' or 'what should I commit this as?'")
    public String gitGenerateCommitMessage() {
        try {
            var staged = git.stagedDiff();
            if (staged.isBlank() || staged.startsWith("[")) {
                return "No staged changes to generate a commit message from. Stage files first with `git add`.";
            }

            var recent = git.recentCommits(10);
            var context = """
                    ## Staged Diff
                    ```diff
                    %s
                    ```

                    ## Recent Commit Messages (match this style)
                    %s
                    """.formatted(staged, String.join("\n", recent));

            // TODO: pass context to ChatClient
            // return chatClient.prompt()
            //     .system("Generate a conventional commit message...")
            //     .user(context)
            //     .call()
            //     .content();

            return "## Generate Commit Message\n\n"
                    + "**Staged diff** (input for AI):\n\n```diff\n"
                    + (staged.length() > 3000 ? staged.substring(0, 3000) + "\n... [truncated]" : staged)
                    + "\n```\n\n"
                    + "**Recent commits** (style reference):\n\n"
                    + String.join("\n", recent.stream().map(m -> "- " + m).toList())
                    + "\n\n---\n"
                    + "AI generation not yet wired. Will use ChatClient (model-agnostic) to produce a conventional commit message from the diff above.";

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Generate a PR description from the branch diff vs main/master.
     * <p>
     * TODO: inject ChatClient, pass branchDiff() as context,
     * return the LLM's PR description.
     */
    @Tool(description = "Generate a pull request description from the current branch diff vs main/master. Reads the full branch diff, recent commits, and uses AI to produce a structured PR body with summary, changes, and testing notes. Use when the user says 'write a PR description' or 'create a PR'.")
    public String gitGeneratePrDescription() {
        try {
            var branch = git.currentBranch();
            var diff = git.branchDiff();
            var recent = git.recentCommits(10);

            if (diff.startsWith("[")) {
                return "Cannot generate PR description: " + diff;
            }

            var context = """
                    ## Branch: %s

                    ## Changes (diff vs main)
                    ```diff
                    %s
                    ```

                    ## Commits on this branch
                    %s
                    """.formatted(branch,
                    diff.length() > 5000 ? diff.substring(0, 5000) + "\n... [truncated]" : diff,
                    String.join("\n", recent.stream().map(m -> "- " + m).toList()));

            // TODO: pass context to ChatClient
            // return chatClient.prompt()
            //     .system("Generate a PR description...")
            //     .user(context)
            //     .call()
            //     .content();

            return "## Generate PR Description\n\n"
                    + context
                    + "\n\n---\n"
                    + "AI generation not yet wired. Will use ChatClient (model-agnostic) to produce a structured PR description from the branch diff above.";

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
