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
import java.util.function.Supplier;
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
  private final Supplier<TimeProvider> timeProviderSupplier;
  private final Spec spec;
  private final RecentChainData recentChainData;

  public BlockTimelinessTracker(final Spec spec, final RecentChainData recentChainData) {
    this(spec, recentChainData, recentChainData::getStore);
  }

  // implements is_timely from Consensus Spec
  BlockTimelinessTracker(
      final Spec spec,
      final RecentChainData recentChainData,
      final Supplier<TimeProvider> timeProviderSupplier) {
    this.spec = spec;
    final int epochsForTimeliness =
        Math.max(spec.getGenesisSpecConfig().getReorgMaxEpochsSinceFinalization(), 3);
    this.blockTimeliness =
        LimitedMap.createSynchronizedNatural(
            spec.getGenesisSpec().getSlotsPerEpoch() * epochsForTimeliness);
    this.timeProviderSupplier = timeProviderSupplier;
    this.recentChainData = recentChainData;
  }

  public void setBlockTimelinessFromArrivalTime(
      final SignedBeaconBlock block, final UInt64 arrivalTimeMillis) {
    if (blockTimeliness.get(block.getRoot()) != null) {
      return;
    }
    final UInt64 computedSlot =
        spec.getCurrentSlot(
            timeProviderSupplier.get().getTimeInSeconds(), recentChainData.getGenesisTime());
    final Bytes32 root = block.getRoot();
    if (computedSlot.isGreaterThan(block.getMessage().getSlot())) {
      LOG.debug(
          "Block {}:{} is before computed slot {}, timeliness set to false.",
          root,
          block.getSlot(),
          computedSlot);
      blockTimeliness.put(root, false);
      return;
    }
    recentChainData
        .getCurrentSlot()
        .ifPresent(
            slot -> {
              final UInt64 slotStartTimeMillis =
                  spec.getSlotStartTimeMillis(slot, recentChainData.getGenesisTimeMillis());
              final int millisIntoSlot =
                  arrivalTimeMillis.minusMinZero(slotStartTimeMillis).intValue();

              final UInt64 timelinessLimit =
                  spec.getMillisPerSlot(slot).dividedBy(INTERVALS_PER_SLOT);

              final boolean isTimely =
                  block.getMessage().getSlot().equals(slot)
                      && timelinessLimit.isGreaterThan(millisIntoSlot);
              LOG.debug(
                  "Block {}:{} arrived at {} ms into slot {}, timeliness limit is {} ms. result: {}",
                  root,
                  block.getSlot(),
                  millisIntoSlot,
                  computedSlot,
                  timelinessLimit,
                  isTimely);
              blockTimeliness.put(root, isTimely);
            });
  }

  Optional<Boolean> isBlockTimely(final Bytes32 root) {
    return Optional.ofNullable(blockTimeliness.get(root));
  }

  // is_proposing_on_time from consensus-spec
  // 'on time' is before we're half-way to the attester time. logically, if the slot is 3 segments,
  // then splitting into 6 segments is half-way to the attestation time.
  public boolean isProposingOnTime(final UInt64 slot) {
    final UInt64 slotStartTimeMillis =
        spec.getSlotStartTimeMillis(slot, recentChainData.getGenesisTimeMillis());
    final UInt64 timelinessLimit = spec.getMillisPerSlot(slot).dividedBy(INTERVALS_PER_SLOT * 2);
    final UInt64 currentTimeMillis = timeProviderSupplier.get().getTimeInMillis();
    final boolean isTimely =
        currentTimeMillis.minusMinZero(slotStartTimeMillis).isLessThan(timelinessLimit);
    LOG.debug(
        "Check ProposingOnTime for slot {}, slot start time is {} ms and current time is {} ms, limit is {} ms result: {}",
        slot,
        slotStartTimeMillis,
        currentTimeMillis,
        timelinessLimit,
        isTimely);
    return isTimely;
  }

  // Implements is_head_late form consensus-spec
  // caveat: if the root was not found, will default to it being timely,
  // on the basis that it's not safe to make choices about blocks we don't know about
  public boolean isBlockLate(final Bytes32 root) {
    return !isBlockTimely(root).orElse(true);
  }
}
