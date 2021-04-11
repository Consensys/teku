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
import java.util.ArrayList;
import java.util.Optional;

public class Eth1RequestExceptionsContainer extends RuntimeException {
  private Optional<ArrayList<Throwable>> thrownErrors;

  public Eth1RequestExceptionsContainer() {
    super("One or more eth1 endpoints thrown an Exception");
    thrownErrors = Optional.empty();
  }

  public void add(Throwable error) {
    // Lazily init ArrayList
    thrownErrors =
        thrownErrors
            .or(() -> Optional.of(new ArrayList<>()))
            .map(
                al -> {
                  al.add(error);
                  return al;
                });
  }

  public boolean containsExceptionSolvableWithSmallerRange() {
    return thrownErrors
        .map(
            al ->
                al.stream()
                    .anyMatch(
                        err -> {
                          final Throwable rootCause = Throwables.getRootCause(err);
                          return (rootCause instanceof SocketTimeoutException
                              || rootCause instanceof RejectedRequestException);
                        }))
        .orElse(Boolean.FALSE);
  }
}
