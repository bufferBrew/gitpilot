package com.gitpilot.agent.repository;

import com.gitpilot.agent.config.GitConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * All git operations via JGit — no CLI dependency.
 */
@Component
public class GitRepository {

    private final Path repoPath;

    public GitRepository(GitConfig config) {
        this.repoPath = Path.of(config.getRepoPath());
    }

    // ---- repo handle ----

    private Repository openRepo() throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(repoPath.resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .build();
    }

    private Git openGit() throws IOException {
        return new Git(openRepo());
    }

    // ---- read operations ----

    /**
     * Current branch short name, e.g. "main" or "feat/my-branch".
     */
    public String currentBranch() throws IOException {
        try (var repo = openRepo()) {
            return repo.getBranch();
        }
    }

    /**
     * Default branch name — "main" if it exists, otherwise "master".
     */
    public String defaultBranch() throws IOException {
        try (var repo = openRepo()) {
            for (String candidate : List.of("main", "master")) {
                try {
                    repo.resolve("refs/heads/" + candidate);
                    return candidate;
                } catch (Exception ignored) {
                }
            }
            return "main"; // fallback
        }
    }

    /**
     * Diff of the current branch against the default branch (main/master).
     * Returns empty string if they point to the same commit.
     */
    public String branchDiff() throws IOException {
        try (var repo = openRepo();
             var git = new Git(repo)) {

            var defaultBranch = defaultBranch();
            var currentBranch = currentBranch();

            if (currentBranch.equals(defaultBranch)) {
                return "[on default branch — no branch diff available]";
            }

            var head = repo.resolve("refs/heads/" + currentBranch);
            var base = repo.resolve("refs/heads/" + defaultBranch);

            if (head == null || base == null || head.equals(base)) {
                return "[no changes vs " + defaultBranch + "]";
            }

            return formatDiff(repo, base, head);
        }
    }

    /**
     * Diff of staged changes (what would be committed).
     */
    public String stagedDiff() throws IOException {
        try (var repo = openRepo();
             var git = new Git(repo)) {

            var head = repo.resolve("HEAD");
            if (head == null) {
                return "[no commits yet]";
            }

            var indexDiff = git.diff()
                    .setNewTree(repo.newObjectInserter().newParser().parseTree(
                            repo.readDirCache().writeTree(repo.newObjectInserter())))
                    .setOldTree(repo.parseCommit(head).getTree())
                    .call();

            return formatDiff(repo, head, null);
        } catch (GitAPIException e) {
            throw new IOException("Failed to get staged diff: " + e.getMessage(), e);
        }
    }

    /**
     * Diff of working-tree changes (unstaged).
     */
    public String unstagedDiff() throws IOException {
        try (var repo = openRepo();
             var git = new Git(repo)) {

            var diffOut = new ByteArrayOutputStream();
            try (var formatter = new DiffFormatter(diffOut)) {
                formatter.setRepository(repo);
                var status = git.status().call();
                for (String file : status.getUncommittedChanges()) {
                    // diff the working tree vs index for each file
                }
            }
        } catch (GitAPIException e) {
            throw new IOException("Failed to get unstaged diff: " + e.getMessage(), e);
        }
        return ""; // placeholder
    }

    /**
     * Short status: branch name + modified/staged/untracked file lists.
     */
    public String shortStatus() throws IOException {
        try (var repo = openRepo();
             var git = new Git(repo)) {

            var status = git.status().call();
            var sb = new StringBuilder();
            sb.append("Branch: ").append(currentBranch()).append("\n\n");

            appendFileList(sb, "Staged", status.getChanged());
            appendFileList(sb, "Modified (unstaged)", status.getModified());
            appendFileList(sb, "Untracked", status.getUntracked());

            if (status.getChanged().isEmpty()
                    && status.getModified().isEmpty()
                    && status.getUntracked().isEmpty()) {
                sb.append("Working tree clean.\n");
            }

            return sb.toString();
        } catch (GitAPIException e) {
            throw new IOException("Failed to get status: " + e.getMessage(), e);
        }
    }

    /**
     * Recent commit messages (default: last 10).
     */
    public List<String> recentCommits(int count) throws IOException {
        try (var repo = openRepo();
             var git = new Git(repo)) {

            var messages = new ArrayList<String>();
            for (var commit : git.log().setMaxCount(count).call()) {
                messages.add(commit.getShortMessage());
            }
            return messages;
        } catch (GitAPIException e) {
            throw new IOException("Failed to get recent commits: " + e.getMessage(), e);
        }
    }

    // ---- write operations ----

    /**
     * Create and switch to a new branch from the current HEAD.
     */
    public String createBranch(String branchName) throws IOException {
        try (var git = openGit()) {
            git.branchCreate()
                    .setName(branchName)
                    .call();
            git.checkout()
                    .setName(branchName)
                    .call();
            return "Created and switched to branch: " + branchName;
        } catch (GitAPIException e) {
            throw new IOException("Failed to create branch '" + branchName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Stage all files and commit with the given message.
     */
    public String commit(String message) throws IOException {
        try (var git = openGit()) {
            // Stage everything (git add -A)
            git.add().addFilepattern(".").call();

            var commit = git.commit()
                    .setMessage(message)
                    .call();

            return "Committed " + commit.abbreviate(7).name()
                    + ": " + message;
        } catch (GitAPIException e) {
            throw new IOException("Failed to commit: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the repo has staged changes ready to commit.
     */
    public boolean hasStagedChanges() throws IOException {
        try (var git = openGit()) {
            var status = git.status().call();
            return !status.getChanged().isEmpty() || !status.getAdded().isEmpty();
        } catch (GitAPIException e) {
            throw new IOException("Failed to check status: " + e.getMessage(), e);
        }
    }

    // ---- helpers ----

    private String formatDiff(Repository repo, ObjectId oldId, ObjectId newId) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var formatter = new DiffFormatter(out)) {
            formatter.setRepository(repo);
            formatter.format(oldId, newId);
        }
        var diff = out.toString();
        // Limit to 8000 chars — LLMs don't need raw full diffs
        if (diff.length() > 8000) {
            diff = diff.substring(0, 8000) + "\n... [diff truncated at 8000 chars]";
        }
        return diff;
    }

    private void appendFileList(StringBuilder sb, String label, Set<String> files) {
        if (!files.isEmpty()) {
            sb.append("**").append(label).append(":**\n");
            for (var f : files) {
                sb.append("  - ").append(f).append("\n");
            }
            sb.append("\n");
        }
    }
}
