# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Three Azure Function apps (Node.js, Python, Java) that demonstrate Datadog APM instrumentation. Each runs a Timer Trigger every second, generating ~1 trace/sec continuously.

## Local Development

All three require [Azure Functions Core Tools v4](https://learn.microsoft.com/en-us/azure/azure-functions/functions-run-local): `npm install -g azure-functions-core-tools@4`

Copy `local.settings.json.example` to `local.settings.json` and fill in `DD_API_KEY`, `DD_SITE`, `DD_SERVICE`, `DD_ENV`.

### Node.js (`nodejs/`)
```bash
npm install
func start
```

### Python (`python/`)
```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
func start
```

To build a Linux x64 deployment zip: `./build.sh`

### Java (`java/`)
```bash
mvn clean package   # also downloads Datadog agents to target/agents/
func start
```

Deploy to Azure: `mvn azure-functions:deploy`

## Datadog Instrumentation Rules

The order of initialization is critical:

- **Node.js** ([timerTrigger.js](nodejs/src/functions/timerTrigger.js)): `require('@datadog/serverless-compat').start()` must be the **first** line, before `dd-trace` init and before `@azure/functions`
- **Python** ([function_app.py](python/function_app.py)): `from datadog_serverless_compat import start` + `start()` must run before all other imports; `import ddtrace.auto` comes second
- **Java** ([Function.java](java/src/main/java/com/example/Function.java)): No code changes needed — agents loaded via `-javaagent` JVM flags set in `languageWorkers__java__arguments` (Consumption plan) or `JAVA_OPTS` (Premium/Dedicated)

## Java Agent Setup

Agents are auto-downloaded by `maven-dependency-plugin` into `target/agents/` during `mvn package`:
- `dd-serverless-compat-java-agent.jar` — must be listed **first** in JVM args
- `dd-java-agent.jar`

For Azure deployment, agents must be at `/home/site/wwwroot/agents/`.

## Timer Schedule

All three use the 6-field NCRONTAB `*/1 * * * * *` (every second).
