# PR: TWS health check hostname, timing, stale-result cleanup, and tests

## Summary

- Replaced the hardcoded `"localhost"` value for `fromServer` with real hostname resolution via `InetAddress.getLocalHost().getHostName()`.
- Implemented `requestTimeMs` using `Duration.between(checkTriggeredAt, responseReceivedAt).toMillis()`.
- Added stale health-check-result cleanup: a derived repository method (`deleteByFromServerAndCheckTriggeredAtBefore`) removes a given server's own results older than a configurable threshold, run once per `performHealthCheck()` invocation.
- Added unit tests covering `HealthCheckService` end to end: happy path, non-2xx response, null response, TWS exception branches (connect timeout, client error, server error, unexpected exception), and multi-controller behavior. 9 tests, all passing.

## Beyond the original ask, flag for extra review attention

1. **Refactored `performHealthCheck` / `performHealthCheckOnController`.** Extracted per-controller document construction and the TWS call into its own method that builds and returns a fully-populated document, instead of mutating a document passed in from the caller. This is a structural change only, the existing error-handling behavior (one controller's failure aborts the whole run) was not changed.
2. **Made the stale-result threshold configurable.** Added `staleDataRemovalThreshold` to the `application-properties` Mongo collection (alongside the existing `healthCheckInterval`), exposed via `HealtCheckintervalConfig`, default `3600000` ms (60 minutes), instead of hardcoding it in `HealthCheckService`.

Neither was explicitly requested, both felt like reasonable extensions of the assigned work, but worth a closer look in review since they touch more surface area than the original scope.

## Not included in this PR

- Grafana dashboards/queries. Still pending input from whoever is taking this to prod; the "delete stale docs so Grafana isn't overloaded" reasoning I was given is not yet confirmed as the actual justification, and the dashboard shape itself is still open.

## Testing

- `HealthCheckServiceTest`: 9/9 passing, covering document population, hostname/timing fields, stale-result cleanup call, and all TWS failure branches.
