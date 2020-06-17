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

package tech.pegasys.teku.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.bytes_to_int;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_seed;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.int_to_bytes;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.teku.util.config.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.TARGET_AGGREGATORS_PER_COMMITTEE;

import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;

public class CommitteeUtil {

  /**
   * Return the shuffled validator index corresponding to ``seed`` (and ``index_count``).
   *
   * @param index
   * @param index_count
   * @param seed
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_merkle_branch</a>
   */
  public static int compute_shuffled_index(int index, int index_count, Bytes32 seed) {
    checkArgument(index < index_count, "CommitteeUtil.get_shuffled_index1");

    int indexRet = index;
    byte[] powerOfTwoNumbers = {1, 2, 4, 8, 16, 32, 64, (byte) 128};

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          toIntExact(
              Long.remainderUnsigned(
                  bytes_to_int(Hash.sha2_256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  index_count));
      int flip = Math.floorMod(pivot - indexRet, index_count);
      if (flip < 0) {
        // Account for flip being negative
        flip += index_count;
      }

      int position = (indexRet < flip) ? flip : indexRet;

      Bytes positionDiv256 = int_to_bytes(Math.floorDiv(position, 256), 4);
      Bytes source = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, positionDiv256));

      // The byte type is signed in Java, but the right shift should be fine as we just use bit 0.
      // But we can't use % in the normal way because of signedness, so we `& 1` instead.
      byte theByte = source.get(Math.floorDiv(Math.floorMod(position, 256), 8));
      byte theMask = powerOfTwoNumbers[Math.floorMod(position, 8)];
      if ((theByte & theMask) != 0) {
        indexRet = flip;
      }
    }

    return indexRet;
  }

  private static List<Integer> shuffle_list(List<Integer> input, Bytes32 seed) {
    int[] indexes = input.stream().mapToInt(i -> i).toArray();
    shuffle_list(indexes, seed);
    return Arrays.stream(indexes).boxed().collect(Collectors.toList());
  }

  /**
   * Ported from Lighthouse client:
   * https://github.com/sigp/lighthouse/blob/master/eth2/utils/swap_or_not_shuffle/src/shuffle_list.rs
   * NOTE: shuffling the whole list with this method 200x-300x faster than shuffling it by
   * individual indexes
   */
  public static void shuffle_list(int[] input, Bytes32 seed) {
    if (input.length == 0) {
      return;
    }
    int rounds = SHUFFLE_ROUND_COUNT;
    int list_size = input.length;

    for (int r = rounds - 1; r >= 0; r--) {
      Bytes roundAsByte = Bytes.of((byte) r);

      int pivot =
          toIntExact(
              Long.remainderUnsigned(
                  bytes_to_int(Hash.sha2_256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  list_size));

      int mirror = (pivot + 1) >> 1;
      Bytes source = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, int_to_bytes(pivot >> 8, 4)));
      byte byte_v = source.get((pivot & 0xFF) >> 3);

      for (int i = 0; i < mirror; i++) {
        int j = pivot - i;

        if ((j & 0xff) == 0xff) {
          source = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, int_to_bytes(j >> 8, 4)));
        }

        if ((j & 0x07) == 0x07) {
          byte_v = source.get((j & 0xff) >> 3);
        }
        int bit_v = (byte_v >> (j & 0x07)) & 0x01;

        if (bit_v == 1) {
          int tmp = input[i];
          input[i] = input[j];
          input[j] = tmp;
        }
      }
      mirror = (pivot + list_size + 1) >> 1;
      int end = list_size - 1;

      source = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, int_to_bytes(end >> 8, 4)));
      byte_v = source.get((end & 0xff) >> 3);

      for (int i = pivot + 1, loop_iter = 0; i < mirror; i++, loop_iter++) {

        int j = end - loop_iter;

        if ((j & 0xff) == 0xff) {
          source = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, int_to_bytes(j >> 8, 4)));
        }

        if ((j & 0x07) == 0x07) {
          byte_v = source.get((j & 0xff) >> 3);
        }
        int bit_v = (byte_v >> (j & 0x07)) & 0x01;

        if (bit_v == 1) {
          int tmp = input[i];
          input[i] = input[j];
          input[j] = tmp;
        }
      }
    }
  }

  /**
   * Return from ``indices`` a random index sampled by effective balance.
   *
   * @param state
   * @param indices
   * @param seed
   * @return
   */
  public static int compute_proposer_index(BeaconState state, List<Integer> indices, Bytes32 seed) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");
    UnsignedLong MAX_RANDOM_BYTE = UnsignedLong.valueOf(255); // Math.pow(2, 8) - 1;
    int i = 0;
    Bytes32 hash = null;
    while (true) {
      int candidate_index =
          indices.get(compute_shuffled_index(i % indices.size(), indices.size(), seed));
      if (i % 32 == 0) {
        hash = Hash.sha2_256(Bytes.concatenate(seed, int_to_bytes(Math.floorDiv(i, 32), 8)));
      }
      int random_byte = UnsignedBytes.toInt(hash.get(i % 32));
      UnsignedLong effective_balance =
          state.getValidators().get(candidate_index).getEffective_balance();
      if (effective_balance
              .times(MAX_RANDOM_BYTE)
              .compareTo(
                  UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE)
                      .times(UnsignedLong.valueOf(random_byte)))
          >= 0) {
        return candidate_index;
      }
      i++;
    }
  }

  private static List<Integer> compute_committee_shuffle(
      BeaconState state, List<Integer> indices, Bytes32 seed, int fromIndex, int toIndex) {
    if (fromIndex < toIndex) {
      int index_count = indices.size();
      checkArgument(fromIndex < index_count, "CommitteeUtil.get_shuffled_index1");
      checkArgument(toIndex <= index_count, "CommitteeUtil.get_shuffled_index1");
    }
    return BeaconStateCache.getTransitionCaches(state)
        .getCommitteeShuffle()
        .get(seed, s -> shuffle_list(indices, s))
        .subList(fromIndex, toIndex);
  }

  /**
   * Computes indices of a new committee based upon the seed parameter
   *
   * @param indices
   * @param seed
   * @param index
   * @param count
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_merkle_branch</a>
   */
  public static List<Integer> compute_committee(
      BeaconState state, List<Integer> indices, Bytes32 seed, int index, int count) {
    int start = Math.floorDiv(indices.size() * index, count);
    int end = Math.floorDiv(indices.size() * (index + 1), count);
    return compute_committee_shuffle(state, indices, seed, start, end);
  }

  /**
   * Return the beacon committee at ``slot`` for ``index``.
   *
   * @param state
   * @param slot
   * @param index
   * @return
   */
  public static List<Integer> get_beacon_committee(
      BeaconState state, UnsignedLong slot, UnsignedLong index) {
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconCommittee()
        .get(
            Pair.of(slot, index),
            p -> {
              UnsignedLong epoch = compute_epoch_at_slot(slot);
              UnsignedLong committees_per_slot = get_committee_count_at_slot(state, slot);
              int committeeIndex =
                  toIntExact(
                      slot.mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
                          .times(committees_per_slot)
                          .plus(index)
                          .longValue());
              int count =
                  toIntExact(
                      committees_per_slot.times(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).longValue());
              return compute_committee(
                  state,
                  get_active_validator_indices(state, epoch),
                  get_seed(state, epoch, DOMAIN_BEACON_ATTESTER),
                  committeeIndex,
                  count);
            });
  }

  public static int getAggregatorModulo(final int committeeSize) {
    return TARGET_AGGREGATORS_PER_COMMITTEE == 0
        ? 1
        : Math.max(1, committeeSize / TARGET_AGGREGATORS_PER_COMMITTEE);
  }

  public static boolean isAggregator(final BLSSignature slot_signature, final int modulo) {
    return (bytes_to_int(Hash.sha2_256(slot_signature.toBytes()).slice(0, 8)) % modulo) == 0;
  }

  public static int getSubnetId(final Attestation attestation) {
    return committeeIndexToSubnetId(attestation.getData().getIndex());
  }

  public static int committeeIndexToSubnetId(final UnsignedLong committeeIndex) {
    return committeeIndexToSubnetId(toIntExact(committeeIndex.longValue()));
  }

  public static int committeeIndexToSubnetId(final int committeeIndex) {
    return committeeIndex % ATTESTATION_SUBNET_COUNT;
  }
}
