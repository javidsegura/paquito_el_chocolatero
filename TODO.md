/**
 * Runs a TWS health check against every registered controller, persisting one
 * {@link HealthCheckResultDocument} per controller, then purges this server's
 * stale results older than the configured retention window.
 *
 * @throws CustomException if a controller's TWS call fails in a way that
 *         prevents the health check from completing
 */
public void performHealthCheck() { ... }

/**
 * Deletes health check results for the given server whose trigger time is
 * older than the configured stale-data threshold.
 *
 * @param fromServer hostname the results were recorded under
 */