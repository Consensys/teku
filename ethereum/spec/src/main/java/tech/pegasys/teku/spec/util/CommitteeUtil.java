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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.bytes_to_int64;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.uint_to_bytes;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import com.google.common.primitives.UnsignedBytes;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;

import java.util.List;

public class CommitteeUtil {
  private final SpecConstants specConstants;

  public CommitteeUtil(final SpecConstants specConstants) {
    this.specConstants = specConstants;
  }

  public int computeShuffledIndex(int index, int index_count, Bytes32 seed) {
    checkArgument(index < index_count, "CommitteeUtil.computeShuffledIndex1");

    int indexRet = index;
    final int shuffleRoundCount = specConstants.getShuffleRoundCount();

    for (int round = 0; round < shuffleRoundCount; round++) {

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

  public int computeProposerIndex(BeaconState state, List<Integer> indices, Bytes32 seed) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");
    UInt64 MAX_RANDOM_BYTE = UInt64.valueOf(255); // Math.pow(2, 8) - 1;
    int i = 0;
    final int total = indices.size();
    Bytes32 hash = null;
    while (true) {
      int candidate_index = indices.get(computeShuffledIndex(i % total, total, seed));
      if (i % 32 == 0) {
        hash = Hash.sha2_256(Bytes.concatenate(seed, uint_to_bytes(Math.floorDiv(i, 32), 8)));
      }
      int random_byte = UnsignedBytes.toInt(hash.get(i % 32));
      UInt64 effective_balance = state.getValidators().get(candidate_index).getEffective_balance();
      if (effective_balance
          .times(MAX_RANDOM_BYTE)
          .isGreaterThanOrEqualTo(specConstants.getMaxEffectiveBalance().times(random_byte))) {
        return candidate_index;
      }
      i++;
    }
  }

  public int getAggregatorModulo(final int committeeSize) {
    return specConstants.getTargetAggregatorsPerCommittee() == 0
        ? 1
        : Math.max(1, committeeSize / specConstants.getTargetAggregatorsPerCommittee());
  }
}
