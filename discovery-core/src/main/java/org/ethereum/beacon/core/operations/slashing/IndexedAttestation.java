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

package org.ethereum.beacon.core.operations.slashing;

import com.google.common.base.Objects;
import java.util.List;
import java.util.function.Function;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.collections.ReadList;

/**
 * Slashable attestation data structure.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#indexedattestation">IndexedAttestation</a>
 *     in the spec.
 */
@SSZSerializable
public class IndexedAttestation {
  /** Indices with custody bit equal to 0. */
  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final ReadList<Integer, ValidatorIndex> custodyBit0Indices;
  /** Indices with custody bit equal to 1. */
  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final ReadList<Integer, ValidatorIndex> custodyBit1Indices;
  /** Attestation data */
  @SSZ private final AttestationData data;
  /** Aggregate signature */
  @SSZ private final BLSSignature signature;

  public IndexedAttestation(
      List<ValidatorIndex> custodyBit0Indices,
      List<ValidatorIndex> custodyBit1Indices,
      AttestationData data,
      BLSSignature signature,
      SpecConstants specConstants) {
    this(
        ReadList.wrap(
            custodyBit0Indices,
            Function.identity(),
            specConstants.getMaxValidatorsPerCommittee().longValue()),
        ReadList.wrap(
            custodyBit1Indices,
            Function.identity(),
            specConstants.getMaxValidatorsPerCommittee().longValue()),
        data,
        signature);
  }

  public IndexedAttestation(
      ReadList<Integer, ValidatorIndex> custodyBit0Indices,
      ReadList<Integer, ValidatorIndex> custodyBit1Indices,
      AttestationData data,
      BLSSignature signature,
      SpecConstants specConstants) {
    this(
        custodyBit0Indices.maxSize() == ReadList.VARIABLE_SIZE
            ? custodyBit0Indices.cappedCopy(
                specConstants.getMaxValidatorsPerCommittee().longValue())
            : custodyBit0Indices,
        custodyBit1Indices.maxSize() == ReadList.VARIABLE_SIZE
            ? custodyBit1Indices.cappedCopy(
                specConstants.getMaxValidatorsPerCommittee().longValue())
            : custodyBit1Indices,
        data,
        signature);
  }

  private IndexedAttestation(
      ReadList<Integer, ValidatorIndex> custodyBit0Indices,
      ReadList<Integer, ValidatorIndex> custodyBit1Indices,
      AttestationData data,
      BLSSignature signature) {
    this.custodyBit0Indices = custodyBit0Indices;
    this.custodyBit1Indices = custodyBit1Indices;
    this.data = data;
    this.signature = signature;
  }

  public ReadList<Integer, ValidatorIndex> getCustodyBit0Indices() {
    return custodyBit0Indices;
  }

  public ReadList<Integer, ValidatorIndex> getCustodyBit1Indices() {
    return custodyBit1Indices;
  }

  public AttestationData getData() {
    return data;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IndexedAttestation that = (IndexedAttestation) o;
    return Objects.equal(custodyBit0Indices, that.custodyBit0Indices)
        && Objects.equal(custodyBit1Indices, that.custodyBit1Indices)
        && Objects.equal(data, that.data)
        && Objects.equal(signature, that.signature);
  }

  @Override
  public int hashCode() {
    int result = custodyBit0Indices.hashCode();
    result = 31 * result + custodyBit1Indices.hashCode();
    result = 31 * result + data.hashCode();
    result = 31 * result + signature.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "IndexedAttestation["
        + "data="
        + data.toString()
        + ", custodyBit0Indices="
        + custodyBit0Indices
        + ", custodyBit1Indices="
        + custodyBit1Indices
        + ", sig="
        + signature
        + "]";
  }
}
