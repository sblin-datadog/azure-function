package com.example;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

/**
 * Azure Function instrumented with Datadog APM.
 *
 * The Datadog Java agent (dd-java-agent.jar) and the serverless compatibility
 * layer (dd-serverless-compat-java-agent.jar) are attached via JVM arguments
 * configured in local.settings.json (key: languageWorkers__java__arguments).
 * No Datadog imports are required in application code.
 */
public class Function {

    @FunctionName("TimerTrigger")
    public void run(
            // 6-field NCRONTAB: fires every second
            @TimerTrigger(name = "timerInfo", schedule = "*/1 * * * * *")
            String timerInfo,
            final ExecutionContext context) {

        context.getLogger().info("Timer tick — trace generated");
    }
}
