/*
 * Copyright Consensys Software Inc., 2026
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

import com.google.common.annotations.VisibleForTesting;
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
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;

public class LateBlockReorgLogic {
  private static final Logger LOG = LogManager.getLogger();
  protected final Map<Bytes32, Boolean> blockTimeliness;
  private final Supplier<TimeProvider> timeProviderSupplier;
  private final Spec spec;
  private final RecentChainData recentChainData;

  public LateBlockReorgLogic(final Spec spec, final RecentChainData recentChainData) {
    this(spec, recentChainData, recentChainData::getStore);
  }

  LateBlockReorgLogic(
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
                  spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis());
              final int millisIntoSlot =
                  arrivalTimeMillis.minusMinZero(slotStartTimeMillis).intValue();
              final int timelinessLimit = spec.getAttestationDueMillis(slot);

              final boolean isTimely =
                  block.getMessage().getSlot().equals(slot) && timelinessLimit > millisIntoSlot;
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

  // implements is_timely from Consensus Spec
  Optional<Boolean> isBlockTimely(final Bytes32 root) {
    return Optional.ofNullable(blockTimeliness.get(root));
  }

  // is_proposing_on_time from consensus-spec
  // 'on time' is before we're half-way to the attester time. logically, if the slot is 3 segments,
  // then splitting into 6 segments is half-way to the attestation time.
  public boolean isProposingOnTime(final UInt64 slot) {
    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis());
    final int timelinessLimit = spec.getProposerReorgCutoffMillis(slot);
    final UInt64 currentTimeMillis = timeProviderSupplier.get().getTimeInMillis();
    final boolean isTimely =
        currentTimeMillis.minusMinZero(slotStartTimeMillis).isLessThanOrEqualTo(timelinessLimit);
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

  // implements get_proposer_head from Consensus Spec
  public Bytes32 getProposerHead(final Bytes32 headRoot, final UInt64 slot) {
    LOG.debug("start getProposerHead");
    final boolean isProposerBoostActive = isProposerBoostActive(headRoot);
    final boolean isShufflingStableAndForkChoiceOk = isForkChoiceStableAndFinalizationOk(slot);
    final boolean isProposingOnTime = isProposingOnTime(slot);
    final boolean isHeadLate = isBlockLate(headRoot);
    final Optional<SignedBeaconBlock> maybeHead = getStore().getBlockIfAvailable(headRoot);
    // cheap checks of that list:
    // (isHeadLate, isShufflingStable, isFinalizationOk, isProposingOnTime);
    // and  isProposerBoostActive (assert condition);
    // finally need head block to make further checks
    if (!isHeadLate
        || !isShufflingStableAndForkChoiceOk
        || !isProposingOnTime
        || isProposerBoostActive
        || maybeHead.isEmpty()) {
      LOG.debug(
          "getProposerHead - return headRoot - isHeadLate {}, isForkChoiceStableAndFinalizationOk {}, isProposingOnTime {}, isProposerBoostActive {}, head.isEmpty {}",
          () -> isHeadLate,
          () -> isShufflingStableAndForkChoiceOk,
          () -> isProposingOnTime,
          () -> isProposerBoostActive,
          headRoot::isEmpty);
      return headRoot;
    }

    final SignedBeaconBlock head = maybeHead.get();
    final boolean isFfgCompetitive = isFfgCompetetive(headRoot, head.getParentRoot());
    final boolean isSingleSlotReorg = isSingleSlotReorg(head, slot);

    // from the initial list, check
    // isFfgCompetitive, isSingleSlotReorg
    if (!isFfgCompetitive || !isSingleSlotReorg) {
      LOG.debug(
          "getProposerHead - return headRoot - isFfgCompetitive {}, isSingleSlotReorg {}",
          isFfgCompetitive,
          isSingleSlotReorg);
      return headRoot;
    }
    final boolean isHeadWeak = getStore().isHeadWeak(headRoot);
    final boolean isParentStrong = getStore().isParentStrong(head.getParentRoot());
    // finally, the parent must be strong, and the current head must be weak.
    if (isHeadWeak && isParentStrong) {
      LOG.debug("getProposerHead - return parentRoot - isHeadWeak true && isParentStrong true");
      return head.getParentRoot();
    }

    LOG.debug("getProposerHead - return headRoot");
    return headRoot;
  }

  // implements should_override_forkchoice_update from Consensus-spec
  //     return all([head_late, shuffling_stable, ffg_competitive, finalization_ok,
  //                proposing_reorg_slot, single_slot_reorg,
  //                head_weak, parent_strong])
  public boolean shouldOverrideForkChoiceUpdate(final Bytes32 headRoot) {
    final Optional<SignedBeaconBlock> maybeHead = getStore().getBlockIfAvailable(headRoot);
    final Optional<UInt64> maybeCurrentSlot = recentChainData.getCurrentSlot();
    if (isMissingData(maybeHead, maybeCurrentSlot)) {
      LOG.debug(
          "shouldOverrideForkChoiceUpdate head - maybeHead {}, maybeCurrentSlot {}.",
          maybeHead,
          maybeCurrentSlot);
      return false;
    }
    if (!isBlockLate(headRoot)) {
      // ! isHeadLate, or we don't have data we need (currentSlot and the block in question)
      LOG.debug("shouldOverrideForkChoiceUpdate head - is not late.");
      return false;
    }
    final SignedBeaconBlock head = maybeHead.orElseThrow();
    final UInt64 currentSlot = maybeCurrentSlot.orElseThrow();
    final UInt64 proposalSlot = head.getSlot().increment();
    final boolean isShufflingStableAndForkChoiceOk =
        isForkChoiceStableAndFinalizationOk(proposalSlot);

    final boolean isFfgCompetitive = isFfgCompetetive(headRoot, head.getParentRoot());
    final Optional<UInt64> maybeParentSlot =
        recentChainData.getSlotForBlockRoot(head.getParentRoot());

    if (!isShufflingStableAndForkChoiceOk || !isFfgCompetitive || maybeParentSlot.isEmpty()) {
      LOG.debug(
          "shouldOverrideForkChoiceUpdate isShufflingStableAndForkChoiceOk {}, isFfgCompetitive {}, maybeParentSlot {}",
          isShufflingStableAndForkChoiceOk,
          isFfgCompetitive,
          maybeParentSlot);
      return false;
    }

    if (!shouldOverrideFcuCheckWeights(head, headRoot, proposalSlot, currentSlot)) {
      return false;
    }

    return shouldOverrideFcuCheckProposerPreState(proposalSlot, head.getParentRoot());
  }

  boolean shouldOverrideFcuCheckWeights(
      final SignedBeaconBlock head,
      final Bytes32 headRoot,
      final UInt64 proposalSlot,
      final UInt64 currentSlot) {

    final boolean isProposingOnTime = isProposingOnTime(proposalSlot);
    final boolean isCurrentTimeOk =
        head.getSlot().equals(currentSlot)
            || (currentSlot.equals(proposalSlot) && isProposingOnTime);
    final boolean isSingleSlotReorg = isSingleSlotReorg(head, proposalSlot);

    if (!isSingleSlotReorg || !isCurrentTimeOk) {
      LOG.debug(
          "shouldOverrideForkChoiceUpdate isSingleSlotReorg {}, isCurrentTimeOk {}",
          isSingleSlotReorg,
          isCurrentTimeOk);
      return false;
    }
    if (currentSlot.isGreaterThan(head.getSlot())) {
      final boolean isHeadWeak = getStore().isHeadWeak(headRoot);
      final boolean isParentStrong = getStore().isParentStrong(head.getParentRoot());
      if (!isHeadWeak || !isParentStrong) {
        LOG.debug(
            "shouldOverrideForkChoiceUpdate isHeadWeak {}, isParentStrong {}",
            isHeadWeak,
            isParentStrong);
        return false;
      }
    }
    return true;
  }

  boolean shouldOverrideFcuCheckProposerPreState(
      final UInt64 proposalSlot, final Bytes32 parentRoot) {
    // Only suppress the fork choice update if we are confident that we will propose the next
    LOG.debug("Need parent state");
    final Optional<BeaconState> maybeParentState = getStore().getBlockStateIfAvailable(parentRoot);
    if (maybeParentState.isEmpty()) {
      LOG.debug("shouldOverrideForkChoice could not retrieve parent state from cache");
      return false;
    }
    try {
      final BeaconState proposerPreState = spec.processSlots(maybeParentState.get(), proposalSlot);
      final int proposerIndex = getProposerIndex(proposerPreState, proposalSlot);
      if (!recentChainData.isValidatorConnected(proposerIndex, proposalSlot)) {
        LOG.debug(
            "shouldOverrideForkChoiceUpdate isValidatorConnected({}) {}, ", proposerIndex, false);
        return false;
      }
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.trace("Failed to process", e);
      return false;
    }

    return true;
  }

  boolean isSingleSlotReorg(final SignedBeaconBlock head, final UInt64 slot) {
    final Optional<UInt64> maybeParentSlot =
        recentChainData.getSlotForBlockRoot(head.getParentRoot());
    return maybeParentSlot.map(uInt64 -> uInt64.increment().equals(head.getSlot())).orElse(false)
        && head.getSlot().increment().equals(slot);
  }

  boolean isProposerBoostActive(final Bytes32 headRoot) {
    return getStore().getProposerBoostRoot().map(root -> !root.equals(headRoot)).orElse(false);
  }

  boolean isFfgCompetetive(final Bytes32 headRoot, final Bytes32 parentRoot) {
    return getStore().isFfgCompetitive(headRoot, parentRoot).orElse(false);
  }

  boolean isForkChoiceStableAndFinalizationOk(final UInt64 slot) {
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(slot).getForkChoiceUtil();
    return forkChoiceUtil.isShufflingStable(slot)
        && forkChoiceUtil.isFinalizationOk(getStore(), slot);
  }

  boolean isMissingData(
      final Optional<SignedBeaconBlock> maybeHead, final Optional<UInt64> maybeCurrentSlot) {
    if (maybeHead.isEmpty() || maybeCurrentSlot.isEmpty()) {
      LOG.debug(
          "shouldOverrideForkChoiceUpdate head {}, currentSlot {}",
          () -> maybeHead.map(SignedBeaconBlock::getRoot),
          () -> maybeCurrentSlot);
      return true;
    }
    return false;
  }

  @VisibleForTesting
  protected int getProposerIndex(final BeaconState proposerPreState, final UInt64 proposalSlot) {
    return spec.getBeaconProposerIndex(proposerPreState, proposalSlot);
  }

  private ReadOnlyStore getStore() {
    return recentChainData.getStore();
  }
}
