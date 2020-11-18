/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.core;

import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.teku.util.config.Constants.SAFE_SLOTS_TO_UPDATE_JUSTIFIED;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import java.time.Instant;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.lookup.IndexedAttestationProvider;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.data.BlockProcessingRecord;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;

public class ForkChoiceUtil {

  private static final Logger LOG = LogManager.getLogger();

  public static UInt64 get_slots_since_genesis(ReadOnlyStore store, boolean useUnixTime) {
    UInt64 time = useUnixTime ? UInt64.valueOf(Instant.now().getEpochSecond()) : store.getTime();
    return getCurrentSlot(time, store.getGenesisTime());
  }

  public static UInt64 getCurrentSlot(UInt64 currentTime, UInt64 genesisTime) {
    if (currentTime.isLessThan(genesisTime)) {
      return UInt64.ZERO;
    }
    return currentTime.minus(genesisTime).dividedBy(SECONDS_PER_SLOT);
  }

  public static UInt64 getSlotStartTime(UInt64 slotNumber, UInt64 genesisTime) {
    return genesisTime.plus(slotNumber.times(SECONDS_PER_SLOT));
  }

  public static UInt64 get_current_slot(ReadOnlyStore store, boolean useUnixTime) {
    return UInt64.valueOf(GENESIS_SLOT).plus(get_slots_since_genesis(store, useUnixTime));
  }

  public static UInt64 get_current_slot(ReadOnlyStore store) {
    return get_current_slot(store, false);
  }

  public static UInt64 compute_slots_since_epoch_start(UInt64 slot) {
    return slot.minus(compute_start_slot_at_epoch(compute_epoch_at_slot(slot)));
  }

  /**
   * Get the ancestor of ``block`` with slot number ``slot``.
   *
   * @param forkChoiceStrategy
   * @param root
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.10.1/specs/phase0/fork-choice.md#get_ancestor</a>
   */
  public static Optional<Bytes32> get_ancestor(
      ForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 slot) {
    return forkChoiceStrategy.getAncestor(root, slot);
  }

