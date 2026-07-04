# 02 — Development

## Workflow: code → docs → commit → merge to main

Every change follows this sequence. No exceptions.

### 1. Code

Make the change. Keep it minimal — one concern per commit.

If you're adding a Tier 1 (pure git) tool:
```java
@Tool(description = "What this tool does — shown to the LLM")
public String myGitTool(
    @ToolParam(description = "What this param means") String param) {
    // 1. Call GitRepository method
    // 2. Format result as Markdown
    // 3. Return String (MCP tool results are text)
}
```

If you're adding a Tier 2 (AI-assisted) tool:
```java
private final ChatClient chatClient;  // injected

@Tool(description = "...")
public String myAiTool(@ToolParam(description = "...") String input) {
    var context = git.someMethod();
    return chatClient.prompt()
        .system("You are a git workflow assistant.")
        .user(context)
        .call()
        .content();
}
```

### 2. Docs

Update docs in the **same commit** as the code change.

| Change | Update |
|---|---|
| New tool | `docs/03-MCP-Tool-Registry.md` + `docs/README.md` status |
| Architecture change | `docs/01-Architecture.md` |
| New convention or pattern | `docs/02-Development.md` |
| Config/env/setup change | `README.md` |
| Any change | Obsidian: `gitpilot/project-status.md` |

### 3. Verify

```powershell
mvn compile          # must pass — zero errors
```

### 4. Commit

```
<type>: <imperative, lowercase, ≤72 chars>

<body — optional, wrap at 72. Explain WHY, not WHAT.>

Related: docs/<file>.md
Obsidian: gitpilot/<note>.md
```

Types:
- `feat:` — new tool, new capability
- `fix:` — bug fix
- `docs:` — documentation-only change
- `chore:` — build, deps, config, comments
- `refactor:` — restructure without behavior change

Examples:
```
feat: add gitCreateFeatureBranch tool

Creates and switches to a new branch using JGit.
First write tool in the server.

Related: docs/03-MCP-Tool-Registry.md
```

### 5. Merge to main

This is a solo project. Default to **committing directly to main**.

For experimental work, create a short-lived branch:

```powershell
git checkout -b feat/my-experiment
# ... work, commit ...
git checkout main
git merge feat/my-experiment
git branch -d feat/my-experiment
```

Never:
- Amend commits already on main
- Force-push to main
- Leave stale branches (delete after merge)

## Tool patterns

### Tier 1: Pure git tool

```java
@Tool(description = "Show current git status — branch, staged, modified, untracked")
public String gitStatus() {
    try {
        return git.shortStatus();
    } catch (Exception e) {
        return "Error reading git status: " + e.getMessage();
    }
}
```

### Tier 2: AI-assisted tool

```java
private final ChatClient chatClient;

@Tool(description = "Generate a conventional commit message from staged changes")
public String gitGenerateCommitMessage() {
    var staged = git.stagedDiff();
    var recent = git.recentCommits(10);
    var context = """
            ## Staged Diff
            ```diff
            %s
            ```
            ## Recent Commits (match this style)
            %s
            """.formatted(staged, String.join("\n", recent));

    return chatClient.prompt()
        .system("Generate a conventional commit message...")
        .user(context)
        .call()
        .content();
}
```

### GitRepository pattern

```java
@Component
public class GitRepository {
    // All JGit operations. Returns strings, never throws to the tool layer
    // (catch and wrap as IOException with a clear message).
}
```

## Adding a new tool — checklist

- [ ] GitRepository method (or use existing)
- [ ] `@Tool` method in `GitTools.java`
- [ ] `@ToolParam` on every parameter with a `description`
- [ ] Sensible default for optional params (null check)
- [ ] Exception handling — catch and return error string, never throw
- [ ] `mvn compile` passes
- [ ] Entry in `docs/03-MCP-Tool-Registry.md`
- [ ] `docs/README.md` status updated
- [ ] Commit with `feat:` prefix

## Wiring AI (Tier 2) — checklist

- [ ] Provider configured in `application.yml` (or env vars)
- [ ] `ChatClient` injected via constructor
- [ ] System prompt defines the role: "you are a git workflow assistant..."
- [ ] User prompt passes structured context (diff, commit history) — not raw text
- [ ] Diff truncated to reasonable size (LLM context window is finite)
- [ ] Graceful degradation if AI call fails
- [ ] No hardcoded model names or provider types

## Obsidian sync

After every change, update the Obsidian vault at
`C:\Users\kartik\Documents\Obsidian Vault\gitpilot\`:

- **Every change:** update `project-status.md` (what changed, why)
- **Architecture change:** update `01-Architecture.md` to match
- **New tool:** update `03-MCP-Tool-Registry.md` to match `docs/`
- **Major decision:** add entry to `14-Decision-Log.md`

Vault notes can be looser than docs/ — they're for thinking, not just
reference. Use Obsidian links (`[[note-name]]`) to connect ideas.
