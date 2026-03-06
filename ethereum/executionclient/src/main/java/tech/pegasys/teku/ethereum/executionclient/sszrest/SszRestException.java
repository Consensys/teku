/*
 * Copyright Consensys Software Inc., 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.ethereum.executionclient.sszrest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class SszRestException extends Exception {

  private final int httpStatusCode;
  private final boolean networkError;

  public SszRestException(final int httpStatusCode, final String message) {
    super(message);
    this.httpStatusCode = httpStatusCode;
    this.networkError = false;
  }

  private SszRestException(final String message, final Throwable cause) {
    super(message, cause);
    this.httpStatusCode = -1;
    this.networkError = true;
  }

  public int getHttpStatusCode() {
    return httpStatusCode;
  }

  public boolean isNetworkError() {
    return networkError;
  }

  /** Parse a text/plain error response body per the execution-apis SSZ spec. */
  public static SszRestException fromTextError(final byte[] body, final int httpStatusCode) {
    final String message =
        body.length > 0
            ? new String(body, StandardCharsets.UTF_8)
            : "HTTP " + httpStatusCode;
    return new SszRestException(httpStatusCode, message);
  }

  public static SszRestException fromNetworkError(final Throwable cause) {
    return new SszRestException("SSZ-REST network error: " + cause.getMessage(), cause);
  }

  public static boolean isNetworkError(final Throwable throwable) {
    final Throwable cause = unwrapCause(throwable);
    if (cause instanceof SszRestException sszRestEx) {
      return sszRestEx.isNetworkError();
    }
    return cause instanceof ConnectException
        || cause instanceof SocketTimeoutException
        || cause instanceof UnknownHostException
        || cause instanceof IOException;
  }

  @SuppressWarnings("ReferenceComparison")
  private static Throwable unwrapCause(final Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current != current.getCause()) {
      current = current.getCause();
    }
    return current;
  }
}
