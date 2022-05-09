/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.services.executionlayer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.BuilderStatus;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerServiceTest {

  private final EventChannels eventChannels = mock(EventChannels.class);
  private final EventLogger eventLogger = mock(EventLogger.class);
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
      mock(ExecutionWeb3jClientProvider.class);
  private final ExecutionWeb3jClientProvider builderWeb3jClientProvider =
      mock(ExecutionWeb3jClientProvider.class);
  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);

  private final ExecutionLayerService underTest =
      new ExecutionLayerService(
          eventChannels,
          eventLogger,
          engineWeb3jClientProvider,
          Optional.of(builderWeb3jClientProvider),
          executionLayerChannel);

  @Test
  public void addsSubscribersToTheEventChannelOnServiceStart() {
    when(eventChannels.subscribe(any(), any())).thenReturn(eventChannels);

    underTest.start().reportExceptions();

    verify(eventChannels).subscribe(SlotEventsChannel.class, underTest);
    verify(eventChannels).subscribe(ExecutionLayerChannel.class, executionLayerChannel);

    underTest.stop().reportExceptions();
  }

  @Test
  public void performsBuilderHealthCheckOnSlot() {
    // Given builder status is ok
    when(executionLayerChannel.builderStatus())
        .thenReturn(SafeFuture.completedFuture(BuilderStatus.withOkStatus()));

    // When
    underTest.onSlot(UInt64.ONE);

    // Then
    verifyNoInteractions(eventLogger);

    // Given builder status is not ok
    when(executionLayerChannel.builderStatus())
        .thenReturn(SafeFuture.completedFuture(BuilderStatus.withFailedStatus("oops")));

    // When
    underTest.onSlot(UInt64.valueOf(2L));

    // Then
    verify(eventLogger).executionBuilderIsOffline("oops");

    // Given builder status is back ok
    when(executionLayerChannel.builderStatus())
        .thenReturn(SafeFuture.completedFuture(BuilderStatus.withOkStatus()));

    // When
    underTest.onSlot(UInt64.valueOf(3L));

    // Then
    verify(eventLogger).executionBuilderIsBackOnline();
  }
}
