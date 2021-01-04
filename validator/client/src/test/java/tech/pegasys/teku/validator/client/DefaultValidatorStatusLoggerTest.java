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

package tech.pegasys.teku.validator.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class DefaultValidatorStatusLoggerTest {

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final List<BLSPublicKey> validatorKeys = List.of(BLSPublicKey.random(0));
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final DefaultValidatorStatusLogger logger =
      new DefaultValidatorStatusLogger(validatorKeys, validatorApiChannel, asyncRunner);

  @Test
  @SuppressWarnings("unchecked")
  void shouldRetryPrintingInitialValidatorStatuses() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Collections.EMPTY_MAP)));

    logger.printInitialValidatorStatuses().reportExceptions();
    verify(validatorApiChannel).getValidatorStatuses(validatorKeys);

    asyncRunner.executeQueuedActions();

    verify(validatorApiChannel, times(2)).getValidatorStatuses(validatorKeys);

    asyncRunner.executeQueuedActions();

    verify(validatorApiChannel, times(3)).getValidatorStatuses(validatorKeys);

    asyncRunner.executeUntilDone();

    verify(validatorApiChannel, times(3)).getValidatorStatuses(validatorKeys);
  }
}
