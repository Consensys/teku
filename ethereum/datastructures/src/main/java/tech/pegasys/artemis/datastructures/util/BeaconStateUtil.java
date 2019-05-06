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
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import net.consensys.cava.ssz.SSZ;
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
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class BeaconStateUtil {

  private static final ALogger LOG = new ALogger(BeaconStateUtil.class.getName());

  /**
   * Get the genesis BeaconState.
   *
   * @param state
   * @param genesis_validator_deposits
   * @param genesis_time
   * @param genesis_eth1_data
   * @return
   * @throws IllegalStateException
   */
  public static BeaconStateWithCache get_genesis_beacon_state(
      BeaconStateWithCache state,
      ArrayList<Deposit> genesis_validator_deposits,
      UnsignedLong genesis_time,
      Eth1Data genesis_eth1_data)
      throws IllegalStateException {

    // Process initial deposits
    for (Deposit deposit : genesis_validator_deposits) {
      process_deposit(state, deposit);
    }

    // Process initial activations
    for (int validator_index = 0;
        validator_index < state.getValidator_registry().size();
        validator_index++) {
      List<UnsignedLong> balances = state.getValidator_balances();
      if (balances.get(validator_index).compareTo(UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT)) >= 0) {
        activate_validator(state, validator_index, true);
      }
    }

    List<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), UnsignedLong.valueOf(GENESIS_EPOCH));
    Bytes32 genesis_active_index_root =
        HashTreeUtil.hash_tree_root(
            SSZTypes.LIST_OF_BASIC,
            active_validator_indices.stream()
                .map(item -> SSZ.encodeUInt64(item))
                .collect(Collectors.toList()));
    for (int index = 0; index < state.getLatest_active_index_roots().size(); index++) {
      state.getLatest_active_index_roots().set(index, genesis_active_index_root);
    }
    state.setCurrent_shuffling_seed(generate_seed(state, UnsignedLong.valueOf(GENESIS_EPOCH)));

    return state;
  }

  /**
   * Process a deposit from Ethereum 1.0. Note that this function mutates ``state``.
   *
   * @param state
   * @param deposit
   */
  public static void process_deposit(BeaconState state, Deposit deposit) {
    DepositInput deposit_input = deposit.getDeposit_data().getDeposit_input();

    //   Should equal 8 bytes for deposit_data.amount +
    //                8 bytes for deposit_data.timestamp +
    //                176 bytes for deposit_data.deposit_input
    //   It should match the deposit_data in the eth1.0 deposit contract
    Bytes serialized_deposit_data = deposit.getDeposit_data().toBytes();

    // Deposits must be processed in order
    checkArgument(
        Objects.equals(state.getDeposit_index(), deposit.getIndex()), "Deposits not in order");

    // Verify the Merkle branch
    /*checkArgument(
    verify_merkle_branch(
        Hash.keccak256(serialized_deposit_data),
        deposit.getProof(),
        DEPOSIT_CONTRACT_TREE_DEPTH,
        toIntExact(deposit.getIndex().longValue()),
        state.getLatest_eth1_data().getDeposit_root()),
    "Merkle branch is not valid");*/

    //  Increment the next deposit index we are expecting. Note that this
    //  needs to be done here because while the deposit contract will never
    //  create an invalid Merkle branch, it may admit an invalid deposit
    //  object, and we need to be able to skip over it
    state.setDeposit_index(state.getDeposit_index().plus(UnsignedLong.ONE));

    List<BLSPublicKey> validator_pubkeys =
        state.getValidator_registry().stream()
            .map(Validator::getPubkey)
            .collect(Collectors.toList());

    BLSPublicKey pubkey = deposit_input.getPubkey();

    UnsignedLong amount = deposit.getDeposit_data().getAmount();
    Bytes32 withdrawal_credentials = deposit_input.getWithdrawal_credentials();

    if (!validator_pubkeys.contains(pubkey)) {
      // Verify the proof of possession
      boolean proof_is_valid =
          bls_verify(
              pubkey,
              deposit_input.signed_root("proof_of_possession"),
              deposit_input.getProof_of_possession(),
              get_domain(state.getFork(), get_current_epoch(state), DOMAIN_DEPOSIT));
      if (!proof_is_valid) {
        return;
      }

      // Add new validator
      Validator validator =
          new Validator(
              pubkey,
              withdrawal_credentials,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH,
              false,
              false);

      // Note: In phase 2 registry indices that have been withdrawn for a long time will be
      // recycled.
      state.getValidator_registry().add(validator);
      state.getValidator_balances().add(amount);
    } else {
      // Increase balance by deposit amount
      int index = validator_pubkeys.indexOf(pubkey);
      state
          .getValidator_balances()
          .set(index, state.getValidator_balances().get(index).plus(amount));
    }
  }

  /**
   * Verify that the given ``leaf`` is on the merkle branch ``proof`` starting with the given
   * ``root``.
   *
   * @param leaf
   * @param proof
   * @param depth
   * @param index
   * @param root
   * @return
   */
  private static boolean verify_merkle_branch(
      Bytes32 leaf, List<Bytes32> proof, int depth, int index, Bytes32 root) {
    Bytes32 value = leaf;
    for (int i = 0; i < depth; i++) {
      if (index / Math.pow(2, i) % 2 == 0) {
        value = Hash.keccak256(Bytes.concatenate(proof.get(i), value));
      } else {
        value = Hash.keccak256(Bytes.concatenate(value, proof.get(i)));
      }
    }
    return value.equals(root);
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
      BeaconState state, UnsignedLong slot, boolean registry_change)
      throws IllegalArgumentException {
    UnsignedLong epoch = slot_to_epoch(slot);
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong next_epoch = get_next_epoch(state);

    checkArgument(
        previous_epoch.compareTo(epoch) <= 0 && epoch.compareTo(next_epoch) <= 0,
        "get_crosslink_committees_at_slot: epoch out of range");

    UnsignedLong committees_per_epoch = UnsignedLong.ZERO;
    UnsignedLong current_committees_per_epoch = UnsignedLong.ZERO;
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

      UnsignedLong epochs_since_last_registry_update =
          current_epoch.minus(state.getValidator_registry_update_epoch());
      if (registry_change) {
        committees_per_epoch = get_next_epoch_committee_count(state);
        seed = generate_seed(state, next_epoch);
        shuffling_epoch = next_epoch;
        current_committees_per_epoch = get_current_epoch_committee_count(state);
        shuffling_start_shard =
            state
                .getCurrent_shuffling_start_shard()
                .plus(current_committees_per_epoch)
                .mod(UnsignedLong.valueOf(SHARD_COUNT));
      } else if (epochs_since_last_registry_update.compareTo(UnsignedLong.ONE) > 0
          && is_power_of_two(epochs_since_last_registry_update)) {
        committees_per_epoch = get_next_epoch_committee_count(state);
        seed = generate_seed(state, next_epoch);
        shuffling_epoch = next_epoch;
        shuffling_start_shard = state.getCurrent_shuffling_start_shard();
      } else {
        committees_per_epoch = get_current_epoch_committee_count(state);
        seed = state.getCurrent_shuffling_seed();
        shuffling_epoch = state.getCurrent_shuffling_epoch();
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
            .plus(committees_per_slot.times(offset))
            .mod(UnsignedLong.valueOf(Constants.SHARD_COUNT));

    ArrayList<CrosslinkCommittee> crosslink_committees_at_slot = new ArrayList<>();
    for (long i = 0; i < committees_per_slot.longValue(); i++) {
      CrosslinkCommittee committee =
          new CrosslinkCommittee(
              slot_start_shard.plus(UnsignedLong.ONE).mod(UnsignedLong.valueOf(SHARD_COUNT)),
              shuffling.get(committees_per_slot.mod(UnsignedLong.valueOf(SHARD_COUNT)).intValue()));
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

  /**
   * Return the number of committees in the previous epoch of the given state.
   *
   * @param state - The state under consideration.
   * @return The number of committees in the previous epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_previous_epoch_committee_count">get_previous_epoch_committee_count
   *     - Spec v0.4</a>
   */
  private static UnsignedLong get_previous_epoch_committee_count(BeaconState state) {
    List<Integer> previous_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getPrevious_shuffling_epoch());
    return get_epoch_committee_count(UnsignedLong.valueOf(previous_active_validators.size()));
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
  public static UnsignedLong get_current_epoch_committee_count(BeaconState state) {
    List<Integer> current_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getCurrent_shuffling_epoch());
    return get_epoch_committee_count(UnsignedLong.valueOf(current_active_validators.size()));
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
  private static UnsignedLong get_next_epoch_committee_count(BeaconState state) {
    List<Integer> next_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), get_current_epoch(state).plus(UnsignedLong.ONE));
    return get_epoch_committee_count(UnsignedLong.valueOf(next_active_validators.size()));
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
  public static Bytes32 generate_seed(BeaconState state, UnsignedLong epoch)
      throws IllegalArgumentException {
    Bytes32 randao_mix =
        get_randao_mix(state, epoch.minus(UnsignedLong.valueOf(Constants.MIN_SEED_LOOKAHEAD)));
    Bytes32 index_root = get_active_index_root(state, epoch);
    Bytes32 epochBytes = int_to_bytes32(epoch.longValue());
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
  public static Bytes32 get_active_index_root(BeaconState state, UnsignedLong epoch) {
    checkArgument(
        get_current_epoch(state)
                .minus(UnsignedLong.valueOf(LATEST_ACTIVE_INDEX_ROOTS_LENGTH))
                .plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY))
                .compareTo(epoch)
            < 0,
        "get_active_index_root: first check");
    checkArgument(
        epoch.compareTo(get_current_epoch(state).plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY)))
            <= 0,
        "get_active_index_root: second check");

    int index = epoch.mod(UnsignedLong.valueOf(LATEST_ACTIVE_INDEX_ROOTS_LENGTH)).intValue();
    return state.getLatest_active_index_roots().get(index);
  }

  public static Bytes32 getShard_block_root(BeaconState state, UnsignedLong shard) {
    return state
        .getLatest_crosslinks()
        .get(toIntExact(shard.longValue()) % Constants.SHARD_COUNT)
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
  public static UnsignedLong get_effective_balance(BeaconState state, int index) {
    return min(
        state.getValidator_balances().get(index),
        UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT));
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
   * Adds and returns the effective balances for the validators referenced by the given indices.
   *
   * @param state - The current BeaconState.
   * @param validators - A list of validator indices to get the total balance for.
   * @return The combined effective balance of the list of validators.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_total_balance">get_total_balance
   *     - Spec v0.4</a>
   */
  public static UnsignedLong get_total_balance(BeaconState state, List<Integer> validators) {
    UnsignedLong total_balance = UnsignedLong.ZERO;
    for (Integer index : validators) {
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
    return current_epoch_minus_one;
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
   * Return the starting slot of the given epoch.
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
   * Initiate exit for the validator of the given 'index'. Note that this function mutates 'state'.
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

    UnsignedLong delayed_activation_exit_epoch =
        get_delayed_activation_exit_epoch(get_current_epoch(state));
    // The following updates only occur if not previous exited
    if (validator.getExit_epoch().compareTo(delayed_activation_exit_epoch) > 0) {
      validator.setExit_epoch(delayed_activation_exit_epoch);
    }
  }

  /**
   * Slash the validator with index ``index``. Note that this function mutates ``state``.
   *
   * @param state - The current BeaconState. NOTE: State will be mutated per spec logic.
   * @param index - The index of the validator that will be penalized.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#penalize_validator">
   *     spec</a>
   */
  public static void slash_validator(BeaconState state, int index) {
    Validator validator = state.getValidator_registry().get(index);
    checkArgument(
        state.getSlot().compareTo(get_epoch_start_slot(validator.getWithdrawable_epoch()))
            < 0); // [TO BE
    // REMOVED IN PHASE 2]
    exit_validator(state, index);
    int slashed_balances_index = get_current_epoch(state).intValue() % LATEST_SLASHED_EXIT_LENGTH;
    state
        .getLatest_slashed_balances()
        .set(
            slashed_balances_index,
            state
                .getLatest_slashed_balances()
                .get(slashed_balances_index)
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

    validator.setWithdrawable_epoch(
        get_current_epoch(state).plus(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH)));
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
    validator.setWithdrawable_epoch(
        get_current_epoch(state)
            .plus(UnsignedLong.valueOf(Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY)));
  }

  public static Bytes32 get_randao_mix(BeaconState state, UnsignedLong epoch) {
    checkArgument(
        get_current_epoch(state)
                .minus(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH))
                .compareTo(epoch)
            < 0,
        "get_randao_mix: first check");
    checkArgument(epoch.compareTo(get_current_epoch(state)) <= 0, "get_randao_mix: second check");
    UnsignedLong index = epoch.mod(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH));
    return state.getLatest_randao_mixes().get(index.intValue());
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
  public static Bytes32 get_block_root(BeaconState state, UnsignedLong slot) {
    checkArgument(
        slot.compareTo(state.getSlot()) < 0,
        "checkArgument threw an exception in get_block_root()");
    checkArgument(
        state
                .getSlot()
                .compareTo(slot.plus(UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)))
            <= 0,
        "checkArgument threw an exception in get_block_root()");
    // Todo: Remove .intValue() as soon as our list wrapper supports unsigned longs
    return state
        .getLatest_block_roots()
        .get(slot.mod(UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)).intValue());
  }

  /**
   * Return the state root at a recent ``slot``.
   *
   * @param state
   * @param slot
   * @return
   */
  public static Bytes32 get_state_root(BeaconState state, UnsignedLong slot) {
    checkArgument(
        state
                .getSlot()
                .compareTo(slot.plus(UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)))
            <= 0,
        "checkArgument threw an exception in get_state_root()");
    checkArgument(
        slot.compareTo(state.getSlot()) < 0,
        "checkArgument threw an exception in get_state_root()");
    // Todo: Remove .intValue() as soon as our list wrapper supports unsigned longs
    return state
        .getLatest_state_roots()
        .get(slot.mod(UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)).intValue());
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
   * @param list_size The size of the list from which the element is taken.
   * @param seed Initial seed value used for randomization.
   * @return The index from the original list that is now at position `index`
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_permuted_index">get_permuted_index
   *     - Spec v0.4</a>
   */
  @VisibleForTesting
  public static int get_permuted_index(int index, int list_size, Bytes32 seed) {
    checkArgument(index < list_size, "get_permuted_index(): index greater than list size");

    // The spec says that we should handle up to 2^40 validators, but we can't do this,
    // so we just fall back to int (2^31 validators).
    // checkArgument(list_size <= 1099511627776L); // 2^40

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
          toIntExact(
              Long.remainderUnsigned(
                  bytes_to_int(Hash.keccak256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  list_size));
      int flip = (pivot - indexRet) % list_size;
      if (flip < 0) {
        // Account for flip being negative
        flip += list_size;
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
   * @param list_size The size of the list from which the element is taken. Must not exceed 2^31.
   * @param seed Initial seed value used for randomization.
   * @return The permuted arrays of indices
   */
  public static int[] shuffle(int list_size, Bytes32 seed) {

    if (list_size == 0) {
      return new int[0];
    }

    //  In the following, great care is needed around signed and unsigned values.
    //  Note that the % (modulo) operator in Java behaves differently from the
    //  modulo operator in python:
    //    Python -1 % 13 = 12
    //    Java   -1 % 13 = -1

    //  Using UnsignedLong doesn't help us as some quantities can legitimately be negative.

    // Note: this should be faster than manually creating the list in a for loop
    // https://stackoverflow.com/questions/10242380/how-can-i-generate-a-list-or-array-of-sequential-integers-in-java
    int[] indices = IntStream.rangeClosed(0, list_size - 1).toArray();

    // int[] indices = new int[list_size];
    // for (int i = 0; i < list_size; i++) {
    //   indices[i] = i;
    // }

    byte[] powerOfTwoNumbers = {1, 2, 4, 8, 16, 32, 64, (byte) 128};

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      Bytes hashBytes = Bytes.EMPTY;
      for (int i = 0; i < (list_size + 255) / 256; i++) {
        Bytes iAsBytes4 = int_to_bytes(i, 4);
        hashBytes = Bytes.wrap(hashBytes, Hash.keccak256(Bytes.wrap(seed, roundAsByte, iAsBytes4)));
      }

      // This needs to be unsigned modulo.
      int pivot =
          toIntExact(
              Long.remainderUnsigned(
                  bytes_to_int(Hash.keccak256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  list_size));

      for (int i = 0; i < list_size; i++) {

        int flip = (pivot - indices[i]) % list_size;
        if (flip < 0) {
          // Account for flip being negative
          flip += list_size;
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
   * Splits ``values`` into ``split_count`` pieces.
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
  public static boolean is_power_of_two(UnsignedLong value) {
    long longValue = value.longValue();
    return longValue > 0 && (longValue & (longValue - 1)) == 0;
  }

  public static int get_beacon_proposer_index(
      BeaconState state, UnsignedLong slot, boolean registry_change)
      throws IllegalArgumentException {
    if (state instanceof BeaconStateWithCache
        && ((BeaconStateWithCache) state).getCurrentBeaconProposerIndex() > -1) {
      return ((BeaconStateWithCache) state).getCurrentBeaconProposerIndex();
    } else {
      UnsignedLong epoch = slot_to_epoch(slot);
      UnsignedLong current_epoch = get_current_epoch(state);
      UnsignedLong previous_epoch = get_previous_epoch(state);
      UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

      checkArgument(
          previous_epoch.compareTo(epoch) <= 0 && epoch.compareTo(next_epoch) <= 0,
          "get_beacon_proposer_index: slot not in range");

      List<Integer> first_committee =
          get_crosslink_committees_at_slot(state, slot, registry_change).get(0).getCommittee();
      // TODO: replace slot.intValue() with an UnsignedLong value
      return first_committee.get(epoch.intValue() % first_committee.size());
    }
  }

  public static int get_beacon_proposer_index(BeaconState state, UnsignedLong slot) {
    return get_beacon_proposer_index(state, slot, false);
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
   * TODO It may make sense to move this to {@link Fork}.
   *
   * <p>Get the domain number that represents the fork meta and signature domain.
   *
   * @param fork - The Fork to retrieve the verion for.
   * @param epoch - The epoch to retrieve the fork version for. See {@link
   *     #get_fork_version(Fork,UnsignedLong)}
   * @param domain_type - The domain type. See
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#signature-domains
   * @return The fork version and signature domain. This format ((fork version << 32) +
   *     SignatureDomain) is used to partition BLS signatures.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_domain">get_domain
   *     - Spec v0.4</a>
   */
  public static UnsignedLong get_domain(Fork fork, UnsignedLong epoch, int domain_type) {
    // TODO Investigate this further:
    // We deviate from the spec, adding domain_type first then concatting fork version on to it.
    // The spec does this in the opposite order. It smells a lot like an endianness problem.
    // The question is, it is Java/us, or is it a spec bug.
    return UnsignedLong.valueOf(
        bytes_to_int(Bytes.wrap(int_to_bytes(domain_type, 4), get_fork_version(fork, epoch))));
  }

  /**
   * Return the epoch at which an activation or exit triggered in `epoch` takes effect. g
   *
   * @param epoch - The epoch under consideration.
   * @return The epoch at which an activation or exit in the given `epoch` will take effect.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_delayed_activation_exit_epoch">get_delayed_activation_exit_epoch
   *     - Spec v0.4</a>
   */
  public static UnsignedLong get_delayed_activation_exit_epoch(UnsignedLong epoch) {
    return epoch.plus(UnsignedLong.ONE).plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY));
  }

  /**
   * Extract the bit in ``bitfield`` at position ``i``.
   *
   * @param bitfield - The Bytes value that describes the bitfield to operate on.
   * @param i - The index.
   * @return The bit at bitPosition from the given bitfield.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_bitfield_bit">get_bitfield_bit
   *     - Spec v0.4</a>
   */
  public static int get_bitfield_bit(Bytes bitfield, int i) {
    return (bitfield.get(i / 8) >>> (i % 8)) % 2;
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
        Bytes.wrap(new byte[slashable_attestation.getCustody_bitfield().size()]))) return false;

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
    List<Bytes32> message_hashes =
        Arrays.asList(
            new AttestationDataAndCustodyBit(slashable_attestation.getData(), false)
                .hash_tree_root(),
            new AttestationDataAndCustodyBit(slashable_attestation.getData(), true)
                .hash_tree_root());
    BLSSignature signature = slashable_attestation.getAggregate_signature();
    UnsignedLong domain =
        get_domain(
            state.getFork(),
            slot_to_epoch(slashable_attestation.getData().getSlot()),
            DOMAIN_ATTESTATION);

    return bls_verify_multiple(pubkeys, message_hashes, signature, domain);
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
  public static Bytes get_fork_version(Fork fork, UnsignedLong epoch) {
    if (epoch.compareTo(fork.getEpoch()) < 0) {
      return fork.getPrevious_version();
    } else {
      return fork.getCurrent_version();
    }
  }

  /**
   * Returns the participant indices for the attestation_data and bitfield.
   *
   * @param state - The BeaconState under consideration.
   * @param attestation_data - The AttestationData under consideration.
   * @param bitfield - The participation bitfield under consideration.
   * @return The participant indices for the attestation_data and participation_bitfield.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_attestation_participants">get_attestation_participants
   *     - Spec v0.4</a>
   */
  public static ArrayList<Integer> get_attestation_participants(
      BeaconState state, AttestationData attestation_data, Bytes bitfield)
      throws IllegalArgumentException {

    // Find the committee in the list with the desired shard
    ArrayList<CrosslinkCommittee> crosslink_committees =
        BeaconStateUtil.get_crosslink_committees_at_slot(state, attestation_data.getSlot());

    checkArgument(
        crosslink_committees.stream()
            .map(i -> i.getShard())
            .collect(Collectors.toList())
            .contains(attestation_data.getShard()),
        "get_attestation_participants: first check");

    CrosslinkCommittee crosslink_committee = null;
    for (CrosslinkCommittee committee : crosslink_committees) {
      if (committee.getShard().equals(attestation_data.getShard())) {
        crosslink_committee = committee;
        break;
      }
    }

    checkArgument(
        verify_bitfield(Bytes.wrap(bitfield), crosslink_committee.getCommitteeSize()),
        "checkArgument threw and exception in get_attestation_participants()");

    // Find the participating attesters in the committee
    ArrayList<Integer> participants = new ArrayList<>();
    for (int i = 0; i < crosslink_committee.getCommitteeSize(); i++) {
      int participation_bit = get_bitfield_bit(Bytes.wrap(bitfield), i);
      if (participation_bit == 1) {
        participants.add(crosslink_committee.getCommittee().get(i));
      }
    }
    return participants;
  }

  /** Activate the validator with the given 'index'. Note that this function mutates 'state'. */
  @VisibleForTesting
  public static void activate_validator(BeaconState state, int index, boolean is_genesis) {
    Validator validator = state.getValidator_registry().get(index);
    validator.setActivation_epoch(
        is_genesis
            ? UnsignedLong.valueOf(GENESIS_EPOCH)
            : BeaconStateUtil.get_delayed_activation_exit_epoch(
                BeaconStateUtil.get_current_epoch(state)));
  }

  /**
   * Check if ``attestation_data_1`` and ``attestation_data_2`` have the same target.
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
    UnsignedLong target_epoch_1 = slot_to_epoch(attestation_data_1.getSlot());
    UnsignedLong target_epoch_2 = slot_to_epoch(attestation_data_2.getSlot());
    return target_epoch_1.compareTo(target_epoch_2) == 0;
  }

  /**
   * Check if ``attestation_data_1`` surrounds ``attestation_data_2``.
   *
   * @param attestation_data_1
   * @param attestation_data_2
   * @return
   */
  public static boolean is_surround_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    UnsignedLong source_epoch_1 = attestation_data_1.getSource_epoch();
    UnsignedLong source_epoch_2 = attestation_data_2.getSource_epoch();
    UnsignedLong target_epoch_1 = slot_to_epoch(attestation_data_1.getSlot());
    UnsignedLong target_epoch_2 = slot_to_epoch(attestation_data_2.getSlot());
    return source_epoch_1.compareTo(source_epoch_2) < 0
        && target_epoch_2.compareTo(target_epoch_1) < 0;
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
   * @param value - The value to be converted to bytes.
   * @param numBytes - The number of bytes to be returned.
   * @return The value represented as the requested number of bytes.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#int_to_bytes1-int_to_bytes2-">int_to_bytes
   *     - Spec v0.4</a>
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

  /**
   * @param data - The value to be converted to int.
   * @return An integer representation of the bytes value given.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#bytes_to_int">bytes_to_int
   *     - Spec v0.4</a>
   */
  public static long bytes_to_int(Bytes data) {
    return data.toLong(ByteOrder.LITTLE_ENDIAN);
  }
}
