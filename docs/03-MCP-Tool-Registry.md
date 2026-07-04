# 03 — MCP Tool Registry

Every MCP tool registered by this server, with its signature, git operations, and status.

## Tool inventory

| Tool | Tier | Status | AI? | Since |
|---|---|---|---|---|
| `gitStatus` | 1 | implemented | No | 0.2.0 |
| `gitCreateFeatureBranch` | 1 | implemented | No | 0.2.0 |
| `gitCommit` | 1 | implemented | No | 0.2.0 |
| `gitShowStagedChanges` | 1 | implemented | No | 0.2.0 |
| `gitGenerateCommitMessage` | 2 | scaffold (AI stub) | Yes | 0.2.0 |
| `gitGeneratePrDescription` | 2 | scaffold (AI stub) | Yes | 0.2.0 |

---

## Tier 1 — Pure Git Tools

### `gitStatus`

**Status:** implemented

```java
@Tool(description = "Show current git status — branch name, staged files, modified files, and untracked files...")
public String gitStatus()
```

**Git operations:** `git.status().call()` → staged/changed/modified/untracked sets
**Returns:** Markdown with branch name + categorized file lists
**Error handling:** catches Exception, returns error string

### `gitCreateFeatureBranch`

**Status:** implemented

```java
@Tool(description = "Create a new git branch and switch to it. Use when the user asks to start a feature...")
public String gitCreateFeatureBranch(
    @ToolParam(description = "Branch name, e.g. 'feat/add-login' or 'fix/null-pointer'") String branchName)
```

**Git operations:** `git.branchCreate().setName(name).call()` → `git.checkout().setName(name).call()`
**Returns:** confirmation with branch name
**Validation:** branch name required, non-blank
**Error handling:** catches GitAPIException, returns error string

### `gitCommit`

**Status:** implemented

```java
@Tool(description = "Stage all changes (git add -A) and commit with the given message...")
public String gitCommit(
    @ToolParam(description = "The commit message. Follow conventional commits...") String message)
```

**Git operations:** `git.add().addFilepattern(".").call()` → `git.commit().setMessage(msg).call()`
**Returns:** abbreviated commit hash + message
**Validation:** message required, non-blank
**Error handling:** catches GitAPIException, returns error string

### `gitShowStagedChanges`

**Status:** implemented

```java
@Tool(description = "Show the staged diff and recent commit history...")
public String gitShowStagedChanges()
```

**Git operations:** `git.diff()` with index tree vs HEAD tree → `git.log().setMaxCount(10).call()`
**Returns:** Markdown with staged diff (truncated at 8000 chars) + last 10 commit messages
**AI note:** This is the input context for `gitGenerateCommitMessage`

---

## Tier 2 — AI-Assisted Tools

### `gitGenerateCommitMessage`

**Status:** scaffold — AI stub (returns context + placeholder, no ChatClient call yet)

```java
@Tool(description = "Generate a conventional commit message from the currently staged changes...")
public String gitGenerateCommitMessage()
```

**Git operations:** `stagedDiff()` + `recentCommits(10)` → context block
**Planned AI flow:** ChatClient.prompt().system("Generate a conventional commit message...").user(context).call().content()
**Returns:** currently returns context + "AI generation not yet wired" note
**TODO:** inject ChatClient, wire the AI call

### `gitGeneratePrDescription`

**Status:** scaffold — AI stub (returns context + placeholder, no ChatClient call yet)

```java
@Tool(description = "Generate a pull request description from the current branch diff vs main/master...")
public String gitGeneratePrDescription()
```

**Git operations:** `currentBranch()` + `branchDiff()` + `recentCommits(10)` → context block
**Planned AI flow:** ChatClient.prompt().system("Generate a PR description...").user(context).call().content()
**Edge case:** returns early if on default branch or no diff
**Returns:** currently returns context + "AI generation not yet wired" note
**TODO:** inject ChatClient, wire the AI call

---

## Template for future tools

```markdown
## `toolName`

**Status:** planned | implemented | deprecated (since version)

### Signature
<java method signature>

### Git operations
<JGit calls the tool makes>

### Returns
<description of output format>

### AI integration (Tier 2 only)
<ChatClient flow, system prompt, provider gating>
```
