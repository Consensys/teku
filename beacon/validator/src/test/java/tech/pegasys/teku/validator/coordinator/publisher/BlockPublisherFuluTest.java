/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

class BlockPublisherFuluTest {
  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel =
      mock(DataColumnSidecarGossipChannel.class);
  private final CustodyGroupCountManager custodyGroupCountManager =
      mock(CustodyGroupCountManager.class);
  private final BlockPublisherFulu blockPublisherFulu =
      new BlockPublisherFulu(
          mock(AsyncRunner.class),
          mock(BlockFactory.class),
          mock(BlockImportChannel.class),
          mock(BlockGossipChannel.class),
          dataColumnSidecarGossipChannel,
          mock(DutyMetrics.class),
          custodyGroupCountManager,
          OptionalInt.empty(),
          true);
  private final int dasPublishWithholdColumnsEverySlots = 10;
  final BlockPublisherFulu blockPublisherFuluTest =
      new BlockPublisherFulu(
          mock(AsyncRunner.class),
          mock(BlockFactory.class),
          mock(BlockImportChannel.class),
          mock(BlockGossipChannel.class),
          dataColumnSidecarGossipChannel,
          mock(DutyMetrics.class),
          custodyGroupCountManager,
          OptionalInt.of(dasPublishWithholdColumnsEverySlots),
          true);

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final List<DataColumnSidecar> dataColumnSidecars =
      dataStructureUtil.randomDataColumnSidecars();

  @Test
  void publishBlobSidecars_shouldThrow() {
    final BlobSidecar blobSidecar = mock(BlobSidecar.class);
    final List<BlobSidecar> blobSidecars = List.of(blobSidecar);
    assertThatThrownBy(
            () ->
                blockPublisherFulu.publishBlobSidecars(
                    blobSidecars, BlockPublishingPerformance.NOOP))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Unexpected call to publishBlobSidecars in Fulu");
  }

  @Test
  void publishDataColumnSidecars() {
    blockPublisherFulu.publishDataColumnSidecars(
        dataColumnSidecars, BlockPublishingPerformance.NOOP);

    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
  }

  @Test
  void mustPublishAll_isAlwaysTrueWhenWithholdIsEmpty() {
    for (UInt64 i = UInt64.ZERO; i.isLessThan(1_000_000); i = i.increment()) {
      assertThat(blockPublisherFulu.mustPublishAll(i)).isTrue();
    }
  }

  @Test
  void mustPublishAll_isAlwaysFalseWhenWithholdIsSetBeforeFirstUse() {
    for (UInt64 i = UInt64.ZERO; i.isLessThan(1_000); i = i.increment()) {
      assertThat(blockPublisherFuluTest.mustPublishAll(i)).isFalse();
    }

    // when trying to publish in any slot, non-custodied columns will be withheld
    final List<UInt64> custodiedColumns = List.of(UInt64.valueOf(1), UInt64.valueOf(3));
    when(custodyGroupCountManager.getCustodyColumnIndices()).thenReturn(custodiedColumns);
    blockPublisherFuluTest.publishDataColumnSidecars(
        dataColumnSidecars, BlockPublishingPerformance.NOOP);
    final List<DataColumnSidecar> expectedDataColumnSidecars =
        dataColumnSidecars.stream()
            .filter(sidecar -> custodiedColumns.contains(sidecar.getIndex()))
            .toList();
    // only custodied columns are published
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(expectedDataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
  }

  @Test
  void mustPublishAll_isResetAfterPublish() {
    assertThat(blockPublisherFuluTest.mustPublishAll(UInt64.ZERO)).isFalse();

    final List<UInt64> custodiedColumns = List.of(UInt64.valueOf(1), UInt64.valueOf(3));
    when(custodyGroupCountManager.getCustodyColumnIndices()).thenReturn(custodiedColumns);
    blockPublisherFuluTest.publishDataColumnSidecars(
        dataColumnSidecars, BlockPublishingPerformance.NOOP);
    // only custodied columns are published
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(
            dataColumnSidecars.stream()
                .filter(sidecar -> custodiedColumns.contains(sidecar.getIndex()))
                .toList(),
            RemoteOrigin.LOCAL_PROPOSAL);

    final UInt64 lastSlot = dataColumnSidecars.getFirst().getSlot();
    // publish all next newWithholdSlots slots
    for (UInt64 currentSlot = lastSlot;
        currentSlot.isLessThan(lastSlot.plus(dasPublishWithholdColumnsEverySlots));
        currentSlot = currentSlot.increment()) {
      assertThat(blockPublisherFuluTest.mustPublishAll(currentSlot)).isTrue();
    }

    // but after that we need to withhold again
    for (UInt64 currentSlot = lastSlot.plus(dasPublishWithholdColumnsEverySlots + 1);
        currentSlot.isLessThan(lastSlot.plus(dasPublishWithholdColumnsEverySlots * 2));
        currentSlot = currentSlot.increment()) {
      assertThat(blockPublisherFuluTest.mustPublishAll(currentSlot)).isFalse();
    }
  }
}
