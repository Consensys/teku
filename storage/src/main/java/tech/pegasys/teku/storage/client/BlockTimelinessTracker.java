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

package tech.pegasys.teku.storage.client;

import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;

import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BlockTimelinessTracker {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Bytes32, Boolean> blockTimeliness;
  private final TimeProvider timeProvider;
  private final Spec spec;
  private final RecentChainData recentChainData;

  // implements is_timely from Consensus Spec
  public BlockTimelinessTracker(
      final Spec spec, final RecentChainData recentChainData, final TimeProvider timeProvider) {
    this.spec = spec;
    final int epochsForTimeliness =
        Math.max(spec.getGenesisSpecConfig().getReorgMaxEpochsSinceFinalization(), 3);
    this.blockTimeliness =
        LimitedMap.createSynchronizedNatural(
            spec.getGenesisSpec().getSlotsPerEpoch() * epochsForTimeliness);
    this.timeProvider = timeProvider;
    this.recentChainData = recentChainData;
  }

  public void setBlockTimelinessFromArrivalTime(
      final SignedBeaconBlock block, final UInt64 arrivalTimeMillis) {
    final UInt64 genesisTime = recentChainData.getGenesisTime();
    final UInt64 computedSlot = spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
    if (computedSlot.isGreaterThan(block.getMessage().getSlot())) {
      LOG.debug(
          "Block {}:{} is before computed slot {}, and won't be checked for timeliness",
          block.getRoot(),
          block.getSlot(),
          computedSlot);
      return;
    }
    recentChainData
        .getCurrentSlot()
        .ifPresent(
            slot -> {
              final UInt64 slotStartTimeMillis =
                  spec.getSlotStartTimeMillis(slot, genesisTime.times(1000));
              final int millisIntoSlot =
                  arrivalTimeMillis.minusMinZero(slotStartTimeMillis).intValue();

              final UInt64 timelinessLimit =
                  spec.getMillisPerSlot(slot).dividedBy(INTERVALS_PER_SLOT);

              final boolean isTimely =
                  block.getMessage().getSlot().equals(slot)
                      && timelinessLimit.isGreaterThan(millisIntoSlot);
              LOG.debug(
                  "Block {}:{} arrived at {} ms into slot {}, timeliness limit is {} ms. result: {}",
                  block.getRoot(),
                  block.getSlot(),
                  millisIntoSlot,
                  computedSlot,
                  timelinessLimit,
                  isTimely);
              blockTimeliness.put(block.getRoot(), isTimely);
            });
  }

  public Optional<Boolean> isBlockTimely(final Bytes32 root) {
    return Optional.ofNullable(blockTimeliness.get(root));
  }
}
