package tech.pegasys.teku.validator.remote;

import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;

public class FailoverRequestException extends RuntimeException {

  public FailoverRequestException(
      final String method, final Map<HttpUrl, Throwable> capturedExceptions) {
    super(createErrorMessage(method, capturedExceptions));
  }

  private static String createErrorMessage(
      final String method, final Map<HttpUrl, Throwable> capturedExceptions) {
    final String prefix =
        String.format(
            "Remote request (%s) failed on all configured Beacon Node endpoints.%n", method);
    final String errorSummary =
        capturedExceptions.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining(System.lineSeparator()));
    return prefix + errorSummary;
  }
}
