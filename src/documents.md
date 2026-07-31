# Mock documents — health_check_results

Mixed active/inactive mock data for the `health_check_results` collection (`batchcmdb` db),
for testing Grafana panels that need more than the all-`active: true` data currently there.

Uses `fromServer: "MOCKSERVER01"` on purpose, not a real hostname. The stale-result cleanup
(`removeStaleHealthCheckResults`) only deletes documents matching the *current* server's own
hostname, so mock docs under a fake server name won't get silently purged by the scheduled job
while you're testing panels.

Paste this into Compass's shell (mongosh tab) or `mongosh` directly, connected to `batchcmdb`:

```js
db.health_check_results.insertMany([
  {
    controllerName: "TWCI",
    fromServer: "MOCKSERVER01",
    active: true,
    checkTriggeredAt: new Date(),
    responseReceivedAt: new Date(Date.now() + 850),
    requestTimeMs: 850,
    errorMessage: null,
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "TWCE",
    fromServer: "MOCKSERVER01",
    active: false,
    checkTriggeredAt: new Date(Date.now() - 5 * 60000),
    responseReceivedAt: new Date(Date.now() - 5 * 60000),
    requestTimeMs: 0,
    errorMessage: "TWS service unavailable",
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "OPSC",
    fromServer: "MOCKSERVER01",
    active: false,
    checkTriggeredAt: new Date(Date.now() - 12 * 60000),
    responseReceivedAt: new Date(Date.now() - 12 * 60000),
    requestTimeMs: 0,
    errorMessage: "Invalid request to TWS",
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "ZOPS",
    fromServer: "MOCKSERVER01",
    active: false,
    checkTriggeredAt: new Date(Date.now() - 20 * 60000),
    responseReceivedAt: new Date(Date.now() - 20 * 60000),
    requestTimeMs: 0,
    errorMessage: "TWS service error",
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "TWS1",
    fromServer: "MOCKSERVER01",
    active: false,
    checkTriggeredAt: new Date(Date.now() - 30 * 60000),
    responseReceivedAt: new Date(Date.now() - 30 * 60000),
    requestTimeMs: 0,
    errorMessage: "Validation service error",
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "OPCC",
    fromServer: "MOCKSERVER01",
    active: false,
    checkTriggeredAt: new Date(Date.now() - 45 * 60000),
    responseReceivedAt: new Date(Date.now() - 45 * 60000),
    requestTimeMs: 0,
    errorMessage: "TWS service unavailable",
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "OPCR",
    fromServer: "MOCKSERVER01",
    active: true,
    checkTriggeredAt: new Date(),
    responseReceivedAt: new Date(Date.now() + 1200),
    requestTimeMs: 1200,
    errorMessage: null,
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "OPPC",
    fromServer: "MOCKSERVER01",
    active: true,
    checkTriggeredAt: new Date(),
    responseReceivedAt: new Date(Date.now() + 640),
    requestTimeMs: 640,
    errorMessage: null,
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "TWCB",
    fromServer: "MOCKSERVER02",
    active: false,
    checkTriggeredAt: new Date(Date.now() - 8 * 60000),
    responseReceivedAt: new Date(Date.now() - 8 * 60000),
    requestTimeMs: 0,
    errorMessage: "Invalid request to TWS",
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  },
  {
    controllerName: "TWCB",
    fromServer: "MOCKSERVER02",
    active: true,
    checkTriggeredAt: new Date(),
    responseReceivedAt: new Date(Date.now() + 2100),
    requestTimeMs: 2100,
    errorMessage: null,
    _class: "com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument"
  }
])
```

## What this gives you to test with

- 5 `active: false` docs across 4 different `errorMessage` values (all 4 exception branches
  represented), for the error-breakdown bar chart and the active-vs-inactive pie chart.
- Mixed `requestTimeMs` values (640-2100ms) across both active and inactive docs, for the
  request-time panel.
- `checkTriggeredAt` staggered 0-45 minutes in the past, so if you rerun the stale-cleanup
  sanity check query later, some of these should still be inside a 1-hour window and some
  right at the edge, useful for watching the threshold behavior directly.
- A second `fromServer` (`MOCKSERVER02`) with one active/one inactive doc for the same
  controller (`TWCB`), to sanity-check that panels grouping by `fromServer` or filtering by it
  behave correctly with more than one server represented.

## Cleanup

To remove all mock data once you're done testing:

```js
db.health_check_results.deleteMany({ fromServer: { $in: ["MOCKSERVER01", "MOCKSERVER02"] } })
```
