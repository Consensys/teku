/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.operations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0;

/**
 * Interface used to represent different types of attestations ({@link AttestationPhase0} and {@link
 * tech.pegasys.teku.spec.datastructures.state.PendingAttestation})
 */
public interface Attestation extends SszData, SszContainer {

  @Override
  AttestationSchema<? extends Attestation> getSchema();

  UInt64 getEarliestSlotForForkChoiceProcessing(final Spec spec);

  Collection<Bytes32> getDependentBlockRoots();

  AttestationData getData();

  SszBitlist getAggregationBits();

  UInt64 getFirstCommitteeIndex();

  default Optional<SszBitvector> getCommitteeBits() {
    return Optional.empty();
  }

  default SszBitvector getCommitteeBitsRequired() {
    return getCommitteeBits()
        .orElseThrow(() -> new IllegalArgumentException("Missing committee bits"));
  }

  BLSSignature getAggregateSignature();

  default Optional<List<UInt64>> getCommitteeIndices() {
    return Optional.empty();
  }

  default List<UInt64> getCommitteeIndicesRequired() {
    return getCommitteeIndices()
        .orElseThrow(() -> new IllegalArgumentException("Missing committee indices"));
  }

  boolean requiresCommitteeBits();
}
