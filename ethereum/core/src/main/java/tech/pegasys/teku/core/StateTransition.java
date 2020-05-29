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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.util.async.SafeFuture.reportExceptions;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.teku.util.config.Constants.ZERO_HASH;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.blockvalidator.BatchBlockValidator;
import tech.pegasys.teku.core.blockvalidator.BlockValidator;
import tech.pegasys.teku.core.blockvalidator.BlockValidator.BlockValidationResult;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.metrics.EpochMetrics;

public class StateTransition {

  private static final Logger LOG = LogManager.getLogger();

  private static BlockValidator createDefaultBlockValidator() {
    return new BatchBlockValidator();
  }

  private final Optional<EpochMetrics> epochMetrics;
  private final BlockValidator blockValidator;

  public StateTransition() {
    this(Optional.empty(), createDefaultBlockValidator());
  }

  public StateTransition(EpochMetrics epochMetrics) {
    this(Optional.of(epochMetrics), createDefaultBlockValidator());
  }

  public StateTransition(BlockValidator blockValidator) {
    this(Optional.empty(), blockValidator);
  }

  public static Map<Bytes32, BeaconState> produceStatesForBlocks(
      final Bytes32 baseBlockRoot,
      final BeaconState baseState,
      final Collection<SignedBeaconBlock> newBlocks) {
    final Map<Bytes32, BeaconState> statesByRoot = new HashMap<>();

    // Initialize states with the base state
    statesByRoot.put(baseBlockRoot, baseState);

    // Index blocks by parent root
    final Map<Bytes32, List<SignedBeaconBlock>> blocksByParent = new HashMap<>();
    for (SignedBeaconBlock currentBlock : newBlocks) {
      final List<SignedBeaconBlock> blockList =
          blocksByParent.computeIfAbsent(currentBlock.getParent_root(), (key) -> new ArrayList<>());
      blockList.add(currentBlock);
    }

    // Generate states
    final Deque<Bytes32> parentRoots = new ArrayDeque<>();
    parentRoots.push(baseBlockRoot);
    while (!parentRoots.isEmpty()) {
      final Bytes32 parentRoot = parentRoots.pop();
      final BeaconState parentState = statesByRoot.get(parentRoot);
      final List<SignedBeaconBlock> blocks =
          blocksByParent.computeIfAbsent(parentRoot, (key) -> Collections.emptyList());
      for (SignedBeaconBlock block : blocks) {
        final Bytes32 blockRoot = block.getMessage().hash_tree_root();
        processBlock(parentState, block)
            .ifPresent(
                state -> {
                  statesByRoot.put(blockRoot, state);
                  parentRoots.push(blockRoot);
                });
      }
    }

    return statesByRoot;
  }

  private static Optional<BeaconState> processBlock(
      final BeaconState preState, final SignedBeaconBlock block) {
    StateTransition stateTransition = new StateTransition();
    try {
      final BeaconState postState = stateTransition.initiate(preState, block);
      return Optional.of(postState);
    } catch (StateTransitionException e) {
      LOG.trace(
          "Unable to produce state for block at slot {} ({})",
          block.getSlot(),
          block.getMessage().hash_tree_root());
      return Optional.empty();
    }
  }

  private StateTransition(Optional<EpochMetrics> epochMetrics, BlockValidator blockValidator) {
    this.epochMetrics = epochMetrics;
    this.blockValidator = blockValidator;
  }

