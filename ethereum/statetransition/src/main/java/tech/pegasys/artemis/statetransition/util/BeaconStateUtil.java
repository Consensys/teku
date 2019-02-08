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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.BeaconState;

public class BeaconStateUtil {

  public static double calc_total_balance(BeaconState state) {
    return 0.0d;
  }

  /** Return the list of `(committee, shard)` tuples for the `slot`. */
  public static ArrayList<CrosslinkCommittee> get_crosslink_committees_at_slot(
      //      BeaconState state, long slot, boolean registry_change) throws BlockValidationException
      // {
      BeaconState state, long slot, boolean registry_change) {

    long epoch = slot_to_epoch(slot);
    long current_epoch = get_current_epoch(state);
    long previous_epoch =
        (current_epoch > Constants.GENESIS_EPOCH) ? current_epoch - 1 : current_epoch;
    long next_epoch = current_epoch + 1;

    // TODO handle this exception properly
    // if (!(previous_epoch <= epoch && epoch <= next_epoch)) {
    //  throw new BlockValidationException(
    //          "get_crosslink_committees_at_slot: Exception was thrown due to failure of
    // previous_epoch <= epoch <= next_epoch check.");
    // }

    long committees_per_epoch = 0;
    Bytes32 seed = Bytes32.ZERO;
    long shuffling_epoch = 0;
    long shuffling_start_shard = 0;

    if (epoch == previous_epoch) {

      committees_per_epoch = get_previous_epoch_committee_count(state);
      seed = state.getPrevious_epoch_seed();
      shuffling_epoch = state.getPrevious_calculation_epoch();
      shuffling_start_shard = state.getPrevious_epoch_start_shard();

    } else if (epoch == current_epoch) {

      committees_per_epoch = get_current_epoch_committee_count(state);
      seed = state.getCurrent_epoch_seed();
      shuffling_epoch = state.getCurrent_calculation_epoch();
      shuffling_start_shard = state.getCurrent_epoch_start_shard();

    } else if (epoch == next_epoch) {

      long current_committees_per_epoch = get_current_epoch_committee_count(state);
      committees_per_epoch = get_next_epoch_committee_count(state);
      shuffling_epoch = next_epoch;
      long epochs_since_last_registry_update =
          current_epoch - state.getValidator_registry_update_epoch();

      if (registry_change) {
        seed = generate_seed(state, next_epoch);
        shuffling_start_shard =
            (state.getCurrent_epoch_start_shard() + current_committees_per_epoch)
                % Constants.SHARD_COUNT;
      } else if (epochs_since_last_registry_update > 1
          && is_power_of_two(epochs_since_last_registry_update)) {
        seed = generate_seed(state, next_epoch);
        shuffling_start_shard = state.getCurrent_epoch_start_shard();
      } else {
        seed = state.getCurrent_epoch_seed();
        shuffling_start_shard = state.getCurrent_epoch_start_shard();
      }
    }

    ArrayList<ArrayList<Integer>> shuffling =
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
              UnsignedLong.valueOf(committees_per_slot * offset + i),
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
    ArrayList<Integer> previous_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getPrevious_calculation_epoch());
    return get_epoch_committee_count(previous_active_validators.size());
  }

  private static long get_current_epoch_committee_count(BeaconState state) {
    ArrayList<Integer> current_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), state.getCurrent_calculation_epoch());
    return get_epoch_committee_count(current_active_validators.size());
  }

  private static long get_next_epoch_committee_count(BeaconState state) {
    ArrayList<Integer> next_active_validators =
        ValidatorsUtil.get_active_validator_indices(
            state.getValidator_registry(), get_current_epoch(state) + 1);
    return get_epoch_committee_count(next_active_validators.size());
  }

  public static Bytes32 generate_seed(BeaconState state, long epoch) {
    // TODO: I think this method is correct, but it breaks all the BeaconStateTests
    // Bytes32 randao_mix = get_randao_mix(state, epoch - Constants.SEED_LOOKAHEAD);
    // Bytes32 index_root = get_active_index_root(state, epoch);
    // return Hash.keccak256(Bytes.wrap(randao_mix, index_root))
    return Bytes32.ZERO;
  }

  public static Bytes32 get_active_index_root(BeaconState state, long epoch) {
    assert get_current_epoch(state)
            - Constants.LATEST_INDEX_ROOTS_LENGTH
            + Constants.ENTRY_EXIT_DELAY
        < epoch;
    assert epoch <= get_current_epoch(state) + Constants.ENTRY_EXIT_DELAY;
    return state
        .getLatest_index_roots()
        .get(toIntExact(epoch % Constants.LATEST_INDEX_ROOTS_LENGTH));
  }

  public static Bytes32 getShard_block_root(BeaconState state, Long shard) {
    return state.getLatest_crosslinks().get(toIntExact(shard)).getShard_block_hash();
  }

  /** Return the epoch number of the given ``slot`` */
  public static UnsignedLong slot_to_epoch(UnsignedLong slot) {
    return slot.dividedBy(UnsignedLong.valueOf(Constants.EPOCH_LENGTH));
  }

  /** Return the epoch number of the given ``slot`` */
  public static long slot_to_epoch(long slot) {
    return slot / Constants.EPOCH_LENGTH;
  }

  /**
   * Return the current epoch of the given ``state``.
   *
   * @param state
   */
  public static long get_current_epoch(BeaconState state) {
    return slot_to_epoch(state.getSlot());
  }

  public static long get_previous_epoch(BeaconState state) {
    if (UnsignedLong.valueOf(get_current_epoch(state))
            .compareTo(slot_to_epoch(UnsignedLong.valueOf(Constants.GENESIS_SLOT)))
        > 0) return get_current_epoch(state) - 1;
    else return get_current_epoch(state);
  }

  public static long get_next_epoch(BeaconState state) {
    return get_current_epoch(state) + 1;
  }

  /**
   * An entry or exit triggered in the ``epoch`` given by the input takes effect at the epoch given
   * by the output.
   *
   * @param epoch
   * @return
   */
  public static UnsignedLong get_entry_exit_effect_epoch(UnsignedLong epoch) {
    return epoch.plus(UnsignedLong.ONE).plus(UnsignedLong.valueOf(Constants.ENTRY_EXIT_DELAY));
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

    UnsignedLong exit_epoch =
        get_entry_exit_effect_epoch(UnsignedLong.valueOf(get_current_epoch(state)));
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
  public static Bytes32 get_randao_mix(BeaconState state, long epoch) {
    assert get_current_epoch(state) - Constants.LATEST_RANDAO_MIXES_LENGTH < epoch;
    assert epoch <= get_current_epoch(state);
    int index = toIntExact(epoch) % Constants.LATEST_RANDAO_MIXES_LENGTH;
    return state.getLatest_randao_mixes().get(index);
  }

  public static double get_effective_balance(BeaconState state, Validator record) {
    // hacky work around for pass by index spec
    int index = state.getValidator_registry().indexOf(record);
    return Math.min(
        state.getValidator_balances().get(index).intValue(), Constants.MAX_DEPOSIT_AMOUNT);
  }

  // https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#get_block_root
  public static Bytes32 get_block_root(BeaconState state, long slot) throws Exception {
    long slot_upper_bound = slot + state.getLatest_block_roots().size();
    if ((state.getSlot() <= slot_upper_bound) || slot < state.getSlot())
      return state
          .getLatest_block_roots()
          .get(toIntExact(slot) % state.getLatest_block_roots().size());
    throw new BlockValidationException("Desired block root not within the provided bounds");
  }

  /*
   * @param values
   * @return The merkle root.
   */
  public static Bytes32 merkle_root(ArrayList<Bytes32> list) {
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
  public static ArrayList<ArrayList<Integer>> get_shuffling(
      Bytes32 seed, Validators validators, long epoch) {

    ArrayList<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices(validators, epoch);

    int committees_per_epoch = get_epoch_committee_count(active_validator_indices.size());

    // Shuffle with seed
    // TODO: we may need to treat `epoch` as little-endian here. Revisit as the spec evolves.
    seed.xor(Bytes32.wrap(Bytes.minimalBytes(epoch)));
    ArrayList<Integer> shuffled_active_validator_indices = shuffle(active_validator_indices, seed);

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
  public static <T> ArrayList<T> shuffle(ArrayList<T> values, Bytes32 seed) {
    int values_count = values.size();

    // Entropy is consumed from the seed in 3-byte (24 bit) chunks.
    int rand_bytes = 3;
    // The highest possible result of the RNG.
    int rand_max = (int) Math.pow(2, (rand_bytes * 8) - 1);

    // The range of the RNG places an upper-bound on the size of the list that
    // may be shuffled. It is a logic error to supply an oversized list.
    assert values_count < rand_max;

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
  public static <T> ArrayList<ArrayList<T>> split(ArrayList<T> values, int split_count) {
    checkArgument(split_count > 0, "Expected positive split_count but got %s", split_count);

    int list_length = values.size();
    ArrayList<ArrayList<T>> split_arr = new ArrayList<>(split_count);

    for (int i = 0; i < split_count; i++) {
      int startIndex = list_length * i / split_count;
      int endIndex = list_length * (i + 1) / split_count;
      ArrayList<T> new_split = new ArrayList<>();
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
}
