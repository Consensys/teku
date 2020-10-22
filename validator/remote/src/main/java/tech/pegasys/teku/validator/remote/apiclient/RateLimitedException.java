package tech.pegasys.teku.validator.remote.apiclient;

public class RateLimitedException extends RuntimeException {
  public RateLimitedException(final String url) {
    super("Request rejected due to exceeding rate limit for URL: " + url);
  }
}
