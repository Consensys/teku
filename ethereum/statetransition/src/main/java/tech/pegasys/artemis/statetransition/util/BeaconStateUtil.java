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
import java.util.HashMap;
import java.util.Iterator;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.ShardCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.BeaconState;

public class BeaconStateUtil {

  public static double calc_total_balance(BeaconState state) {
    return 0.0d;
  }

  public static ArrayList<HashMap<Long, ShardCommittee>> get_crosslink_committees_at_slot(
      BeaconState state, long slot) throws BlockValidationException {
    long state_epoch_slot = slot - (slot % Constants.EPOCH_LENGTH);

    if (isOffsetEqualToSlot(slot, state_epoch_slot)) {
      // problems with get_shuffling implementation. Should take 3 parameters and this version takes
      // 4
      // 0 has been entered as a place holder parameter to cover the difference
      long committees_per_slot = get_previous_epoch_committee_count_per_slot(state);
      ArrayList<ArrayList<ShardCommittee>> shuffling =
          BeaconState.get_shuffling(
              state.getPrevious_epoch_randao_mix(),
              state.getValidator_registry(),
              0,
              state.getPrevious_epoch_calculation_slot());
      long offset = slot % Constants.EPOCH_LENGTH;
      long slot_start_shard = 0l;

      if (slot < state_epoch_slot)
        slot_start_shard =
            (getPrevious_epoch_start_shard(state) + committees_per_slot * offset)
                % Constants.EPOCH_LENGTH;
      else
        slot_start_shard =
            (getCurrent_epoch_start_shard(state) + committees_per_slot * offset)
                % Constants.EPOCH_LENGTH;

      ArrayList<HashMap<Long, ShardCommittee>> crosslink_committees_at_slot =
          new ArrayList<HashMap<Long, ShardCommittee>>();
      Iterator<ArrayList<ShardCommittee>> itr = shuffling.iterator();
      for (ArrayList<ShardCommittee> committees : shuffling) {
        for (int i = 0; i < committees_per_slot; i++) {
          HashMap<Long, ShardCommittee> committee = new HashMap<Long, ShardCommittee>();
          committee.put(
              committees_per_slot * offset + i,
              committees.get(toIntExact(slot_start_shard + i) % Constants.SHARD_COUNT));
          crosslink_committees_at_slot.add(committee);
        }
      }
      return crosslink_committees_at_slot;
    } else
      throw new BlockValidationException(
          "calc_total_balance: Exception was thrown for failure of isOffsetEqualToSlot checking slot offset could not be calculated with values provided.");
  }

  private static long getCurrent_epoch_start_shard(BeaconState state) {
    // todo
    return 0l;
  }

  private static long get_previous_epoch_committee_count_per_slot(BeaconState state) {
    // todo
    return 0l;
  }

  private static long getPrevious_epoch_start_shard(BeaconState state) {
    // todo
    return 0l;
  }

  private static boolean isOffsetEqualToSlot(long slot, long state_epoch_slot) {
    return (state_epoch_slot <= slot + Constants.EPOCH_LENGTH)
        && (slot < state_epoch_slot + Constants.EPOCH_LENGTH);
  }

  public static Bytes32 getShard_block_root(BeaconState state, Long shard) {
    return state.getLatest_crosslinks().get(toIntExact(shard)).getShard_block_hash();
  }

  /**
   * Return the epoch number of the given ``slot``
   *
   * @return
   */
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
    if (get_current_epoch(state) > slot_to_epoch(Constants.GENESIS_SLOT))
      return get_current_epoch(state) - 1;
    else return get_current_epoch(state);
  }

  public static long get_next_epoch(BeaconState state) {
    return get_current_epoch(state) + 1;
  }

  /**
   * Return the randao mix at a recent ``epoch``.
   *
   * @return
   */
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
        state.getValidator_balances().get(index).intValue(),
        Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH);
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
   * Shuffles ``validators`` into shard committees using ``seed`` as entropy.
   *
   * @param seed
   * @param validators
   * @param epoch
   * @return
   */
  public static ArrayList<ArrayList<ShardCommittee>> get_new_shuffling(
      Bytes32 seed, Validators validators, long epoch) {
    ArrayList<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices_at_epoch(validators, epoch);

    int committees_per_slot =
        clamp(
            1,
            Constants.SHARD_COUNT / Constants.EPOCH_LENGTH,
            active_validator_indices.size()
                / Constants.EPOCH_LENGTH
                / Constants.TARGET_COMMITTEE_SIZE);

    // Shuffle with seed
    ArrayList<Integer> shuffled_active_validator_indices = shuffle(active_validator_indices, seed);

    // Split the shuffled list into epoch_length pieces
    ArrayList<ArrayList<Integer>> validators_per_slot =
        split(shuffled_active_validator_indices, Constants.EPOCH_LENGTH);

    ArrayList<ArrayList<ShardCommittee>> output = new ArrayList<>();
    for (int slot = 0; slot < validators_per_slot.size(); slot++) {
      // Split the shuffled list into committees_per_slot pieces
      ArrayList<ArrayList<Integer>> shard_indices =
          split(validators_per_slot.get(slot), committees_per_slot);
      ArrayList<ShardCommittee> shard_committees = new ArrayList<>();

      // todo after refactor of the spec this method requires rework
      int shard_id_start = 0; // crosslinking_start_shard + slot * committees_per_slot;

      for (int shard_position = 0; shard_position < shard_indices.size(); shard_position++) {
        shard_committees.add(
            new ShardCommittee(
                UnsignedLong.valueOf((shard_id_start + shard_position) % Constants.SHARD_COUNT),
                shard_indices.get(shard_position),
                UnsignedLong.valueOf(active_validator_indices.size())));
      }

      output.add(shard_committees);
    }

    return output;
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
}
