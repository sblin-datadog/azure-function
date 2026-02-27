# Datadog-Instrumented Azure Functions

This project demonstrates how to instrument Azure Functions with [Datadog APM](https://docs.datadoghq.com/serverless/azure_functions/) across three runtimes. Each function app runs a Timer Trigger every second to produce a continuous stream of traces, making it easy to verify the instrumentation end-to-end.

## Structure

| Directory  | Runtime        | Datadog libraries                                    |
|------------|----------------|------------------------------------------------------|
| `nodejs/`  | Node.js 20+    | `@datadog/serverless-compat`, `dd-trace`             |
| `python/`  | Python 3.11+   | `datadog-serverless-compat`, `ddtrace`               |
| `java/`    | Java 17+       | `dd-java-agent.jar`, `dd-serverless-compat-java-agent.jar` |

## Prerequisites

- **Azure Functions Core Tools** v4: `npm install -g azure-functions-core-tools@4`
- **Node.js 20+** (for Node.js function)
- **Python 3.11+** (for Python function)
- **Java 17+ and Maven 3.8+** (for Java function)
- A **Datadog account** with an API key

## Quick Start

Each sub-directory has its own `README.md` with language-specific instructions. The general flow is:

1. Copy `local.settings.json.example` to `local.settings.json` and fill in your Datadog credentials.
2. Install dependencies.
3. Run `func start`.
4. Open [Datadog Trace Explorer](https://app.datadoghq.com/apm/traces) and filter by `service:azure-fn-<lang>`.

### Run all three (separate terminals)

```bash
# Terminal 1 – Node.js
cd nodejs && npm install && func start

# Terminal 2 – Python
cd python && pip install -r requirements.txt && func start

# Terminal 3 – Java
cd java && mvn clean package && func start
```

## Environment Variables

All three apps use the same Datadog variables, set inside `local.settings.json → Values`:

| Variable     | Description                  | Example            |
|--------------|------------------------------|--------------------|
| `DD_API_KEY` | Your Datadog API key         | `abc123...`        |
| `DD_SITE`    | Your Datadog site            | `datadoghq.com`    |
| `DD_SERVICE` | Service name in Datadog      | `azure-fn-nodejs`  |
| `DD_ENV`     | Deployment environment       | `demo`             |

## Verifying Traces

After starting any function, wait ~5 seconds then check:

- **Trace Explorer**: `https://app.datadoghq.com/apm/traces`
- Filter by `service:azure-fn-nodejs` (or `python` / `java`)
- You should see ~1 trace per second

## Deploying to Azure

For each language, refer to the sub-directory README for Azure deployment steps. When deployed, move the `DD_*` variables from `local.settings.json` to **Azure App Settings** (Configuration → Application settings).

> **Note**: For Java on a Consumption plan, set `languageWorkers__java__arguments` in App Settings.
> For Premium/Dedicated plans, use `JAVA_OPTS`.
