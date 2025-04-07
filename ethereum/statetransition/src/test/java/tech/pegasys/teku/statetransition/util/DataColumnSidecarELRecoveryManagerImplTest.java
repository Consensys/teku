/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand.createCustodyGroupCountManager;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELRecoveryManager;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarELRecoveryManagerImplTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 historicalTolerance = UInt64.valueOf(5);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final KZG kzg = mock(KZG.class);

  private final List<KZGCellAndProof> kzgCellAndProofs =
      IntStream.range(0, 128)
          .mapToObj(
              __ ->
                  new KZGCellAndProof(
                      new KZGCell(Bytes.random(2048)), KZGProof.fromBytesCompressed(Bytes48.ZERO)))
          .toList();

  @SuppressWarnings("unchecked")
  final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher = mock(Consumer.class);

  final CustodyGroupCountManager custodyGroupCountManager = createCustodyGroupCountManager(4);

  private final DataColumnSidecarELRecoveryManager dataColumnSidecarELRecoveryManager =
      new PoolFactory(metricsSystem)
          .createDataColumnSidecarELRecoveryManager(
              spec,
              asyncRunner,
              recentChainData,
              executionLayer,
              kzg,
              dataColumnSidecarPublisher,
              custodyGroupCountManager);

  private UInt64 currentSlot = historicalTolerance.times(2);

  @BeforeEach
  public void setup() {
    when(executionLayer.engineGetBlobAndProofs(any(), eq(currentSlot)))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(kzg.computeCellsAndProofs(any())).thenReturn(kzgCellAndProofs);
    setSlot(currentSlot);
  }

  private void setSlot(final UInt64 slot) {
    currentSlot = slot;
    dataColumnSidecarELRecoveryManager.onSlot(slot);
    when(recentChainData.computeTimeAtSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  public void onNewBlock_startsFromFuluBlocks() {
    final Spec minimalWithFuluForkEpoch =
        TestSpecFactory.createMinimalWithFuluForkEpoch(UInt64.ONE);
    final DataColumnSidecarELRecoveryManager dataColumnSidecarELRecoveryManagerCustom =
        new PoolFactory(metricsSystem)
            .createDataColumnSidecarELRecoveryManager(
                minimalWithFuluForkEpoch,
                asyncRunner,
                recentChainData,
                executionLayer,
                kzg,
                dataColumnSidecarPublisher,
                custodyGroupCountManager);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    dataColumnSidecarELRecoveryManagerCustom.onSlot(UInt64.ONE);
    dataColumnSidecarELRecoveryManagerCustom.onNewBlock(block, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);

    final int fuluSlot = minimalWithFuluForkEpoch.slotsPerEpoch(UInt64.ZERO) + 1;
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(fuluSlot);
    dataColumnSidecarELRecoveryManagerCustom.onSlot(UInt64.valueOf(fuluSlot));
    dataColumnSidecarELRecoveryManagerCustom.onNewBlock(block2, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(executionLayer).engineGetBlobAndProofs(any(), any());
  }

  @Test
  public void onNewBlock_ignoresLocal() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.of(RemoteOrigin.LOCAL_PROPOSAL));
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void onNewDataColumnSidecar_ignoresLocalOrRecovered() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block, UInt64.ZERO);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
        dataColumnSidecar, RemoteOrigin.LOCAL_PROPOSAL);
    dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
        dataColumnSidecar, RemoteOrigin.RECOVERED);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void onNewDataColumnSidecar_startsRecovery() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block, UInt64.ZERO);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
        dataColumnSidecar, RemoteOrigin.GOSSIP);
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(executionLayer).engineGetBlobAndProofs(any(), any());
  }

  @Test
  public void shouldNotPublish_whenNotCurrentSlot() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot.minus(1));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void shouldNotPublish_whenNotAllBlobsRetrieved() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars =
        dataStructureUtil.randomBlobSidecarsForBlock(block).subList(0, 1);
    final List<Optional<BlobAndProof>> blobAndProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndProof(blobSidecar.getBlob(), dataStructureUtil.randomKZGProof()))
            .map(Optional::of)
            .toList();
    when(executionLayer.engineGetBlobAndProofs(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndProofs));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(executionLayer).engineGetBlobAndProofs(any(), any());
    verifyNoInteractions(dataColumnSidecarPublisher);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldPublish_whenAllBlobsRetrieved() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<Optional<BlobAndProof>> blobAndProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndProof(blobSidecar.getBlob(), dataStructureUtil.randomKZGProof()))
            .map(Optional::of)
            .toList();
    when(executionLayer.engineGetBlobAndProofs(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndProofs));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    final ArgumentCaptor<List<DataColumnSidecar>> dataColumnSidecarsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(dataColumnSidecarPublisher).accept(dataColumnSidecarsCaptor.capture());
    assertThat(dataColumnSidecarsCaptor.getValue().size()).isEqualTo(4);
  }
}
