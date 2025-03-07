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

import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;

public class AggregatorUtil {
  public static Attestation aggregateAttestations(
      final Attestation firstAttestation, final Attestation... attestations) {
    SszBitlist aggregateBits = firstAttestation.getAggregationBits();
    final List<BLSSignature> signatures = new ArrayList<>();
    signatures.add(firstAttestation.getAggregateSignature());

    final Supplier<SszBitvector> committeeBitsSupplier;
    final IntSet participationIndices = new IntOpenHashSet();

    for (Attestation attestation : attestations) {
      aggregateBits = aggregateBits.or(attestation.getAggregationBits());
      signatures.add(attestation.getAggregateSignature());
      if (firstAttestation.getCommitteeBits().isPresent()) {
        participationIndices.addAll(attestation.getCommitteeBitsRequired().getAllSetBits());
        checkState(
            participationIndices.size() == 1,
            "this test util doesn't support generating cross-committee aggregations");
      }
    }

    if (firstAttestation.getCommitteeBits().isPresent()) {
      committeeBitsSupplier =
          firstAttestation
              .getSchema()
              .getCommitteeBitsSchema()
              .map(
                  committeeBitsSchema ->
                      (Supplier<SszBitvector>)
                          () -> committeeBitsSchema.ofBits(participationIndices))
              .orElse(() -> null);
    } else {
      committeeBitsSupplier = () -> null;
    }

    return firstAttestation
        .getSchema()
        .create(
            aggregateBits,
            firstAttestation.getData(),
            BLS.aggregate(signatures),
            committeeBitsSupplier);
  }
}
