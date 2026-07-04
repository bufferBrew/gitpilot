# gitpilot

Model-agnostic Java MCP server providing **git workflow tools** —
branches, commits, commit message generation, and PR descriptions.
Works on any git repo. No hardcoded LLM provider — swap
Anthropic ↔ OpenAI ↔ Ollama in config.

## What it does

gitpilot exposes git operations as typed MCP tools that Claude Code
(or any MCP client) can call:

| Tier | Tool | AI? | What it does |
|---|---|---|---|
| 1 | `gitStatus` | No | Current branch, staged/unstaged/untracked files |
| 1 | `gitCreateFeatureBranch` | No | Create and switch to `feat/<name>` |
| 1 | `gitCommit` | No | Stage all + commit with provided message |
| 1 | `gitShowStagedChanges` | No | Staged diff + recent commits (context for a commit message) |
| 2 | `gitGenerateCommitMessage` | Yes | Generate conventional commit message from staged diff |
| 2 | `gitGeneratePrDescription` | Yes | Generate structured PR body from branch diff |

Tier 1 works with zero API keys. Tier 2 needs an LLM provider configured.

## How it fits

```
Claude Code / MCP Client
    │
    ├── stdio (MCP) ── gitpilot (Java)
    │                   git tools: status, branch, commit, PR description
    │                   operates on any local git repo via JGit
    │
    └── ... other MCP servers ...
```

## Quick start

### Prerequisites

- Java 21+
- A git repo (defaults to CWD; override with `GIT_REPO_PATH`)
- Gradle wrapper (`./gradlew`) — IntelliJ generates it on project open

### Run (tier 1 — no API keys needed)

```powershell
cd gitpilot
./gradlew bootRun
```

### Run (tier 2 — with AI provider)

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
./gradlew bootRun
```

The server starts and listens on stdio for MCP JSON-RPC messages.

### Register with Claude Code

```json
{
  "mcpServers": {
    "gitpilot": {
      "command": "cmd",
      "args": ["/c", "C:\\Users\\kartik\\Desktop\\code\\gitpilot\\gradlew.bat", "bootRun"],
      "env": {
        "GIT_REPO_PATH": "C:\\Users\\kartik\\Desktop\\code\\ai_dashboard"
      }
    }
  }
}
```

Add `ANTHROPIC_API_KEY` to `env` for AI tools.

## Architecture

See [docs/01-Architecture.md](docs/01-Architecture.md) for the full picture.

- **Model-agnostic:** AI tools use Spring AI `ChatClient`. Swap providers in config.
- **Git-first:** Tier 1 tools are pure JGit — useful with zero AI dependencies.
- **MCP-native:** `@Tool` beans, stdio transport. Any MCP client can call them.

## Development

See [CLAUDE.md](CLAUDE.md) for full conventions and workflow.

```powershell
./gradlew compileJava     # build check
```

## License

Private — solo project.
