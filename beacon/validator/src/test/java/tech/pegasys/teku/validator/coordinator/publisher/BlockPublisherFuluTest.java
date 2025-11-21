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
import java.util.Set;
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
  private final OptionalInt dasPublishWithholdColumnsEverySlots = OptionalInt.empty();
  private final BlockPublisherFulu blockPublisherFulu =
      new BlockPublisherFulu(
          mock(AsyncRunner.class),
          mock(BlockFactory.class),
          mock(BlockImportChannel.class),
          mock(BlockGossipChannel.class),
          dataColumnSidecarGossipChannel,
          mock(DutyMetrics.class),
          custodyGroupCountManager,
          dasPublishWithholdColumnsEverySlots,
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
    final BlockPublisherFulu blockPublisherFulu =
        new BlockPublisherFulu(
            mock(AsyncRunner.class),
            mock(BlockFactory.class),
            mock(BlockImportChannel.class),
            mock(BlockGossipChannel.class),
            dataColumnSidecarGossipChannel,
            mock(DutyMetrics.class),
            custodyGroupCountManager,
            OptionalInt.of(10),
            true);
    for (UInt64 i = UInt64.ZERO; i.isLessThan(1_000); i = i.increment()) {
      assertThat(blockPublisherFulu.mustPublishAll(i)).isFalse();
    }
  }

  @Test
  void mustPublishAll_isResetAfterPublish() {
    final int newWithholdSlots = 10;
    final BlockPublisherFulu blockPublisherFulu =
        new BlockPublisherFulu(
            mock(AsyncRunner.class),
            mock(BlockFactory.class),
            mock(BlockImportChannel.class),
            mock(BlockGossipChannel.class),
            dataColumnSidecarGossipChannel,
            mock(DutyMetrics.class),
            custodyGroupCountManager,
            OptionalInt.of(newWithholdSlots),
            true);
    assertThat(blockPublisherFulu.mustPublishAll(UInt64.ZERO)).isFalse();

    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(List.of(UInt64.valueOf(1), UInt64.valueOf(3)));
    blockPublisherFulu.publishDataColumnSidecars(
        dataColumnSidecars, BlockPublishingPerformance.NOOP);
    final List<DataColumnSidecar> expectedDataColumnSidecars =
        dataColumnSidecars.stream()
            .filter(sidecar -> Set.of(1, 3).contains(sidecar.getIndex().intValue()))
            .toList();
    // only custodied columns are published
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(expectedDataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);

    final UInt64 lastSlot = dataColumnSidecars.getFirst().getSlot();
    // publish all next newWithholdSlots slots
    for (UInt64 currentSlot = lastSlot;
        currentSlot.isLessThan(lastSlot.plus(newWithholdSlots));
        currentSlot = currentSlot.increment()) {
      assertThat(blockPublisherFulu.mustPublishAll(currentSlot)).isTrue();
    }
    // but after that we need to withhold again
    for (UInt64 currentSlot = lastSlot.plus(newWithholdSlots + 1);
        currentSlot.isLessThan(lastSlot.plus(newWithholdSlots * 2));
        currentSlot = currentSlot.increment()) {
      assertThat(blockPublisherFulu.mustPublishAll(currentSlot)).isFalse();
    }
  }

  @Test
  void publishDataColumnSidecarsWithheld() {
    final int newWithholdSlots = 10;
    final BlockPublisherFulu blockPublisherFulu =
        new BlockPublisherFulu(
            mock(AsyncRunner.class),
            mock(BlockFactory.class),
            mock(BlockImportChannel.class),
            mock(BlockGossipChannel.class),
            dataColumnSidecarGossipChannel,
            mock(DutyMetrics.class),
            custodyGroupCountManager,
            OptionalInt.of(newWithholdSlots),
            true);

    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(List.of(UInt64.valueOf(1), UInt64.valueOf(3)));
    blockPublisherFulu.publishDataColumnSidecars(
        dataColumnSidecars, BlockPublishingPerformance.NOOP);
    final List<DataColumnSidecar> expectedDataColumnSidecars =
        dataColumnSidecars.stream()
            .filter(sidecar -> Set.of(1, 3).contains(sidecar.getIndex().intValue()))
            .toList();
    // only custodied columns are published
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(expectedDataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);

    // next slot are all published
    final UInt64 lastSlot = dataColumnSidecars.getFirst().getSlot();
    final List<DataColumnSidecar> nextSlotDataColumnSidecars =
        dataStructureUtil.randomDataColumnSidecars(lastSlot.increment());
    blockPublisherFulu.publishDataColumnSidecars(
        nextSlotDataColumnSidecars, BlockPublishingPerformance.NOOP);
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(nextSlotDataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);

    // but after withhold slots DataColumnSidecars are filtered again
    final List<DataColumnSidecar> afterWithholdIsOverDataColumnSidecars =
        dataStructureUtil.randomDataColumnSidecars(lastSlot.plus(newWithholdSlots + 1));
    blockPublisherFulu.publishDataColumnSidecars(
        afterWithholdIsOverDataColumnSidecars, BlockPublishingPerformance.NOOP);
    final List<DataColumnSidecar> expectedAfterWithholdIsOverDataColumnSidecars =
        afterWithholdIsOverDataColumnSidecars.stream()
            .filter(sidecar -> Set.of(1, 3).contains(sidecar.getIndex().intValue()))
            .toList();
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(
            expectedAfterWithholdIsOverDataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
  }
}
