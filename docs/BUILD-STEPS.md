# Build gitpilot from Scratch — Step-by-Step

> Personal learning guide. No code — just steps and the "why" behind each one.
> Build this yourself to understand how MCP servers work.

---

## Phase 1: The skeleton

### Step 1 — Create the Maven project

You need a Spring Boot 3.4 project with two special dependencies:

- `spring-ai-starter-mcp-server` — this is the magic. It auto-discovers your `@Tool` methods and exposes them as an MCP server over stdio. Without it, you'd have to hand-write JSON-RPC framing.

- `org.eclipse.jgit` — pure Java git. No shelling out to `git.exe`. Works identically on Windows, Mac, and Linux.

Add the Spring milestones repository because Spring AI releases go through there first.

**Why these versions:** Spring Boot 3.4 requires Java 21. Spring AI 1.0.4 is the latest stable. JGit 7.0 is the most recent major.

### Step 2 — Configure the transport

In `application.yml`, set `spring.ai.mcp.server.transport: stdio`.

This tells Spring AI: "don't start an HTTP server, don't bind a port. Just listen on stdin and write to stdout." This is what Claude Code expects — it spawns your process and talks to it over pipes.

### Step 3 — Set up your config class

Create a `@ConfigurationProperties` class for anything configurable. For gitpilot, the only config is the repo path. Default it to `user.dir` (the current working directory where the MCP client spawns the process).

**Why a config class:** Spring Boot can inject these values from env vars, yml files, or command-line args. You get env-var override for free.

### Step 4 — Write the application entry point

A standard `@SpringBootApplication` class with a `main` method. Nothing MCP-specific here — Spring AI wires everything automatically.

---

## Phase 2: Git operations (no AI yet)

### Step 5 — Build the GitRepository class

This is where all JGit calls live. It's a plain `@Component` — Spring creates one instance, and your tools inject it.

JGit works with a `Repository` object (the `.git` directory). Every operation opens the repo, does the work, and closes it. Use try-with-resources so you never leak file handles.

**What it needs to do:**

1. Open the repo — `FileRepositoryBuilder` finds `.git` from any path
2. Read the current branch name
3. Read the default branch (try `main`, fall back to `master`)
4. Get status: which files are staged, modified, untracked
5. Get the staged diff: compare the index tree to HEAD
6. Get the branch diff: compare the feature branch to main/master
7. Get recent commit messages (last 10, for style context)
8. Create a branch and switch to it
9. Stage everything (`git add -A`) and commit with a message

**Why a separate repository class:** Tools should be thin — they orchestrate and format. Repository handles the JGit complexity. If you ever need to swap JGit for something else, you only change one class.

### Step 6 — Handle edge cases in the repository

