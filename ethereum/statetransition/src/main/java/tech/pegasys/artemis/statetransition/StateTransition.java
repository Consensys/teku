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

package tech.pegasys.artemis.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_block_header;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_eth1_data;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_operations;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_randao;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_final_updates;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_justification_and_finalization;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_registry_updates;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_rewards_and_penalties;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_slashings;
import static tech.pegasys.artemis.util.async.SafeFuture.reportExceptions;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.util.config.Constants.ZERO_HASH;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.metrics.EpochMetrics;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.util.bls.BLS;

public class StateTransition {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<EpochMetrics> epochMetrics;

  public StateTransition() {
    this.epochMetrics = Optional.empty();
  }

  public StateTransition(EpochMetrics epochMetrics) {
    this.epochMetrics = Optional.of(epochMetrics);
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
      final BeaconBlock block = signed_block.getMessage();

      // Process slots (including those with no blocks) since block
      BeaconState postSlotState = process_slots(preState, block.getSlot());

      // Verify signature
      if (validateStateRootAndSignatures) {
        checkArgument(
            verify_block_signature(postSlotState, signed_block),
            "state_transition: Verify signature");
      }
      // Process_block
      BeaconState postState = process_block(postSlotState, block, validateStateRootAndSignatures);

      Bytes32 stateRoot = postState.hash_tree_root();
      // Validate state root (`validate_state_root == True` in production)
      if (validateStateRootAndSignatures) {
        checkArgument(
            block.getState_root().equals(stateRoot),
            "Block state root does NOT match the calculated state root!\n"
                + "Block state root: "
                + signed_block.getMessage().getState_root().toHexString()
                + "New state root: "
                + stateRoot.toHexString());
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

  private static boolean verify_block_signature(
      final BeaconState state, SignedBeaconBlock signed_block) {
    final Validator proposer = state.getValidators().get(get_beacon_proposer_index(state));
    final Bytes signing_root =
        compute_signing_root(signed_block.getMessage(), get_domain(state, DOMAIN_BEACON_PROPOSER));
    return BLS.verify(proposer.getPubkey(), signing_root, signed_block.getSignature());
  }

  public BeaconState initiate(BeaconState state, SignedBeaconBlock block)
      throws StateTransitionException {
    return initiate(state, block, true);
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes block
   *
   * @throws BlockProcessingException
   */
  private BeaconState process_block(
      BeaconState preState, BeaconBlock block, boolean validateStateRootAndSignatures)
      throws BlockProcessingException {
    return preState.updated(
        state -> {
          process_block_header(state, block);
          process_randao(state, block.getBody(), validateStateRootAndSignatures);
          process_eth1_data(state, block.getBody());
          process_operations(state, block.getBody());
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
          process_justification_and_finalization(state);
          process_rewards_and_penalties(state);
          process_registry_updates(state);
          // @process_reveal_deadlines
          // @process_challenge_deadlines
          process_slashings(state);
          // @update_period_committee
          process_final_updates(state);
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
          preState.getSlot().compareTo(slot) <= 0,
          "process_slots: State slot higher than given slot");
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
          final UnsignedLong currentEpoch = get_current_epoch(state);
          long pendingExits =
              state.getValidators().stream()
                  .filter(
                      v ->
                          !v.getExit_epoch().equals(FAR_FUTURE_EPOCH)
                              && currentEpoch.compareTo(v.getExit_epoch()) < 0)
                  .count();

          metrics.onEpoch(
              state.getPrevious_justified_checkpoint().getEpoch(),
              state.getCurrent_justified_checkpoint().getEpoch(),
              state.getFinalized_checkpoint().getEpoch(),
              state.getPrevious_epoch_attestations().size(),
              state.getCurrent_epoch_attestations().size(),
              pendingExits);
        });
  }
}
