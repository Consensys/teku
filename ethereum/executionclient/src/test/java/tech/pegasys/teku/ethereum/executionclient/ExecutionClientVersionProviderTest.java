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
  public void doesNotPublishExecutionClientVersionIfFailed() {
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    new ExecutionClientVersionProvider(
        executionLayerChannel, publishChannel, ClientVersion.UNKNOWN);
    verify(publishChannel, never()).onExecutionClientVersion(any());
  }

  @Test
  public void doesNotTryToUpdateExecutionClientVersionIfElHasNotBeenUnavailable() {
    final ExecutionClientVersionProvider executionClientVersionProvider =
        new ExecutionClientVersionProvider(
            executionLayerChannel, publishChannel, ClientVersion.UNKNOWN);

    executionClientVersionProvider.onAvailabilityUpdated(true);
    // EL called only one time
    verify(executionLayerChannel, times(1)).engineGetClientVersion(any());
  }

  @Test
  public void updatesExecutionClientVersionElIsAvailableAfterBeingUnavailable() {
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
}
