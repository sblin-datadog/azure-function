# Azure Function — Java + Datadog APM

This Azure Function App runs a Timer Trigger every second and generates one Datadog trace per invocation.

**Official docs**: https://docs.datadoghq.com/serverless/azure_functions/?tab=java

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java (JDK) | 17+ | https://adoptium.net |
| Maven | 3.8+ | https://maven.apache.org |
| Azure Functions Core Tools | 4.x | `npm install -g azure-functions-core-tools@4` |
| Datadog account | — | API key required |

---

## Step 1 — Build the project and download Datadog agents

```bash
mvn clean package
```

`mvn package` automatically downloads two Datadog JARs into `target/agents/` via `maven-dependency-plugin`:

| JAR | Purpose |
|-----|---------|
| `dd-java-agent.jar` | Datadog APM tracer (bytecode instrumentation) |
| `dd-serverless-compat-java-agent.jar` | Azure Functions compatibility layer (lifecycle hooks + trace flushing) |

These JARs are loaded as `-javaagent` arguments — **no Datadog imports are needed in `Function.java`**.

---

## Step 2 — Configure Datadog credentials

Copy the example settings file and fill in your values:

```bash
cp local.settings.json.example local.settings.json
```

Edit `local.settings.json`:

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "languageWorkers__java__arguments": "-javaagent:target/agents/dd-serverless-compat-java-agent.jar -javaagent:target/agents/dd-java-agent.jar",
    "DD_API_KEY": "<YOUR_DATADOG_API_KEY>",
    "DD_SITE": "datadoghq.com",
    "DD_SERVICE": "azure-fn-java",
    "DD_ENV": "demo"
  }
}
```

> `local.settings.json` is gitignored — never commit it.

---

## Step 3 — How instrumentation works

Java instrumentation is entirely **agent-based** — no application code changes are required.

The two `-javaagent` flags (set in `languageWorkers__java__arguments`) are loaded by the JVM at startup **before** any application class:

```
-javaagent:target/agents/dd-serverless-compat-java-agent.jar
-javaagent:target/agents/dd-java-agent.jar
```

**Load order matters**:
1. `dd-serverless-compat-java-agent` sets up Azure Functions lifecycle hooks so traces are flushed before the worker shuts down.
2. `dd-java-agent` performs bytecode instrumentation of all loaded classes, automatically creating spans for each function invocation.

**Azure Functions plan — JVM options variable**:

| Plan | Environment variable |
|------|---------------------|
| Consumption | `languageWorkers__java__arguments` |
| Premium / Dedicated | `JAVA_OPTS` |

---

## Step 4 — Run locally

```bash
func start
```

You should see output like:

```
[2025-xx-xx] Executing 'Functions.TimerTrigger'
Timer tick — trace generated
```

The function fires every second (`*/1 * * * * *` NCRONTAB schedule).

---

## Step 5 — Verify traces in Datadog

1. Open https://app.datadoghq.com/apm/traces
2. Filter: `service:azure-fn-java`
3. Within a few seconds you should see traces appearing at ~1/second

---

## Deploy to Azure

```bash
# Login and create resources
az login
az group create --name myResourceGroup --location eastus
az storage account create --name mystorageaccount --resource-group myResourceGroup --sku Standard_LRS
az functionapp create \
  --resource-group myResourceGroup \
  --consumption-plan-location eastus \
  --runtime java \
  --runtime-version 17 \
  --functions-version 4 \
  --name azure-fn-java-demo \
  --storage-account mystorageaccount \
  --os-type linux

# Upload the agent JARs to a storage location accessible by the function app
# (e.g. mount a storage share or include them in the deployment package)

# Set Datadog app settings
az functionapp config appsettings set \
  --name azure-fn-java-demo \
  --resource-group myResourceGroup \
  --settings \
    DD_API_KEY="<YOUR_KEY>" \
    DD_SITE="datadoghq.com" \
    DD_SERVICE="azure-fn-java" \
    DD_ENV="production" \
    "languageWorkers__java__arguments=-javaagent:/home/site/wwwroot/agents/dd-serverless-compat-java-agent.jar -javaagent:/home/site/wwwroot/agents/dd-java-agent.jar"

# Deploy
mvn azure-functions:deploy
```

> **Note on JAR paths in Azure**: When deployed, the JARs must be bundled with your deployment package and the paths in `languageWorkers__java__arguments` updated accordingly (e.g. `/home/site/wwwroot/agents/`). The `mvn azure-functions:deploy` goal uses the `azure-functions-maven-plugin` configuration in `pom.xml`.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| No traces in Datadog | Wrong `DD_API_KEY` or `DD_SITE` | Double-check `local.settings.json` |
| `target/agents/` is empty | Build not run | Run `mvn clean package` first |
| `Could not open javaagent jar` | Wrong JAR path in JVM args | Verify paths in `languageWorkers__java__arguments` match `target/agents/` |
| Traces show but service name is wrong | `DD_SERVICE` not set | Add it to `local.settings.json → Values` |
| Consumption plan: agents not found after deploy | JARs not in deployment package | Include `target/agents/` in the deployment artifact |
