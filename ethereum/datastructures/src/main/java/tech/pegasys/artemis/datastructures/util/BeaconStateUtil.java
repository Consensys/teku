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
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_FORK_VERSION;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_START_SHARD;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_INDEX_ROOTS_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_SLASHED_EXIT_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.MAX_INDICES_PER_SLASHABLE_VOTE;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.hash_tree_root;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.integerListHashTreeRoot;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSException;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class BeaconStateUtil {

  private static final ALogger LOG = new ALogger(BeaconStateUtil.class.getName());

  public static BeaconState get_initial_beacon_state(
      ArrayList<Deposit> initial_validator_deposits,
      UnsignedLong genesis_time,
      Eth1Data latest_eth1_data)
      throws IllegalStateException {

    ArrayList<Bytes32> latest_randao_mixes =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_RANDAO_MIXES_LENGTH, Constants.ZERO_HASH));
    ArrayList<Bytes32> latest_block_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_BLOCK_ROOTS_LENGTH, Constants.ZERO_HASH));
    ArrayList<Bytes32> latest_index_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_INDEX_ROOTS_LENGTH, Constants.ZERO_HASH));
    ArrayList<UnsignedLong> latest_penalized_balances =
        new ArrayList<>(Collections.nCopies(LATEST_SLASHED_EXIT_LENGTH, UnsignedLong.ZERO));
    ArrayList<Crosslink> latest_crosslinks = new ArrayList<>(SHARD_COUNT);

    for (int i = 0; i < SHARD_COUNT; i++) {
      latest_crosslinks.add(new Crosslink(UnsignedLong.valueOf(GENESIS_SLOT), Bytes32.ZERO));
    }

    BeaconState state =
        new BeaconState(
            // Misc
            UnsignedLong.valueOf(GENESIS_SLOT),
            genesis_time,
            new Fork(
                UnsignedLong.valueOf(GENESIS_FORK_VERSION),
                UnsignedLong.valueOf(GENESIS_FORK_VERSION),
                UnsignedLong.valueOf(GENESIS_EPOCH)),

            // Validator registry
            new ArrayList<>(),
            new ArrayList<>(),
            UnsignedLong.valueOf(GENESIS_EPOCH),

            // Randomness and committees
            latest_randao_mixes,
            UnsignedLong.valueOf(GENESIS_START_SHARD),
            UnsignedLong.valueOf(GENESIS_START_SHARD),
            UnsignedLong.valueOf(GENESIS_EPOCH),
            UnsignedLong.valueOf(GENESIS_EPOCH),
            ZERO_HASH,
            ZERO_HASH,

            // Finality
            UnsignedLong.valueOf(GENESIS_EPOCH),
            UnsignedLong.valueOf(GENESIS_EPOCH),
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(GENESIS_EPOCH),

            // Recent state
            latest_crosslinks,
            latest_block_roots,
            latest_index_roots,
            latest_penalized_balances,
            new ArrayList<>(),
            new ArrayList<>(),

            // Ethereum 1.0 chain data
            latest_eth1_data,
            new ArrayList<>(),
            UnsignedLong.valueOf(initial_validator_deposits.size()));

    // Process initial deposits
    for (Deposit validator_deposit : initial_validator_deposits) {
      DepositInput deposit_input = validator_deposit.getDeposit_data().getDeposit_input();
      process_deposit(
          state,
          deposit_input.getPubkey(),
          validator_deposit.getDeposit_data().getAmount(),
          deposit_input.getProof_of_possession(),
          deposit_input.getWithdrawal_credentials());
    }

    // Process initial activations
    int index = 0;
    for (Validator validator : state.getValidator_registry()) {
      List<UnsignedLong> balances = state.getValidator_balances();
      if (balances.get(index).compareTo(UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT)) >= 0) {
        activate_validator(state, validator, true);
      }
      index++;
    }

    List<Validator> activeValidators =
        ValidatorsUtil.get_active_validators(
            state.getValidator_registry(), UnsignedLong.valueOf(GENESIS_EPOCH));
    Bytes32 genesis_active_index_root =
        integerListHashTreeRoot(
            ValidatorsUtil.get_active_validator_indices(
                state.getValidator_registry(), UnsignedLong.valueOf(GENESIS_EPOCH)));
    for (Bytes32 root : state.getLatest_active_index_roots()) {
      root = genesis_active_index_root;
    }
    state.setCurrent_shuffling_seed(generate_seed(state, UnsignedLong.valueOf(GENESIS_EPOCH)));

    return state;
  }

  /**
   * Return the list of `(committee, shard)` tuples for the `slot`.
   *
   * <p>Note: There are two possible shufflings for crosslink committees for a ``slot`` in the next
   * epoch -- with and without a `registry_change`
   *
   * @param state The beacon state under consideration.
   * @param slot The slot number.
   * @param registry_change True if we are considering a registry change.
   * @return
   */
  public static ArrayList<CrosslinkCommittee> get_crosslink_committees_at_slot(
      BeaconState state, UnsignedLong slot, boolean registry_change)
      throws IllegalArgumentException {
    UnsignedLong epoch = slot_to_epoch(slot);
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong next_epoch = get_next_epoch(state);

    checkArgument(
        previous_epoch.compareTo(epoch) <= 0 && epoch.compareTo(next_epoch) <= 0,
        "checkArgument threw and exception in get_crosslink_committees_at_slot()");

    UnsignedLong committees_per_epoch = UnsignedLong.ZERO;
    Bytes32 seed = Bytes32.ZERO;
    UnsignedLong shuffling_epoch = UnsignedLong.ZERO;
    UnsignedLong shuffling_start_shard = UnsignedLong.ZERO;

    if (epoch.compareTo(current_epoch) == 0) {

      committees_per_epoch = get_current_epoch_committee_count(state);
      seed = state.getCurrent_shuffling_seed();
      shuffling_epoch = state.getCurrent_shuffling_epoch();
      shuffling_start_shard = state.getCurrent_shuffling_start_shard();

    } else if (epoch.compareTo(previous_epoch) == 0) {

      committees_per_epoch = get_previous_epoch_committee_count(state);
      seed = state.getPrevious_shuffling_seed();
      shuffling_epoch = state.getPrevious_shuffling_epoch();
      shuffling_start_shard = state.getPrevious_shuffling_start_shard();

    } else if (epoch.compareTo(next_epoch) == 0) {

      UnsignedLong current_committees_per_epoch = get_current_epoch_committee_count(state);
      committees_per_epoch = get_next_epoch_committee_count(state);
      shuffling_epoch = next_epoch;
      UnsignedLong epochs_since_last_registry_update =
          current_epoch.minus(state.getValidator_registry_update_epoch());
      if (registry_change) {
        seed = generate_seed(state, next_epoch);
        shuffling_start_shard =
            state
                .getCurrent_shuffling_start_shard()
                .plus(current_committees_per_epoch)
                .mod(UnsignedLong.valueOf(Constants.SHARD_COUNT));
      } else if (epochs_since_last_registry_update.compareTo(UnsignedLong.ONE) > 0
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
    UnsignedLong offset = slot.mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong committees_per_slot =
        committees_per_epoch.dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong slot_start_shard =
        shuffling_start_shard
            .plus(committees_per_slot)
            .times(offset)
            .mod(UnsignedLong.valueOf(Constants.SHARD_COUNT));

    ArrayList<CrosslinkCommittee> crosslink_committees_at_slot = new ArrayList<>();
    for (long i = 0; i < committees_per_slot.longValue(); i++) {
      CrosslinkCommittee committee =
          new CrosslinkCommittee(
              committees_per_slot.times(offset).plus(UnsignedLong.valueOf(i)),
              shuffling.get(toIntExact(slot_start_shard.longValue() + i) % Constants.SHARD_COUNT));
      crosslink_committees_at_slot.add(committee);
    }
    return crosslink_committees_at_slot;
  }

  /** This is a wrapper that defaults `registry_change` to false when it is not provided */
  public static ArrayList<CrosslinkCommittee> get_crosslink_committees_at_slot(
      BeaconState state, UnsignedLong slot) throws IllegalArgumentException {
    return get_crosslink_committees_at_slot(state, slot, false);
  }

  /*
   * TODO: Note from spec - Note: this definition and the next few definitions
   * make heavy use of repetitive computing. Production implementations are
   * expected to appropriately use caching/memoization to avoid redoing work.
   */

  private static UnsignedLong get_previous_epoch_committee_count(BeaconState state) {
    List<Integer> previous_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getPrevious_shuffling_epoch());
    return get_epoch_committee_count(UnsignedLong.valueOf(previous_active_validators.size()));
  }

  /**
   * returns the number of crosslink committees active in this epoch
   *
   * @param state
   * @return
   */
  public static UnsignedLong get_current_epoch_committee_count(BeaconState state) {
    List<Integer> current_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getCurrent_shuffling_epoch());
    return get_epoch_committee_count(UnsignedLong.valueOf(current_active_validators.size()));
  }

  private static UnsignedLong get_next_epoch_committee_count(BeaconState state) {
    List<Integer> next_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), get_current_epoch(state).plus(UnsignedLong.ONE));
    return get_epoch_committee_count(UnsignedLong.valueOf(next_active_validators.size()));
  }

  public static Bytes32 generate_seed(BeaconState state, UnsignedLong epoch)
      throws IllegalArgumentException {
    Bytes32 randao_mix =
        get_randao_mix(state, epoch.minus(UnsignedLong.valueOf(Constants.MIN_SEED_LOOKAHEAD)));
    Bytes32 index_root = get_active_index_root(state, epoch);
    Bytes32 epochBytes = int_to_bytes32(epoch.longValue());
    return Hash.keccak256(Bytes.wrap(randao_mix, index_root, epochBytes));
  }

  public static Bytes32 get_active_index_root(BeaconState state, UnsignedLong epoch) {
    checkArgument(
        // Since we're using UnsignedLong here, we can't subtract
        // LATEST_INDEX_ROOTS_LENGTH
        get_current_epoch(state)
                .plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY))
                .compareTo(epoch.plus(UnsignedLong.valueOf(LATEST_INDEX_ROOTS_LENGTH)))
            < 0,
        "checkArgument threw and exception in get_active_indesx_root()");
    checkArgument(
        epoch.compareTo(get_current_epoch(state).plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY)))
            <= 0,
        "checkArgument threw and exception in get_active_index_root()");

    List<Bytes32> index_roots = state.getLatest_active_index_roots();
    int index = epoch.mod(UnsignedLong.valueOf(LATEST_INDEX_ROOTS_LENGTH)).intValue();
    return state.getLatest_active_index_roots().get(index);
  }

  public static Bytes32 getShard_block_root(BeaconState state, UnsignedLong shard) {
    return state.getLatest_crosslinks().get(toIntExact(shard.longValue())).getCrosslink_data_root();
  }

  /**
   * return the min(validator balance, MAX_DEPOSIT) for a given validator index
   *
   * @param state
   * @param index
   * @return
   */
  public static UnsignedLong get_effective_balance(BeaconState state, int index) {
    return min(
        state.getValidator_balances().get(index),
        UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT));
  }

  /**
   * return the min(validator balance, MAX_DEPOSIT) for a given validator
   *
   * @param state
   * @param record
   * @return
   */
  public static UnsignedLong get_effective_balance(BeaconState state, Validator record) {
    int index = state.getValidator_registry().indexOf(record);
    return get_effective_balance(state, index);
  }

  /**
   * calculate the total balance from the previous epoch
   *
   * @param state
   * @return
   */
  public static UnsignedLong previous_total_balance(BeaconState state) {
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);
    List<Integer> previous_active_validators =
        ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), previous_epoch);
    return get_total_balance(state, previous_active_validators);
  }

  /**
   * Adds and returns the effective balances for the validators in the given CrossLinkCommittee.
   *
   * @param state - The current BeaconState.
   * @param crosslink_committee - The CrosslinkCommittee with the committee of validators to get the
   *     total balance for.
   * @return The combined effective balance of the list of validators.
   */
  public static UnsignedLong get_total_balance(
      BeaconState state, CrosslinkCommittee crosslink_committee) {
    return get_total_balance(state, crosslink_committee.getCommittee());
  }

  /**
   * Adds and returns the effective balances for the validators referenced by the given indices.
   *
   * @param state - The current BeaconState.
   * @param validator_indices - A list of validator indices to get the total balance for.
   * @return The combined effective balance of the list of validators.
   */
  public static UnsignedLong get_total_balance(BeaconState state, List<Integer> validator_indices) {
    UnsignedLong total_balance = UnsignedLong.ZERO;
    for (Integer index : validator_indices) {
      total_balance = total_balance.plus(BeaconStateUtil.get_effective_balance(state, index));
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
  public static UnsignedLong slot_to_epoch(UnsignedLong slot) {
    return slot.dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
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
  public static UnsignedLong get_previous_epoch(BeaconState state) {
    UnsignedLong current_epoch_minus_one = get_current_epoch(state).minus(UnsignedLong.ONE);
    UnsignedLong genesis_epoch = UnsignedLong.valueOf(GENESIS_EPOCH);
    return current_epoch_minus_one.compareTo(genesis_epoch) >= 0
        ? current_epoch_minus_one
        : genesis_epoch;
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
  public static UnsignedLong get_current_epoch(BeaconState state) {
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
  public static UnsignedLong get_next_epoch(BeaconState state) {
    return get_current_epoch(state).plus(UnsignedLong.ONE);
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
  public static UnsignedLong get_epoch_start_slot(UnsignedLong epoch) {
    return epoch.times(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
  }

  /**
   * An entry or exit triggered in the ``epoch`` given by the input takes effect at the epoch given
   * by the output.
   *
   * @param epoch
   */
  public static UnsignedLong get_entry_exit_effect_epoch(UnsignedLong epoch) {
    return epoch.plus(UnsignedLong.ONE).plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY));
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

    UnsignedLong exit_epoch = get_entry_exit_effect_epoch(get_current_epoch(state));
    // The following updates only occur if not previous exited
    if (validator.getExit_epoch().compareTo(exit_epoch) <= 0) {
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
    exit_validator(state, index);
    Validator validator = state.getValidator_registry().get(index);
    state
        .getLatest_slashed_balances()
        .set(
            get_current_epoch(state).intValue() % LATEST_SLASHED_EXIT_LENGTH,
            state
                .getLatest_slashed_balances()
                .get(get_current_epoch(state).intValue() % LATEST_SLASHED_EXIT_LENGTH)
                .plus(get_effective_balance(state, index)));

    int whistleblower_index = get_beacon_proposer_index(state, state.getSlot());
    UnsignedLong whistleblower_reward =
        get_effective_balance(state, index)
            .dividedBy(UnsignedLong.valueOf(WHISTLEBLOWER_REWARD_QUOTIENT));
    state
        .getValidator_balances()
        .set(
            whistleblower_index,
            state.getValidator_balances().get(whistleblower_index).plus(whistleblower_reward));
    state
        .getValidator_balances()
        .set(index, state.getValidator_balances().get(index).minus(whistleblower_reward));

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
        get_current_epoch(state)
            .plus(UnsignedLong.valueOf(Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY)));
  }

  /** Return the randao mix at a recent ``epoch``. */
  public static Bytes32 get_randao_mix(BeaconState state, UnsignedLong epoch) {
    checkArgument(
        // If we're going to use UnsignedLongs then we can't subtract
        // LATEST_RANDAO_MIXES_LENGTH
        // here
        get_current_epoch(state)
                .compareTo(epoch.plus(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
            < 0,
        "checkArgument threw and exception in get_randao_mix()");
    checkArgument(
        epoch.compareTo(get_current_epoch(state)) <= 0,
        "checkArgument threw and exception in get_randao_mix()");
    UnsignedLong index = epoch.mod(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH));
    List<Bytes32> randao_mixes = state.getLatest_randao_mixes();
    return randao_mixes.get(index.intValue());
  }

  // Return the block root at a recent ``slot``.
  public static Bytes32 get_block_root(BeaconState state, UnsignedLong slot) {
    checkArgument(
        state
                .getSlot()
                .compareTo(slot.plus(UnsignedLong.valueOf(Constants.LATEST_BLOCK_ROOTS_LENGTH)))
            <= 0);
    checkArgument(
        slot.compareTo(state.getSlot()) < 0,
        "checkArgument threw and exception in get_block_root()");
    // Todo: Remove .intValue() as soon as our list wrapper supports unsigned longs
    return state
        .getLatest_block_roots()
        .get(slot.mod(UnsignedLong.valueOf(Constants.LATEST_BLOCK_ROOTS_LENGTH)).intValue());
  }

  /*
   * @param values
   *
   * @return The merkle root.
   */
  public static Bytes32 merkle_root(List<Bytes32> list) throws IllegalStateException {
    // https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#merkle_root
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
   * Return the number of committees in one epoch.
   *
   * @param active_validator_count
   * @return
   */
  public static UnsignedLong get_epoch_committee_count(UnsignedLong active_validator_count) {

    return max(
            UnsignedLong.ONE,
            min(
                UnsignedLong.valueOf(Constants.SHARD_COUNT)
                    .dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH)),
                active_validator_count
                    .dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
                    .dividedBy(UnsignedLong.valueOf(Constants.TARGET_COMMITTEE_SIZE))))
        .times(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
  }

  /**
   * Shuffles ``validators`` into shard committees using ``seed`` as entropy.
   *
   * @param seed
   * @param validators
   * @param epoch
   * @return
   */
  public static List<List<Integer>> get_shuffling(
      Bytes32 seed, List<Validator> validators, UnsignedLong epoch) throws IllegalStateException {

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

    int committeesPerEpoch = get_epoch_committee_count(UnsignedLong.valueOf(length)).intValue();

    return split(shuffled_active_validator_indices, committeesPerEpoch);
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
     * Using UnsignedLong doesn't help us as some quantities can legitimately be negative.
     */

    int indexRet = index;
    byte[] powerOfTwoNumbers = {1, 2, 4, 8, 16, 32, 64, (byte) 128};

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          (int)
              Long.remainderUnsigned(
                  bytes_to_int(Hash.keccak256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  listSize);
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

    //  Using UnsignedLong doesn't help us as some quantities can legitimately be negative.

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
          (int)
              Long.remainderUnsigned(
                  bytes_to_int(Hash.keccak256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  listSize);

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

  /** Returns true if the operand is an exact power of two */
  public static boolean is_power_of_two(UnsignedLong value) {
    long longValue = value.longValue();
    return longValue != 0 && ((longValue - 1) & longValue) == 0;
  }

  /**
   * Returns the beacon proposer index for the 'slot'.
   *
   * @param state
   * @param slot
   * @return
   */
  public static int get_beacon_proposer_index(BeaconState state, UnsignedLong slot)
      throws IllegalArgumentException {
    List<Integer> first_committee =
        get_crosslink_committees_at_slot(state, slot).get(0).getCommittee();
    // TODO: replace slot.intValue() with an UnsignedLong value
    return first_committee.get(slot.intValue() % first_committee.size());
  }

  /**
   * Process a deposit from Ethereum 1.0 (and add a new validator) or tops up an existing
   * validator's balance. NOTE: This function has side-effects and mutates 'state'.
   *
   * @param state - The current BeaconState. NOTE: State will be mutated per spec logic.
   * @param pubkey - The validator's public key.
   * @param amount - The amount to add to the validator's balance (in Gwei).
   * @param proof_of_possession - The validator's proof of posession
   * @param withdrawal_credentials - The withdrawal credentials for the deposit to be processed.
   * @throws BLSException
   */
  public static void process_deposit(
      BeaconState state,
      BLSPublicKey pubkey,
      UnsignedLong amount,
      BLSSignature proof_of_possession,
      Bytes32 withdrawal_credentials) {

    // Retrieve validatorRegistry and validatorBalances references.
    List<Validator> validatorRegistry = state.getValidator_registry();
    List<UnsignedLong> validatorBalances = state.getValidator_balances();

    // Verify that the proof of posession is valid before processing the deposit.
    checkArgument(
        validate_proof_of_possession(state, pubkey, proof_of_possession, withdrawal_credentials),
        "checkArgument threw and exception in process_deposit()");

    // Retrieve the list of validator's public keys from the current state.
    List<BLSPublicKey> validator_pubkeys =
        validatorRegistry.stream()
            .map(validator -> validator.getPubkey())
            .collect(Collectors.toList());

    // If the provided pubkey isn't in the state, add a new validator to the
    // registry.
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
      validatorBalances.set(validatorIndex, validatorBalances.get(validatorIndex).plus(amount));
    }
  }

  /**
   * Return the min of two UnsignedLong values
   *
   * @param value1
   * @param value2
   * @return
   */
  @VisibleForTesting
  public static UnsignedLong min(UnsignedLong value1, UnsignedLong value2) {
    if (value1.compareTo(value2) <= 0) {
      return value1;
    } else {
      return value2;
    }
  }

  /**
   * Return the max of two UnsignedLong values
   *
   * @param value1
   * @param value2
   * @return
   */
  public static UnsignedLong max(UnsignedLong value1, UnsignedLong value2) {
    if (value1.compareTo(value2) >= 0) {
      return value1;
    } else {
      return value2;
    }
  }

  /**
   * Validates the supplied proof_of_possession is the valid BLS signature for the DepositInput
   * (pubkey and withdrawal credentials).
   *
   * @param state - The current Beaconstate.
   * @param pubkey - The pubkey for the current deposit data to verify.
   * @param proof_of_possession - The BLS signature proof of possession to validate.
   * @param withdrawal_credentials - The withdrawal credentials for the current deposit data to
   *     verify.
   * @return True if the BLS signature is a valid proof of posession, false otherwise.
   * @throws BLSException
   */
  static boolean validate_proof_of_possession(
      BeaconState state,
      BLSPublicKey pubkey,
      BLSSignature proof_of_possession,
      Bytes32 withdrawal_credentials) {
    // Create a new DepositInput with the pubkey and withdrawal_credentials.
    // The signature is empty as per spec, since it is verified in the bls_verify subsequent step.
    DepositInput proof_of_possession_data =
        new DepositInput(pubkey, withdrawal_credentials, Constants.EMPTY_SIGNATURE);

    UnsignedLong domain = get_domain(state.getFork(), get_current_epoch(state), DOMAIN_DEPOSIT);
    Bytes32 message = Bytes32.ZERO;
    try {
      message = hash_tree_root(proof_of_possession_data.toBytes());
    } catch (Exception e) {
      LOG.log(
          Level.FATAL,
          "validate_proof_of_possession(): Error calculating the hash_tree_root(proof_of_possession). "
              + e);
    }
    return bls_verify(pubkey, message, proof_of_possession, domain);
  }

  /**
   * TODO It seems to make sense to move this to {@link Fork}.
   *
   * <p>Get the domain number that represents the fork meta and signature domain.
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#get_domain
   *
   * @param fork - The Fork to retrieve the verion for.
   * @param epoch - The epoch to retrieve the fork version for. See {@link
   *     #get_fork_version(Fork,UnsignedLong)}
   * @param domain_type - The domain type. See
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#signature-domains
   * @return The fork version and signature domain. This format ((fork version << 32) +
   *     SignatureDomain) is used to partition BLS signatures.
   */
  public static UnsignedLong get_domain(Fork fork, UnsignedLong epoch, int domain_type) {
    return get_fork_version(fork, epoch)
        .times(UnsignedLong.valueOf((long) Math.pow(2, 32)))
        .plus(UnsignedLong.valueOf(domain_type));
  }

  /**
   * Extract the bit in ``bitfield`` at position ``i``.
   *
   * @param bitfield - The Bytes value that describes the bitfield to operate on.
   * @param bitPosition - The index.
   */
  public static int get_bitfield_bit(Bytes bitfield, int bitPosition) {
    return (bitfield.get(bitPosition / 8) >>> (bitPosition % 8)) % 2;
  }

  /**
   * Verify ``bitfield`` against the ``committee_size``.
   *
   * @param bitfield
   * @param committee_size
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
   * @param state
   * @param slashable_attestation
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

    ArrayList<UnsignedLong> custody_bit_0_indices = new ArrayList<>();
    ArrayList<UnsignedLong> custody_bit_1_indices = new ArrayList<>();

    ListIterator<UnsignedLong> it = slashable_attestation.getValidator_indices().listIterator();
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
    UnsignedLong domain =
        get_domain(
            state.getFork(),
            slot_to_epoch(slashable_attestation.getData().getSlot()),
            DOMAIN_ATTESTATION);

    return bls_verify_multiple(pubkeys, messages, signature, domain);
  }

  /**
   * TODO It seems to make sense to move this to {@link Fork}.
   *
   * <p>Returns the fork version of the given epoch.
   *
   * @param fork - The Fork to retrieve the version for.
   * @param epoch - The epoch to retrieve the fork version for.
   * @return The fork version of the given epoch. (previousVersion if epoch < fork.epoch, otherwise
   *     currentVersion)
   */
  public static UnsignedLong get_fork_version(Fork fork, UnsignedLong epoch) {
    if (epoch.compareTo(fork.getEpoch()) < 0) {
      return fork.getPrevious_version();
    } else {
      return fork.getCurrent_version();
    }
  }

  /**
   * Returns the participant indices at for the 'attestation_data' and 'participation_bitfield'.
   *
   * @param state
   * @param attestation_data
   * @param participation_bitfield
   * @return
   */
  public static ArrayList<Integer> get_attestation_participants(
      BeaconState state, AttestationData attestation_data, byte[] participation_bitfield)
      throws IllegalArgumentException {
    // Find the relevant committee

    ArrayList<CrosslinkCommittee> crosslink_committees =
        BeaconStateUtil.get_crosslink_committees_at_slot(state, attestation_data.getSlot());

    // TODO: checkArgument attestation_data.shard in [shard for _, shard in crosslink_committees]

    CrosslinkCommittee crosslink_committee = null;
    for (CrosslinkCommittee curr_crosslink_committee : crosslink_committees) {
      if (curr_crosslink_committee.getShard().equals(attestation_data.getShard())) {
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
   * @param validator the validator.
   */
  @VisibleForTesting
  public static void activate_validator(
      BeaconState state, Validator validator, boolean is_genesis) {
    validator.setActivation_epoch(
        is_genesis
            ? UnsignedLong.valueOf(GENESIS_EPOCH)
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
    return (int) Math.ceil(8.0 / div);
  }

  /**
   * Assumes 'attestation_data_1' is distinct from 'attestation_data_2'.
   *
   * @param attestation_data_1
   * @param attestation_data_2
   * @return True if the provided 'AttestationData' are slashable due to a 'double vote'.
   */
  public static boolean is_double_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    UnsignedLong target_epoch_1 =
        attestation_data_1.getSlot().dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong target_epoch_2 =
        attestation_data_2.getSlot().dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    return target_epoch_1.compareTo(target_epoch_2) == 0;
  }

  /**
   * Note: parameter order matters as this function only checks that 'attestation_data_1' surrounds
   * 'attestation_data_2'.
   *
   * @param attestation_data_1
   * @param attestation_data_2
   * @return True if the provided 'AttestationData' are slashable due to a 'surround vote'.
   */
  public static boolean is_surround_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long source_epoch_1 = attestation_data_1.getJustified_epoch().longValue() / SLOTS_PER_EPOCH;
    long source_epoch_2 = attestation_data_2.getJustified_epoch().longValue() / SLOTS_PER_EPOCH;
    UnsignedLong target_epoch_1 =
        attestation_data_1.getSlot().dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong target_epoch_2 =
        attestation_data_2.getSlot().dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    return source_epoch_1 < source_epoch_2
        && (UnsignedLong.valueOf(source_epoch_2 + 1).compareTo(target_epoch_2) == 0)
        && target_epoch_2.compareTo(target_epoch_1) < 0;
  }

  /**
   * The largest integer 'x' such that 'x**2' is less than 'n'.
   *
   * @param n highest bound of x.
   * @return x
   */
  public static UnsignedLong integer_squareroot(UnsignedLong n) {
    checkArgument(
        n.compareTo(UnsignedLong.ZERO) >= 0,
        "checkArgument threw and exception in integer_squareroot()");
    UnsignedLong TWO = UnsignedLong.valueOf(2L);
    UnsignedLong x = n;
    UnsignedLong y = x.plus(UnsignedLong.ONE).dividedBy(TWO);
    while (y.compareTo(x) < 0) {
      x = y;
      y = x.plus(n.dividedBy(x)).dividedBy(TWO);
    }
    return x;
  }

  /**
   * Convert a long value into a number of bytes, little endian.
   *
   * <p>If numBytes is more than the size of a long then the returned value is right-padded with
   * zero bytes. If numBytes is less than the size of a long then the value is truncated.
   *
   * @param value The value to be converted to bytes.
   * @param numBytes The number of bytes to be returned.
   * @return The requested number of bytes.
   */
  public static Bytes int_to_bytes(long value, int numBytes) {
    final int longBytes = Long.SIZE / 8;
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

  public static Bytes32 int_to_bytes32(UnsignedLong value) {
    return int_to_bytes32(value.longValue());
  }

  public static long bytes_to_int(Bytes bytes) {
    return bytes.toLong(ByteOrder.LITTLE_ENDIAN);
  }
}
