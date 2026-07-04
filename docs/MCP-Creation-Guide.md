# How to Build a Custom MCP Server — Step-by-Step Guide

> Personal reference. Not read by Claude as instructions. Written 2026-07-04.

This is the guide I used to build `gitpilot`. It covers creating an MCP server from zero, registering tools, testing locally, and wiring it to Claude Code.

---

## 1. What even is an MCP server

MCP (Model Context Protocol) is a JSON-RPC protocol over stdio (or HTTP). The server exposes **tools** — callable functions the LLM can invoke. The client (Claude Code, Claude Desktop, etc.) discovers them at startup and calls them as needed.

Key concepts:

| Concept | What it is |
|---|---|
| **Server** | A process that registers tools and listens on stdio |
| **Tool** | A named function with a description, typed parameters, and a return value |
| **Transport** | How the client and server talk. stdio is the default for local tools. |
| **Tool discovery** | Client sends `tools/list` at startup. Server returns all registered tools with their schemas. |
| **Tool call** | Client sends `tools/call` with a tool name and args. Server executes and returns the result. |

The protocol is model-agnostic — any LLM, any language, any client.

---

## 2. Pick your stack

| Language | MCP SDK | Good for |
|---|---|---|
| **Java / Spring Boot** | Spring AI (`spring-ai-mcp-server-spring-boot-starter`) | JVM ecosystem, JDBC, existing Spring shops |
| **Node / TypeScript** | `@modelcontextprotocol/sdk` | Quick iteration, existing Node projects |
| **Python** | `mcp` (Anthropic's official Python SDK) | Data science, scripting |

For `gitpilot`, I used **Java + Spring Boot + Spring AI**. The rest of this guide follows that path, but the concepts are the same for any language.

### pom.xml essentials

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.3</version>
</parent>

<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.0.4</spring-ai.version>
</properties>

<dependencies>
    <!-- Required: MCP server with stdio transport -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
    </dependency>

    <!-- Add your own: JDBC, HTTP client, whatever your tools need -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

---

## 3. Configure the transport

Spring AI auto-configures the server when it sees `spring-ai-mcp-server-spring-boot-starter` on the classpath. You just need this in `application.yml`:

```yaml
spring:
  ai:
    mcp:
      server:
        transport: stdio   # or "http" if you want a REST endpoint
```

That's it. No port binding, no servlet container needed. The Spring Boot app starts, registers all `@Tool` beans, and blocks on stdio waiting for JSON-RPC messages.

---

## 4. Write your first tool

A tool is just a method annotated with `@Tool`. Spring AI scans for these and registers them automatically.

```java
package com.example.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MyFirstTool {

    @Tool(description = "Return a friendly greeting for the given name")
    public String greet(
            @ToolParam(description = "The name to greet") String name) {

        return "Hello, " + name + "!";
    }
}
```

### Rules for tools

1. **The class must be a Spring bean** (`@Component`, `@Service`, or a `@Bean` method).
2. **`@Tool(description = "...")`** — this is what the LLM sees. Write it like you're explaining the tool to a colleague. Be specific about:
   - What the tool does
   - When to use it
   - What the output looks like
3. **`@ToolParam(description = "...")`** — every parameter needs a description. The LLM uses this to decide what value to pass.
4. **Return a String.** MCP tool results are text. Format them with Markdown for readability.
5. **Handle nulls gracefully.** If a param is optional, check for null and provide a default.

### Good description vs bad description

```java
// BAD — too vague
@Tool(description = "Get data")

// BAD — describes implementation, not purpose
@Tool(description = "Query the source_items table and return a count")

// GOOD — says what and when, LLM can decide if it's the right tool
@Tool(description = "Return a summary of recent dashboard activity — inbox counts, news headlines, and last sync time. Use this when the user asks about their dashboard status or what's new.")
```

---

## 5. Tool patterns

### Pattern A: Read tool (DB query only — no AI)

```java
@Tool(description = "Summarize recent dashboard activity")
public String getDashboardSummary(
        @ToolParam(description = "Number of days to look back, default 7") Integer days) {

    // 1. Default handling
    if (days == null || days < 1) {
        days = 7;
    }

    // 2. Query your data source (DB, API, file, whatever)
    var itemCount = repository.countRecentItems(days);

    // 3. Format as Markdown (the LLM renders Markdown natively)
    return """
           ## Dashboard Summary (last %d days)

           - **Inbox items:** %d
           """.formatted(days, itemCount);
}
```

### Pattern B: AI tool (DB → LLM → response)

```java
private final ChatClient chatClient;  // injected, provider from application.yml

@Tool(description = "Analyze inbox items for trends and anomalies")
public String analyzeInbox(
        @ToolParam(description = "What kind of analysis to perform") String analysisType) {

    // 1. Gather context from DB
    var items = repository.getRecentItems(7);

    // 2. Pass to LLM via Spring AI ChatClient (no vendor-specific code)
    return chatClient.prompt()
        .system("You are an AI dashboard analyst. Be concise.")
        .user("Items: %s\n\nAnalysis requested: %s".formatted(items, analysisType))
        .call()
        .content();  // returns the LLM's text response
}
```

### Pattern C: Parameterized action tool

```java
@Tool(description = "Search dashboard items by keyword. Use when the user asks to find something specific.")
public String search(
        @ToolParam(description = "Search keyword or phrase") String query,
        @ToolParam(description = "Max results, default 10") Integer limit) {

    if (limit == null || limit < 1) {
        limit = 10;
    }

    var results = repository.search(query, limit);

    if (results.isEmpty()) {
        return "No results found for: " + query;
    }

    var sb = new StringBuilder();
    sb.append("## Search results for: ").append(query).append("\n\n");
    for (var r : results) {
        sb.append("- **").append(r.title()).append("** (").append(r.provider()).append(")\n");
    }
    return sb.toString();
}
```

---

## 6. The repository layer (data access)

Keep SQL (or API calls) in a separate `@Repository` class. Tools stay focused on orchestration and formatting.

```java
package com.example.agent.repository;

import org.springframework.jdbc.client.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    private final JdbcClient jdbc;

    public DashboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int countRecentItems(int days) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM source_items
                WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '%d days'
                """.formatted(days))
            .query(Integer.class)
            .single();
    }
}
```

### Why JdbcClient and not JPA

- No entity classes, no annotations, no lazy-loading surprises
- Direct SQL = you know exactly what query runs
- Matches the Supabase/PostgREST pattern the parent project uses
- Less magic = easier to debug when something goes wrong

---

## 7. Test locally before wiring to Claude

### 7.1 Compile check

```powershell
mvn compile
```

Fix every error before proceeding. A broken build wastes time.

### 7.2 Manual JSON-RPC test

Spring AI MCP listens on stdio. You can send raw JSON-RPC to test:

```powershell
# Run the server and pipe a tools/list request
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | mvn spring-boot:run
```

This won't give you a clean response (logs mix in), but it verifies the server starts and responds. Better: use `mcp-cli` or write a 5-line Python script.

### 7.3 Quick Python test client (optional)

```python
import subprocess, json

proc = subprocess.Popen(
    ["mvn", "spring-boot:run"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    text=True
)

# Send tools/list
request = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}})
# Read first JSON-RPC message from stdout...
# (Real implementation needs framing — newline-delimited JSON)
```

Honestly, for a first test, just register it with Claude Code and try a prompt. It's faster.

---

## 8. Register with Claude Code

Add to your MCP config (`claude mcp add` or directly in the config file):

```json
{
  "mcpServers": {
    "gitpilot": {
      "command": "mvn",
      "args": ["-f", "C:\\Users\\kartik\\Desktop\\code\\gitpilot", "spring-boot:run"],
      "env": {
        "SUPABASE_URL": "https://your-project.supabase.co",
        "SUPABASE_SERVICE_ROLE_KEY": "sb-..."
      }
    }
  }
}
```

### What happens at startup

1. Claude Code spawns `mvn -f ... spring-boot:run` as a child process
2. Spring Boot starts (takes ~3–5 seconds)
3. Claude Code sends `tools/list` over stdio
4. Server returns all `@Tool` methods with their descriptions and param schemas
5. Claude Code merges these tools with its own built-in tools
6. When Claude decides to call your tool, it sends `tools/call` — Spring AI dispatches to your method

### Watching it work

Enable MCP logging in Claude Code:

```json
{
  "mcpServers": {
    "gitpilot": {
      "command": "mvn",
      "args": ["-f", "C:\\Users\\kartik\\Desktop\\code\\gitpilot", "spring-boot:run"],
      "env": { "...": "..." },
      "logLevel": "debug"
    }
  }
}
```

Then check Claude Code's MCP logs to see the raw JSON-RPC traffic.

---

## 9. Debugging common issues

### Spring Boot starts but Claude can't connect

**Check:** Is `transport: stdio` set in `application.yml`? Without it, Spring AI defaults to serving HTTP on a port — but Claude Code expects stdio.

**Check:** Are you running the server separately and trying to connect? That won't work for stdio. Claude Code needs to spawn the process itself. If you want the server running independently, use HTTP transport instead.

### "Tool not found"

**Check:** Is the class annotated with `@Component` (or another Spring stereotype)? If Spring doesn't know about the bean, Spring AI won't register its tools.

**Check:** Are you accidentally using `@Service` instead of `@Component`? Both work — any Spring bean will be scanned. But if you manually instantiate the class with `new`, Spring won't see it.

### Tool returns errors

**Check:** Are you handling null params? LLMs sometimes omit optional params entirely.

**Check:** Are exceptions being swallowed? Add `try/catch` around your tool body and return a clear error message. Don't throw — the MCP protocol will show a raw stack trace to the user.

```java
@Tool(description = "...")
public String myTool(...) {
    try {
        // do stuff
    } catch (Exception e) {
        return "Error: " + e.getMessage() + " — check the logs for details.";
    }
}
```

### Server is slow on first call

`mvn spring-boot:run` starts from zero every time. For faster startup once the project stabilizes:

```powershell
mvn package -DskipTests
java -jar target/gitpilot-0.1.0.jar
```

Then update your MCP config to run the jar directly instead of `mvn spring-boot:run`.

---

## 10. Adding a second tool (the real workflow)

After your first tool works, every new tool follows this checklist:

1. **Repository method** — write the SQL in a `@Repository` class
2. **Tool method** — add `@Tool` + `@ToolParam` in a `*Tools.java` class
3. **Default handling** — null check on every optional param
4. **Markdown output** — format the return value for LLM readability
5. **`mvn compile`** — must pass
6. **Restart Claude Code** — new tools are only discovered at startup

That's the loop. Add tool → compile → restart → test with a prompt.

---

## 11. When to add AI to a tool

Don't rush this. A tool that queries a database and returns formatted text is already useful — the LLM calling it does the "thinking."

Add AI (ChatClient) to a tool when:

- The data needs **summarization** (e.g., "condense these 50 items into a paragraph")
- The data needs **classification** (e.g., "categorize this email as urgent/normal/spam")
- The data needs **scoring** (e.g., "rank these items by importance based on the user's interests")
- The tool needs to **generate** something (e.g., "write a draft response to this email")

Don't add AI when:

- The tool just returns counts or lists
- The tool just formats existing data
- The LLM caller can do the thinking itself (it already is an LLM)

---

## 12. Model-agnostic design

The whole point of using Spring AI is that you never write provider-specific code. This:

```java
// WRONG — vendor lock-in
var anthropic = new Anthropic(apiKey);
var response = anthropic.messages().create(...);

// RIGHT — provider from config
var response = chatClient.prompt()
    .system("You are a dashboard analyst.")
    .user(userPrompt)
    .call()
    .content();
```

The provider is set in `application.yml`:

```yaml
# Option A: Anthropic
spring.ai.anthropic.api-key: ${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.options.model: claude-opus-4-8

# Option B: OpenAI
# spring.ai.openai.api-key: ${OPENAI_API_KEY}
# spring.ai.openai.chat.options.model: gpt-4o

# Option C: Ollama (local)
# spring.ai.ollama.base-url: http://localhost:11434
# spring.ai.ollama.chat.options.model: llama3
```

Same code, no changes. The `ChatClient` interface abstracts the provider.

---

## 13. Full project checklist (building from zero)

- [ ] `pom.xml` — Spring Boot 3.4+ + Spring AI MCP starter + your deps
- [ ] `application.yml` — `spring.ai.mcp.server.transport: stdio`
- [ ] `application-local.yml` — local secrets (gitignored)
- [ ] `.env.example` — env var template
- [ ] `.gitignore` — `target/`, `application-local.yml`, `.env`, IDE files
- [ ] `@SpringBootApplication` class — just the main method
- [ ] Config class — read env vars / secrets as `@ConfigurationProperties`
- [ ] First repository — one SQL query
- [ ] First tool — one `@Tool` method calling the repository
- [ ] `mvn compile` passes
- [ ] Registered in Claude Code MCP config
- [ ] Test with a prompt: "what tools do you have?" / "call my tool"
- [ ] `CLAUDE.md` — instructions for Claude in this repo
- [ ] `README.md` — human-readable overview
- [ ] `docs/` — architecture, development, tool registry
- [ ] Git initialized and committed
