/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.attestation;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

public class AggregatorUtil {
  public static Attestation aggregateAttestations(
      final Attestation firstAttestation, final Attestation... attestations) {
    final Bitlist aggregateBits = firstAttestation.getAggregation_bits().copy();
    final List<BLSSignature> signatures = new ArrayList<>();
    signatures.add(firstAttestation.getAggregate_signature());

    for (Attestation attestation : attestations) {
      aggregateBits.setAllBits(attestation.getAggregation_bits());
      signatures.add(attestation.getAggregate_signature());
    }
    return new Attestation(aggregateBits, firstAttestation.getData(), BLS.aggregate(signatures));
  }
}
