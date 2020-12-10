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
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.teku.util.config.Constants.ZERO_HASH;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.blockvalidator.BatchBlockValidator;
import tech.pegasys.teku.core.blockvalidator.BlockValidator;
import tech.pegasys.teku.core.blockvalidator.BlockValidator.BlockValidationResult;
import tech.pegasys.teku.core.epoch.EpochProcessor;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.lookup.IndexedAttestationProvider;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateTransition {

  private static final Logger LOG = LogManager.getLogger();

  private static BlockValidator createDefaultBlockValidator() {
    return new BatchBlockValidator();
  }

  private final BlockValidator blockValidator;

  public StateTransition() {
    this(createDefaultBlockValidator());
  }

  public StateTransition(BlockValidator blockValidator) {
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
    return initiate(
        preState,
        signed_block,
        validateStateRootAndSignatures,
        IndexedAttestationProvider.DIRECT_PROVIDER);
  }

  public BeaconState initiate(
      BeaconState preState,
      SignedBeaconBlock signedBlock,
      boolean validateStateRootAndSignatures,
      final IndexedAttestationProvider indexedAttestationProvider)
      throws StateTransitionException {
    try {
      // * Process slots (including those with no blocks) since block
      // * beaconStateConsumer only consumes the missing slots here,
      //   the new block will be processed when adding to the store.
      BeaconState postSlotState = process_slots(preState, signedBlock.getMessage().getSlot());

      return processAndValidateBlock(
          signedBlock, indexedAttestationProvider, postSlotState, validateStateRootAndSignatures);
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      LOG.warn("State Transition error", e);
      throw new StateTransitionException(e);
    }
  }

  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final IndexedAttestationProvider indexedAttestationProvider,
      final BeaconState blockSlotState,
      final boolean validateStateRootAndSignatures)
      throws StateTransitionException {
    BlockValidator blockValidator =
        validateStateRootAndSignatures ? this.blockValidator : BlockValidator.NOOP;
    try {
      // Process_block
      BeaconState postState = process_block(blockSlotState, signedBlock.getMessage());

      BlockValidationResult blockValidationResult =
          blockValidator
              .validate(blockSlotState, signedBlock, postState, indexedAttestationProvider)
              .join();

      if (!blockValidationResult.isValid()) {
        throw new BlockProcessingException(blockValidationResult.getReason());
      }

      return postState;
    } catch (final IllegalArgumentException | BlockProcessingException e) {
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
  public BeaconState process_block(BeaconState preState, BeaconBlock block)
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
   * Processes slot
   */
  private static BeaconState process_slot(BeaconState preState) {
    return preState.updated(
        state -> {
          // Cache state root
          Bytes32 previous_state_root = state.hash_tree_root();
          int index = state.getSlot().mod(SLOTS_PER_HISTORICAL_ROOT).intValue();
          state.getState_roots().set(index, previous_state_root);

          // Cache latest block header state root
          BeaconBlockHeader latest_block_header = state.getLatest_block_header();
          if (latest_block_header.getStateRoot().equals(ZERO_HASH)) {
            BeaconBlockHeader latest_block_header_new =
                new BeaconBlockHeader(
                    latest_block_header.getSlot(),
                    latest_block_header.getProposerIndex(),
                    latest_block_header.getParentRoot(),
                    previous_state_root,
                    latest_block_header.getBodyRoot());
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
  public BeaconState process_slots(BeaconState preState, UInt64 slot)
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
        if (state.getSlot().plus(UInt64.ONE).mod(SLOTS_PER_EPOCH).equals(UInt64.ZERO)) {
          state = EpochProcessor.processEpoch(state);
        }
        state = state.updated(s -> s.setSlot(s.getSlot().plus(UInt64.ONE)));
      }
      return state;
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage(), e);
      throw new SlotProcessingException(e);
    }
  }
}
