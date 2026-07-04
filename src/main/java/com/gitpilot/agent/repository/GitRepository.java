package com.gitpilot.agent.repository;

import com.gitpilot.agent.config.GitConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * All git operations via JGit — no CLI dependency.
 * <p>
 * Handles reading (status, diff, log) and writing (branch, commit) against
 * a local git repository. Every method opens and closes its own repo handle
 * via try-with-resources — no shared state, no stale handles.
 * <p>
 * Methods that detect error states (fresh repo, clean tree, invalid input)
 * return descriptive strings like {@code "[no commits yet]"} rather than
 * throwing — the tool layer can check for these brackets to decide what to
 * surface to the user.
 *
 * @author bufferbrew
 * @since 0.2.0
 */
@Component
public class GitRepository {

    private final Path repoPath;

    public GitRepository(GitConfig config) {
        this.repoPath = Path.of(config.getRepoPath());
    }

    /** Regex for valid git branch names (git-check-ref-format). */
    private static final Pattern VALID_BRANCH = Pattern.compile(
            "^(?![/.])(?!.*//)(?!.*\\.\\.)(?!.*[@\\{])[a-zA-Z0-9][a-zA-Z0-9._/-]*(?<![./])$");

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
     * Returns "HEAD (no commits yet)" for a fresh repo.
     */
    public String currentBranch() throws IOException {
        try (var repo = openRepo()) {
            if (repo.resolve("HEAD") == null) {
                return "HEAD (no commits yet)";
            }
            var branch = repo.getBranch();
            // On a fresh repo, getBranch() may return "HEAD" literally
            if (branch == null || branch.equals("HEAD")) {
                return "HEAD (no commits yet)";
            }
            return branch;
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
     * Diffs HEAD tree against the index (staging area).
     */
    public String stagedDiff() throws IOException {
        try (var repo = openRepo()) {

            var head = repo.resolve("HEAD");
            if (head == null) {
                return "[no commits yet — nothing to diff]";
            }

            var headCommit = repo.parseCommit(head);
            var reader = repo.newObjectReader();

            // Parse HEAD tree
            var oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, headCommit.getTree());

            // Build index tree (staged state)
            var inserter = repo.newObjectInserter();
            var indexTreeId = repo.readDirCache().writeTree(inserter);
            var newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, indexTreeId);

            var out = new ByteArrayOutputStream();
            try (var formatter = new DiffFormatter(out)) {
                formatter.setRepository(repo);
                formatter.format(oldTreeIter, newTreeIter);
            }

            var diff = out.toString();
            if (diff.isBlank()) {
                return "[no staged changes]";
            }
            if (diff.length() > 8000) {
                diff = diff.substring(0, 8000) + "\n... [diff truncated at 8000 chars]";
            }
            return diff;
        }
    }

    /**
     * Diff of working-tree changes (unstaged).
     * Uses DiffFormatter.scan() to compare index tree against the working tree.
     */
    public String unstagedDiff() throws IOException {
        try (var repo = openRepo()) {

            var reader = repo.newObjectReader();

            // Build index tree (staged state) as the "old" baseline
            var inserter = repo.newObjectInserter();
            var indexTreeId = repo.readDirCache().writeTree(inserter);
            var indexTreeIter = new CanonicalTreeParser();
            indexTreeIter.reset(reader, indexTreeId);

            // FileTreeIterator represents the working tree
            var workTreeIter = new FileTreeIterator(repo);

            var out = new ByteArrayOutputStream();
            try (var formatter = new DiffFormatter(out)) {
                formatter.setRepository(repo);
                // scan() returns DiffEntry list; format() writes each entry's diff
                var entries = formatter.scan(indexTreeIter, workTreeIter);
                for (var entry : entries) {
                    formatter.format(entry);
                }
            }

            var diff = out.toString();
            if (diff.isBlank()) {
                return "[no unstaged changes]";
            }
            if (diff.length() > 8000) {
                diff = diff.substring(0, 8000) + "\n... [diff truncated at 8000 chars]";
            }
            return diff;
        }
    }

    /**
     * Short status: branch name + modified/staged/untracked file lists.
     */
    public String shortStatus() throws IOException {
        try (var repo = openRepo();
             var git = new Git(repo)) {

            var status = git.status().call();
            var sb = new StringBuilder();

            var branch = currentBranch();
            sb.append("Branch: ").append(branch).append("\n");

            if (repo.resolve("HEAD") == null) {
                sb.append("(fresh repo — no commits yet)\n\n");
                // Still show the file lists — they matter for the first commit
                appendFileList(sb, "Files (will be in root commit)", status.getUntracked());
                if (status.getUntracked().isEmpty()) {
                    sb.append("No files yet. Make your first commit!\n");
                }
                return sb.toString();
            }

            sb.append("\n");
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
     * Returns an empty list for a fresh repo with no commits.
     */
    public List<String> recentCommits(int count) throws IOException {
        try (var repo = openRepo()) {
            if (repo.resolve("HEAD") == null) {
                return List.of("[no commits yet]");
            }
        }
        try (var git = openGit()) {
            var messages = new ArrayList<String>();
            for (var commit : git.log().setMaxCount(count).call()) {
                messages.add(commit.getShortMessage());
            }
            if (messages.isEmpty()) {
                return List.of("[no commits yet]");
            }
            return messages;
        } catch (GitAPIException e) {
            throw new IOException("Failed to get recent commits: " + e.getMessage(), e);
        }
    }

    // ---- write operations ----

    /**
     * Create and switch to a new branch from the current HEAD.
     *
     * @throws IOException if the branch name is invalid or already exists
     */
    public String createBranch(String branchName) throws IOException {
        // Validate branch name
        if (branchName == null || branchName.isBlank()) {
            throw new IOException("Branch name cannot be empty.");
        }
        if (branchName.startsWith("/") || branchName.endsWith("/")) {
            throw new IOException("Branch name cannot start or end with '/'.");
        }
        if (branchName.contains("..")) {
            throw new IOException("Branch name cannot contain '..'.");
        }
        if (branchName.contains(" ")) {
            throw new IOException("Branch name cannot contain spaces.");
        }
        if (!VALID_BRANCH.matcher(branchName).matches()) {
            throw new IOException("Invalid branch name '" + branchName
                    + "'. Use letters, digits, '/', '-', '_', and '.' only.");
        }

        try (var git = openGit()) {
            // Check if the branch already exists
            var existing = git.branchList().call();
            for (var ref : existing) {
                var shortName = ref.getName().replace("refs/heads/", "");
                if (shortName.equals(branchName)) {
                    throw new IOException("Branch '" + branchName + "' already exists.");
                }
            }

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
     *
     * @throws IOException if nothing to commit or the commit fails
     */
    public String commit(String message) throws IOException {
        try (var git = openGit()) {
            // Stage everything (git add -A)
            git.add().addFilepattern(".").call();

            // Verify there's something staged before attempting commit
            var status = git.status().call();
            if (status.getChanged().isEmpty()
                    && status.getAdded().isEmpty()
                    && status.getRemoved().isEmpty()) {
                throw new IOException("Nothing to commit — working tree is clean.");
            }

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
