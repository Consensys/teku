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

package org.ethereum.beacon.core.state;

import static java.util.Collections.emptyList;
import static tech.pegasys.artemis.util.collections.ReadList.VARIABLE_SIZE;

import java.util.List;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Compact committee type.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compactcommittee">CompactCommittee</a>
 *     in the spec.
 */
@SSZSerializable
public class CompactCommittee {

  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final ReadList<Integer, BLSPubkey> pubkeys;

  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final ReadList<Integer, UInt64> compactValidators;

  public CompactCommittee(
      ReadList<Integer, BLSPubkey> pubkeys,
      ReadList<Integer, UInt64> compactValidators,
      SpecConstants specConstants) {
    this.pubkeys =
        pubkeys.maxSize() == VARIABLE_SIZE
            ? pubkeys.cappedCopy(specConstants.getMaxValidatorsPerCommittee().longValue())
            : pubkeys;
    this.compactValidators =
        compactValidators.maxSize() == VARIABLE_SIZE
            ? compactValidators.cappedCopy(specConstants.getMaxValidatorsPerCommittee().longValue())
            : compactValidators;
  }

  public CompactCommittee(
      List<BLSPubkey> pubkeys, List<UInt64> compactValidators, SpecConstants specConstants) {
    this(
        ReadList.wrap(
            pubkeys, Integer::new, specConstants.getMaxValidatorsPerCommittee().longValue()),
        ReadList.wrap(
            compactValidators,
            Integer::new,
            specConstants.getMaxValidatorsPerCommittee().longValue()),
        specConstants);
  }

  public static CompactCommittee getEmpty(SpecConstants specConstants) {
    return new CompactCommittee(emptyList(), emptyList(), specConstants);
  }

  public ReadList<Integer, BLSPubkey> getPubkeys() {
    return pubkeys;
  }

  public ReadList<Integer, UInt64> getCompactValidators() {
    return compactValidators;
  }
}
