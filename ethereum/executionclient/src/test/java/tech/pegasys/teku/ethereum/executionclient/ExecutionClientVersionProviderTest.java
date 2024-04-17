/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.ethereum.executionclient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionClientVersionProviderTest {

  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);
  private final ExecutionClientVersionChannel publishChannel =
      mock(ExecutionClientVersionChannel.class);
  private final ClientVersion executionClientVersion =
      new ClientVersion("BU", "besu", "1.0.0", Bytes4.fromHexString("8dba2981"));

  @BeforeEach
  public void setUp() {
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(executionClientVersion)));
  }

  @Test
  public void pushUnknownExecutionClientVersionInChannel_whenFailed() {
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    new ExecutionClientVersionProvider(
        executionLayerChannel, publishChannel, ClientVersion.UNKNOWN);
    verify(publishChannel).onExecutionClientVersion(ClientVersion.UNKNOWN);
  }

  @Test
  public void doesNotTryToUpdateExecutionClientVersion_whenElHasNotBeenUnavailable() {
    final ExecutionClientVersionProvider executionClientVersionProvider =
        new ExecutionClientVersionProvider(
            executionLayerChannel, publishChannel, ClientVersion.UNKNOWN);

    executionClientVersionProvider.onAvailabilityUpdated(true);
    // EL called only one time
    verify(executionLayerChannel).engineGetClientVersion(any());
    verify(publishChannel).onExecutionClientVersion(executionClientVersion);
  }

  @Test
  public void updatesExecutionClientVersion_whenElIsAvailableAfterBeingUnavailable() {
    final ExecutionClientVersionProvider executionClientVersionProvider =
        new ExecutionClientVersionProvider(
            executionLayerChannel, publishChannel, ClientVersion.UNKNOWN);

    verify(publishChannel).onExecutionClientVersion(executionClientVersion);

    final ClientVersion updatedExecutionClientVersion =
        new ClientVersion("BU", "besu", "1.0.1", Bytes4.fromHexString("efd1bc70"));
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(updatedExecutionClientVersion)));

    executionClientVersionProvider.onAvailabilityUpdated(false);
    executionClientVersionProvider.onAvailabilityUpdated(true);
    // EL called two times
    verify(executionLayerChannel, times(2)).engineGetClientVersion(any());

    verify(publishChannel).onExecutionClientVersion(updatedExecutionClientVersion);

    executionClientVersionProvider.onAvailabilityUpdated(false);
    executionClientVersionProvider.onAvailabilityUpdated(true);

    // EL called three times
    verify(executionLayerChannel, times(3)).engineGetClientVersion(any());

    // 1st time - executionClientVersion, 2nd time - updatedExecutionClientVersion, 3rd time -
    // ignoring the same
    verify(publishChannel, times(2)).onExecutionClientVersion(any());
  }

  @Test
  public void doesNotPushUnknownVersionInChannel_whenELIsDownInTheMiddle() {
    final ExecutionClientVersionProvider executionClientVersionProvider =
        new ExecutionClientVersionProvider(
            executionLayerChannel, publishChannel, ClientVersion.UNKNOWN);

    // Good start
    verify(publishChannel).onExecutionClientVersion(executionClientVersion);
    reset(publishChannel);

    // EL is broken
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    executionClientVersionProvider.onAvailabilityUpdated(false);
    executionClientVersionProvider.onAvailabilityUpdated(true);
    // EL called two times
    verify(executionLayerChannel, times(2)).engineGetClientVersion(any());
    // UNKNOWN version is not pushed in the channel
    verify(publishChannel, never()).onExecutionClientVersion(any());

    // EL is back
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(executionClientVersion)));

    executionClientVersionProvider.onAvailabilityUpdated(false);
    executionClientVersionProvider.onAvailabilityUpdated(true);
    // EL called 3 times
    verify(executionLayerChannel, times(3)).engineGetClientVersion(any());
    // Version is the same, not pushed in the channel
    verify(publishChannel, never()).onExecutionClientVersion(any());
  }
}
