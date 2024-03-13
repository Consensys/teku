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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

class DefaultGraffitiProviderImplTest {

  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);

  @BeforeEach
  public void setUp() {
    final ClientVersion executionClientVersion =
        new ClientVersion("BU", "besu", "1.0.0", Bytes4.fromHexString("8dba2981"));
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(executionClientVersion)));
  }

  @Test
  public void getsExecutionAndTekuDefaultGraffitiIfEngineClientVersionSucceedsOnInitialization() {
    final DefaultGraffitiProviderImpl defaultGraffitiProvider =
        new DefaultGraffitiProviderImpl(executionLayerChannel);

    assertExecutionAndTekuGraffiti(defaultGraffitiProvider.getDefaultGraffiti());
  }

  @Test
  public void getsDefaultGraffitiFallbackIfEngineGetClientVersionFailsOnInitialization() {
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    final DefaultGraffitiProviderImpl defaultGraffitiProvider =
        new DefaultGraffitiProviderImpl(executionLayerChannel);

    assertGraffitiFallback(defaultGraffitiProvider.getDefaultGraffiti());
  }

  @Test
  public void doesNotTryToUpdateDefaultGraffitiIfElHasNotBeenUnavailable() {
    final DefaultGraffitiProviderImpl defaultGraffitiProvider =
        new DefaultGraffitiProviderImpl(executionLayerChannel);

    assertExecutionAndTekuGraffiti(defaultGraffitiProvider.getDefaultGraffiti());

    defaultGraffitiProvider.onAvailabilityUpdated(true);

    // no change in graffiti
    assertExecutionAndTekuGraffiti(defaultGraffitiProvider.getDefaultGraffiti());
    // EL called only one time
    verify(executionLayerChannel, times(1)).engineGetClientVersion(any());
  }

  @Test
  public void updatesDefaultGraffitiIfElIsAvailableAfterBeingUnavailable() {
    final DefaultGraffitiProviderImpl defaultGraffitiProvider =
        new DefaultGraffitiProviderImpl(executionLayerChannel);

    assertExecutionAndTekuGraffiti(defaultGraffitiProvider.getDefaultGraffiti());

    final ClientVersion updatedExecutionClientVersion =
        new ClientVersion("BU", "besu", "1.0.1", Bytes4.fromHexString("efd1bc70"));
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(updatedExecutionClientVersion)));

    defaultGraffitiProvider.onAvailabilityUpdated(false);
    defaultGraffitiProvider.onAvailabilityUpdated(true);

    // graffiti has changed
    final Bytes32 changedGraffiti = defaultGraffitiProvider.getDefaultGraffiti();
    assertThat(new String(changedGraffiti.toArrayUnsafe(), StandardCharsets.UTF_8))
        .matches("BUefd1bc70TK[0-9a-fA-F]{8}.+");
    // EL called two times
    verify(executionLayerChannel, times(2)).engineGetClientVersion(any());
  }

  private void assertExecutionAndTekuGraffiti(final Bytes32 graffiti) {
    assertThat(new String(graffiti.toArrayUnsafe(), StandardCharsets.UTF_8))
        .matches("BU8dba2981TK[0-9a-fA-F]{8}.+");
  }

  private void assertGraffitiFallback(final Bytes32 graffiti) {
    assertThat(new String(graffiti.toArrayUnsafe(), StandardCharsets.UTF_8)).startsWith("teku/v");
  }
}
