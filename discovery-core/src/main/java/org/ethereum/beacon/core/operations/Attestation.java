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

package org.ethereum.beacon.core.operations;

import static tech.pegasys.artemis.util.collections.ReadList.VARIABLE_SIZE;

import com.google.common.base.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.collections.Bitlist;

/**
 * Attests on a block linked to particular slot in particular shard.
 *
 * @see BeaconBlockBody
 * @see AttestationData
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#attestation">Attestation
 *     in the spec</a>
 */
@SSZSerializable
public class Attestation {

  /** A bitfield where each bit corresponds to a validator attested to the {@link #data}. */
  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final Bitlist aggregationBits;
  /** Attestation data object. */
  @SSZ private final AttestationData data;
  /** Proof of custody bitfield. */
  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final Bitlist custodyBits;
  /** A product of aggregation of signatures from different validators to {@link #data}. */
  @SSZ private final BLSSignature signature;

  public Attestation(
      Bitlist aggregationBits,
      AttestationData data,
      Bitlist custodyBits,
      BLSSignature signature,
      SpecConstants specConstants) {
    this(
        aggregationBits.maxSize() == VARIABLE_SIZE
            ? aggregationBits.cappedCopy(specConstants.getMaxValidatorsPerCommittee().longValue())
            : aggregationBits,
        data,
        custodyBits.maxSize() == VARIABLE_SIZE
            ? custodyBits.cappedCopy(specConstants.getMaxValidatorsPerCommittee().longValue())
            : custodyBits,
        signature);
  }

  private Attestation(
      Bitlist aggregationBits, AttestationData data, Bitlist custodyBits, BLSSignature signature) {
    this.aggregationBits = aggregationBits;
    this.data = data;
    this.custodyBits = custodyBits;
    this.signature = signature;
  }

  public AttestationData getData() {
    return data;
  }

  public Bitlist getAggregationBits() {
    return aggregationBits;
  }

  public Bitlist getCustodyBits() {
    return custodyBits;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Attestation that = (Attestation) o;
    return Objects.equal(data, that.data)
        && Objects.equal(aggregationBits, that.aggregationBits)
        && Objects.equal(custodyBits, that.custodyBits)
        && Objects.equal(signature, that.signature);
  }

  @Override
  public int hashCode() {
    int result = data.hashCode();
    result = 31 * result + aggregationBits.hashCode();
    result = 31 * result + custodyBits.hashCode();
    result = 31 * result + signature.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return toString(null, null);
  }

  private String getSignerIndices() {
    return aggregationBits.getBits().stream().map(i -> "" + i).collect(Collectors.joining("+"));
  }

  public String toString(@Nullable SpecConstants spec, @Nullable Time beaconStart) {
    return "Attestation["
        + data.toString()
        + ", attesters="
        + getSignerIndices()
        + ", custodyBits="
        + custodyBits
        + ", sig="
        + signature
        + "]";
  }

  public String toStringShort(@Nullable SpecConstants spec) {
    return "epoch="
        + getData().getTarget().getEpoch().toString()
        + "/"
        + getData().getCrosslink().getShard().toString()
        + "/"
        + getData().getBeaconBlockRoot().toStringShort()
        + "/"
        + getSignerIndices();
  }
}
