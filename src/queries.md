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

## 3. Active vs inactive count

```
db.health_check_results.aggregate([
  { "$group": { "_id": "$active", "count": { "$sum": 1 } } }
])
```

## 4. Request time, short window

```
db.health_check_results.aggregate([
  { "$sort": { "checkTriggeredAt": -1 } },
  { "$limit": 100 },
  { "$project": { "controllerName": 1, "requestTimeMs": 1, "checkTriggeredAt": 1 } }
])
```

## 5. Error breakdown for failing controllers

```
db.health_check_results.aggregate([
  { "$match": { "active": false } },
  { "$group": { "_id": "$errorMessage", "count": { "$sum": 1 }, "controllers": { "$addToSet": "$controllerName" } } }
])
```

## 6. Confirm stale-cleanup is purging old docs

Should return close to 0, depending on how recently the scheduler last ran.

```
db.health_check_results.aggregate([
  { "$match": { "checkTriggeredAt": { "$lt": { "$dateSubtract": { "startDate": "$$NOW", "unit": "millisecond", "amount": 3600000 } } } } },
  { "$count": "staleDocsRemaining" }
])
```