  public BeaconState initiate(BeaconState preState, SignedBeaconBlock signed_block)
      throws StateTransitionException {
    return initiate(preState, signed_block, true);
  }
  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Runs state transition up to and with the given block
   *
   * @param preState
   * @param signed_block
   * @param validateStateRootAndSignatures
   * @return
   * @throws StateTransitionException
   */
  public BeaconState initiate(
      BeaconState preState, SignedBeaconBlock signed_block, boolean validateStateRootAndSignatures)
      throws StateTransitionException {
    try {
      BlockValidator blockValidator =
          validateStateRootAndSignatures ? this.blockValidator : BlockValidator.NOP;
      final BeaconBlock block = signed_block.getMessage();

      // Process slots (including those with no blocks) since block
      BeaconState postSlotState = process_slots(preState, block.getSlot());

      // Process_block
      BeaconState postState = process_block(postSlotState, block);

      BlockValidationResult blockValidationResult =
          blockValidator.validate(postSlotState, signed_block, postState).join();

      if (!blockValidationResult.isValid()) {
        throw new BlockProcessingException(blockValidationResult.getReason());
      }

      return postState;
    } catch (SlotProcessingException
        | BlockProcessingException
        | EpochProcessingException
        | IllegalArgumentException e) {
      LOG.warn("State Transition error", e);
      throw new StateTransitionException(e);
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes block
   *
   * @throws BlockProcessingException
   */
  private BeaconState process_block(BeaconState preState, BeaconBlock block)
      throws BlockProcessingException {
    return preState.updated(
        state -> {
          BlockProcessorUtil.process_block_header(state, block);
          BlockProcessorUtil.process_randao_no_validation(state, block.getBody());
          BlockProcessorUtil.process_eth1_data(state, block.getBody());
          BlockProcessorUtil.process_operations_no_validation(state, block.getBody());
        });
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes epoch
   *
   * @throws EpochProcessingException
   */
  private static BeaconState process_epoch(BeaconState preState) throws EpochProcessingException {
    return preState.updated(
        state -> {
          // Note: the lines with @ label here will be inserted here in a future phase
          EpochProcessorUtil.process_justification_and_finalization(state);
          EpochProcessorUtil.process_rewards_and_penalties(state);
          EpochProcessorUtil.process_registry_updates(state);
          // @process_reveal_deadlines
          // @process_challenge_deadlines
          EpochProcessorUtil.process_slashings(state);
          // @update_period_committee
          EpochProcessorUtil.process_final_updates(state);
          // @after_process_final_updates
        });
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes slot
   */
  private static BeaconState process_slot(BeaconState preState) {
    return preState.updated(
        state -> {
          // Cache state root
          Bytes32 previous_state_root = state.hash_tree_root();
          int index =
              state.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT)).intValue();
          state.getState_roots().set(index, previous_state_root);

          // Cache latest block header state root
          BeaconBlockHeader latest_block_header = state.getLatest_block_header();
          if (latest_block_header.getState_root().equals(ZERO_HASH)) {
            BeaconBlockHeader latest_block_header_new =
                new BeaconBlockHeader(
                    latest_block_header.getSlot(),
                    latest_block_header.getProposer_index(),
                    latest_block_header.getParent_root(),
                    previous_state_root,
                    latest_block_header.getBody_root());
            state.setLatest_block_header(latest_block_header_new);
          }

          // Cache block root
          Bytes32 previous_block_root = state.getLatest_block_header().hash_tree_root();
          state.getBlock_roots().set(index, previous_block_root);
        });
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes slots through state slot through given slot
   *
   * @throws EpochProcessingException
   * @throws SlotProcessingException
   */
  public BeaconState process_slots(BeaconState preState, UnsignedLong slot)
      throws SlotProcessingException, EpochProcessingException {
    try {
      checkArgument(
          preState.getSlot().compareTo(slot) < 0,
          "process_slots: State slot %s higher than given slot %s",
          preState.getSlot(),
          slot);
      BeaconState state = preState;
      while (state.getSlot().compareTo(slot) < 0) {
        state = process_slot(state);
        // Process epoch on the start slot of the next epoch
        if (state
            .getSlot()
            .plus(UnsignedLong.ONE)
            .mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
            .equals(UnsignedLong.ZERO)) {
          BeaconState epochState = process_epoch(state);
          reportExceptions(CompletableFuture.runAsync(() -> recordMetrics(epochState)));
          state = epochState;
        }
        state = state.updated(s -> s.setSlot(s.getSlot().plus(UnsignedLong.ONE)));
      }
      return state;
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage(), e);
      throw new SlotProcessingException(e);
    }
  }

  private synchronized void recordMetrics(BeaconState state) {
    epochMetrics.ifPresent(
        metrics -> {
          final UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
          long pendingExits =
              state.getValidators().stream()
                  .filter(
                      v ->
                          !v.getExit_epoch().equals(FAR_FUTURE_EPOCH)
                              && currentEpoch.compareTo(v.getExit_epoch()) < 0)
                  .count();

          metrics.onEpoch(
              state.getPrevious_epoch_attestations().size(),
              state.getCurrent_epoch_attestations().size(),
              pendingExits);
        });
  }
}
