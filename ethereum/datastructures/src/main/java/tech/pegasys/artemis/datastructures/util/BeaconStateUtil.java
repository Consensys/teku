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

package tech.pegasys.artemis.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.ACTIVATION_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_SLASHED_EXIT_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.MAX_INDICES_PER_SLASHABLE_VOTE;
import static tech.pegasys.artemis.datastructures.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.hash_tree_root;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.integerListHashTreeRoot;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSException;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class BeaconStateUtil {

  private static final ALogger LOG = new ALogger(BeaconStateUtil.class.getName());

  public static BeaconStateWithCache get_genesis_beacon_state(
      BeaconStateWithCache state,
      ArrayList<Deposit> genesis_validator_deposits,
      long genesis_time,
      Eth1Data latest_eth1_data)
      throws IllegalStateException {

    // Process genesis deposits
    for (Deposit deposit : genesis_validator_deposits) {
      process_deposit(state, deposit);
    }
    // Process genesis activations
    for (int validator_index = 0;
        validator_index < state.getValidator_registry().size();
        validator_index++) {
      if (get_effective_balance(state, validator_index) >= MAX_DEPOSIT_AMOUNT) {
        activate_validator(state, validator_index, true);
      }
    }
    Bytes32 genesis_active_index_root =
        integerListHashTreeRoot(
            ValidatorsUtil.get_active_validator_indices(
                state.getValidator_registry(), GENESIS_EPOCH));
    for (int index = 0; index < LATEST_ACTIVE_INDEX_ROOTS_LENGTH; index++) {
      state.getLatest_active_index_roots().set(index, genesis_active_index_root);
    }
    state.setCurrent_shuffling_seed(generate_seed(state, GENESIS_EPOCH));

    return state;
  }

  /**
   * Return the list of (committee, shard) tuples (implemented as CrosslinkCommittee) for the slot.
   *
   * <p>Note: There are two possible shufflings for crosslink committees for a ``slot`` in the next
   * epoch -- with and without a `registry_change`
   *
   * @param state - The beacon state under consideration.
   * @param slot - The slot number.
   * @param registry_change - True if we are considering a registry change.
   * @return The list of CrosslinkCommittees for the slot.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_crosslink_committees_at_slot">get_crosslink_committees_at_slot
   *     - Spec v0.4</a>
   */
  public static ArrayList<CrosslinkCommittee> get_crosslink_committees_at_slot(
      BeaconState state, long slot, boolean registry_change) throws IllegalArgumentException {
    long epoch = slot_to_epoch(slot);
    long current_epoch = get_current_epoch(state);
    long previous_epoch = get_previous_epoch(state);
    long next_epoch = get_next_epoch(state);

    checkArgument(
        previous_epoch <= epoch && epoch <= next_epoch,
        "checkArgument threw an exception in get_crosslink_committees_at_slot()");

    long committees_per_epoch = 0;
    Bytes32 seed = Bytes32.ZERO;
    long shuffling_epoch = 0;
    long shuffling_start_shard = 0;

    if (epoch == current_epoch) {
      committees_per_epoch = get_current_epoch_committee_count(state);
      seed = state.getCurrent_shuffling_seed();
      shuffling_epoch = state.getCurrent_shuffling_epoch();
      shuffling_start_shard = state.getCurrent_shuffling_start_shard();

    } else if (epoch == previous_epoch) {

      committees_per_epoch = get_previous_epoch_committee_count(state);
      seed = state.getPrevious_shuffling_seed();
      shuffling_epoch = state.getPrevious_shuffling_epoch();
      shuffling_start_shard = state.getPrevious_shuffling_start_shard();

    } else if (epoch == next_epoch) {

      long current_committees_per_epoch = get_current_epoch_committee_count(state);
      committees_per_epoch = get_next_epoch_committee_count(state);
      shuffling_epoch = next_epoch;
      long epochs_since_last_registry_update =
          current_epoch - state.getValidator_registry_update_epoch();
      if (registry_change) {
        seed = generate_seed(state, next_epoch);
        shuffling_start_shard =
            (state.getCurrent_shuffling_start_shard() + current_committees_per_epoch)
                % Constants.SHARD_COUNT;
      } else if (epochs_since_last_registry_update > 1
          && is_power_of_two(epochs_since_last_registry_update)) {
        seed = generate_seed(state, next_epoch);
        shuffling_start_shard = state.getCurrent_shuffling_start_shard();
      } else {
        seed = state.getCurrent_shuffling_seed();
        shuffling_start_shard = state.getCurrent_shuffling_start_shard();
      }
    }

    List<List<Integer>> shuffling =
        get_shuffling(seed, state.getValidator_registry(), shuffling_epoch);
    long offset = slot % SLOTS_PER_EPOCH;
    long committees_per_slot = committees_per_epoch / SLOTS_PER_EPOCH;
    long slot_start_shard =
        (shuffling_start_shard + committees_per_slot * offset) % Constants.SHARD_COUNT;

    ArrayList<CrosslinkCommittee> crosslink_committees_at_slot = new ArrayList<>();
    for (long i = 0; i < committees_per_slot; i++) {
      CrosslinkCommittee committee =
          new CrosslinkCommittee(
              (slot_start_shard + i) % Constants.SHARD_COUNT,
              shuffling.get(toIntExact(committees_per_slot * offset + i) % shuffling.size()));
      crosslink_committees_at_slot.add(committee);
    }
    return crosslink_committees_at_slot;
  }

  /** This is a wrapper that defaults `registry_change` to false when it is not provided */
  public static ArrayList<CrosslinkCommittee> get_crosslink_committees_at_slot(
      BeaconState state, long slot) throws IllegalArgumentException {
    return get_crosslink_committees_at_slot(state, slot, false);
  }

  /*
   * TODO: Note from spec - Note: this definition and the next few definitions
   * make heavy use of repetitive computing. Production implementations are
   * expected to appropriately use caching/memoization to avoid redoing work.
   */

  /**
   * Return the number of committees in the previous epoch of the given state.
   *
   * @param state - The state under consideration.
   * @return The number of committees in the previous epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_previous_epoch_committee_count">get_previous_epoch_committee_count
   *     - Spec v0.4</a>
   */
  private static long get_previous_epoch_committee_count(BeaconState state) {
    List<Integer> previous_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getPrevious_shuffling_epoch());
    return get_epoch_committee_count(previous_active_validators.size());
  }

  /**
   * Returns the number of committees in the current epoch of the given state.
   *
   * @param state - The state under consideration.
   * @return The number of committees in the current epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_current_epoch_committee_count">get_current_epoch_committee_count
   *     - Spec v0.4</a>
   */
  public static long get_current_epoch_committee_count(BeaconState state) {
    List<Integer> current_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getCurrent_shuffling_epoch());
    return get_epoch_committee_count(current_active_validators.size());
  }

  /**
   * Returns the number of committees in the next epoch of the given state.
   *
   * @param state - The state under consideration.
   * @return The number of committees in the next epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_next_epoch_committee_count">get_next_epoch_committee_count
   *     - Spec v0.4</a>
   */
  private static long get_next_epoch_committee_count(BeaconState state) {
    List<Integer> next_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), get_current_epoch(state) + 1);
    return get_epoch_committee_count(next_active_validators.size());
  }

  /**
   * Generate a seed for the given epoch.
   *
   * @param state - The BeaconState under consideration.
   * @param epoch - The epoch to generate a seed for.
   * @return A generated seed for the given epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#generate_seed">generate_seed
   *     - Spec v0.4</a>
   */
  public static Bytes32 generate_seed(BeaconState state, long epoch)
      throws IllegalArgumentException {
    Bytes32 randao_mix = get_randao_mix(state, epoch - Constants.MIN_SEED_LOOKAHEAD);
    Bytes32 index_root = get_active_index_root(state, epoch);
    Bytes32 epochBytes = int_to_bytes32(epoch);
    return Hash.keccak256(Bytes.wrap(randao_mix, index_root, epochBytes));
  }

  /**
   * Returns the index root at a recent epoch.
   *
   * @param state - The BeaconState under consideration.
   * @param epoch - The epoch to get the index root for.
   * @return The index root at a given recent epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_active_index_root">get_active_index_root
   *     - Spec v0.4</a>
   */
  public static Bytes32 get_active_index_root(BeaconState state, long epoch) {
    checkArgument(
        get_current_epoch(state) - LATEST_ACTIVE_INDEX_ROOTS_LENGTH + ACTIVATION_EXIT_DELAY < epoch,
        "checkArgument threw and exception in get_active_indesx_root()");
    checkArgument(
        epoch <= get_current_epoch(state) + ACTIVATION_EXIT_DELAY,
        "checkArgument threw and exception in get_active_index_root()");

    int index = toIntExact(epoch) % LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
    return state.getLatest_active_index_roots().get(index);
  }

  public static Bytes32 getShard_block_root(BeaconState state, long shard) {
    return state
        .getLatest_crosslinks()
        .get(toIntExact(shard) % Constants.SHARD_COUNT)
        .getCrosslink_data_root();
  }

  /**
   * Returns the effective balance (also known as "balance at stake") for a validator with the given
   * index.
   *
   * @param state - The BeaconState under consideration.
   * @param index - The index of the validator to consider.
   * @return The smaller of either the validator's balance at stake or MAX_DEPOSIT_AMOUNT.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_effective_balance">get_effective_balance
   *     - Spec v0.4</a>
   */
  public static long get_effective_balance(BeaconState state, int index) {
    return Math.min(state.getValidator_balances().get(index), Constants.MAX_DEPOSIT_AMOUNT);
  }

  /**
   * Returns the effective balance (also known as "balance at stake") for a validator with the given
   * index.
   *
   * <p><b>Note:</b> This is a convenience method which is not defined in the spec.
   *
   * @param state - The BeaconState under consideration.
   * @param record - The Validator to retrieve the balance for.
   * @return The smaller of either the validator's balance at stake or MAX_DEPOSIT_AMOUNT.
   */
  public static long get_effective_balance(BeaconState state, Validator record) {
    int index = state.getValidator_registry().indexOf(record);
    return get_effective_balance(state, index);
  }

  /**
   * calculate the total balance from the previous epoch
   *
   * @param state
   * @return
   */
  public static long previous_total_balance(BeaconState state) {
    long previous_epoch = BeaconStateUtil.get_previous_epoch(state);
    List<Integer> previous_active_validators =
        ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), previous_epoch);
    return get_total_balance(state, previous_active_validators);
  }

  /**
   * Adds and returns the effective balances for the validators in the given CrossLinkCommittee.
   *
   * <p><b>Note:</b> This is a convenience method which is not defined in the spec.
   *
   * @param state - The current BeaconState.
   * @param crosslink_committee - The CrosslinkCommittee with the committee of validators to get the
   *     total balance for.
   * @return The combined effective balance of the list of validators.
   */
  public static long get_total_balance(BeaconState state, CrosslinkCommittee crosslink_committee) {
    return get_total_balance(state, crosslink_committee.getCommittee());
  }

  /**
   * Adds and returns the effective balances for the validators referenced by the given indices.
   *
   * @param state - The current BeaconState.
   * @param validator_indices - A list of validator indices to get the total balance for.
   * @return The combined effective balance of the list of validators.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_total_balance">get_total_balance
   *     - Spec v0.4</a>
   */
  public static long get_total_balance(BeaconState state, List<Integer> validator_indices) {
    long total_balance = 0;
    for (Integer index : validator_indices) {
      total_balance = total_balance + BeaconStateUtil.get_effective_balance(state, index);
    }
    return total_balance;
  }

  /**
   * Returns the epoch number of the given slot.
   *
   * @param slot - The slot number under consideration.
   * @return The epoch associated with the given slot number.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#slot_to_epoch">slot_to_epoch
   *     - Spec v0.4</a>
   */
  public static long slot_to_epoch(long slot) {
    return slot / SLOTS_PER_EPOCH;
  }

  /**
   * Return the previous epoch of the given state.
   *
   * @param state The beacon state under consideration.
   * @return The previous epoch number for the given state.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_previous_epoch">get_previous_epoch
   *     - Spec v0.4</a>
   */
  public static long get_previous_epoch(BeaconState state) {
    long current_epoch_minus_one = get_current_epoch(state) - 1;
    return Math.max(current_epoch_minus_one, GENESIS_EPOCH);
  }

  /**
   * Return the current epoch of the given state.
   *
   * @param state The beacon state under consideration.
   * @return The current epoch number for the given state.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_current_epoch">get_current_epoch
   *     - Spec v0.4</a>
   */
  public static long get_current_epoch(BeaconState state) {
    return slot_to_epoch(state.getSlot());
  }

  /**
   * Return the next epoch of the given state.
   *
   * <p><b>This method is no longer in the spec as of v0.4, but is retained here for
   * convenience.</b>
   *
   * @param state The beacon state under consideration.
   * @return The next epoch number.
   */
  public static long get_next_epoch(BeaconState state) {
    return get_current_epoch(state) + 1;
  }

  /**
   * Return the slot that the given epoch starts at.
   *
   * @param epoch - The epoch under consideration.
   * @return The slot that the given epoch starts at.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_epoch_start_slot">get_epoch_start_slot
   *     - Spec v0.4</a>
   */
  public static long get_epoch_start_slot(long epoch) {
    return epoch * SLOTS_PER_EPOCH;
  }

  /**
   * An entry or exit triggered in the ``epoch`` given by the input takes effect at the epoch given
   * by the output.
   *
   * @param epoch
   */
  public static long get_entry_exit_effect_epoch(long epoch) {
    return epoch + 1 + ACTIVATION_EXIT_DELAY;
  }

  /**
   * Initiate exit for the validator with the given 'index'. Note that this function mutates
   * 'state'.
   *
   * @param index The index of the validator.
   */
  public static void initiate_validator_exit(BeaconState state, int index) {
    Validator validator = state.getValidator_registry().get(index);
    validator.setInitiatedExit(true);
  }

  /**
   * Exit the validator of the given ``index``. Note that this function mutates ``state``.
   *
   * @param state
   * @param index
   */
  public static void exit_validator(BeaconState state, int index) {
    Validator validator = state.getValidator_registry().get(index);

    long exit_epoch = get_entry_exit_effect_epoch(get_current_epoch(state));
    // The following updates only occur if not previous exited
    if (validator.getExit_epoch() <= exit_epoch) {
      return;
    }

    validator.setExit_epoch(exit_epoch);
  }

  /**
   * Penalize the validator of the given index. NOTE: This function has side-effects and mutates
   * 'state'. This functions adds whistleblower reward to the whistleblower balance and subtracts
   * whistleblower reward from the bad validator.
   *
   * @param state - The current BeaconState. NOTE: State will be mutated per spec logic.
   * @param index - The index of the validator that will be penalized.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#penalize_validator">
   *     spec</a>
   */
  public static void penalize_validator(BeaconState state, int index) {
    Validator validator = state.getValidator_registry().get(index);
    if (validator.getWithdrawal_epoch() != -1) {
      checkArgument(
          state.getSlot() < get_epoch_start_slot(validator.getWithdrawal_epoch()),
          "checkArgument threw and exception in get_randao_mix()");
    }
    exit_validator(state, index);
    state
        .getLatest_slashed_balances()
        .set(
            toIntExact(get_current_epoch(state)) % LATEST_SLASHED_EXIT_LENGTH,
            state
                    .getLatest_slashed_balances()
                    .get(toIntExact(get_current_epoch(state)) % LATEST_SLASHED_EXIT_LENGTH)
                + get_effective_balance(state, index));

    int whistleblower_index = get_beacon_proposer_index(state, state.getSlot());
    long whistleblower_reward = get_effective_balance(state, index) / WHISTLEBLOWER_REWARD_QUOTIENT;
    state
        .getValidator_balances()
        .set(
            whistleblower_index,
            state.getValidator_balances().get(whistleblower_index) + whistleblower_reward);
    state
        .getValidator_balances()
        .set(index, state.getValidator_balances().get(index) - whistleblower_reward);

    validator.setSlashed(true);
  }

  /**
   * Set the validator with the given ``index`` as withdrawable
   * ``MIN_VALIDATOR_WITHDRAWABILITY_DELAY`` after the current epoch. Note that this function
   * mutates ``state``.
   *
   * @param state
   * @param index
   */
  public static void prepare_validator_for_withdrawal(BeaconState state, int index) {
    Validator validator = state.getValidator_registry().get(index);
    validator.setWithdrawal_epoch(
        get_current_epoch(state) + Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY);
  }

  /**
   * Returns the randao mix at a recent epoch.
   *
   * @param state - The BeaconState under consideration.
   * @param epoch - The epoch to get the randao mix for.
   * @return The randao mix at the given epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_randao_mix">get_randao_mix
   *     - Spec v0.4</a>
   */
  public static Bytes32 get_randao_mix(BeaconState state, long epoch) {
    checkArgument(
        get_current_epoch(state) - LATEST_RANDAO_MIXES_LENGTH < epoch,
        "checkArgument threw and exception in get_randao_mix()");
    checkArgument(
        epoch <= get_current_epoch(state), "checkArgument threw and exception in get_randao_mix()");
    long index = epoch % LATEST_RANDAO_MIXES_LENGTH;
    List<Bytes32> randao_mixes = state.getLatest_randao_mixes();
    if (index == -1) index = 0;
    return randao_mixes.get(toIntExact(index));
  }

  /**
   * Returns the block root at a recent slot.
   *
   * @param state - The BeaconState under consideration.
   * @param slot - The slot to return the block root for.
   * @return The block root at the given slot.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_block_root">get_block_root
   *     - Spec v0.4</a>
   */
  public static Bytes32 get_block_root(BeaconState state, long slot) {
    checkArgument(state.getSlot() <= slot + Constants.LATEST_BLOCK_ROOTS_LENGTH);
    checkArgument(slot < state.getSlot(), "checkArgument threw and exception in get_block_root()");
    // Todo: Remove .intValue() as soon as our list wrapper supports unsigned longs
    return state.getLatest_block_roots().get((int) slot % Constants.LATEST_BLOCK_ROOTS_LENGTH);
  }

  /**
   * Merkelize given values (where list.size() is a power of 2), and return the merkle root.
   *
   * <p><b>NOTE:</b> The leaves are not hashed.
   *
   * @param list - The List of values to get the merkle root for.
   * @return The merkle root for the provided list of values.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#merkle_root">merkle_root
   *     - Spec v0.4</a>
   */
  public static Bytes32 merkle_root(List<Bytes32> list) throws IllegalStateException {
    Bytes32[] o = new Bytes32[list.size() * 2];
    for (int i = 0; i < list.size(); i++) {
      o[i + list.size()] = list.get(i);
    }
    for (int i = list.size() - 1; i > 0; i--) {
      o[i] = Hash.keccak256(Bytes.wrap(o[i * 2], o[i * 2 + 1]));
    }
    return o[1];
  }

  /**
   * Returns the number of committees in one epoch.
   *
   * @param active_validator_count - The number of active validators.
   * @return The number of committees in one epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_epoch_committee_count">get_epoch_committee_count
   *     - Spec v0.4</a>
   */
  public static long get_epoch_committee_count(long active_validator_count) {

    return Math.max(
            1,
            Math.min(
                Constants.SHARD_COUNT / SLOTS_PER_EPOCH,
                active_validator_count / SLOTS_PER_EPOCH / Constants.TARGET_COMMITTEE_SIZE))
        * SLOTS_PER_EPOCH;
  }

  /**
   * Shuffle active validators and splits into crosslink committees.
   *
   * @param seed - A shuffling seed.
   * @param validators - The list of validators to shuffle.
   * @param epoch - Epoch under consideration.
   * @return A list of committees (each of list of validator indices)
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_shuffling">get_shuffling
   *     - Spec v0.4</a>
   */
  public static List<List<Integer>> get_shuffling(
      Bytes32 seed, List<Validator> validators, long epoch) throws IllegalStateException {

    List<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices(validators, epoch);
    int length = active_validator_indices.size();

    List<Integer> shuffled_indices =
        Arrays.stream(shuffle(length, seed)).boxed().collect(Collectors.toList());
    List<Integer> shuffled_active_validator_indices =
        active_validator_indices
            .parallelStream()
            .map(i -> active_validator_indices.get(shuffled_indices.get(i)))
            .collect(Collectors.toList());

    int committeesPerEpoch = toIntExact(get_epoch_committee_count(length));

    List<List<Integer>> split = split(shuffled_active_validator_indices, committeesPerEpoch);
    return split;
  }

  /**
   * Return `p(index)` in a pseudorandom permutation `p` of `0...list_size-1` with ``seed`` as
   * entropy.
   *
   * <p>Utilizes 'swap or not' shuffling found in
   * https://link.springer.com/content/pdf/10.1007%2F978-3-642-32009-5_1.pdf. See the 'generalized
   * domain' algorithm on page 3.
   *
   * @param index The index in the permuatation we wish to get the value of.
   * @param listSize The size of the list from which the element is taken.
   * @param seed Initial seed value used for randomization.
   * @return The index from the original list that is now at position `index`
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_permuted_index">get_permuted_index
   *     - Spec v0.4</a>
   */
  @VisibleForTesting
  public static int get_permuted_index(int index, int listSize, Bytes32 seed) {
    checkArgument(index < listSize);

    // The spec says that we should handle up to 2^40 validators, but we can't do this,
    // so we just fall back to int (2^31 validators).
    // checkArgument(listSize <= 1099511627776L); // 2^40

    /*
     * In the following, great care is needed around signed and unsigned values.
     * Note that the % (modulo) operator in Java behaves differently from the
     * modulo operator in python:
     *   Python -1 % 13 = 12
     *   Java   -1 % 13 = -1
     *
     */

    int indexRet = index;
    byte[] powerOfTwoNumbers = {1, 2, 4, 8, 16, 32, 64, (byte) 128};

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          toIntExact(
              Long.remainderUnsigned(
                  bytes_to_int(Hash.keccak256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  listSize));
      int flip = (pivot - indexRet) % listSize;
      if (flip < 0) {
        // Account for flip being negative
        flip += listSize;
      }

      int position = (indexRet < flip) ? flip : indexRet;

      Bytes positionDiv256 = int_to_bytes(position / 256, 4);
      Bytes source = Hash.keccak256(Bytes.wrap(seed, roundAsByte, positionDiv256));

      // The byte type is signed in Java, but the right shift should be fine as we just use bit 0.
      // But we can't use % in the normal way because of signedness, so we `& 1` instead.
      byte theByte = source.get(position % 256 / 8);
      byte theMask = powerOfTwoNumbers[position % 8];
      if ((theByte & theMask) != 0) {
        indexRet = flip;
      }
    }

    return indexRet;
  }

  /**
   * Return shuffled indices in a pseudorandom permutation `0...list_size-1` with ``seed`` as
   * entropy.
   *
   * <p>Utilizes 'swap or not' shuffling found in
   * https://link.springer.com/content/pdf/10.1007%2F978-3-642-32009-5_1.pdf See the 'generalized
   * domain' algorithm on page 3.
   *
   * <p>The result of this should be the same as calling get_permuted_index() for each index in the
   * list
   *
   * @param listSize The size of the list from which the element is taken. Must not exceed 2^31.
   * @param seed Initial seed value used for randomization.
   * @return The permuted arrays of indices
   */
  public static int[] shuffle(int listSize, Bytes32 seed) {

    //  In the following, great care is needed around signed and unsigned values.
    //  Note that the % (modulo) operator in Java behaves differently from the
    //  modulo operator in python:
    //    Python -1 % 13 = 12
    //    Java   -1 % 13 = -1

    // Note: this should be faster than manually creating the list in a for loop
    // https://stackoverflow.com/questions/10242380/how-can-i-generate-a-list-or-array-of-sequential-integers-in-java
    int[] indices = IntStream.rangeClosed(0, listSize - 1).toArray();

    // int[] indices = new int[listSize];
    // for (int i = 0; i < listSize; i++) {
    //   indices[i] = i;
    // }

    byte[] powerOfTwoNumbers = {1, 2, 4, 8, 16, 32, 64, (byte) 128};

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      Bytes hashBytes = Bytes.EMPTY;
      for (int i = 0; i < (listSize + 255) / 256; i++) {
        Bytes iAsBytes4 = int_to_bytes(i, 4);
        hashBytes = Bytes.wrap(hashBytes, Hash.keccak256(Bytes.wrap(seed, roundAsByte, iAsBytes4)));
      }

      // This needs to be unsigned modulo.
      int pivot =
          toIntExact(
              Long.remainderUnsigned(
                  bytes_to_int(Hash.keccak256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  listSize));

      for (int i = 0; i < listSize; i++) {

        int flip = (pivot - indices[i]) % listSize;
        if (flip < 0) {
          // Account for flip being negative
          flip += listSize;
        }

        int hashPosition = (indices[i] < flip) ? flip : indices[i];
        byte theByte = hashBytes.get(hashPosition / 8);
        byte theMask = powerOfTwoNumbers[hashPosition % 8];
        if ((theByte & theMask) != 0) {
          indices[i] = flip;
        }
      }
    }

    return indices;
  }

  /**
   * Converts byte[] (wrapped by BytesValue) to int.
   *
   * @param src byte[] (wrapped by BytesValue)
   * @param pos Index in Byte[] array
   * @return converted int
   * @throws IllegalArgumentException if pos is a negative value.
   */
  @VisibleForTesting
  public static int bytes3ToInt(Bytes src, int pos) {
    checkArgument(pos >= 0, "Expected positive pos but got %s", pos);
    return ((src.get(pos) & 0xFF) << 16)
        | ((src.get(pos + 1) & 0xFF) << 8)
        | (src.get(pos + 2) & 0xFF);
  }

  /**
   * Splits provided list into a given number of pieces pieces.
   *
   * @param values The original list of validators.
   * @param split_count The number of pieces to split the array into.
   * @return The list of validators split into split_count pieces.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#split">split
   *     - Spec v0.4</a>
   */
  public static <T> List<List<T>> split(List<T> values, int split_count) {
    checkArgument(split_count > 0, "Expected positive split_count but got %s", split_count);

    int list_length = values.size();
    List<List<T>> split_arr = new ArrayList<>(split_count);

    for (int i = 0; i < split_count; i++) {
      int startIndex = list_length * i / split_count;
      int endIndex = list_length * (i + 1) / split_count;
      List<T> new_split = new ArrayList<>();
      for (int j = startIndex; j < endIndex; j++) {
        new_split.add(values.get(j));
      }
      split_arr.add(new_split);
    }
    return split_arr;
  }

  /**
   * Checks if the numerical value provided is a power of 2.
   *
   * @param value - The number under consideration.
   * @return True if value is an exact power of 2, false otherwise.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#is_power_of_two">is_power_of_two
   *     - Spec v0.4</a>
   */
  public static boolean is_power_of_two(long value) {
    return value != 0 && ((value - 1) & value) == 0;
  }

  /**
   * Returns the beacon proposer index for the slot.
   *
   * @param state - The BeaconState under consideration.
   * @param slot - The slot to retrieve the beacon proposer index for.
   * @return The beacon proposer index for the given slot.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_beacon_proposer_index">get_beacon_proposer_index
   *     - Spec v0.4</a>
   */
  public static int get_beacon_proposer_index(BeaconState state, long slot)
      throws IllegalArgumentException {
    if (state instanceof BeaconStateWithCache
        && ((BeaconStateWithCache) state).getCurrentBeaconProposerIndex() > -1) {
      return ((BeaconStateWithCache) state).getCurrentBeaconProposerIndex();
    } else {
      List<Integer> first_committee =
          get_crosslink_committees_at_slot(state, slot).get(0).getCommittee();

      return first_committee.get(Math.toIntExact(slot % first_committee.size()));
    }
  }

  /**
   * Process a deposit from Ethereum 1.0 (and add a new validator) or tops up an existing
   * validator's balance. NOTE: This function has side-effects and mutates 'state'.
   *
   * @param state - The current BeaconState. NOTE: State will be mutated per spec logic.
   * @param deposit - The deposit information to add as a new validator or top up.
   * @throws BLSException
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#process_deposit">process_deposit
   *     - Spec v0.4</a>
   */
  public static void process_deposit(BeaconState state, Deposit deposit) {
    // Retrieve DepositInput reference from Deposit
    DepositInput depositInput = deposit.getDeposit_data().getDeposit_input();

    // Validates the proof_of_possession is the valid BLS signature for the DepositInput (pubkey and
    // withdrawal credentials).
    long domain = get_domain(state.getFork(), get_current_epoch(state), DOMAIN_DEPOSIT);
    checkArgument(
        bls_verify(
            depositInput.getPubkey(),
            depositInput.signedRoot("proof_of_possession"),
            depositInput.getProof_of_possession(),
            domain));

    // Get Pubkey, Deposit Amount, and Withdrawal Credentials from Deposit
    BLSPublicKey pubkey = depositInput.getPubkey();
    long amount = deposit.getDeposit_data().getAmount();
    Bytes32 withdrawal_credentials = depositInput.getWithdrawal_credentials();

    // Retrieve validatorRegistry and validatorBalances references.
    List<Validator> validatorRegistry = state.getValidator_registry();
    List<Long> validatorBalances = state.getValidator_balances();

    // Retrieve the list of validator's public keys from the current state.
    List<BLSPublicKey> validator_pubkeys =
        validatorRegistry.stream().map(Validator::getPubkey).collect(Collectors.toList());

    // If the pubkey isn't in the state, add a new validator to the registry.
    // Otherwise, top up the balance for the validator whose pubkey was provided.
    if (!validator_pubkeys.contains(pubkey)) {
      // We depend on our add operation appending the below objects at the same index.
      checkArgument(
          validatorRegistry.size() == validatorBalances.size(),
          "checkArgument threw and exception in process_deposit()");
      validatorRegistry.add(
          new Validator(
              pubkey,
              withdrawal_credentials,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH,
              false,
              false));
      validatorBalances.add(amount);
    } else {
      int validatorIndex = validator_pubkeys.indexOf(pubkey);
      checkArgument(
          validatorRegistry
              .get(validatorIndex)
              .getWithdrawal_credentials()
              .equals(withdrawal_credentials),
          "checkArgument threw and exception in process_deposit()");
      validatorBalances.set(validatorIndex, validatorBalances.get(validatorIndex) + amount);
    }
  }

  /**
   * TODO It may make sense to move this to {@link Fork}.
   *
   * <p>Get the domain number that represents the fork meta and signature domain.
   *
   * @param fork - The Fork to retrieve the verion for.
   * @param epoch - The epoch to retrieve the fork version for. See {@link #get_fork_version(Fork,
   *     long)}
   * @param domain_type - The domain type. See
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#signature-domains
   * @return The fork version and signature domain. This format ((fork version << 32) +
   *     SignatureDomain) is used to partition BLS signatures.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_domain">get_domain
   *     - Spec v0.4</a>
   */
  public static long get_domain(Fork fork, long epoch, int domain_type) {
    return get_fork_version(fork, epoch) * 4294967296L + domain_type;
  }

  /**
   * Return the epoch at which an activation or exit triggered in `epoch` takes effect.
   *
   * @param epoch - The epoch under consideration.
   * @return The epoch at which an activation or exit in the given `epoch` will take effect.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_delayed_activation_exit_epoch">get_delayed_activation_exit_epoch
   *     - Spec v0.4</a>
   */
  public static long get_delayed_activation_exit_epoch(long epoch) {
    return epoch + 1 + ACTIVATION_EXIT_DELAY;
  }

  /**
   * Extract the bit in bitfield at bitPosition.
   *
   * @param bitfield - The Bytes value that describes the bitfield to operate on.
   * @param bitPosition - The index.
   * @return The bit at bitPosition from the given bitfield.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_bitfield_bit">get_bitfield_bit
   *     - Spec v0.4</a>
   */
  public static int get_bitfield_bit(Bytes bitfield, int bitPosition) {
    return (bitfield.get(bitPosition / 8) >>> (bitPosition % 8)) % 2;
  }

  /**
   * Verify ``bitfield`` against the ``committee_size``.
   *
   * @param bitfield - The bitfield under consideration.
   * @param committee_size - The size of the committee associated with the bitfield.
   * @return True if the given bitfield is valid for the given committee_size, false otherwise.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#verify_bitfield">verify_bitfield
   *     - Spec v0.4</a>
   */
  public static boolean verify_bitfield(Bytes bitfield, int committee_size) {
    if (bitfield.size() != (committee_size + 7) / 8) return false;

    for (int i = committee_size; i < bitfield.size() * 8; i++) {
      if (get_bitfield_bit(bitfield, i) == 0b1) return false;
    }
    return true;
  }

  /**
   * Verify validity of ``slashable_attestation`` fields.
   *
   * @param state - The current BeaconState under consideration.
   * @param slashable_attestation - The SlashableAttestation under consideration/to be validated.
   * @return True if the given slashable attestation is valid and has a proper BLS signature, false
   *     otherwise.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#verify_slashable_attestation">verify_slashable_attestation
   *     - Spec v0.4<a/>
   */
  public static boolean verify_slashable_attestation(
      BeaconState state, SlashableAttestation slashable_attestation) {
    if (!Objects.equals(
        slashable_attestation.getCustody_bitfield(),
        Bytes.wrap(new byte[slashable_attestation.getCustody_bitfield().size()])))
      return false; // [TO BE REMOVED IN PHASE 1]

    if (slashable_attestation.getValidator_indices().size() == 0) return false;

    for (int i = 0; i < slashable_attestation.getValidator_indices().size() - 1; i++) {
      if (slashable_attestation
              .getValidator_indices()
              .get(i)
              .compareTo(slashable_attestation.getValidator_indices().get(i + 1))
          >= 0) return false;
    }

    if (!verify_bitfield(
        slashable_attestation.getCustody_bitfield(),
        slashable_attestation.getValidator_indices().size())) return false;

    if (slashable_attestation.getValidator_indices().size() > MAX_INDICES_PER_SLASHABLE_VOTE)
      return false;

    ArrayList<Long> custody_bit_0_indices = new ArrayList<>();
    ArrayList<Long> custody_bit_1_indices = new ArrayList<>();

    ListIterator<Long> it = slashable_attestation.getValidator_indices().listIterator();
    while (it.hasNext()) {
      if (get_bitfield_bit(slashable_attestation.getCustody_bitfield(), it.nextIndex()) == 0b0) {
        custody_bit_0_indices.add(it.next());
      } else {
        custody_bit_1_indices.add(it.next());
      }
    }

    ArrayList<BLSPublicKey> custody_bit_0_pubkeys = new ArrayList<>();
    for (int i = 0; i < custody_bit_0_indices.size(); i++) {
      custody_bit_0_pubkeys.add(state.getValidator_registry().get(i).getPubkey());
    }
    ArrayList<BLSPublicKey> custody_bit_1_pubkeys = new ArrayList<>();
    for (int i = 0; i < custody_bit_1_indices.size(); i++) {
      custody_bit_1_pubkeys.add(state.getValidator_registry().get(i).getPubkey());
    }

    List<BLSPublicKey> pubkeys =
        Arrays.asList(
            bls_aggregate_pubkeys(custody_bit_0_pubkeys),
            bls_aggregate_pubkeys(custody_bit_1_pubkeys));
    List<Bytes32> messages =
        Arrays.asList(
            hash_tree_root(
                new AttestationDataAndCustodyBit(slashable_attestation.getData(), false).toBytes()),
            hash_tree_root(
                new AttestationDataAndCustodyBit(slashable_attestation.getData(), true).toBytes()));
    BLSSignature signature = slashable_attestation.getAggregate_signature();
    long domain =
        get_domain(
            state.getFork(),
            slot_to_epoch(slashable_attestation.getData().getSlot()),
            DOMAIN_ATTESTATION);

    return bls_verify_multiple(pubkeys, messages, signature, domain);
  }

  /**
   * TODO It may make sense to move this to {@link Fork}.
   *
   * <p>Returns the fork version of the given epoch.
   *
   * @param fork - The Fork to retrieve the version for.
   * @param epoch - The epoch to retrieve the fork version for.
   * @return The fork version of the given epoch. (previousVersion if epoch < fork.epoch, otherwise
   *     currentVersion)
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_fork_version">get_fork_version
   *     - Spec v0.4</a>
   */
  public static long get_fork_version(Fork fork, long epoch) {
    if (epoch < fork.getEpoch()) {
      return fork.getPrevious_version();
    } else {
      return fork.getCurrent_version();
    }
  }

  /**
   * Returns the participant indices for the attestation_data and participation_bitfield.
   *
   * @param state - The BeaconState under consideration.
   * @param attestation_data - The AttestationData under consideration.
   * @param participation_bitfield - The participation bitfield under consideration.
   * @return The participant indices for the attestation_data and participation_bitfield.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_attestation_participants">get_attestation_participants
   *     - Spec v0.4</a>
   */
  public static ArrayList<Integer> get_attestation_participants(
      BeaconState state, AttestationData attestation_data, byte[] participation_bitfield)
      throws IllegalArgumentException {

    // Find the relevant committee in the list with the desired shard
    ArrayList<CrosslinkCommittee> crosslink_committees =
        BeaconStateUtil.get_crosslink_committees_at_slot(state, attestation_data.getSlot());

    // TODO: checkArgument attestation_data.shard in [shard for _, shard in crosslink_committees]

    CrosslinkCommittee crosslink_committee = null;
    for (CrosslinkCommittee curr_crosslink_committee : crosslink_committees) {
      if (curr_crosslink_committee.getShard() == attestation_data.getShard()) {
        crosslink_committee = curr_crosslink_committee;
        break;
      }
    }

    checkArgument(
        verify_bitfield(Bytes.wrap(participation_bitfield), crosslink_committee.getCommitteeSize()),
        "checkArgument threw and exception in get_attestation_participants()");

    // Find the participating attesters in the committee
    ArrayList<Integer> participants = new ArrayList<>();
    for (int i = 0; i < crosslink_committee.getCommitteeSize(); i++) {
      int participation_bit = get_bitfield_bit(Bytes.wrap(participation_bitfield), i);
      if (participation_bit == 1) {
        participants.add(crosslink_committee.getCommittee().get(i));
      }
    }
    return participants;
  }

  /**
   * Activate the validator with the given 'index'. Note that this function mutates 'state'.
   *
   * @param validator_index the validator index.
   */
  @VisibleForTesting
  public static void activate_validator(
      BeaconState state, int validator_index, boolean is_genesis) {
    state
        .getValidator_registry()
        .get(validator_index)
        .setActivation_epoch(
            is_genesis
                ? GENESIS_EPOCH
                : BeaconStateUtil.get_entry_exit_effect_epoch(
                    BeaconStateUtil.get_current_epoch(state)));
  }

  /**
   * Return the smallest integer r such that r * div >= 8.
   *
   * @param div
   */
  private static int ceil_div8(int div) {
    checkArgument(div > 0, "Expected positive div but got %s", div);
    return toIntExact(Double.valueOf(Math.ceil(8.0 / div)).longValue());
  }

  /**
   * Assumes 'attestation_data_1' is distinct from 'attestation_data_2'.
   *
   * @param attestation_data_1 - The first AttestationData to check.
   * @param attestation_data_2 - The second AttestationData to check.
   * @return True if the provided 'AttestationData' are slashable due to a 'double vote', false
   *     otherwise.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#is_double_vote">is_double_vote
   *     - Spec v0.4<a/>
   */
  public static boolean is_double_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long target_epoch_1 = slot_to_epoch(attestation_data_1.getSlot());
    long target_epoch_2 = slot_to_epoch(attestation_data_2.getSlot());
    return target_epoch_1 == target_epoch_2;
  }

  /**
   * Note: parameter order matters as this function only checks that 'attestation_data_1' surrounds
   * 'attestation_data_2'.
   *
   * @param attestation_data_1 - The first AttestationData to check.
   * @param attestation_data_2 - The second AttestationData to check.
   * @return True if the provided 'AttestationData' are slashable due to a 'surround vote', false
   *     otherwise.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#is_surround_vote">is_surround_vote
   *     - Spec v0.4</a>
   */
  public static boolean is_surround_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long source_epoch_1 = attestation_data_1.getJustified_epoch();
    long source_epoch_2 = attestation_data_2.getJustified_epoch();
    long target_epoch_1 = slot_to_epoch(attestation_data_1.getSlot());
    long target_epoch_2 = slot_to_epoch(attestation_data_2.getSlot());
    return source_epoch_1 < source_epoch_2 && target_epoch_2 < target_epoch_1;
  }

  /**
   * The largest integer 'x' such that 'x**2' is less than 'n'.
   *
   * @param n - The highest bound of x.
   * @return The largest integer 'x' such that 'x**2' is less than 'n'.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#integer_squareroot">integer_squareroot
   *     - Spec v0.4</a>
   */
  public static long integer_squareroot(long n) {
    checkArgument(n >= 0, "checkArgument threw and exception in integer_squareroot()");
    long x = n;
    long y = (x + 1) / 2L;
    while (y < x) {
      x = y;
      y = (x + n / x) / 2L;
    }
    return x;
  }

  /**
   * Convert a long value into a number of bytes, little endian.
   *
   * <p>If numBytes is more than the size of a long then the returned value is right-padded with
   * zero bytes. If numBytes is less than the size of a long then the value is truncated.
   *
   * @param value - The value to be converted to bytes.
   * @param numBytes - The number of bytes to be returned.
   * @return The value represented as the requested number of bytes.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#int_to_bytes1-int_to_bytes2-">int_to_bytes
   *     - Spec v0.4</a>
   */
  public static Bytes int_to_bytes(long value, int numBytes) {
    final int longBytes = Long.SIZE / 8;
    // TODO: akhila fix
    Bytes valueBytes = Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
    if (numBytes <= longBytes) {
      return valueBytes.slice(0, numBytes);
    } else {
      return Bytes.wrap(valueBytes, Bytes.wrap(new byte[numBytes - longBytes]));
    }
  }

  public static Bytes32 int_to_bytes32(long value) {
    return Bytes32.wrap(int_to_bytes(value, 32));
  }

  /**
   * @param bytes - The value to be converted to int.
   * @return An integer representation of the bytes value given.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#bytes_to_int">bytes_to_int
   *     - Spec v0.4</a>
   */
  public static long bytes_to_int(Bytes bytes) {
    return bytes.toLong(ByteOrder.LITTLE_ENDIAN);
  }
}
