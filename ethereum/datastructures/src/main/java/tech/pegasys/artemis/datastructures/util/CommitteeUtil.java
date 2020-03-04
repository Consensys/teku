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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.bytes_to_int;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_seed;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.util.config.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateCache;

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
  public static Integer compute_shuffled_index(int index, int index_count, Bytes32 seed) {
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
      List<Integer> indices, Bytes32 seed, int index, int count) {
    int start = Math.floorDiv(indices.size() * index, count);
    int end = Math.floorDiv(indices.size() * (index + 1), count);
    return IntStream.range(start, end)
        .map(i -> indices.get(compute_shuffled_index(i, indices.size(), seed)))
        .boxed()
        .collect(Collectors.toList());
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
                  get_active_validator_indices(state, epoch),
                  get_seed(state, epoch, DOMAIN_BEACON_ATTESTER),
                  committeeIndex,
                  count);
            });
  }
}
