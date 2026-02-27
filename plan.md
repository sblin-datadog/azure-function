# Plan: Datadog-Instrumented Azure Functions (Node.js, Java, Python)

## Context

This project demonstrates how to instrument Azure Functions with Datadog observability. Three separate Azure Function Apps will be created — one per language (Node.js, Python, Java) — each running a Timer Trigger every second to produce a continuous stream of traces. This serves as a reference implementation and step-by-step guide for the [official Datadog Azure Functions docs](https://docs.datadoghq.com/serverless/azure_functions/).

---

## Directory Structure

```
azureFunction/
├── README.md                  ← top-level overview and quick-start
├── nodejs/
│   ├── README.md              ← Node.js-specific step-by-step guide
│   ├── package.json
│   ├── host.json
│   ├── local.settings.json    ← gitignored template with DD_* vars
│   └── src/
│       └── functions/
│           └── timerTrigger.js
├── python/
│   ├── README.md              ← Python-specific step-by-step guide
│   ├── requirements.txt
│   ├── host.json
│   ├── local.settings.json
│   └── function_app.py
└── java/
    ├── README.md              ← Java-specific step-by-step guide
    ├── pom.xml
    ├── host.json
    ├── local.settings.json
    └── src/main/java/com/example/
        └── Function.java
```

---

## Implementation Details

### Common Datadog Environment Variables (all three apps)

Set in `local.settings.json` → `Values` for local dev, and in Azure App Settings for deployed apps:

| Variable        | Description                        |
|-----------------|------------------------------------|
| `DD_API_KEY`    | Datadog API key                    |
| `DD_SITE`       | e.g. `datadoghq.com`               |
| `DD_SERVICE`    | e.g. `azure-fn-nodejs`             |
| `DD_ENV`        | e.g. `demo`                        |

---

### 1. Node.js Function

**Runtime**: Azure Functions v4 (Node.js 20+), programming model v4 (`@azure/functions`)

**Key packages** (`package.json`):
```json
{
  "dependencies": {
    "@azure/functions": "^4.x",
    "@datadog/serverless-compat": "latest",
    "dd-trace": "latest"
  }
}
```

**Entry point** (`src/functions/timerTrigger.js`):
```js
// MUST be first — before any other require()
require('@datadog/serverless-compat').start();
const tracer = require('dd-trace').init();
const { app } = require('@azure/functions');

app.timer('timerTrigger', {
  schedule: '*/1 * * * * *',   // every second (6-field NCRONTAB)
  runOnStartup: true,
  handler: async (myTimer, context) => {
    const span = tracer.startSpan('azure.function.timer');
    context.log('Timer tick — trace generated');
    span.finish();
  }
});
```

**`host.json`**: set `"extensionBundle"` for Azure Functions v4.

---

### 2. Python Function

**Runtime**: Azure Functions v2 (Python 3.11+), programming model v2

**Key packages** (`requirements.txt`):
```
azure-functions
datadog-serverless-compat
ddtrace
```

**Entry point** (`function_app.py`) — imports MUST be in this order:
```python
from datadog_serverless_compat import start
import ddtrace.auto        # auto-instruments libraries
start()

import azure.functions as func
import logging
from ddtrace import tracer

app = func.FunctionApp()

@app.timer_trigger(
    schedule="*/1 * * * * *",
    arg_name="myTimer",
    run_on_startup=True
)
def timer_trigger(myTimer: func.TimerRequest) -> None:
    with tracer.trace("azure.function.timer", service="azure-fn-python"):
        logging.info("Timer tick — trace generated")
```

---

### 3. Java Function

**Runtime**: Azure Functions v4 (Java 17+)

**Step 1 – Agents via Maven** (`pom.xml` uses `maven-dependency-plugin` to download both JARs into `target/agents/` during `mvn package`):
```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>download-dd-agents</id>
      <phase>prepare-package</phase>
      <goals><goal>copy</goal></goals>
      <!-- downloads dd-java-agent and dd-serverless-compat-java-agent -->
    </execution>
  </executions>
</plugin>
```
> The JARs land in `target/agents/` (gitignored). No manual download step.

**Step 2 – JVM options** in `local.settings.json`:
```json
{
  "Values": {
    "languageWorkers__java__arguments":
      "-javaagent:target/agents/dd-serverless-compat-java-agent.jar -javaagent:target/agents/dd-java-agent.jar"
  }
}
```
> For Premium/Dedicated plan in Azure, use `JAVA_OPTS` instead.

**`pom.xml`**: standard Azure Functions Maven archetype (no extra Datadog Maven dep needed — agents do everything).

**Function** (`Function.java`):
```java
@FunctionName("TimerTrigger")
public void run(
    @TimerTrigger(name = "timerInfo", schedule = "*/1 * * * * *")
    String timerInfo,
    final ExecutionContext context) {
    context.getLogger().info("Timer tick — trace generated");
}
```

---

## READMEs

Each `README.md` (top-level + per-language) documents the **step-by-step process**:

1. Prerequisites (Azure Functions Core Tools, language runtime, Datadog account)
2. Installation commands
3. Environment variable configuration
4. How to run locally (`func start`)
5. How to verify traces in Datadog Trace Explorer
6. Deployment to Azure (optional section)
7. Troubleshooting tips

---

## Verification / Testing

| Step | Command / Action |
|------|-----------------|
| Run Node.js locally | `cd nodejs && npm install && func start` |
| Run Python locally | `cd python && pip install -r requirements.txt && func start` |
| Run Java locally | `cd java && mvn clean package && func start` |
| Confirm traces | In Datadog UI → APM → Trace Explorer → filter by `service:azure-fn-<lang>` |
| Confirm 1 trace/sec | Check trace count grows ~60/min per function |

---

## Files Created

| File | Notes |
|------|-------|
| `README.md` | Top-level overview |
| `nodejs/README.md`, `package.json`, `host.json`, `local.settings.json.example`, `src/functions/timerTrigger.js` | Full Node.js app |
| `python/README.md`, `requirements.txt`, `host.json`, `local.settings.json.example`, `function_app.py` | Full Python app |
| `java/README.md`, `pom.xml`, `host.json`, `local.settings.json.example`, `src/main/java/com/example/Function.java` | Full Java app |
| `.gitignore` | Ignores `local.settings.json`, `node_modules`, `.venv`, `target/` |
