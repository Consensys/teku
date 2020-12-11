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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.bytes_to_int64;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_per_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_seed;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.uint_to_bytes;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.teku.util.config.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.TARGET_AGGREGATORS_PER_COMMITTEE;

import com.google.common.primitives.UnsignedBytes;
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

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

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          bytes_to_int64(Hash.sha2_256(Bytes.wrap(seed, roundAsByte)).slice(0, 8))
              .mod(index_count)
              .intValue();
      int flip = Math.floorMod(pivot + index_count - indexRet, index_count);
      int position = Math.max(indexRet, flip);

      Bytes positionDiv256 = uint_to_bytes(Math.floorDiv(position, 256), 4);
      Bytes hashBytes = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, positionDiv256));

      int bitIndex = position & 0xff;
      int theByte = hashBytes.get(bitIndex / 8);
      int theBit = (theByte >> (bitIndex & 0x07)) & 1;
      if (theBit != 0) {
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
   * Shuffles a list of integers in-place with ``seed`` as entropy.
   *
   * <p>Utilizes 'swap or not' shuffling found in
   * https://link.springer.com/content/pdf/10.1007%2F978-3-642-32009-5_1.pdf See the 'generalized
   * domain' algorithm on page 3.
   *
   * <p>The result of this should be the same using compute_shuffled_index() on each index in the
   * list, but is vastly more efficient due to reuse of the hashed pseudorandom data.
   *
   * @param input The list to be shuffled.
   * @param seed Initial seed value used for randomization.
   */
  public static void shuffle_list(int[] input, Bytes32 seed) {

    int listSize = input.length;
    if (listSize == 0) {
      return;
    }

    for (int round = SHUFFLE_ROUND_COUNT - 1; round >= 0; round--) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          bytes_to_int64(Hash.sha2_256(Bytes.wrap(seed, roundAsByte)).slice(0, 8))
              .mod(listSize)
              .intValue();

      Bytes hashBytes = Bytes.EMPTY;
      int mirror1 = (pivot + 2) / 2;
      int mirror2 = (pivot + listSize) / 2;
      for (int i = mirror1; i <= mirror2; i++) {

        int flip, bitIndex;
        if (i <= pivot) {
          flip = pivot - i;
          bitIndex = i & 0xff;
          if (bitIndex == 0 || i == mirror1) {
            hashBytes = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, uint_to_bytes(i / 256, 4)));
          }
        } else {
          flip = pivot + listSize - i;
          bitIndex = flip & 0xff;
          if (bitIndex == 0xff || i == pivot + 1) {
            hashBytes = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, uint_to_bytes(flip / 256, 4)));
          }
        }

        int theByte = hashBytes.get(bitIndex / 8);
        int theBit = (theByte >> (bitIndex & 0x07)) & 1;
        if (theBit != 0) {
          int tmp = input[i];
          input[i] = input[flip];
          input[flip] = tmp;
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
    UInt64 MAX_RANDOM_BYTE = UInt64.valueOf(255); // Math.pow(2, 8) - 1;
    int i = 0;
    final int total = indices.size();
    Bytes32 hash = null;
    while (true) {
      int candidate_index = indices.get(compute_shuffled_index(i % total, total, seed));
      if (i % 32 == 0) {
        hash = Hash.sha2_256(Bytes.concatenate(seed, uint_to_bytes(Math.floorDiv(i, 32), 8)));
      }
      int random_byte = UnsignedBytes.toInt(hash.get(i % 32));
      UInt64 effective_balance = state.getValidators().get(candidate_index).getEffective_balance();
      if (effective_balance
          .times(MAX_RANDOM_BYTE)
          .isGreaterThanOrEqualTo(MAX_EFFECTIVE_BALANCE.times(random_byte))) {
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
  public static List<Integer> get_beacon_committee(BeaconState state, UInt64 slot, UInt64 index) {
    // Make sure state is within range of the slot being queried
    validateStateForCommitteeQuery(state, slot);

    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconCommittee()
        .get(
            Pair.of(slot, index),
            p -> {
              UInt64 epoch = compute_epoch_at_slot(slot);
              UInt64 committees_per_slot = get_committee_count_per_slot(state, epoch);
              int committeeIndex =
                  slot.mod(SLOTS_PER_EPOCH).times(committees_per_slot).plus(index).intValue();
              int count = committees_per_slot.times(SLOTS_PER_EPOCH).intValue();
              return compute_committee(
                  state,
                  get_active_validator_indices(state, epoch),
                  get_seed(state, epoch, DOMAIN_BEACON_ATTESTER),
                  committeeIndex,
                  count);
            });
  }

  private static void validateStateForCommitteeQuery(BeaconState state, UInt64 slot) {
    final UInt64 oldestQueryableSlot = getEarliestQueryableSlotForTargetSlot(slot);
    checkArgument(
        state.getSlot().compareTo(oldestQueryableSlot) >= 0,
        "Committee information must be derived from a state no older than the previous epoch. State at slot %s is older than cutoff slot %s",
        state.getSlot(),
        oldestQueryableSlot);
  }

  /**
   * Calculates the earliest slot queryable for assignments at the given slot
   *
   * @param slot The slot for which we want to retrieve committee information
   * @return The earliest slot from which we can query committee assignments
   */
  public static UInt64 getEarliestQueryableSlotForTargetSlot(final UInt64 slot) {
    final UInt64 epoch = compute_epoch_at_slot(slot);
    return getEarliestQueryableSlotForTargetEpoch(epoch);
  }

  /**
   * Calculates the earliest slot queryable for assignments at the given epoch
   *
   * @param epoch The epoch for which we want to retrieve committee information
   * @return The earliest slot from which we can query committee assignments
   */
  public static UInt64 getEarliestQueryableSlotForTargetEpoch(final UInt64 epoch) {
    final UInt64 previousEpoch = epoch.compareTo(UInt64.ZERO) > 0 ? epoch.minus(UInt64.ONE) : epoch;
    return compute_start_slot_at_epoch(previousEpoch);
  }

  public static int getAggregatorModulo(final int committeeSize) {
    return TARGET_AGGREGATORS_PER_COMMITTEE == 0
        ? 1
        : Math.max(1, committeeSize / TARGET_AGGREGATORS_PER_COMMITTEE);
  }

  public static boolean isAggregator(final BLSSignature slot_signature, final int modulo) {
    return bytes_to_int64(Hash.sha2_256(slot_signature.toSSZBytes()).slice(0, 8))
        .mod(modulo)
        .isZero();
  }

  /**
   * Compute the correct subnet for an attestation for Phase 0.
   *
   * <p>Note, this mimics expected Phase 1 behavior where attestations will be mapped to their shard
   * subnet.
   *
   * @param state
   * @param attestation
   * @return
   */
  public static int computeSubnetForAttestation(
      final BeaconState state, final Attestation attestation) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    final UInt64 committeeIndex = attestation.getData().getIndex();
    return computeSubnetForCommittee(state, attestationSlot, committeeIndex);
  }

  public static int computeSubnetForCommittee(
      final BeaconState state, final UInt64 attestationSlot, final UInt64 committeeIndex) {
    return computeSubnetForCommittee(
        attestationSlot,
        committeeIndex,
        get_committee_count_per_slot(state, compute_epoch_at_slot(attestationSlot)));
  }

  public static int computeSubnetForCommittee(
      final UInt64 attestationSlot, final UInt64 committeeIndex, final UInt64 committeesPerSlot) {
    final UInt64 slotsSinceEpochStart = attestationSlot.mod(SLOTS_PER_EPOCH);
    final UInt64 committeesSinceEpochStart = committeesPerSlot.times(slotsSinceEpochStart);
    return committeesSinceEpochStart.plus(committeeIndex).mod(ATTESTATION_SUBNET_COUNT).intValue();
  }
}
