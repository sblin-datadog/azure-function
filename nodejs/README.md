# Azure Function — Node.js + Datadog APM

This Azure Function App runs a Timer Trigger every second and generates one Datadog trace per invocation.

**Official docs**: https://docs.datadoghq.com/serverless/azure_functions/?tab=nodejs

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Node.js | 20+ | https://nodejs.org |
| Azure Functions Core Tools | 4.x | `npm install -g azure-functions-core-tools@4` |
| Datadog account | — | API key required |

---

## Step 1 — Install dependencies

```bash
npm install
```

This installs:
- `@azure/functions` — Azure Functions v4 programming model
- `@datadog/serverless-compat` — Datadog serverless compatibility layer for Azure Functions
- `dd-trace` — Datadog APM tracer for Node.js

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
    "FUNCTIONS_WORKER_RUNTIME": "node",
    "DD_API_KEY": "<YOUR_DATADOG_API_KEY>",
    "DD_SITE": "datadoghq.com",
    "DD_SERVICE": "azure-fn-nodejs",
    "DD_ENV": "demo"
  }
}
```

> `local.settings.json` is gitignored — never commit it.

---

## Step 3 — How instrumentation works

Open `src/functions/timerTrigger.js`. The **two critical lines must appear first**, before any other `require()`:

```js
require('@datadog/serverless-compat').start();  // 1. Start Azure Functions compat layer
const tracer = require('dd-trace').init();       // 2. Initialize APM tracer
```

**Why this order matters**: `@datadog/serverless-compat` hooks into the Azure Functions host lifecycle to correctly flush traces before the worker shuts down. If it is loaded after other modules, those modules may not be fully instrumented.

Each timer invocation creates a manual span:

```js
const span = tracer.startSpan('azure.function.timer');
// ... your work here ...
span.finish();
```

---

## Step 4 — Run locally

```bash
func start
```

You should see output like:

```
[2025-xx-xx] Timer trigger 'timerTrigger' fired.
Timer tick — trace generated
```

The function fires every second (`*/1 * * * * *` NCRONTAB schedule).

---

## Step 5 — Verify traces in Datadog

1. Open https://app.datadoghq.com/apm/traces
2. Filter: `service:azure-fn-nodejs`
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
  --runtime node \
  --runtime-version 20 \
  --functions-version 4 \
  --name azure-fn-nodejs-demo \
  --storage-account mystorageaccount

# Set Datadog app settings
az functionapp config appsettings set \
  --name azure-fn-nodejs-demo \
  --resource-group myResourceGroup \
  --settings \
    DD_API_KEY="<YOUR_KEY>" \
    DD_SITE="datadoghq.com" \
    DD_SERVICE="azure-fn-nodejs" \
    DD_ENV="production"

# Deploy
func azure functionapp publish azure-fn-nodejs-demo
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| No traces in Datadog | Wrong `DD_API_KEY` or `DD_SITE` | Double-check `local.settings.json` |
| `Cannot find module 'dd-trace'` | Dependencies not installed | Run `npm install` |
| Traces show but service name is wrong | `DD_SERVICE` not set | Add it to `local.settings.json → Values` |
| Function not triggering | Core Tools version | Ensure `azure-functions-core-tools@4` |
