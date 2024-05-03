/*
 * Copyright Consensys Software Inc., 2022
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
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.ArrayUtils;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;

public class AggregatorUtil {
  public static Attestation aggregateAttestations(
      final Attestation firstAttestation, final Attestation... attestations) {
    SszBitlist aggregateBits = firstAttestation.getAggregationBits();
    final List<BLSSignature> signatures = new ArrayList<>();
    signatures.add(firstAttestation.getAggregateSignature());

    for (Attestation attestation : attestations) {
      aggregateBits = aggregateBits.or(attestation.getAggregationBits());
      signatures.add(attestation.getAggregateSignature());
    }

    final Supplier<SszBitvector> committeeBitsSupplier =
        buildCommiteeBitsSupplier(firstAttestation, attestations);

    return firstAttestation
        .getSchema()
        .create(
            aggregateBits,
            firstAttestation.getData(),
            committeeBitsSupplier,
            BLS.aggregate(signatures));
  }

  private static Supplier<SszBitvector> buildCommiteeBitsSupplier(
      final Attestation firstAttestation, final Attestation... attestations) {
    final Supplier<SszBitvector> committeeBitsSupplier;
    if (firstAttestation.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      committeeBitsSupplier =
          () ->
              Arrays.stream(ArrayUtils.addAll(attestations, firstAttestation))
                  .map(Attestation::getCommitteeBitsRequired)
                  .reduce(SszBitvector::or)
                  .orElseThrow(
                      () -> new IllegalArgumentException("Error while aggregating committee bit"));
    } else {
      committeeBitsSupplier = () -> null;
    }
    return committeeBitsSupplier;
  }
}
