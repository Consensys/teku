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

package tech.pegasys.artemis.statetransition.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.datastructures.Constants.ENTRY_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_FORK_VERSION;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_START_SHARD;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_INDEX_ROOTS_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.crypto.Hash;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.BLSSignature;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.PendingAttestationRecord;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.statetransition.BeaconState;

public class BeaconStateUtil {

  public static BeaconState get_initial_beacon_state(
      ArrayList<Deposit> initial_validator_deposits,
      UnsignedLong genesis_time,
      Eth1Data latest_eth1_data) {

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
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_PENALIZED_EXIT_LENGTH, UnsignedLong.ZERO));
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
            new ArrayList<Validator>(),
            new ArrayList<UnsignedLong>(),
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
            new ArrayList<PendingAttestationRecord>(),
            new ArrayList<Bytes32>(),

            // Ethereum 1.0 chain data
            latest_eth1_data,
            new ArrayList<>());

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

    Bytes32 genesis_active_index_root =
        TreeHashUtil.hash_tree_root(
            ValidatorsUtil.get_active_validators(
                state.getValidator_registry(), UnsignedLong.valueOf(GENESIS_EPOCH)));
    for (Bytes32 root : state.getLatest_index_roots()) {
      root = genesis_active_index_root;
    }
    state.setCurrent_epoch_seed(generate_seed(state, UnsignedLong.valueOf(GENESIS_EPOCH)));

    return state;
  }

  public static double calc_total_balance(BeaconState state) {
    return 0.0d;
  }

  /** Return the list of `(committee, shard)` tuples for the `slot`. */
  public static ArrayList<CrosslinkCommittee> get_crosslink_committees_at_slot(
      //      BeaconState state, long slot, boolean registry_change) throws BlockValidationException
      // {
      BeaconState state, long slot, boolean registry_change) {
    // TODO: convert these values to UnsignedLong
    long epoch = slot_to_epoch(UnsignedLong.valueOf(slot)).longValue();
    long current_epoch = get_current_epoch(state).longValue();
    long previous_epoch =
        (current_epoch > Constants.GENESIS_EPOCH) ? current_epoch - 1 : current_epoch;
    long next_epoch = current_epoch + 1;

    assertTrue(previous_epoch <= epoch && epoch <= next_epoch);

    long committees_per_epoch = 0;
    Bytes32 seed = Bytes32.ZERO;
    long shuffling_epoch = 0;
    long shuffling_start_shard = 0;

    if (epoch == previous_epoch) {

      committees_per_epoch = get_previous_epoch_committee_count(state);
      seed = state.getPrevious_epoch_seed();
      // TODO: convert these values to UnsignedLong
      shuffling_epoch = state.getPrevious_calculation_epoch().longValue();
      shuffling_start_shard = state.getPrevious_epoch_start_shard().longValue();

    } else if (epoch == current_epoch) {
      // TODO: convert these values to UnsignedLong
      committees_per_epoch = get_current_epoch_committee_count(state);
      seed = state.getCurrent_epoch_seed();
      shuffling_epoch = state.getCurrent_calculation_epoch().longValue();
      shuffling_start_shard = state.getCurrent_epoch_start_shard().longValue();

    } else if (epoch == next_epoch) {
      // TODO: convert these values to UnsignedLong
      long current_committees_per_epoch = get_current_epoch_committee_count(state);
      committees_per_epoch = get_next_epoch_committee_count(state);
      shuffling_epoch = next_epoch;
      long epochs_since_last_registry_update =
          current_epoch - state.getValidator_registry_update_epoch().longValue();
      // TODO: convert these values to UnsignedLong
      if (registry_change) {
        seed = generate_seed(state, next_epoch);
        shuffling_start_shard =
            (state.getCurrent_epoch_start_shard().longValue() + current_committees_per_epoch)
                % Constants.SHARD_COUNT;
      } else if (epochs_since_last_registry_update > 1
          && is_power_of_two(epochs_since_last_registry_update)) {
        seed = generate_seed(state, next_epoch);
        shuffling_start_shard = state.getCurrent_epoch_start_shard().longValue();
      } else {
        seed = state.getCurrent_epoch_seed();
        shuffling_start_shard = state.getCurrent_epoch_start_shard().longValue();
      }
    }

    List<List<Integer>> shuffling =
        get_shuffling(seed, state.getValidator_registry(), shuffling_epoch);
    long offset = slot % Constants.EPOCH_LENGTH;
    long committees_per_slot = committees_per_epoch / Constants.EPOCH_LENGTH;
    long slot_start_shard =
        (shuffling_start_shard + committees_per_slot * offset) % Constants.SHARD_COUNT;

    ArrayList<CrosslinkCommittee> crosslink_committees_at_slot =
        new ArrayList<CrosslinkCommittee>();
    for (int i = 0; i < committees_per_slot; i++) {
      CrosslinkCommittee committee =
          new CrosslinkCommittee(
              UnsignedLong.fromLongBits(committees_per_slot * offset + i),
              shuffling.get(toIntExact(slot_start_shard + i) % Constants.SHARD_COUNT));
      crosslink_committees_at_slot.add(committee);
    }
    return crosslink_committees_at_slot;
  }

  /** This is a wrapper that defaults `registry_change` to false when it is not provided */
  public static ArrayList<CrosslinkCommittee> get_crosslink_committees_at_slot(
      BeaconState state, long slot) {
    return get_crosslink_committees_at_slot(state, slot, false);
  }

  /* TODO: Note from spec -
   * Note: this definition and the next few definitions make heavy use of repetitive computing.
   * Production implementations are expected to appropriately use caching/memoization to avoid redoing work.
   */

  private static long get_previous_epoch_committee_count(BeaconState state) {
    List<Integer> previous_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getPrevious_calculation_epoch());
    return get_epoch_committee_count(previous_active_validators.size());
  }

  private static long get_current_epoch_committee_count(BeaconState state) {
    List<Integer> current_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getCurrent_calculation_epoch());
    return get_epoch_committee_count(current_active_validators.size());
  }

  private static long get_next_epoch_committee_count(BeaconState state) {
    List<Integer> next_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), get_current_epoch(state).plus(UnsignedLong.ONE));
    return get_epoch_committee_count(next_active_validators.size());
  }

  public static Bytes32 generate_seed(BeaconState state, UnsignedLong epoch) {
    Bytes32 randao_mix =
        get_randao_mix(state, epoch.minus(UnsignedLong.valueOf(Constants.SEED_LOOKAHEAD)));
    Bytes32 index_root = get_active_index_root(state, epoch);
    return Hash.keccak256(Bytes.wrap(randao_mix, index_root));
  }

  // TODO remove this shim when long values are all consistent
  public static Bytes32 generate_seed(BeaconState state, long epoch) {
    return generate_seed(state, UnsignedLong.valueOf(epoch));
  }

  public static Bytes32 get_active_index_root(BeaconState state, UnsignedLong epoch) {
    assertTrue(
        // Since we're using UnsignedLong here, we can't subtract LATEST_INDEX_ROOTS_LENGTH
        get_current_epoch(state)
                .plus(UnsignedLong.valueOf(ENTRY_EXIT_DELAY))
                .compareTo(epoch.plus(UnsignedLong.valueOf(LATEST_INDEX_ROOTS_LENGTH)))
            < 0);
    assertTrue(
        epoch.compareTo(get_current_epoch(state).plus(UnsignedLong.valueOf(ENTRY_EXIT_DELAY)))
            <= 0);

    List<Bytes32> index_roots = state.getLatest_index_roots();
    int index = epoch.mod(UnsignedLong.valueOf(LATEST_INDEX_ROOTS_LENGTH)).intValue();
    return state.getLatest_index_roots().get(index);
  }

  public static Bytes32 getShard_block_root(BeaconState state, Long shard) {
    return state.getLatest_crosslinks().get(toIntExact(shard)).getShard_block_root();
  }

  /** Return the epoch number of the given ``slot`` */
  public static UnsignedLong slot_to_epoch(UnsignedLong slot) {
    return slot.dividedBy(UnsignedLong.valueOf(Constants.EPOCH_LENGTH));
  }

  /**
   * Return the current epoch of the given ``state``.
   *
   * @param state
   */
  public static UnsignedLong get_current_epoch(BeaconState state) {
    return slot_to_epoch(state.getSlot());
  }

  public static UnsignedLong get_previous_epoch(BeaconState state) {
    if (get_current_epoch(state).compareTo(slot_to_epoch(UnsignedLong.valueOf(GENESIS_SLOT))) > 0)
      return get_current_epoch(state).minus(UnsignedLong.ONE);
    else return get_current_epoch(state);
  }

  public static UnsignedLong get_next_epoch(BeaconState state) {
    return get_current_epoch(state).plus(UnsignedLong.ONE);
  }

  /**
   * An entry or exit triggered in the ``epoch`` given by the input takes effect at the epoch given
   * by the output.
   *
   * @param epoch
   * @return
   */
  public static UnsignedLong get_entry_exit_effect_epoch(UnsignedLong epoch) {
    return epoch.plus(UnsignedLong.ONE).plus(UnsignedLong.valueOf(ENTRY_EXIT_DELAY));
  }

  /**
   * Initiate exit for the validator with the given 'index'. Note that this function mutates
   * 'state'.
   *
   * @param index The index of the validator.
   */
  @VisibleForTesting
  public static void initiate_validator_exit(BeaconState state, int index) {
    Validator validator = state.getValidator_registry().get(index);
    validator.setStatus_flags(UnsignedLong.valueOf(Constants.INITIATED_EXIT));
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
   * Penalize the validator of the given ``index``. Note that this function mutates ``state``.
   *
   * @param state
   * @param index
   */
  public static void penalize_validator(BeaconState state, int index) {
    // TODO: implement from 0.1 spec
  }

  /** Return the randao mix at a recent ``epoch``. */
  public static Bytes32 get_randao_mix(BeaconState state, UnsignedLong epoch) {
    assertTrue(
        // If we're going to use UnsignedLongs then we can't subtract LATEST_RANDAO_MIXES_LENGTH
        // here
        get_current_epoch(state)
                .compareTo(epoch.plus(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
            < 0);
    assertTrue(epoch.compareTo(get_current_epoch(state)) <= 0);
    UnsignedLong index = epoch.mod(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH));
    List<Bytes32> randao_mixes = state.getLatest_randao_mixes();
    return randao_mixes.get(index.intValue());
  }

  // https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#get_block_root
  public static Bytes32 get_block_root(BeaconState state, long slot) throws Exception {
    long slot_upper_bound = slot + state.getLatest_block_roots().size();
    // TODO: change values to UnsignedLong
    if ((state.getSlot().compareTo(UnsignedLong.fromLongBits(slot_upper_bound)) <= 0)
        || UnsignedLong.valueOf(slot).compareTo(state.getSlot()) < 0)
      return state
          .getLatest_block_roots()
          .get(toIntExact(slot) % state.getLatest_block_roots().size());
    throw new BlockValidationException("Desired block root not within the provided bounds");
  }

  /*
   * @param values
   * @return The merkle root.
   */
  public static Bytes32 merkle_root(List<Bytes32> list) {
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
  public static int get_epoch_committee_count(int active_validator_count) {
    return clamp(
            1,
            Constants.SHARD_COUNT / Constants.EPOCH_LENGTH,
            active_validator_count / Constants.EPOCH_LENGTH / Constants.TARGET_COMMITTEE_SIZE)
        * Constants.EPOCH_LENGTH;
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
      Bytes32 seed, List<Validator> validators, long epoch) {

    List<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices(validators, UnsignedLong.valueOf(epoch));

    int committees_per_epoch = get_epoch_committee_count(active_validator_indices.size());

    // Shuffle with seed
    // TODO: we may need to treat `epoch` as little-endian here. Revisit as the spec evolves.
    seed.xor(Bytes32.wrap(Bytes.minimalBytes(epoch)));
    List<Integer> shuffled_active_validator_indices = shuffle(active_validator_indices, seed);

    return split(shuffled_active_validator_indices, committees_per_epoch);
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
   * Returns the shuffled 'values' with seed as entropy.
   *
   * @param values The array.
   * @param seed Initial seed value used for randomization.
   * @return The shuffled array.
   */
  @VisibleForTesting
  public static <T> List<T> shuffle(List<T> values, Bytes32 seed) {
    int values_count = values.size();

    // Entropy is consumed from the seed in 3-byte (24 bit) chunks.
    int rand_bytes = 3;
    // The highest possible result of the RNG.
    int rand_max = (int) Math.pow(2, (rand_bytes * 8) - 1);

    // The range of the RNG places an upper-bound on the size of the list that
    // may be shuffled. It is a logic error to supply an oversized list.
    assertTrue(values_count < rand_max);

    ArrayList<T> output = new ArrayList<>(values);

    Bytes32 source = seed;
    int index = 0;
    while (index < values_count - 1) {
      // Re-hash the `source` to obtain a new pattern of bytes.
      source = Hash.keccak256(source);

      // List to hold values for swap below.
      T tmp;

      // Iterate through the `source` bytes in 3-byte chunks
      for (int position = 0; position < (32 - (32 % rand_bytes)); position += rand_bytes) {
        // Determine the number of indices remaining in `values` and exit
        // once the last index is reached.
        int remaining = values_count - index;
        if (remaining == 1) break;

        // Read 3-bytes of `source` as a 24-bit big-endian integer.
        int sample_from_source = bytes3ToInt(source, position);

        // Sample values greater than or equal to `sample_max` will cause
        // modulo bias when mapped into the `remaining` range.
        int sample_max = rand_max - rand_max % remaining;
        // Perform a swap if the consumed entropy will not cause modulo bias.
        if (sample_from_source < sample_max) {
          // Select a replacement index for the current index
          int replacement_position = (sample_from_source % remaining) + index;
          // Swap the current index with the replacement index.
          tmp = output.get(index);
          output.set(index, output.get(replacement_position));
          output.set(replacement_position, tmp);
          index += 1;
        }
      }
    }

    return output;
  }

  /**
   * Splits 'values' into 'split_count' pieces.
   *
   * @param values The original list of validators.
   * @param split_count The number of pieces to split the array into.
   * @return The list of validators split into N pieces.
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

  /** A helper method for readability. */
  public static int clamp(int minval, int maxval, int x) {
    if (x <= minval) return minval;
    if (x >= maxval) return maxval;
    return x;
  }

  /** Returns true if the operand is an exact power of two */
  public static boolean is_power_of_two(long value) {
    return (value != 0) && (Long.lowestOneBit(value) == Long.highestOneBit(value));
  }

  /**
   * Returns the beacon proposer index for the 'slot'.
   *
   * @param state
   * @param slot
   * @return
   */
  public static int get_beacon_proposer_index(BeaconState state, UnsignedLong slot) {
    // TODO: convert these values to UnsignedLong
    List<Integer> first_committee =
        get_crosslink_committees_at_slot(state, slot.longValue()).get(0).getCommittee();
    // TODO: replace slot.intValue() with an UnsignedLong value
    return first_committee.get(slot.intValue() % first_committee.size());
  }

  /**
   * Process a deposit from Ethereum 1.0. Note that this function mutates 'state'.
   *
   * @param state
   * @param pubkey
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @param amount
   */
  public static void process_deposit(
      BeaconState state,
      Bytes48 pubkey,
      UnsignedLong amount,
      BLSSignature proof_of_possession,
      Bytes32 withdrawal_credentials) {
    assertTrue(
        validate_proof_of_possession(state, pubkey, proof_of_possession, withdrawal_credentials));
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
    int index = getValidatorIndexByPubkey(state, pubkey);

    if (index < 0) {
      Validator validator =
          new Validator(
              pubkey,
              withdrawal_credentials,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH,
              UnsignedLong.ZERO);
      state.getValidator_registry().add(validator);
      state.getValidator_balances().add(amount);
    } else {
      Validator validator = state.getValidator_registry().get(index);
      assertTrue(validator.getWithdrawal_credentials().equals(withdrawal_credentials));
      List<UnsignedLong> balances = state.getValidator_balances();
      UnsignedLong balance = balances.get(index).plus(amount);
      balances.set(index, balance);
    }
  }

  /**
   * get validator index by public key
   *
   * @param state
   * @param pubkey
   * @return
   */
  public static Validator getValidatorByPubkey(BeaconState state, Bytes48 pubkey) {
    for (Validator validator : state.getValidator_registry()) {
      if (validator.getPubkey().equals(pubkey)) return validator;
    }
    return null;
  }

  /**
   * get validator index by public key
   *
   * @param state
   * @param pubkey
   * @return
   */
  public static int getValidatorIndexByPubkey(BeaconState state, Bytes48 pubkey) {
    int result = -1;
    int index = 0;
    for (Validator validator : state.getValidator_registry()) {
      if (validator.getPubkey().toHexString().equals(pubkey.toHexString())) {
        result = index;
        break;
      }
      index++;
    }
    return result;
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
   * @param state
   * @param pubkey
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @return
   */
  private static boolean validate_proof_of_possession(
      BeaconState state,
      Bytes48 pubkey,
      BLSSignature proof_of_possession,
      Bytes32 withdrawal_credentials) {
    // Verify the given ``proof_of_possession``.
    DepositInput proof_of_possession_data =
        new DepositInput(pubkey, withdrawal_credentials, proof_of_possession);

    List<Bytes48> signature =
        Arrays.asList(
            Bytes48.leftPad(proof_of_possession.getC0()),
            Bytes48.leftPad(proof_of_possession.getC1()));
    UnsignedLong domain = get_domain(state.getFork(), state.getSlot(), DOMAIN_DEPOSIT);
    return bls_verify(
        pubkey, TreeHashUtil.hash_tree_root(proof_of_possession_data.toBytes()), signature, domain);
  }

  /**
   * @param fork
   * @param slot
   * @param domain_type
   * @return
   */
  private static UnsignedLong get_domain(Fork fork, UnsignedLong slot, int domain_type) {
    return get_fork_version(fork, slot)
        .times(UnsignedLong.valueOf((long) Math.pow(2, 32)))
        .plus(UnsignedLong.valueOf(domain_type));
  }

  /**
   * @param fork
   * @param epoch
   * @return
   */
  public static UnsignedLong get_fork_version(Fork fork, UnsignedLong epoch) {
    if (epoch.compareTo(fork.getEpoch()) < 0) return fork.getPrevious_version();
    else return fork.getCurrent_version();
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
      BeaconState state, AttestationData attestation_data, byte[] participation_bitfield) {
    // Find the relevant committee

    ArrayList<CrosslinkCommittee> crosslink_committees =
        BeaconStateUtil.get_crosslink_committees_at_slot(
            state, toIntExact(attestation_data.getSlot().longValue()));

    // TODO: assertTrue attestation_data.shard in [shard for _, shard in crosslink_committees]

    CrosslinkCommittee crosslink_committee = null;
    for (CrosslinkCommittee curr_crosslink_committee : crosslink_committees) {
      if (curr_crosslink_committee.getShard().equals(attestation_data.getShard())) {
        crosslink_committee = curr_crosslink_committee;
        break;
      }
    }
    assertTrue(crosslink_committee != null);
    assertTrue(participation_bitfield.length == ceil_div8(crosslink_committee.getCommitteeSize()));

    // Find the participating attesters in the committee
    ArrayList<Integer> participants = new ArrayList<>();
    for (int i = 0; i < crosslink_committee.getCommitteeSize(); i++) {
      int participation_bit = (participation_bitfield[i / 8] >> (7 - (i % 8))) % 2;
      if (participation_bit == 1) {
        participants.add(crosslink_committee.getCommittee().get(i));
      }
    }
    return participants;
  }

  /**
   * @param validators
   * @param current_slot
   * @return The minimum empty validator index.
   */
  private int min_empty_validator_index(
      ArrayList<Validator> validators, ArrayList<Double> validator_balances, int current_slot) {
    for (int i = 0; i < validators.size(); i++) {
      Validator v = validators.get(i);
      double vbal = validator_balances.get(i);
      // todo getLatest_status_change_slot method no longer exists following the recent update
      //      if (vbal == 0
      //          && v.getLatest_status_change_slot().longValue() + ZERO_BALANCE_VALIDATOR_TTL
      //              <= current_slot) {
      return i;
      //      }
    }
    return validators.size();
  }

  public static boolean isValidatorKeyRegistered(BeaconState state, Bytes48 pubkey) {
    for (Validator validator : state.getValidator_registry()) {
      if (validator.getPubkey().equals(pubkey)) return true;
    }
    return false;
  }

  /**
   * Helper function to find the index of the pubkey in the array of validators' pubkeys.
   *
   * @param validator_pubkeys
   * @param pubkey
   * @return The index of the pubkey.
   */
  private int indexOfPubkey(Bytes48[] validator_pubkeys, Bytes48 pubkey) {
    for (int i = 0; i < validator_pubkeys.length; i++) {
      if (validator_pubkeys[i].equals(pubkey)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Activate the validator with the given 'index'. Note that this function mutates 'state'.
   *
   * @param validator the validator.
   */
  @VisibleForTesting
  public static void activate_validator(
      BeaconState state, Validator validator, boolean is_genesis) {
    //    Activate the validator of the given ``index``.
    //    Note that this function mutates ``state``.
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
   * @return
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
  private boolean is_double_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    UnsignedLong target_epoch_1 = attestation_data_1.getSlot().dividedBy(UnsignedLong.valueOf(EPOCH_LENGTH));
    UnsignedLong target_epoch_2 = attestation_data_2.getSlot().dividedBy(UnsignedLong.valueOf(EPOCH_LENGTH));
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
  private boolean is_surround_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long source_epoch_1 = attestation_data_1.getJustified_epoch().longValue() / EPOCH_LENGTH;
    long source_epoch_2 = attestation_data_2.getJustified_epoch().longValue() / EPOCH_LENGTH;
    UnsignedLong target_epoch_1 = attestation_data_1.getSlot().dividedBy(UnsignedLong.valueOf(EPOCH_LENGTH));
    UnsignedLong target_epoch_2 = attestation_data_2.getSlot().dividedBy(UnsignedLong.valueOf(EPOCH_LENGTH));
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
  private int integer_squareroot(int n) {
    int x = n;
    int y = (x + 1) / 2;
    while (y < x) {
      x = y;
      y = (x + n / x) / 2;
    }
    return x;
  }

  private static void assertTrue(boolean value) {
    if (!value) throw new AssertFailed();
  }

  public static class AssertFailed extends RuntimeException {}
}
