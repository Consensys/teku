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

package tech.pegasys.teku.beacon.sync.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ReconstructHistoricalStatesServiceTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private ReconstructHistoricalStatesService service;

  @Test
  public void shouldCompleteExceptionallyWithEmptyResource() {
    createService(Optional.empty());

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompletedExceptionally();
    assertThatSafeFuture(res)
        .isCompletedExceptionallyWith(IllegalStateException.class); // todo check exception message

    verify(chainDataClient, never()).getInitialAnchor();
  }

  @Test
  public void shouldCompleteExceptionallyWithFailedLoadState() {
    createService(Optional.of("invalid")); // invalid resource

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompletedExceptionally();
    assertThatSafeFuture(res).isCompletedExceptionallyWith(InvalidConfigurationException.class);

    verify(chainDataClient, never()).getInitialAnchor();
  }

  @Test
  public void shouldCompleteSkipWithEmptyCheckpoint(@TempDir final Path tempDir)
      throws IOException {
    createService(createGenesisStateResource(tempDir));
    when(chainDataClient.getInitialAnchor())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
  }

  @Test
  public void shouldCompleteStart(@TempDir final Path tempDir) throws IOException {
    createService(createGenesisStateResource(tempDir));
    when(chainDataClient.getInitialAnchor())
        .thenReturn(SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomCheckpoint())));
    when(chainDataClient.getBlockAtSlotExact(any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomSignedBeaconBlock(1))));

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
  }

  private Optional<String> createGenesisStateResource(final Path tempDir) throws IOException {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final File file =
        Files.write(tempDir.resolve("initial-state.ssz"), state.sszSerialize().toArrayUnsafe())
            .toFile();
    return Optional.of(file.getAbsolutePath());
  }

  private void createService(final Optional<String> genesisStateResource) {
    service =
        new ReconstructHistoricalStatesService(
            storageUpdateChannel, chainDataClient, spec, genesisStateResource);
  }
}
