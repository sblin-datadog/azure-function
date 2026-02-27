# Azure Function — Python + Datadog APM

This Azure Function App runs a Timer Trigger every second and generates one Datadog trace per invocation.

**Official docs**: https://docs.datadoghq.com/serverless/azure_functions/?tab=python

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Python | 3.11+ | https://python.org |
| Azure Functions Core Tools | 4.x | `npm install -g azure-functions-core-tools@4` |
| Datadog account | — | API key required |

---

## Step 1 — Create a virtual environment and install dependencies

```bash
python -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

This installs:
- `azure-functions` — Azure Functions Python SDK
- `datadog-serverless-compat` — Datadog serverless compatibility layer for Azure Functions
- `ddtrace` — Datadog APM tracer for Python

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
    "FUNCTIONS_WORKER_RUNTIME": "python",
    "DD_API_KEY": "<YOUR_DATADOG_API_KEY>",
    "DD_SITE": "datadoghq.com",
    "DD_SERVICE": "azure-fn-python",
    "DD_ENV": "demo"
  }
}
```

> `local.settings.json` is gitignored — never commit it.

---

## Step 3 — How instrumentation works

Open `function_app.py`. The **three lines must appear first**, before any other import:

```python
from datadog_serverless_compat import start
import ddtrace.auto   # auto-patches standard libraries (requests, sqlalchemy, etc.)
start()
```

**Why this order matters**:
- `ddtrace.auto` must be imported before any library it patches (e.g. `requests`) to correctly wrap them.
- `start()` hooks into the Azure Functions host lifecycle to ensure traces are flushed before the worker exits.

Each timer invocation creates a trace using a context manager:

```python
with tracer.trace("azure.function.timer", service="azure-fn-python") as span:
    span.set_tag("function.runtime", "python")
    # ... your work here ...
```

---

## Step 4 — Run locally

```bash
func start
```

You should see output like:

```
[2025-xx-xx] Executing 'Functions.timer_trigger'
Timer tick — trace generated
```

The function fires every second (`*/1 * * * * *` NCRONTAB schedule).

---

## Step 5 — Verify traces in Datadog

1. Open https://app.datadoghq.com/apm/traces
2. Filter: `service:azure-fn-python`
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
  --runtime python \
  --runtime-version 3.11 \
  --functions-version 4 \
  --name azure-fn-python-demo \
  --storage-account mystorageaccount \
  --os-type linux

# Set Datadog app settings
az functionapp config appsettings set \
  --name azure-fn-python-demo \
  --resource-group myResourceGroup \
  --settings \
    DD_API_KEY="<YOUR_KEY>" \
    DD_SITE="datadoghq.com" \
    DD_SERVICE="azure-fn-python" \
    DD_ENV="production"

# Deploy
func azure functionapp publish azure-fn-python-demo
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| No traces in Datadog | Wrong `DD_API_KEY` or `DD_SITE` | Double-check `local.settings.json` |
| `ModuleNotFoundError: ddtrace` | Dependencies not installed | Activate venv and run `pip install -r requirements.txt` |
| Import order warning from ddtrace | `ddtrace.auto` imported after other libs | Ensure it's the second line in `function_app.py` |
| Traces show but service name is wrong | `DD_SERVICE` not set | Add it to `local.settings.json → Values` |
