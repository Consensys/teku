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

package tech.pegasys.teku.spec.logic.common.util;

import static tech.pegasys.teku.infrastructure.time.TimeProvider.MILLIS_PER_SECOND;

import java.time.Instant;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class ForkChoiceUtil {

  private final SpecConfig specConfig;
  private final BeaconStateAccessors beaconStateAccessors;
  private final EpochProcessor epochProcessor;
  private final AttestationUtil attestationUtil;
  private final MiscHelpers miscHelpers;

  public ForkChoiceUtil(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final EpochProcessor epochProcessor,
      final AttestationUtil attestationUtil,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
    this.epochProcessor = epochProcessor;
    this.attestationUtil = attestationUtil;
    this.miscHelpers = miscHelpers;
  }

  public UInt64 getSlotsSinceGenesis(ReadOnlyStore store, boolean useUnixTime) {
    UInt64 time =
        useUnixTime ? UInt64.valueOf(Instant.now().getEpochSecond()) : store.getTimeSeconds();
    return getCurrentSlot(time, store.getGenesisTime());
  }

  public UInt64 getCurrentSlot(UInt64 currentTime, UInt64 genesisTime) {
    if (currentTime.isLessThan(genesisTime)) {
      return UInt64.ZERO;
    }
    return currentTime.minus(genesisTime).dividedBy(specConfig.getSecondsPerSlot());
  }

  public UInt64 getCurrentSlotForMillis(UInt64 currentTimeMillis, UInt64 genesisTimeMillis) {
    if (currentTimeMillis.isLessThan(genesisTimeMillis)) {
      return UInt64.ZERO;
    }
    return currentTimeMillis
        .minus(genesisTimeMillis)
        .dividedBy(specConfig.getSecondsPerSlot() * MILLIS_PER_SECOND.longValue());
  }

  public UInt64 getSlotStartTime(UInt64 slotNumber, UInt64 genesisTime) {
    return genesisTime.plus(slotNumber.times(specConfig.getSecondsPerSlot()));
  }

  public UInt64 getSlotStartTimeMillis(UInt64 slotNumber, UInt64 genesisTimeMillis) {
    return genesisTimeMillis.plus(
        slotNumber.times(specConfig.getSecondsPerSlot() * MILLIS_PER_SECOND.longValue()));
  }

  public UInt64 getCurrentSlot(ReadOnlyStore store, boolean useUnixTime) {
    return SpecConfig.GENESIS_SLOT.plus(getSlotsSinceGenesis(store, useUnixTime));
  }

  public UInt64 getCurrentSlot(ReadOnlyStore store) {
    return getCurrentSlot(store, false);
  }

  public UInt64 computeSlotsSinceEpochStart(UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final UInt64 epochStartSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    return slot.minus(epochStartSlot);
  }

  /**
   * Get the ancestor of ``block`` with slot number ``slot``.
   *
   * @param forkChoiceStrategy
   * @param root
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.10.1/specs/phase0/fork-choice.md#get_ancestor</a>
   */
  public Optional<Bytes32> getAncestor(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 slot) {
    return forkChoiceStrategy.getAncestor(root, slot);
  }

  public NavigableMap<UInt64, Bytes32> getAncestors(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      Bytes32 root,
      UInt64 startSlot,
      UInt64 step,
      UInt64 count) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    // minus(ONE) because the start block is included
    final UInt64 endSlot = startSlot.plus(step.times(count)).minus(UInt64.ONE);
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().compareTo(startSlot) > 0) {
      maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
    return roots;
  }

  /**
   * @param forkChoiceStrategy the object that stores information on forks and block roots
   * @param root the root that dictates the block/fork that we walk backwards from
   * @param startSlot the slot (exclusive) until which we walk the chain backwards
   * @return every block root from root (inclusive) to start slot (exclusive) traversing the chain
   *     backwards
   */
  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 startSlot) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().isGreaterThan(startSlot)) {
      maybeAddRoot(startSlot, UInt64.ONE, roots, UInt64.MAX_VALUE, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    return roots;
  }

  private void maybeAddRoot(
      final UInt64 startSlot,
      final UInt64 step,
      final NavigableMap<UInt64, Bytes32> roots,
      final UInt64 endSlot,
      final Bytes32 root,
      final Optional<UInt64> maybeSlot) {
    maybeSlot.ifPresent(
        slot -> {
          if (slot.compareTo(endSlot) <= 0
              && slot.compareTo(startSlot) >= 0
              && slot.minus(startSlot).mod(step).equals(UInt64.ZERO)) {
            roots.put(slot, root);
          }
        });
  }

  // Fork Choice Event Handlers

  /**
   * The implementation differs from the spec in that the time parameter is in milliseconds instead
   * of seconds. This allows the time to be stored in milliseconds which allows for more
   * fine-grained time calculations if required.
   *
   * @param store the store to update
   * @param timeMillis onTick time in milliseconds
   */
  public void onTick(final MutableStore store, final UInt64 timeMillis) {
    // To be extra safe check both time and genesisTime, although time should always be >=
    // genesisTime
    if (store.getTimeMillis().isGreaterThan(timeMillis)
        || store.getGenesisTimeMillis().isGreaterThan(timeMillis)) {
      return;
    }
    final UInt64 previousSlot = getCurrentSlot(store);
    final UInt64 newSlot = getCurrentSlotForMillis(timeMillis, store.getGenesisTimeMillis());
    final UInt64 previousEpoch = miscHelpers.computeEpochAtSlot(previousSlot);
    final UInt64 newEpoch = miscHelpers.computeEpochAtSlot(newSlot);

    // Catch up on any missed epoch transitions
    for (UInt64 currentEpoch = previousEpoch.plus(1);
        currentEpoch.isLessThanOrEqualTo(newEpoch);
        currentEpoch = currentEpoch.plus(1)) {
      final UInt64 firstSlotOfEpoch = miscHelpers.computeStartSlotAtEpoch(currentEpoch);
      final UInt64 slotStartTime =
          getSlotStartTimeMillis(firstSlotOfEpoch, store.getGenesisTimeMillis());
      processOnTick(store, slotStartTime);
    }
    // Catch up final part
    processOnTick(store, timeMillis);
  }

  private void processOnTick(final MutableStore store, final UInt64 timeMillis) {
    final UInt64 previousSlot = getCurrentSlot(store);

    // Update store time
    store.setTimeMillis(timeMillis);

    final UInt64 currentSlot = getCurrentSlot(store);

    if (currentSlot.isGreaterThan(previousSlot)) {
      store.removeProposerBoostRoot();
    }

    // Not a new epoch, return
    if (!(currentSlot.isGreaterThan(previousSlot)
        && computeSlotsSinceEpochStart(currentSlot).isZero())) {
      return;
    }

    // Update store.justified_checkpoint if a better checkpoint is known
    if (!specConfig.getProgressiveBalancesMode().isFull()
        && store
            .getBestJustifiedCheckpoint()
            .getEpoch()
            .isGreaterThan(store.getJustifiedCheckpoint().getEpoch())) {
      store.setJustifiedCheckpoint(store.getBestJustifiedCheckpoint());
    }

    final UInt64 previousEpoch = miscHelpers.computeEpochAtSlot(previousSlot);
    for (ProtoNodeData nodeData : store.getForkChoiceStrategy().getChainHeads(true)) {
      if (miscHelpers.computeEpochAtSlot(nodeData.getSlot()).equals(previousEpoch)) {
        store.pullUpBlockCheckpoints(nodeData.getRoot());
        updateCheckpoints(
            store,
            nodeData.getCheckpoints().getUnrealizedJustifiedCheckpoint(),
            nodeData.getCheckpoints().getUnrealizedFinalizedCheckpoint(),
            nodeData.isOptimistic());
      }
    }
  }

  private void updateCheckpoints(
      final MutableStore store,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final boolean isBlockOptimistic) {
    if (specConfig.getProgressiveBalancesMode().isFull()) {
      if (justifiedCheckpoint.getEpoch().isGreaterThan(store.getJustifiedCheckpoint().getEpoch())) {
        store.setJustifiedCheckpoint(justifiedCheckpoint);
      }
      if (finalizedCheckpoint.getEpoch().isGreaterThan(store.getFinalizedCheckpoint().getEpoch())) {
        store.setFinalizedCheckpoint(finalizedCheckpoint, isBlockOptimistic);
      }
    } else {
      if (justifiedCheckpoint.getEpoch().isGreaterThan(store.getJustifiedCheckpoint().getEpoch())) {
        if (justifiedCheckpoint
            .getEpoch()
            .isGreaterThan(store.getBestJustifiedCheckpoint().getEpoch())) {
          store.setBestJustifiedCheckpoint(justifiedCheckpoint);
        }
        if (shouldUpdateJustifiedCheckpoint(
            store, justifiedCheckpoint, store.getForkChoiceStrategy())) {
          store.setJustifiedCheckpoint(justifiedCheckpoint);
        }
      }

      if (finalizedCheckpoint.getEpoch().isGreaterThan(store.getFinalizedCheckpoint().getEpoch())) {
        store.setFinalizedCheckpoint(finalizedCheckpoint, isBlockOptimistic);
        store.setJustifiedCheckpoint(justifiedCheckpoint);
      }
    }
  }

  @CheckReturnValue
  public AttestationProcessingResult validate(
      final Fork fork,
      final ReadOnlyStore store,
      final ValidateableAttestation validateableAttestation,
      final Optional<BeaconState> maybeState) {
    Attestation attestation = validateableAttestation.getAttestation();
    return validateOnAttestation(store, attestation.getData())
        .ifSuccessful(
            () -> {
              if (maybeState.isEmpty()) {
                return AttestationProcessingResult.UNKNOWN_BLOCK;
              } else {
                return attestationUtil.isValidIndexedAttestation(
                    fork, maybeState.get(), validateableAttestation);
              }
            })
        .ifSuccessful(() -> checkIfAttestationShouldBeSavedForFuture(store, attestation));
  }

  private AttestationProcessingResult validateOnAttestation(
      final ReadOnlyStore store, final AttestationData attestationData) {

    UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(getCurrentSlot(store));
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();

    return validateOnAttestation(forkChoiceStrategy, currentEpoch, attestationData);
  }

  public AttestationProcessingResult validateOnAttestation(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final AttestationData attestationData) {
    final Checkpoint target = attestationData.getTarget();

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    final UInt64 previousEpoch =
        currentEpoch.isGreaterThan(SpecConfig.GENESIS_EPOCH)
            ? currentEpoch.minus(UInt64.ONE)
            : SpecConfig.GENESIS_EPOCH;

    if (!target.getEpoch().equals(previousEpoch) && !target.getEpoch().equals(currentEpoch)) {
      return AttestationProcessingResult.invalid(
          "Attestations must be from the current or previous epoch");
    }

    if (!target.getEpoch().equals(miscHelpers.computeEpochAtSlot(attestationData.getSlot()))) {
      return AttestationProcessingResult.invalid("Attestation slot must be within specified epoch");
    }

    if (!forkChoiceStrategy.contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    final Optional<UInt64> blockSlot =
        forkChoiceStrategy.blockSlot(attestationData.getBeaconBlockRoot());
    if (blockSlot.isEmpty()) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (blockSlot.get().compareTo(attestationData.getSlot()) > 0) {
      return AttestationProcessingResult.invalid(
          "Attestations must not be for blocks in the future. If not, the attestation should not be considered");
    }

    // LMD vote must be consistent with FFG vote target
    final UInt64 targetSlot = miscHelpers.computeStartSlotAtEpoch(target.getEpoch());
    if (getAncestor(forkChoiceStrategy, attestationData.getBeaconBlockRoot(), targetSlot)
        .map(ancestorRoot -> !ancestorRoot.equals(target.getRoot()))
        .orElse(true)) {
      return AttestationProcessingResult.invalid(
          "LMD vote must be consistent with FFG vote target");
    }

    return AttestationProcessingResult.SUCCESSFUL;
  }

  private AttestationProcessingResult checkIfAttestationShouldBeSavedForFuture(
      ReadOnlyStore store, Attestation attestation) {

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    final UInt64 currentSlot = getCurrentSlot(store);
    if (currentSlot.isLessThan(attestation.getData().getSlot())) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    if (currentSlot.equals(attestation.getData().getSlot())) {
      return AttestationProcessingResult.DEFER_FOR_FORK_CHOICE;
    }

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    final UInt64 epochStartSlot =
        miscHelpers.computeStartSlotAtEpoch(attestation.getData().getTarget().getEpoch());
    if (currentSlot.isLessThan(epochStartSlot)) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  public void applyBlockToStore(
      final MutableStore store,
      final SignedBeaconBlock signedBlock,
      final BeaconState postState,
      final boolean isBlockOptimistic,
      final Optional<List<BlobSidecar>> maybeBlobSidecars,
      final UInt64 earliestAffectedSlot) {

    BlockCheckpoints blockCheckpoints = epochProcessor.calculateBlockCheckpoints(postState);

    // If block is from a prior epoch, pull up the post-state to next epoch to realize new finality
    // info
    if (miscHelpers
        .computeEpochAtSlot(signedBlock.getSlot())
        .isLessThan(miscHelpers.computeEpochAtSlot(getCurrentSlot(store)))) {
      blockCheckpoints = blockCheckpoints.realizeNextEpoch();
    }

    updateCheckpoints(
        store,
        blockCheckpoints.getJustifiedCheckpoint(),
        blockCheckpoints.getFinalizedCheckpoint(),
        isBlockOptimistic);

    // Add new block to store
    store.putBlockAndState(
        signedBlock,
        postState,
        blockCheckpoints,
        maybeBlobSidecars,
        miscHelpers
            .toVersionDeneb()
            .map(MiscHelpersDeneb::computeDenebStartSlot)
            .map(denebSlot -> denebSlot.max(earliestAffectedSlot)));
  }

  private UInt64 getFinalizedCheckpointStartSlot(final ReadOnlyStore store) {
    final UInt64 finalizedEpoch = store.getFinalizedCheckpoint().getEpoch();
    return miscHelpers.computeStartSlotAtEpoch(finalizedEpoch);
  }

  public BlockImportResult checkOnBlockConditions(
      final SignedBeaconBlock block, final BeaconState blockSlotState, final ReadOnlyStore store) {
    final UInt64 blockSlot = block.getSlot();
    if (blockSlotState == null) {
      return BlockImportResult.FAILED_UNKNOWN_PARENT;
    }
    if (!blockSlotState.getSlot().equals(blockSlot)) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    if (blockSlot.isGreaterThan(SpecConfig.GENESIS_SLOT)
        && !beaconStateAccessors
            .getBlockRootAtSlot(blockSlotState, blockSlot.minus(1))
            .equals(block.getParentRoot())) {
      // Block is at same slot as its parent or the parent root doesn't match the state
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    if (blockIsFromFuture(store, blockSlot)) {
      return BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE;
    }
    if (!blockDescendsFromLatestFinalizedBlock(block, store, store.getForkChoiceStrategy())) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    // Successful so far
    return BlockImportResult.successful(block);
  }

  private boolean blockIsFromFuture(ReadOnlyStore store, final UInt64 blockSlot) {
    return getCurrentSlot(store).compareTo(blockSlot) < 0;
  }

  public boolean blockDescendsFromLatestFinalizedBlock(
      final BeaconBlockSummary block,
      final ReadOnlyStore store,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    final UInt64 blockSlot = block.getSlot();

    // Make sure this block's slot is after the latest finalized slot
    final UInt64 finalizedEpochStartSlot = getFinalizedCheckpointStartSlot(store);
    return blockIsAfterLatestFinalizedSlot(blockSlot, finalizedEpochStartSlot)
        && hasAncestorAtSlot(
            forkChoiceStrategy,
            block.getParentRoot(),
            finalizedEpochStartSlot,
            finalizedCheckpoint.getRoot());
  }

  private boolean blockIsAfterLatestFinalizedSlot(
      final UInt64 blockSlot, final UInt64 finalizedEpochStartSlot) {
    return blockSlot.compareTo(finalizedEpochStartSlot) > 0;
  }

  /*
  To address the bouncing attack, only update conflicting justified
  checkpoints in the fork choice if in the early slots of the epoch.
  Otherwise, delay incorporation of new justified checkpoint until next epoch boundary.
  See https://ethresear.ch/t/prevention-of-bouncing-attack-on-ffg/6114 for more detailed analysis and discussion.
  */

  private boolean shouldUpdateJustifiedCheckpoint(
      ReadOnlyStore store,
      Checkpoint newJustifiedCheckpoint,
      ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    if (computeSlotsSinceEpochStart(getCurrentSlot(store))
        .isLessThan(specConfig.getSafeSlotsToUpdateJustified())) {
      return true;
    }

    UInt64 justifiedSlot =
        miscHelpers.computeStartSlotAtEpoch(store.getJustifiedCheckpoint().getEpoch());
    return hasAncestorAtSlot(
        forkChoiceStrategy,
        newJustifiedCheckpoint.getRoot(),
        justifiedSlot,
        store.getJustifiedCheckpoint().getRoot());
  }

  private boolean hasAncestorAtSlot(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      Bytes32 root,
      UInt64 slot,
      Bytes32 ancestorRoot) {
    return getAncestor(forkChoiceStrategy, root, slot)
        .map(ancestorAtSlot -> ancestorAtSlot.equals(ancestorRoot))
        .orElse(false);
  }

  public boolean canOptimisticallyImport(final ReadOnlyStore store, final SignedBeaconBlock block) {
    // A block can be optimistically imported either if it's parent contains a non-default payload
    // or if it is at least `SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY` before the current slot.
    // This is to avoid the merge block referencing a non-existent eth1 parent block and causing
    // all nodes to switch to optimistic sync mode.
    if (isExecutionBlock(store, block.getParentRoot())) {
      return true;
    }
    return isBellatrixBlockOld(store, block.getSlot());
  }

  private boolean isBellatrixBlockOld(final ReadOnlyStore store, final UInt64 blockSlot) {
    final Optional<SpecConfigBellatrix> maybeConfig = specConfig.toVersionBellatrix();
    if (maybeConfig.isEmpty()) {
      return false;
    }
    return blockSlot
        .plus(maybeConfig.get().getSafeSlotsToImportOptimistically())
        .isLessThanOrEqualTo(getCurrentSlot(store));
  }

  private boolean isExecutionBlock(final ReadOnlyStore store, final Bytes32 blockRoot) {
    final Optional<Bytes32> parentExecutionRoot =
        store.getForkChoiceStrategy().executionBlockHash(blockRoot);
    return parentExecutionRoot.isPresent() && !parentExecutionRoot.get().isZero();
  }
}
