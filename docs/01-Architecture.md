# 01 — Architecture

## Where gitpilot fits

```
┌──────────────────────────────────────────────────┐
│            Claude Code / MCP Client               │
│  (or any tool-calling LLM — model doesn't matter) │
└──────────────────────┬───────────────────────────┘
                       │ stdio (MCP)
                       ▼
┌──────────────────────────────────────────────────┐
│              gitpilot (Java)                   │
│                                                  │
│  Tier 1 — pure git (JGit, no API keys):          │
│    gitStatus, gitCreateFeatureBranch, gitCommit,  │
│    gitShowStagedChanges                           │
│                                                  │
│  Tier 2 — AI-assisted (ChatClient, from config):  │
│    gitGenerateCommitMessage, gitGeneratePrDesc    │
└──────────────────────┬───────────────────────────┘
                       │ JGit (pure Java)
                       ▼
┌──────────────────────────────────────────────────┐
│               Local Git Repo (.git)               │
│     (any repo — configured via GIT_REPO_PATH)     │
└──────────────────────────────────────────────────┘
```

## Component diagram

```
┌── GitpilotApplication.java ──────────────────────────┐
│                                                        │
│  ┌── config/ ──────────────────────────────────────┐   │
│  │  GitConfig         McpConfig                    │   │
│  │  (repo path)        (stdio transport hook)      │   │
│  └─────────────────────────────────────────────────┘   │
│                                                        │
│  ┌── repository/ ──────────────────────────────────┐   │
│  │  GitRepository                                   │   │
│  │  (JGit: status, diff, branch, commit, log)       │   │
│  └──────────────────────┬──────────────────────────┘   │
│                         │                              │
│  ┌── tools/ ───────────▼───────────────────────────┐   │
│  │  GitTools                                        │   │
│  │  (@Tool methods, call GitRepository,              │   │
│  │   format Markdown, handle exceptions)             │   │
│  └──────────────────────┬──────────────────────────┘   │
│                         │                              │
│  ┌── AI tier (Tier 2) ─▼───────────────────────────┐   │
│  │  ChatClient (Spring AI, provider from yml)        │   │
│  │  - gitGenerateCommitMessage                       │   │
│  │  - gitGeneratePrDescription                       │   │
│  └──────────────────────────────────────────────────┘   │
│                                                        │
└────────────────────┬───────────────────────────────────┘
                     │ stdio (JSON-RPC)
                     ▼
              MCP Client
```

## Data flow

### Tier 1: Pure git tool (e.g. `gitStatus`)

```
1. MCP client → JSON-RPC "tools/call" → stdio
2. Spring AI dispatches to @Tool method
3. GitTools → GitRepository → JGit → .git directory
4. Result formatted as Markdown → String → JSON-RPC response
5. No AI. No network. Pure local git read.
```

### Tier 2: AI-assisted tool (e.g. `gitGenerateCommitMessage`)

```
1. MCP client → JSON-RPC "tools/call" → stdio
2. GitTools reads staged diff + recent commits via GitRepository
3. GitTools passes context → ChatClient.call()
4. ChatClient → Anthropic/OpenAI/Ollama (per application.yml)
5. LLM response (commit message) → formatted String → JSON-RPC response
```

## Key decisions

| Decision | Why |
|---|---|
| JGit, not git CLI | Pure Java — no shell dependency, no platform-specific parsing. Portable. |
| stdio, not HTTP | MCP stdio is the standard for local tools. The git repo is local anyway. |
| Two-tier design | Tier 1 works with zero API keys. The server is useful immediately. |
| Spring AI, not hardcoded SDK | Single ChatClient works with any provider. Model-agnostic by design. |
| Diff truncation (8000 chars) | LLMs don't need raw full diffs. Truncation keeps context manageable. |

## Dependencies

- **JGit** — pure Java git library. No system git needed.
- **Spring AI ChatClient** (optional, Tier 2 only) — needs a provider API key.
- No database, no network beyond the LLM API call for Tier 2.