- What if there's no HEAD commit yet? (fresh repo)
- What if you're already on the default branch?
- What if the branch name has spaces or special chars?
- What if the diff is enormous? (truncate at ~8000 chars — LLMs can't process raw 50KB diffs)

Return clear error messages, not raw exceptions. The tool layer should never see a stack trace.

### Step 7 — Write the Tier 1 tools

Create a `@Component` class called `GitTools`. Inject `GitRepository` via constructor.

Annotate each method with `@Tool(description = "...")` and each parameter with `@ToolParam(description = "...")`.

The `description` strings are critical — they're what the LLM reads to decide whether to call your tool and what to pass. Write them like you're explaining the tool to a colleague who's never seen it.

**Tools to write:**

| Tool | What it does | Edge cases |
|---|---|---|
| `gitStatus` | Returns branch name + file lists | Handle any exception, return error string |
| `gitCreateFeatureBranch` | Creates + switches to a branch | Reject blank names, validate format |
| `gitCommit` | Stage all + commit with message | Reject blank messages |
| `gitShowStagedChanges` | Shows staged diff + recent commits | Handle empty staging area gracefully |

**Why return strings:** MCP tool results are text. Format them as Markdown — the LLM renders Markdown natively, so headings, lists, and code blocks look good to the user.

### Step 8 — Test locally

Run `mvn compile`. Fix everything that breaks.

Then register it with Claude Code — add a block to your MCP config that runs `mvn spring-boot:run` with the gitpilot project directory. Set `GIT_REPO_PATH` to point at a test repo.

Try these prompts:
- "What's my git status?"
- "Create a feature branch called feat/test-tool"
- "Show me the staged changes"

Watch Claude call your tools. Check that the output makes sense.

---

## Phase 3: AI-assisted tools

### Step 9 — Configure a ChatClient

Uncomment the AI section in `application.yml`. Add your provider API key as an env var.

Spring AI creates a `ChatClient` bean automatically when it sees the provider config. You just inject it into your tools class.

**Why ChatClient and not direct Anthropic/OpenAI SDK:** ChatClient is a facade. Write the code once, swap the provider by changing config. No code changes. This is what "model-agnostic" means in practice.

### Step 10 — Write gitGenerateCommitMessage

The flow:

1. Read the staged diff from `GitRepository`
2. If nothing is staged, return a message saying "stage files first"
3. Read the last 10 commit messages for style context
4. Build a prompt:
   - System: "You are a git workflow assistant. Generate a conventional commit message. Follow the style of the recent commits. Be concise."
   - User: the diff + recent commits
5. Call `chatClient.prompt().system(...).user(...).call().content()`
6. Return the LLM's response

**Why pass recent commits:** The LLM needs to match the repo's existing style. If the repo uses `feat:` prefixes, the generated message should too.

### Step 11 — Write gitGeneratePrDescription

The flow:

1. Check you're not on the default branch (can't generate a PR from main)
2. Read the full branch diff vs main/master
3. Read recent commits
4. Build a prompt:
   - System: "Generate a structured PR description. Include: Summary, Changes Made, Testing Notes."
   - User: branch name + truncated diff + commit list
5. Call ChatClient, return the response

**Why truncate the diff:** A full branch diff can be 50K+ characters. LLMs have context limits and charge by token. 5000 characters of diff is usually enough to understand what changed.

### Step 12 — Handle AI failures gracefully

What if the API key is wrong? What if the provider is down? What if the response is malformed?

Wrap the AI call in try/catch. On failure, return a clear error message with what went wrong. Don't crash the tool — the user can always write the commit message manually.

---

## Phase 4: Polish

### Step 13 — Write the project documentation

- `CLAUDE.md` — instructions for Claude when working in this repo
- `README.md` — what it does, how to run it, how to register it
- `docs/01-Architecture.md` — where it fits, component diagram, data flow
- `docs/02-Development.md` — workflow rules, tool patterns, checklists
- `docs/03-MCP-Tool-Registry.md` — every tool catalogued

**Why document before you're done:** You'll forget why you made decisions. Write them down while they're fresh.

### Step 14 — Register with Claude Code permanently

Add a block to your MCP client config:

- Command: `mvn` with args to run spring-boot:run in the gitpilot directory
- Env vars: `GIT_REPO_PATH` pointing to your real repo
- Optional: `ANTHROPIC_API_KEY` for Tier 2 tools

Restart Claude Code. Ask "what tools do you have?" — you should see all 6 gitpilot tools listed.

### Step 15 — Use it for real work

The best test: use your own tool for your own commits. Next time you make a change in any project:

1. `git add <files>`
2. Ask Claude: "generate a commit message for my staged changes"
3. Claude calls `gitGenerateCommitMessage` on your real diff
4. Review the generated message, tweak if needed
5. Ask Claude: "commit with that message"
6. Claude calls `gitCommit`

If you're using it daily, it's working.

---

## What you'll have at the end

A Spring Boot app that:
- Starts in ~3 seconds
- Listens on stdio for MCP messages
- Exposes 6 typed git tools
- Works with zero API keys (Tier 1)
- Adds AI-assisted commit messages and PR descriptions with a provider key (Tier 2)
- Swaps LLM providers by changing one config file
- Runs on Windows, Mac, and Linux identically (JGit is pure Java)

## What you'll understand

- How MCP tools are discovered (tools/list) and called (tools/call)
- How `@Tool` and `@ToolParam` annotations become JSON schemas
- How JGit reads and writes git state without the CLI
- How Spring AI's ChatClient abstracts LLM providers
- How to structure an MCP server so AI features are optional and additive
