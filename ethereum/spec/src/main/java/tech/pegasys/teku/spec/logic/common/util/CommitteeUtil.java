/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.util.ByteUtils.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.util.ByteUtils.uintToBytes;

import com.google.common.primitives.UnsignedBytes;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;

public class CommitteeUtil {
  private final SpecConfig specConfig;

  public CommitteeUtil(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  public int computeShuffledIndex(int index, int index_count, Bytes32 seed) {
    checkArgument(index < index_count, "CommitteeUtil.computeShuffledIndex1");

    int indexRet = index;
    final int shuffleRoundCount = specConfig.getShuffleRoundCount();

    for (int round = 0; round < shuffleRoundCount; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          bytesToUInt64(Hash.sha2_256(Bytes.wrap(seed, roundAsByte)).slice(0, 8))
              .mod(index_count)
              .intValue();
      int flip = Math.floorMod(pivot + index_count - indexRet, index_count);
      int position = Math.max(indexRet, flip);

      Bytes positionDiv256 = uintToBytes(Math.floorDiv(position, 256), 4);
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

  public int computeProposerIndex(BeaconState state, List<Integer> indices, Bytes32 seed) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");
    UInt64 MAX_RANDOM_BYTE = UInt64.valueOf(255); // Math.pow(2, 8) - 1;
    int i = 0;
    final int total = indices.size();
    Bytes32 hash = null;
    while (true) {
      int candidate_index = indices.get(computeShuffledIndex(i % total, total, seed));
      if (i % 32 == 0) {
        hash = Hash.sha2_256(Bytes.concatenate(seed, uintToBytes(Math.floorDiv(i, 32), 8)));
      }
      int random_byte = UnsignedBytes.toInt(hash.get(i % 32));
      UInt64 effective_balance = state.getValidators().get(candidate_index).getEffective_balance();
      if (effective_balance
          .times(MAX_RANDOM_BYTE)
          .isGreaterThanOrEqualTo(specConfig.getMaxEffectiveBalance().times(random_byte))) {
        return candidate_index;
      }
      i++;
    }
  }

  public int getAggregatorModulo(final int committeeSize) {
    return specConfig.getTargetAggregatorsPerCommittee() == 0
        ? 1
        : Math.max(1, committeeSize / specConfig.getTargetAggregatorsPerCommittee());
  }

  public void shuffleList(int[] input, Bytes32 seed) {

    int listSize = input.length;
    if (listSize == 0) {
      return;
    }

    for (int round = specConfig.getShuffleRoundCount() - 1; round >= 0; round--) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          bytesToUInt64(Hash.sha2_256(Bytes.wrap(seed, roundAsByte)).slice(0, 8))
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
            hashBytes = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, uintToBytes(i / 256, 4)));
          }
        } else {
          flip = pivot + listSize - i;
          bitIndex = flip & 0xff;
          if (bitIndex == 0xff || i == pivot + 1) {
            hashBytes = Hash.sha2_256(Bytes.wrap(seed, roundAsByte, uintToBytes(flip / 256, 4)));
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

  public static boolean isAggregator(final BLSSignature slot_signature, final int modulo) {
    return bytesToUInt64(Hash.sha2_256(slot_signature.toSSZBytes()).slice(0, 8))
        .mod(modulo)
        .isZero();
  }

  List<Integer> computeCommittee(
      BeaconState state, List<Integer> indices, Bytes32 seed, int index, int count) {
    int start = Math.floorDiv(indices.size() * index, count);
    int end = Math.floorDiv(indices.size() * (index + 1), count);
    return computeCommitteeShuffle(state, indices, seed, start, end);
  }

  private List<Integer> shuffleList(List<Integer> input, Bytes32 seed) {
    int[] indexes = input.stream().mapToInt(i -> i).toArray();
    shuffleList(indexes, seed);
    return Arrays.stream(indexes).boxed().collect(Collectors.toList());
  }

  List<Integer> computeCommitteeShuffle(
      BeaconState state, List<Integer> indices, Bytes32 seed, int fromIndex, int toIndex) {
    if (fromIndex < toIndex) {
      int indexCount = indices.size();
      checkArgument(fromIndex < indexCount, "CommitteeUtil.getShuffledIndex1");
      checkArgument(toIndex <= indexCount, "CommitteeUtil.getShuffledIndex1");
    }
    return BeaconStateCache.getTransitionCaches(state)
        .getCommitteeShuffle()
        .get(seed, s -> shuffleList(indices, s))
        .subList(fromIndex, toIndex);
  }
}
