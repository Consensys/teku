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

public interface AttestationBitsCalculator {

  static AttestationBitsCalculator fromEmptyFromAttestationSchema(
      AttestationSchema<?> attestationSchema, Supplier<Int2IntMap> committeesSize) {
    return attestationSchema
        .toVersionElectra()
        .map(
            schema ->
                AttestationBitsCalculatorElectra.fromAttestationSchema(
                    schema, committeesSize.get()))
        .orElseGet(() -> AttestationBitsCalculatorPhase0.fromAttestationSchema(attestationSchema));
  }

  static AttestationBitsCalculator of(ValidatableAttestation attestation) {
    return attestation
        .getAttestation()
        .getCommitteeBits()
        .map(
            committeeBits ->
                (AttestationBitsCalculator)
                    new AttestationBitsCalculatorElectra(
                        attestation.getAttestation().getAggregationBits(),
                        committeeBits,
                        attestation.getCommitteesSize().orElseThrow()))
        .orElseGet(
            () ->
                new AttestationBitsCalculatorPhase0(
                    attestation.getAttestation().getAggregationBits()));
  }

  static AttestationBitsCalculator of(
      Attestation attestation, Supplier<Int2IntMap> committeesSize) {
    return attestation
        .getCommitteeBits()
        .map(
            committeeBits ->
                (AttestationBitsCalculator)
                    new AttestationBitsCalculatorElectra(
                        attestation.getAggregationBits(), committeeBits, committeesSize.get()))
        .orElseGet(() -> new AttestationBitsCalculatorPhase0(attestation.getAggregationBits()));
  }

  static AttestationBitsCalculator of(AttestationBitsCalculator attestationBitsCalculator) {
    if (attestationBitsCalculator.requiresCommitteeBits()) {
      new AttestationBitsCalculatorElectra(
          attestationBitsCalculator.getAggregationBits(),
          attestationBitsCalculator.getCommitteeBits(),
          attestationBitsCalculator.getCommitteesSize());
    }
    return new AttestationBitsCalculatorPhase0(attestationBitsCalculator.getAggregationBits());
  }

  void aggregateWith(AttestationBitsCalculator other);

  void aggregateWith(Attestation other);

  boolean supersedes(Attestation other);

  boolean canAggregateWith(Attestation other);

  SszBitlist getAggregationBits();

  SszBitvector getCommitteeBits();

  Int2IntMap getCommitteesSize();

  boolean requiresCommitteeBits();
}
