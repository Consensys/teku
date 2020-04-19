/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.services.beaconchain;

import java.nio.ByteOrder;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.blocks.NodeSlot;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class BeaconChainMetrics {
  private static final long NOT_SET = 0L;
  private final RecentChainData recentChainData;
  private volatile NodeSlot nodeSlot;

  public BeaconChainMetrics(final RecentChainData recentChainData, NodeSlot nodeSlot) {
    this.recentChainData = recentChainData;
    this.nodeSlot = nodeSlot;
  }

  public void initialise(final MetricsSystem metricsSystem) {
    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "slot",
        "Latest slot recorded by the beacon chain",
        this::getCurrentSlotValue);
    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "head_slot",
        "Slot of the head block of the beacon chain",
        this::getHeadSlotValue);
    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "head_root",
        "Root of the head block of the beacon chain",
        this::getHeadRootValue);

    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "finalized_epoch",
        "Current finalized epoch",
        this::getFinalizedEpochValue);
    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "finalized_root",
        "Current finalized root",
        this::getFinalizedRootValue);

    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "current_justified_epoch",
        "Current justified epoch",
        this::getJustifiedEpochValue);
    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "current_justified_root",
        "Current justified root",
        this::getJustifiedRootValue);

    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "previous_justified_epoch",
        "Current previously justified epoch",
        this::getPreviousJustifiedEpochValue);
    metricsSystem.createGauge(
        ArtemisMetricCategory.BEACON,
        "previous_justified_root",
        "Current previously justified root",
        this::getPreviousJustifiedRootValue);
  }

  static long getLongFromRoot(Bytes32 root) {
    return root.getLong(24, ByteOrder.LITTLE_ENDIAN);
  }

  long getCurrentSlotValue() {
    return nodeSlot.longValue();
  }

  long getHeadSlotValue() {
    if (recentChainData.isPreGenesis()) {
      return NOT_SET;
    }
    return recentChainData.getBestSlot().longValue();
  }

  long getFinalizedRootValue() {
    Optional<BeaconBlockAndState> maybeBlockAndState = recentChainData.getBestBlockAndState();
    if (maybeBlockAndState.isPresent()) {
      Bytes32 root = maybeBlockAndState.get().getState().getFinalized_checkpoint().getRoot();
      return getLongFromRoot(root);
    }
    return 0L;
  }

  long getPreviousJustifiedRootValue() {
    Optional<BeaconBlockAndState> maybeBlockAndState = recentChainData.getBestBlockAndState();
    if (maybeBlockAndState.isPresent()) {
      Bytes32 root =
          maybeBlockAndState.get().getState().getPrevious_justified_checkpoint().getRoot();
      return getLongFromRoot(root);
    }
    return 0L;
  }

  long getJustifiedRootValue() {
    Optional<BeaconBlockAndState> maybeBlockAndState = recentChainData.getBestBlockAndState();
    if (maybeBlockAndState.isPresent()) {
      Bytes32 root =
          maybeBlockAndState.get().getState().getCurrent_justified_checkpoint().getRoot();
      return getLongFromRoot(root);
    }
    return 0L;
  }

  long getHeadRootValue() {
    if (recentChainData.isPreGenesis()) {
      return NOT_SET;
    }
    Optional<Bytes32> maybeBlockRoot = recentChainData.getBestBlockRoot();
    return maybeBlockRoot.map(BeaconChainMetrics::getLongFromRoot).orElse(0L);
  }

  long getFinalizedEpochValue() {
    if (recentChainData.isPreGenesis()) {
      return NOT_SET;
    }
    return recentChainData.getFinalizedEpoch().longValue();
  }

  long getJustifiedEpochValue() {
    if (recentChainData.isPreGenesis()) {
      return NOT_SET;
    }
    return recentChainData.getBestJustifiedEpoch().longValue();
  }

  long getPreviousJustifiedEpochValue() {
    Optional<BeaconBlockAndState> maybeBlockAndState = recentChainData.getBestBlockAndState();
    return maybeBlockAndState
        .map(
            beaconBlockAndState ->
                beaconBlockAndState
                    .getState()
                    .getPrevious_justified_checkpoint()
                    .getEpoch()
                    .longValue())
        .orElse(0L);
  }
}
