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

package tech.pegasys.teku.spec.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.bytes_to_int64;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.uint_to_bytes;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.teku.util.config.Constants.SHUFFLE_ROUND_COUNT;

import com.google.common.primitives.UnsignedBytes;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

@Deprecated
class CommitteeUtil {

  /**
   * Return the shuffled validator index corresponding to ``seed`` (and ``index_count``).
   *
   * @param index
   * @param index_count
   * @param seed
   * @deprecated CommitteeUtil should be accessed via Spec.getCommitteeUtil().computeShuffledIndex()
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_merkle_branch</a>
   */
  @Deprecated
  static int compute_shuffled_index(int index, int index_count, Bytes32 seed) {
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

  /**
   * Return from ``indices`` a random index sampled by effective balance.
   *
   * @param state
   * @param indices
   * @param seed
   * @return
   */
  @Deprecated
  static int compute_proposer_index(BeaconState state, List<Integer> indices, Bytes32 seed) {
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
}
