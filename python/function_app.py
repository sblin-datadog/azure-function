# Datadog instrumentation must be initialized BEFORE other imports
from datadog_serverless_compat import start
import ddtrace.auto  # noqa: F401 – auto-patches supported libraries
start()

import logging
import azure.functions as func
from ddtrace import tracer

app = func.FunctionApp()


@app.timer_trigger(
    schedule="*/1 * * * * *",  # every second (6-field NCRONTAB)
    arg_name="myTimer",
    run_on_startup=True,
)
def timer_trigger(myTimer: func.TimerRequest) -> None:
    with tracer.trace(
        "azure.function.timer",
        service="azure-fn-python",
        resource="timerTrigger",
    ) as span:
        span.set_tag("function.runtime", "python")
        logging.info("Timer tick — trace generated")
