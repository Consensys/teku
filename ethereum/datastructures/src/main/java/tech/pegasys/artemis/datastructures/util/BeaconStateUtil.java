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
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EFFECTIVE_BALANCE;
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
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.ACTIVATION_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.CHURN_LIMIT_QUOTIENT;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_SLASHED_EXIT_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.datastructures.Constants.MAX_INDICES_PER_SLASHABLE_VOTE;
import static tech.pegasys.artemis.datastructures.Constants.MIN_PER_EPOCH_CHURN_LIMIT;
import static tech.pegasys.artemis.datastructures.Constants.MIN_SEED_LOOKAHEAD;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.datastructures.Constants.TARGET_COMMITTEE_SIZE;
import static tech.pegasys.artemis.datastructures.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_crosslink_committee;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_epoch_start_shard;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;

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
      List<Deposit> genesis_validator_deposits,
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
      List<UnsignedLong> balances = state.getBalances();
      if (balances.get(validator_index).compareTo(UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT)) >= 0) {
        activate_validator(state, validator_index, true);
      }
    }

    List<Integer> active_validator_indices =
        get_active_validator_indices(
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

    return state;
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
  public static boolean verify_merkle_branch(
      Bytes32 leaf, List<Bytes32> proof, int depth, int index, Bytes32 root) {
    Bytes32 value = leaf;
    for (int i = 0; i < depth; i++) {
      if (Math.floor(index / Math.pow(2, i)) % 2 != 0) {
        value = Hash.sha2_256(Bytes.concatenate(proof.get(i), value));
      } else {
        value = Hash.sha2_256(Bytes.concatenate(value, proof.get(i)));
      }
    }
    return value.equals(root);
  }

  /**
   * Generate a seed for the given ``epoch``.
   *
   * @param state - The BeaconState under consideration.
   * @param epoch - The epoch to generate a seed for.
   * @return A generated seed for the given epoch.
   * @throws IllegalArgumentException
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#generate_seed</a>
   */
  public static Bytes32 generate_seed(BeaconState state, UnsignedLong epoch)
      throws IllegalArgumentException {
    UnsignedLong randaoIndex = epoch.plus(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH - MIN_SEED_LOOKAHEAD));
    Bytes32 randao_mix =
        get_randao_mix(state, randaoIndex);
    Bytes32 index_root = get_active_index_root(state, epoch);
    Bytes32 epochBytes = int_to_bytes32(epoch.longValue());
    return Hash.sha2_256(Bytes.wrap(randao_mix, index_root, epochBytes));
  }

  /**
   * Return the index root at a recent ``epoch``.
   * ``epoch`` expected to be between
   * (current_epoch - LATEST_ACTIVE_INDEX_ROOTS_LENGTH + ACTIVATION_EXIT_DELAY, current_epoch + ACTIVATION_EXIT_DELAY].
   *
   * @param state - The BeaconState under consideration.
   * @param epoch - The epoch to get the index root for.
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_active_index_root</a>
   */
  public static Bytes32 get_active_index_root(BeaconState state, UnsignedLong epoch) {
    int index = epoch.mod(UnsignedLong.valueOf(LATEST_ACTIVE_INDEX_ROOTS_LENGTH)).intValue();
    return state.getLatest_active_index_roots().get(index);
  }

  /**
   * Return the combined effective balance of the ``indices``. (1 Gwei minimum to avoid divisions by zero.)
   *
   * @param state
   * @param indices
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_total_balance</a>
   */
  public static UnsignedLong get_total_balance(BeaconState state, List<Integer> indices){
    UnsignedLong sum = UnsignedLong.ZERO;
    Iterator<Integer> itr = indices.iterator();
    List<Validator> validator_registry = state.getValidator_registry();
    while(itr.hasNext()){
      sum = sum.plus(validator_registry.get(itr.next().intValue()).getEffective_balance());
    }
    return max(sum, UnsignedLong.ONE);
  }

  public static Bytes32 getShard_block_root(BeaconState state, UnsignedLong shard) {
    return state
        .getLatest_crosslinks()
        .get(toIntExact(shard.longValue()) % Constants.SHARD_COUNT)
        .getCrosslink_data_root();
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
   * Return the previous epoch of the given ``state``.
   * Return the current epoch if it's genesis epoch.
   *
   * @param state
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_previous_epoch</a>
   */
  public static UnsignedLong get_previous_epoch(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    return (current_epoch.equals(UnsignedLong.valueOf(GENESIS_EPOCH))) ? UnsignedLong.valueOf(GENESIS_EPOCH) : current_epoch.minus(UnsignedLong.ONE);
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
   * Return the block root at a recent ``epoch``.
   *
   * @param state - The BeaconState under consideration.
   * @param epoch
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_block_root</a>
   */
  public static Bytes32 get_block_root(BeaconState state, UnsignedLong epoch) {
    return get_block_root_at_slot(state, get_epoch_start_slot(epoch));
  }

  /**
   * Return the number of committees at ``epoch``.
   *
   * @param state
   * @param epoch
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_epoch_committee_count</a>
   */
  public static UnsignedLong get_epoch_committee_count(BeaconState state, UnsignedLong epoch){
    List<Integer> active_validator_indices = get_active_validator_indices(state, epoch);
    return max(
            UnsignedLong.ONE,
            min(
                    UnsignedLong.valueOf(Math.floorDiv(SHARD_COUNT, SLOTS_PER_EPOCH)),
                    UnsignedLong.valueOf(active_validator_indices.size()).dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).dividedBy(UnsignedLong.valueOf(TARGET_COMMITTEE_SIZE))
            )
    ).times(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
  }

  /**
   * Return the randao mix at a recent ``epoch``.
   * ``epoch`` expected to be between (current_epoch - LATEST_RANDAO_MIXES_LENGTH, current_epoch].
   * @param state
   * @param epoch
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_randao_mix</a>
   */
  public static Bytes32 get_randao_mix(BeaconState state, UnsignedLong epoch){
    int index = epoch.mod(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)).intValue();
    return state.getLatest_randao_mixes().get(index);
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
   * Returns the index of the proposer for the current epoch
   *
   * @param state
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_beacon_proposer_index</a>
   */
  public static int get_beacon_proposer_index(
      BeaconState state) {
    UnsignedLong epoch = get_current_epoch(state);
    UnsignedLong committees_per_slot = get_epoch_committee_count(state, epoch).dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong offset = committees_per_slot.times(state.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)));
    UnsignedLong shard = (get_epoch_start_shard(state, epoch).plus(offset)).mod(UnsignedLong.valueOf(SHARD_COUNT));

    List<Integer> first_committee = get_crosslink_committee(state, epoch, shard);
    int MAX_RANDOM_BYTE = ((int)(Math.pow(2.0d, 8.0d))) - 1;
    Bytes seed = generate_seed(state, epoch);
    int i = 0;
    while(true){
      int index = epoch.plus(UnsignedLong.valueOf(i)).mod(UnsignedLong.valueOf(first_committee.size())).intValue();
      int candidate_index = first_committee.get(index).intValue();
      Bytes digest = Hash.sha2_256(Bytes.concatenate(seed, int_to_bytes(Math.floorDiv(i, 32), 8)));
      byte random_byte = digest.get(i % 32);
      UnsignedLong effective_balance = state.getValidator_registry().get(candidate_index).getEffective_balance();

      UnsignedLong minRandomBalance = effective_balance.times(UnsignedLong.valueOf(MAX_RANDOM_BYTE));
      UnsignedLong maxRandomBalance = UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE * random_byte);
      if(minRandomBalance.compareTo(maxRandomBalance) >= 0) return candidate_index;
      i++;
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
   * Return the signature domain (fork version concatenated with domain type) of a message.
   *
   * @param state
   * @param domain_type
   * @param message_epoch
   * @return The fork version and signature domain. This format ((fork version << 32) +
   *     SignatureDomain) is used to partition BLS signatures.
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_domain</a>
   */
  public static long get_domain(BeaconState state, int domain_type, UnsignedLong message_epoch) {
    UnsignedLong epoch = (message_epoch == null) ? get_current_epoch(state) : message_epoch;
    Bytes fork_version = (epoch.compareTo(state.getFork().getEpoch()) < 0) ? state.getFork().getPrevious_version() : state.getFork().getCurrent_version();
    return bls_domain(domain_type, fork_version);
  }

  /**
   * Return the bls domain given by the ``domain_type`` and optional 4 byte ``fork_version`` (defaults to zero).
   *
   * @param domain_type
   * @param fork_version
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#bls_domain</a>
   */
  public static long bls_domain(int domain_type, Bytes fork_version){
    return bytes_to_int(Bytes.concatenate(int_to_bytes(domain_type, 4), fork_version));
  }

  /**
   * Return the churn limit based on the active validator count.
   *
   * @param state
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_churn_limit</a>
   */
  public static UnsignedLong get_churn_limit(BeaconState state){
    return max(UnsignedLong.valueOf(MIN_PER_EPOCH_CHURN_LIMIT),
            UnsignedLong.valueOf(Math.floorDiv(get_active_validator_indices(state, get_current_epoch(state)).size(), CHURN_LIMIT_QUOTIENT)));
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
    if (bitfield.size() != (committee_size + 7) / 8) {
      return false;
    }

    for (int i = committee_size; i < bitfield.size() * 8; i++) {
      if (get_bitfield_bit(bitfield, i) == 0b1) {
        return false;
      }
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

  /**
   * Return the block root at a recent ``slot``.
   *
   * @param state
   * @param slot
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_block_root_at_slot</a>
   */
  public static Bytes32 get_block_root_at_slot(BeaconState state, UnsignedLong slot){
    UnsignedLong slotPlusHistoricalRoot = slot.plus(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT));
    checkArgument(slot.compareTo(state.getSlot()) < 0 &&
            state.getSlot().compareTo(slotPlusHistoricalRoot) <= 0
            , "BeaconStateUtil.get_block_root_at_slot");
    int latestBlockRootIndex = slot.mod(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT)).intValue();
    return state.getLatest_block_roots().get(latestBlockRootIndex);
  }

  /**
   * @param m
   * @param n
   * @return
   */
  public static int safeMod(int m, int n){
    return ((n % m) + m) % m;
  }

  /**
   * @param m
   * @param n
   * @return
   */
  public static long safeMod(long m, long n){
    return ((n % m) + m) % m;
  }
}
