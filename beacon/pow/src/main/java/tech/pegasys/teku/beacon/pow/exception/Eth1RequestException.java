/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beacon.pow.exception;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;

public class Eth1RequestException extends RuntimeException {

  public Eth1RequestException() {
    super("Some eth1 endpoints threw an Exception or no eth1 endpoints available");
  }

  public static boolean shouldTryWithSmallerRange(Throwable err) {
    return ExceptionUtil.hasCause(
            err,
            SocketTimeoutException.class,
            RejectedRequestException.class,
            InterruptedIOException.class)
        || ExceptionUtil.getCause(err, Eth1RequestException.class)
            .map(Eth1RequestException::containsExceptionSolvableWithSmallerRange)
            .orElse(false);
  }

  private boolean containsExceptionSolvableWithSmallerRange() {
    return Stream.of(getSuppressed()).anyMatch(Eth1RequestException::shouldTryWithSmallerRange);
  }
}
