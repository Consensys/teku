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

package tech.pegasys.teku.spec.logic.common.statetransition;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

public class StateTransition {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfig specConfig;
  private final BlockProcessor blockProcessor;
  private final EpochProcessor epochProcessor;

  private final BlockValidator blockValidator;

  protected StateTransition(
      final SpecConfig specConfig,
      final BlockProcessor blockProcessor,
      final EpochProcessor epochProcessor,
      final BlockValidator blockValidator) {
    this.specConfig = specConfig;
    this.blockProcessor = blockProcessor;
    this.epochProcessor = epochProcessor;
    this.blockValidator = blockValidator;
  }

  public static StateTransition create(
      final SpecConfig specConfig,
      final BlockProcessor blockProcessor,
      final EpochProcessor epochProcessor,
      final BeaconStateUtil beaconStateUtil,
      final BeaconStateAccessors beaconStateAccessors) {
    final BlockValidator blockValidator =
        BlockValidator.standard(specConfig, beaconStateUtil, blockProcessor, beaconStateAccessors);
    return new StateTransition(specConfig, blockProcessor, epochProcessor, blockValidator);
  }

  public BeaconState initiate(BeaconState preState, SignedBeaconBlock signedBlock)
      throws StateTransitionException {
    return initiate(preState, signedBlock, true);
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
        preState, signed_block, validateStateRootAndSignatures, IndexedAttestationCache.NOOP);
  }

  public BeaconState initiate(
      BeaconState preState,
      SignedBeaconBlock signedBlock,
      boolean validateStateRootAndSignatures,
      final IndexedAttestationCache indexedAttestationCache)
      throws StateTransitionException {
    try {
      // * Process slots (including those with no blocks) since block
      // * beaconStateConsumer only consumes the missing slots here,
      //   the new block will be processed when adding to the store.
      BeaconState postSlotState = processSlots(preState, signedBlock.getMessage().getSlot());

      return processAndValidateBlock(
          signedBlock, postSlotState, validateStateRootAndSignatures, indexedAttestationCache);
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      LOG.warn("State Transition error", e);
      throw new StateTransitionException(e);
    }
  }

  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final boolean validateStateRootAndSignatures,
      final IndexedAttestationCache indexedAttestationCache)
      throws StateTransitionException {
    BlockValidator blockValidator =
        validateStateRootAndSignatures ? this.blockValidator : BlockValidator.NOOP;
    try {
      // Process_block
      BeaconState postState =
          processBlock(blockSlotState, signedBlock.getMessage(), indexedAttestationCache);

      BlockValidationResult blockValidationResult =
          blockValidator.validate(blockSlotState, signedBlock, postState, indexedAttestationCache);

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
  public BeaconState processBlock(BeaconState preState, BeaconBlock block)
      throws BlockProcessingException {
    return processBlock(preState, block, IndexedAttestationCache.NOOP);
  }

  protected BeaconState processBlock(
      BeaconState preState, BeaconBlock block, IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    return preState.updated(state -> processBlock(state, block, indexedAttestationCache));
  }

  protected void processBlock(
      final MutableBeaconState state,
      final BeaconBlock block,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    blockProcessor.processBlockHeader(state, block);
    blockProcessor.processRandaoNoValidation(state, block.getBody());
    blockProcessor.processEth1Data(state, block.getBody());
    blockProcessor.processOperationsNoValidation(state, block.getBody(), indexedAttestationCache);
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes slots through state slot through given slot
   *
   * @throws EpochProcessingException
   * @throws SlotProcessingException
   */
  public BeaconState processSlots(BeaconState preState, UInt64 slot)
      throws SlotProcessingException, EpochProcessingException {
    try {
      checkArgument(
          preState.getSlot().compareTo(slot) < 0,
          "process_slots: State slot %s higher than given slot %s",
          preState.getSlot(),
          slot);
      BeaconState state = preState;
      while (state.getSlot().compareTo(slot) < 0) {
        state = processSlot(state);
        // Process epoch on the start slot of the next epoch
        if (state
            .getSlot()
            .plus(UInt64.ONE)
            .mod(specConfig.getSlotsPerEpoch())
            .equals(UInt64.ZERO)) {
          state = epochProcessor.processEpoch(state);
        }
        state = state.updated(s -> s.setSlot(s.getSlot().plus(UInt64.ONE)));
      }
      return state;
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage(), e);
      throw new SlotProcessingException(e);
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes slot
   */
  private BeaconState processSlot(BeaconState preState) {
    return preState.updated(
        state -> {
          // Cache state root
          Bytes32 previous_state_root = state.hashTreeRoot();
          int index = state.getSlot().mod(specConfig.getSlotsPerHistoricalRoot()).intValue();
          state.getState_roots().setElement(index, previous_state_root);

          // Cache latest block header state root
          BeaconBlockHeader latest_block_header = state.getLatest_block_header();
          if (latest_block_header.getStateRoot().equals(Bytes32.ZERO)) {
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
          Bytes32 previous_block_root = state.getLatest_block_header().hashTreeRoot();
          state.getBlock_roots().setElement(index, previous_block_root);
        });
  }
}