  public static NavigableMap<UInt64, Bytes32> getAncestors(
      ForkChoiceStrategy forkChoiceStrategy,
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
  public static NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      ForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 startSlot) {
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

  private static void maybeAddRoot(
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

  /*
  To address the bouncing attack, only update conflicting justified
  checkpoints in the fork choice if in the early slots of the epoch.
  Otherwise, delay incorporation of new justified checkpoint until next epoch boundary.
  See https://ethresear.ch/t/prevention-of-bouncing-attack-on-ffg/6114 for more detailed analysis and discussion.
  */

  private static boolean should_update_justified_checkpoint(
      ReadOnlyStore store,
      Checkpoint new_justified_checkpoint,
      ForkChoiceStrategy forkChoiceStrategy) {
    if (compute_slots_since_epoch_start(get_current_slot(store, true))
            .compareTo(UInt64.valueOf(SAFE_SLOTS_TO_UPDATE_JUSTIFIED))
        < 0) {
      return true;
    }

    UInt64 justifiedSlot = compute_start_slot_at_epoch(store.getJustifiedCheckpoint().getEpoch());
    return hasAncestorAtSlot(
        forkChoiceStrategy,
        new_justified_checkpoint.getRoot(),
        justifiedSlot,
        store.getJustifiedCheckpoint().getRoot());
  }

  // Fork Choice Event Handlers

  /**
   * @param store
   * @param time
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_tick</a>
   */
  public static void on_tick(MutableStore store, UInt64 time) {
    // To be extra safe check both time and genesisTime, although time should always be >=
    // genesisTime
    if (store.getTime().isGreaterThan(time) || store.getGenesisTime().isGreaterThan(time)) {
      return;
    }
    UInt64 previous_slot = get_current_slot(store);

    // Update store time
    store.setTime(time);

    UInt64 current_slot = get_current_slot(store);

    // Not a new epoch, return
    if (!(current_slot.compareTo(previous_slot) > 0
        && compute_slots_since_epoch_start(current_slot).equals(UInt64.ZERO))) {
      return;
    }

    // Update store.justified_checkpoint if a better checkpoint is known
    if (store
            .getBestJustifiedCheckpoint()
            .getEpoch()
            .compareTo(store.getJustifiedCheckpoint().getEpoch())
        > 0) {
      store.setJustifiedCheckpoint(store.getBestJustifiedCheckpoint());
    }
  }

  /**
   * @param store
   * @param signed_block
   * @param maybePreState
   * @param st
   * @param forkChoiceStrategy
   * @param beaconStateConsumer
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_block</a>
   */
  @CheckReturnValue
  public static BlockImportResult on_block(
      final MutableStore store,
      final SignedBeaconBlock signed_block,
      Optional<BeaconState> maybePreState,
      final StateTransition st,
      final ForkChoiceStrategy forkChoiceStrategy,
      final Consumer<BeaconState> beaconStateConsumer,
      final IndexedAttestationProvider indexedAttestationProvider) {
    final BeaconBlock block = signed_block.getMessage();

    // Return early if precondition checks fail;
    final Optional<BlockImportResult> maybeFailure =
        checkOnBlockConditions(block, maybePreState.orElse(null), store, forkChoiceStrategy);
    if (maybeFailure.isPresent()) {
      return maybeFailure.get();
    }
    final BeaconState preState = maybePreState.orElseThrow();

    // Make a copy of the state to avoid mutability issues
    BeaconState state;

    // Check the block is valid and compute the post-state
    try {
      state =
          st.initiate(
              preState, signed_block, true, beaconStateConsumer, indexedAttestationProvider);
    } catch (StateTransitionException e) {
      return BlockImportResult.failedStateTransition(e);
    }

    // Add new block to store
    store.putBlockAndState(signed_block, state);

    // Update justified checkpoint
    final Checkpoint justifiedCheckpoint = state.getCurrent_justified_checkpoint();
    if (justifiedCheckpoint.getEpoch().compareTo(store.getJustifiedCheckpoint().getEpoch()) > 0) {
      if (justifiedCheckpoint.getEpoch().compareTo(store.getBestJustifiedCheckpoint().getEpoch())
          > 0) {
        store.setBestJustifiedCheckpoint(justifiedCheckpoint);
      }
      if (should_update_justified_checkpoint(store, justifiedCheckpoint, forkChoiceStrategy)) {
        store.setJustifiedCheckpoint(justifiedCheckpoint);
      }
    }

    // Update finalized checkpoint
    final Checkpoint finalizedCheckpoint = state.getFinalized_checkpoint();
    if (finalizedCheckpoint.getEpoch().compareTo(store.getFinalizedCheckpoint().getEpoch()) > 0) {
      store.setFinalizedCheckpoint(finalizedCheckpoint);

      // Potentially update justified if different from store
      if (!store.getJustifiedCheckpoint().equals(state.getCurrent_justified_checkpoint())) {
        // Update justified if new justified is later than store justified
        // or if store justified is not in chain with finalized checkpoint
        if (state
                    .getCurrent_justified_checkpoint()
                    .getEpoch()
                    .compareTo(store.getJustifiedCheckpoint().getEpoch())
                > 0
            || !isFinalizedAncestorOfJustified(forkChoiceStrategy, store)) {
          store.setJustifiedCheckpoint(state.getCurrent_justified_checkpoint());
        }
      }
    }

    final BlockProcessingRecord record = new BlockProcessingRecord(preState, signed_block, state);
    return BlockImportResult.successful(record);
  }

  private static boolean isFinalizedAncestorOfJustified(
      ForkChoiceStrategy forkChoiceStrategy, ReadOnlyStore store) {
    UInt64 finalizedSlot = store.getFinalizedCheckpoint().getEpochStartSlot();
    return hasAncestorAtSlot(
        forkChoiceStrategy,
        store.getJustifiedCheckpoint().getRoot(),
        finalizedSlot,
        store.getFinalizedCheckpoint().getRoot());
  }

  private static Optional<BlockImportResult> checkOnBlockConditions(
      final BeaconBlock block,
      final BeaconState preState,
      final ReadOnlyStore store,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final UInt64 blockSlot = block.getSlot();
    if (preState == null) {
      return Optional.of(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    if (preState.getSlot().compareTo(blockSlot) >= 0) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    if (blockIsFromFuture(store, blockSlot)) {
      return Optional.of(BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE);
    }
    if (!blockDescendsFromLatestFinalizedBlock(block, store, forkChoiceStrategy)) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    return Optional.empty();
  }

  private static boolean blockIsFromFuture(ReadOnlyStore store, final UInt64 blockSlot) {
    return get_current_slot(store).compareTo(blockSlot) < 0;
  }

  private static boolean hasAncestorAtSlot(
      ForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 slot, Bytes32 ancestorRoot) {
    return get_ancestor(forkChoiceStrategy, root, slot)
        .map(ancestorAtSlot -> ancestorAtSlot.equals(ancestorRoot))
        .orElse(false);
  }

  public static boolean blockDescendsFromLatestFinalizedBlock(
      final BeaconBlock block,
      final ReadOnlyStore store,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    final UInt64 blockSlot = block.getSlot();

    // Make sure this block's slot is after the latest finalized slot
    return blockIsAfterLatestFinalizedSlot(blockSlot, finalizedCheckpoint.getEpochStartSlot())
        && hasAncestorAtSlot(
            forkChoiceStrategy,
            block.getParentRoot(),
            finalizedCheckpoint.getEpochStartSlot(),
            finalizedCheckpoint.getRoot());
  }

  private static boolean blockIsAfterLatestFinalizedSlot(
      final UInt64 blockSlot, final UInt64 finalizedEpochStartSlot) {
    if (blockSlot.compareTo(finalizedEpochStartSlot) <= 0) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * Run ``on_attestation`` upon receiving a new ``attestation`` from either within a block or
   * directly on the wire.
   *
   * <p>An ``attestation`` that is asserted as invalid may be valid at a later time, consider
   * scheduling it for later processing in such case.
   *
   * @param store
   * @param validateableAttestation
   * @param maybeTargetState The state corresponding to the attestation target
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_attestation</a>
   */
  @CheckReturnValue
  public static AttestationProcessingResult on_attestation(
      final MutableStore store,
      final ValidateableAttestation validateableAttestation,
      final Optional<BeaconState> maybeTargetState,
      final ForkChoiceStrategy forkChoiceStrategy) {

    Attestation attestation = validateableAttestation.getAttestation();

    return validateOnAttestation(store, attestation, forkChoiceStrategy)
        .ifSuccessful(
            () -> {
              if (validateableAttestation.isValidIndexedAttestation()) {
                return SUCCESSFUL;
              }
              return indexAndValidateAttestation(validateableAttestation, maybeTargetState);
            })
        .ifSuccessful(() -> checkIfAttestationShouldBeSavedForFuture(store, attestation))
        .ifSuccessful(
            () -> {
              IndexedAttestation indexedAttestation =
                  validateableAttestation
                      .getIndexedAttestation()
                      .orElseThrow(
                          () ->
                              new UnsupportedOperationException(
                                  "ValidateableAttestation does not have an IndexedAttestation yet."));
              forkChoiceStrategy.onAttestation(store, indexedAttestation);
              return SUCCESSFUL;
            });
  }

  /**
   * Returns the indexed attestation if attestation is valid, else, returns an empty optional.
   *
   * @param attestation
   * @param maybeTargetState The state corresponding to the attestation target, if it is available
   * @return
   */
  private static AttestationProcessingResult indexAndValidateAttestation(
      ValidateableAttestation attestation, Optional<BeaconState> maybeTargetState) {
    BeaconState targetState;
    try {
      if (maybeTargetState.isEmpty()) {
        return AttestationProcessingResult.UNKNOWN_BLOCK;
      }
      targetState = maybeTargetState.get();
    } catch (final InvalidCheckpointException e) {
      LOG.debug("on_attestation: Attestation target checkpoint is invalid", e);
      return AttestationProcessingResult.invalid("Invalid target checkpoint: " + e.getMessage());
    }

    IndexedAttestation indexedAttestation;
    Optional<IndexedAttestation> maybeIndexedAttestation = attestation.getIndexedAttestation();
    try {
      indexedAttestation =
          maybeIndexedAttestation.orElse(
              get_indexed_attestation(targetState, attestation.getAttestation()));
    } catch (IllegalArgumentException e) {
      LOG.debug("on_attestation: Attestation is not valid: ", e);
      return AttestationProcessingResult.invalid(e.getMessage());
    }
    return is_valid_indexed_attestation(targetState, indexedAttestation)
        .ifSuccessful(
            () -> {
              attestation.setValidIndexedAttestation();
              attestation.setIndexedAttestation(indexedAttestation);
              attestation.saveCommitteeShufflingSeed(targetState);

              return SUCCESSFUL;
            });
  }

  private static AttestationProcessingResult validateOnAttestation(
      final ReadOnlyStore store,
      final Attestation attestation,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint target = attestation.getData().getTarget();
    UInt64 current_epoch = compute_epoch_at_slot(get_current_slot(store));

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    UInt64 previous_epoch =
        current_epoch.compareTo(UInt64.valueOf(GENESIS_EPOCH)) > 0
            ? current_epoch.minus(UInt64.ONE)
            : UInt64.valueOf(GENESIS_EPOCH);

    if (!target.getEpoch().equals(previous_epoch) && !target.getEpoch().equals(current_epoch)) {
      return AttestationProcessingResult.invalid(
          "Attestations must be from the current or previous epoch");
    }

    if (!target.getEpoch().equals(compute_epoch_at_slot(attestation.getData().getSlot()))) {
      return AttestationProcessingResult.invalid("Attestation slot must be within specified epoch");
    }

    if (!forkChoiceStrategy.contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    Optional<UInt64> blockSlot =
        forkChoiceStrategy.blockSlot(attestation.getData().getBeacon_block_root());
    if (blockSlot.isEmpty()) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (blockSlot.get().compareTo(attestation.getData().getSlot()) > 0) {
      return AttestationProcessingResult.invalid(
          "Attestations must not be for blocks in the future. If not, the attestation should not be considered");
    }

    // LMD vote must be consistent with FFG vote target
    final UInt64 target_slot = compute_start_slot_at_epoch(target.getEpoch());
    if (get_ancestor(forkChoiceStrategy, attestation.getData().getBeacon_block_root(), target_slot)
        .map(ancestorRoot -> !ancestorRoot.equals(target.getRoot()))
        .orElse(true)) {
      return AttestationProcessingResult.invalid(
          "LMD vote must be consistent with FFG vote target");
    }

    return SUCCESSFUL;
  }

  private static AttestationProcessingResult checkIfAttestationShouldBeSavedForFuture(
      ReadOnlyStore store, Attestation attestation) {

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    final UInt64 currentSlot = get_current_slot(store);
    if (currentSlot.compareTo(attestation.getData().getSlot()) < 0) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    if (currentSlot.compareTo(attestation.getData().getSlot()) == 0) {
      return AttestationProcessingResult.DEFER_FOR_FORK_CHOICE;
    }

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    if (currentSlot.compareTo(attestation.getData().getTarget().getEpochStartSlot()) < 0) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    return SUCCESSFUL;
  }
}
