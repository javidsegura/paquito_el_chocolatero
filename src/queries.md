# Grafana test queries — TWS health check

Test queries for the `health_check_results` collection (`batchcmdb` database), used to
validate the dev MongoDB Grafana datasource against the TWS health check feature.

Datasource connection string must include the database name in the path segment
(e.g. `.net:37017/batchcmdb?authMechanism=...`), otherwise `db` has no default database
bound to it and queries fail with "database name cannot be empty".

## 1. Connectivity / sanity check

```
db.health_check_results.aggregate([
  {"$sort": {"checkTriggeredAt": -1}},
  {"$limit": 5}
])
```

Confirms rows come back with `controllerName`, `active`, `errorMessage`, `requestTimeMs`.

**Panel type:** Table. Just raw rows, no aggregation shape to visualize.

## 2. Latest status per controller

Current-state snapshot, not a trend. Old docs get purged by the stale-result cleanup, so
there is no long history to build a time-series panel from.

```
db.health_check_results.aggregate([
  { "$sort": { "controllerName": 1, "checkTriggeredAt": -1 } },
  { "$group": {
      "_id": "$controllerName",
      "fromServer": { "$first": "$fromServer" },
      "active": { "$first": "$active" },
      "errorMessage": { "$first": "$errorMessage" },
      "requestTimeMs": { "$first": "$requestTimeMs" },
      "checkTriggeredAt": { "$first": "$checkTriggeredAt" }
  }}
])
```

**Panel type:** Table, one row per controller. Add a cell-value threshold/color mapping on
`active` (green/red) so failures are visible at a glance without a separate stat panel.

## 3. Active vs inactive count

```
db.health_check_results.aggregate([
  { "$group": { "_id": "$active", "count": { "$sum": 1 } } }
])
```

**Panel type:** Pie chart or Bar gauge, two categories (`true`/`false`). A plain Stat panel
also works if you only care about one number (e.g. count of `active: false`).

## 4. Request time, short window

```
db.health_check_results.aggregate([
  { "$sort": { "checkTriggeredAt": -1 } },
  { "$limit": 100 },
  { "$project": { "controllerName": 1, "requestTimeMs": 1, "checkTriggeredAt": 1 } }
])
```

**Panel type:** Time series, `checkTriggeredAt` as the time field, `requestTimeMs` as the
value, split/colored by `controllerName`. The one query here that's genuinely a proper
timeseries shape rather than a snapshot.

## 5. Error breakdown for failing controllers

```
db.health_check_results.aggregate([
  { "$match": { "active": false } },
  { "$group": { "_id": "$errorMessage", "count": { "$sum": 1 }, "controllers": { "$addToSet": "$controllerName" } } }
])
```

**Panel type:** Bar chart (count per `errorMessage`) or Table if you want the
`controllers` array visible per row, bar charts don't render array-valued fields well.

## 6. Confirm stale-cleanup is purging old docs

Should return close to 0, depending on how recently the scheduler last ran.

```
db.health_check_results.aggregate([
  { "$match": { "checkTriggeredAt": { "$lt": { "$dateSubtract": { "startDate": "$$NOW", "unit": "millisecond", "amount": 3600000 } } } } },
  { "$count": "staleDocsRemaining" }
])
```

**Panel type:** Stat panel, single number.
