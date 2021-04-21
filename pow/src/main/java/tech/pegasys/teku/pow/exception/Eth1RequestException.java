/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.pow.exception;

import com.google.common.base.Throwables;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

public class Eth1RequestException extends RuntimeException {

  public Eth1RequestException() {
    super("Some eth1 endpoints threw an Exception or no eth1 endpoints available");
  }

  public boolean containsExceptionSolvableWithSmallerRange() {
    return Stream.of(getSuppressed())
        .anyMatch(
            err -> {
              final Throwable rootCause = Throwables.getRootCause(err);
              return (rootCause instanceof SocketTimeoutException
                  || rootCause instanceof RejectedRequestException);
            });
  }
}
