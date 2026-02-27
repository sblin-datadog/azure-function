// Datadog instrumentation must be initialized BEFORE any other require()
require('@datadog/serverless-compat').start();
const tracer = require('dd-trace').init();

const { app } = require('@azure/functions');

app.timer('timerTrigger', {
    // 6-field NCRONTAB: fires every second
    schedule: '*/1 * * * * *',
    runOnStartup: true,
    handler: async (myTimer, context) => {
        const span = tracer.startSpan('azure.function.timer', {
            tags: {
                'function.name': 'timerTrigger',
                'function.runtime': 'nodejs',
            },
        });

        try {
            context.log('Timer tick — trace generated');
        } finally {
            span.finish();
        }
    },
});
