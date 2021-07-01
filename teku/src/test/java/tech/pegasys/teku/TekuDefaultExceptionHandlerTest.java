/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;

class TekuDefaultExceptionHandlerTest {

  private final StatusLogger log = mock(StatusLogger.class);
  private final TekuDefaultExceptionHandler exceptionHandler = new TekuDefaultExceptionHandler(log);

  @Test
  void logWarningIfAssertFails() {
    final IllegalArgumentException exception = new IllegalArgumentException("whoops");
    exceptionHandler.uncaughtException(Thread.currentThread(), exception);

    verify(log).specificationFailure(any(), eq(exception));
  }

  @Test
  void logFatalIfNonAssertExceptionThrown() {
    final RuntimeException exception = new RuntimeException("test");

    exceptionHandler.uncaughtException(Thread.currentThread(), exception);

    verify(log).unexpectedFailure(anyString(), eq(exception));
  }
}
