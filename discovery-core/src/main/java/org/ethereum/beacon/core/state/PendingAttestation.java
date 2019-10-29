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

import static tech.pegasys.artemis.util.collections.ReadList.VARIABLE_SIZE;

import com.google.common.base.Objects;
import java.util.stream.Collectors;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.collections.Bitlist;

/**
 * An attestation data that have not been processed yet.
 *
 * @see BeaconState
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#pendingattestation">PendingAttestation
 *     in the spec</a>
 */
@SSZSerializable
public class PendingAttestation {

  /** Attester aggregation bitfield. */
  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final Bitlist aggregationBits;
  /** Signed data. */
  @SSZ private final AttestationData data;
  /** Slot in which it was included. */
  @SSZ private final SlotNumber inclusionDelay;
  /** Proposer index. */
  @SSZ private final ValidatorIndex proposerIndex;

  public PendingAttestation(
      Bitlist aggregationBits,
      AttestationData data,
      SlotNumber inclusionDelay,
      ValidatorIndex proposerIndex,
      SpecConstants specConstants) {
    this(
        aggregationBits.maxSize() == VARIABLE_SIZE
            ? aggregationBits.cappedCopy(specConstants.getMaxValidatorsPerCommittee().longValue())
            : aggregationBits,
        data,
        inclusionDelay,
        proposerIndex);
  }

  private PendingAttestation(
      Bitlist aggregationBits,
      AttestationData data,
      SlotNumber inclusionDelay,
      ValidatorIndex proposerIndex) {
    this.aggregationBits = aggregationBits;
    this.data = data;
    this.inclusionDelay = inclusionDelay;
    this.proposerIndex = proposerIndex;
  }

  public Bitlist getAggregationBits() {
    return aggregationBits;
  }

  public AttestationData getData() {
    return data;
  }

  public ValidatorIndex getProposerIndex() {
    return proposerIndex;
  }

  public SlotNumber getInclusionDelay() {
    return inclusionDelay;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PendingAttestation that = (PendingAttestation) o;
    return Objects.equal(data, that.data)
        && Objects.equal(aggregationBits, that.aggregationBits)
        && Objects.equal(proposerIndex, that.proposerIndex)
        && Objects.equal(inclusionDelay, that.inclusionDelay);
  }

  private String getSignerIndices() {
    return aggregationBits.getBits().stream().map(i -> "" + i).collect(Collectors.joining("+"));
  }

  @Override
  public String toString() {
    return "Attestation["
        + data.toString()
        + ", attesters="
        + getSignerIndices()
        + ", proposerIndex="
        + getProposerIndex()
        + ", inclusionDelay=#"
        + getInclusionDelay()
        + "]";
  }

  public String toStringShort() {
    return "epoch="
        + getData().getTarget().getEpoch()
        + "/"
        + "delay="
        + getInclusionDelay()
        + "/"
        + getData().getCrosslink().getShard().toString()
        + "/"
        + getData().getBeaconBlockRoot().toStringShort()
        + "/"
        + getSignerIndices();
  }
}
