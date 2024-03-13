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
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

class DefaultGraffitiProviderImplTest {

  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);

  private final DefaultGraffitiProviderImpl defaultGraffitiProvider =
      new DefaultGraffitiProviderImpl(executionLayerChannel);

  @Test
  public void getsDefaultGraffitiFallbackIfExecutionLayerHasNotBeenCalled() {
    assertDefaultGraffitiFallback(defaultGraffitiProvider.getDefaultGraffiti());
  }

  @Test
  public void getsDefaultGraffitiFallbackIfExecutionLayerIsNotAvailable() {
    defaultGraffitiProvider.onAvailabilityUpdated(false);
    assertDefaultGraffitiFallback(defaultGraffitiProvider.getDefaultGraffiti());
  }

  @Test
  public void getsDefaultGraffitiFallbackIfEngineGetClientVersionFails() {
    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));
    defaultGraffitiProvider.onAvailabilityUpdated(true);
    assertDefaultGraffitiFallback(defaultGraffitiProvider.getDefaultGraffiti());
  }

  @Test
  public void getsExecutionAndTekuDefaultGraffitiIfEngineClientVersionSucceeds() {
    final ClientVersion executionClientVersion =
        new ClientVersion("BU", "besu", "1.0.0", Bytes4.fromHexString("8dba2981"));

    when(executionLayerChannel.engineGetClientVersion(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(executionClientVersion)));
    defaultGraffitiProvider.onAvailabilityUpdated(true);

    final Bytes32 defaultGraffiti = defaultGraffitiProvider.getDefaultGraffiti();

    assertThat(new String(defaultGraffiti.toArrayUnsafe(), StandardCharsets.UTF_8))
        .matches("BU8dba2981TK[0-9a-fA-F]{8}.+");
  }

  private void assertDefaultGraffitiFallback(final Bytes32 graffiti) {
    assertThat(new String(graffiti.toArrayUnsafe(), StandardCharsets.UTF_8)).startsWith("teku/v");
  }
}
