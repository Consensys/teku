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

package tech.pegasys.teku.statetransition.attestation.utils;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

public interface AttestationBitsAggregator {

  static AttestationBitsAggregator fromEmptyFromAttestationSchema(
      AttestationSchema<?> attestationSchema, Supplier<Int2IntMap> committeesSize) {
    return attestationSchema
        .toVersionElectra()
        .map(
            schema ->
                AttestationBitsAggregatorElectra.fromAttestationSchema(
                    schema, committeesSize.get()))
        .orElseGet(() -> AttestationBitsAggregatorPhase0.fromAttestationSchema(attestationSchema));
  }

  static AttestationBitsAggregator of(ValidatableAttestation attestation) {
    return attestation
        .getAttestation()
        .getCommitteeBits()
        .map(
            committeeBits ->
                (AttestationBitsAggregator)
                    new AttestationBitsAggregatorElectra(
                        attestation.getAttestation().getAggregationBits(),
                        committeeBits,
                        attestation.getCommitteesSize().orElseThrow()))
        .orElseGet(
            () ->
                new AttestationBitsAggregatorPhase0(
                    attestation.getAttestation().getAggregationBits()));
  }

  static AttestationBitsAggregator of(
      Attestation attestation, Supplier<Int2IntMap> committeesSize) {
    return attestation
        .getCommitteeBits()
        .map(
            committeeBits ->
                (AttestationBitsAggregator)
                    new AttestationBitsAggregatorElectra(
                        attestation.getAggregationBits(), committeeBits, committeesSize.get()))
        .orElseGet(() -> new AttestationBitsAggregatorPhase0(attestation.getAggregationBits()));
  }

  static AttestationBitsAggregator of(AttestationBitsAggregator attestationBitsCalculator) {
    if (attestationBitsCalculator.requiresCommitteeBits()) {
      return new AttestationBitsAggregatorElectra(
          attestationBitsCalculator.getAggregationBits(),
          attestationBitsCalculator.getCommitteeBits(),
          attestationBitsCalculator.getCommitteesSize());
    }
    return new AttestationBitsAggregatorPhase0(attestationBitsCalculator.getAggregationBits());
  }

  void or(AttestationBitsAggregator other);

  boolean aggregateWith(Attestation other);

  void or(Attestation other);

  boolean isSuperSetOf(Attestation other);

  SszBitlist getAggregationBits();

  SszBitvector getCommitteeBits();

  Int2IntMap getCommitteesSize();

  boolean requiresCommitteeBits();
}
