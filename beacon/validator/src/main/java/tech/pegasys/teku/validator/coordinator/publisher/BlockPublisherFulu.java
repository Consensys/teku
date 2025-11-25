/*
 * Copyright Consensys Software Inc., 2022
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

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class BlockPublisherFulu extends BlockPublisherPhase0 {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel;

  private final OptionalInt dasPublishWithholdColumnsEverySlots;

  private final AtomicReference<UInt64> lastWithheldSlot = new AtomicReference<>(null);

  private final CustodyGroupCountManager custodyGroupCountManager;

  public BlockPublisherFulu(
      final AsyncRunner asyncRunner,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel,
      final DutyMetrics dutyMetrics,
      final CustodyGroupCountManager custodyGroupCountManager,
      final OptionalInt dasPublishWithholdColumnsEverySlots,
      final boolean gossipBlobsAfterBlock) {
    super(
        asyncRunner,
        blockFactory,
        blockGossipChannel,
        blockImportChannel,
        dutyMetrics,
        gossipBlobsAfterBlock);
    this.dataColumnSidecarGossipChannel = dataColumnSidecarGossipChannel;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.dasPublishWithholdColumnsEverySlots = dasPublishWithholdColumnsEverySlots;
    dasPublishWithholdColumnsEverySlots.ifPresent(
        withholdColumnCountValue ->
            LOG.warn(
                "NOTE: Software is running in development mode. "
                    + "Every {} slots non-custodied dataColumnSidecars will "
                    + "be withheld on block publishing",
                withholdColumnCountValue));
  }

  @Override
  void publishBlobSidecars(
      final List<BlobSidecar> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    throw new RuntimeException("Unexpected call to publishBlobSidecars in Fulu");
  }

  @Override
  void publishDataColumnSidecars(
      final List<DataColumnSidecar> dataColumnSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    blockPublishingPerformance.dataColumnSidecarsPublishingInitiated();
    if (dataColumnSidecars.isEmpty()) {
      return;
    }

    final List<DataColumnSidecar> dataColumnSidecarsToPublish;
    if (mustPublishAll(dataColumnSidecars.getFirst().getSlot())) {
      dataColumnSidecarsToPublish = dataColumnSidecars;
    } else {
      final Set<UInt64> custodyColumnIndices =
          new HashSet<>(custodyGroupCountManager.getCustodyColumnIndices());
      dataColumnSidecarsToPublish =
          dataColumnSidecars.stream()
              .filter(sidecar -> custodyColumnIndices.contains(sidecar.getIndex()))
              .toList();
      LOG.warn(
          "Withholding {} non-custodied sidecars at {}",
          dataColumnSidecars.size() - dataColumnSidecarsToPublish.size(),
          dataColumnSidecars.getFirst().getSlotAndBlockRoot());
      lastWithheldSlot.set(dataColumnSidecars.getFirst().getSlot());
    }

    dataColumnSidecarGossipChannel.publishDataColumnSidecars(
        dataColumnSidecarsToPublish, RemoteOrigin.LOCAL_PROPOSAL);
  }

  @VisibleForTesting
  protected boolean mustPublishAll(final UInt64 slot) {
    final boolean publishAll;
    if (dasPublishWithholdColumnsEverySlots.isEmpty()) {
      publishAll = true;
    } else if (lastWithheldSlot.get() == null) {
      publishAll = false;
    } else {
      publishAll =
          slot.minusMinZero(dasPublishWithholdColumnsEverySlots.getAsInt())
              .isLessThan(lastWithheldSlot.get());
    }

    return publishAll;
  }
}
