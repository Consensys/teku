/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

public class ElectraAttestationValidatorTest extends DenebAttestationValidatorTest {

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalElectra(configAdapter);
  }

  @Test
  public void shouldRejectAggregateForMultipleCommittees() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .create(
                attestation.getAggregationBits(),
                attestation.getData(),
                attestation.getAggregateSignature(),
                () -> attestation.getSchema().getCommitteeBitsSchema().orElseThrow().ofBits(1, 3));

    // Sanity check
    assertThat(wrongAttestation.getCommitteeBitsRequired().getBitCount()).isGreaterThan(1);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Rejecting attestation because committee bits count is not 1"));
  }

  @Test
  public void shouldRejectAggregateWithAttestationDataIndexNonZero() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData nonZeroIndexData =
        new AttestationData(
            correctAttestationData.getSlot(),
            UInt64.ONE,
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .create(
                attestation.getAggregationBits(),
                nonZeroIndexData,
                attestation.getAggregateSignature(),
                attestation::getCommitteeBitsRequired);

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isNotEqualTo(UInt64.ZERO);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Attestation data index must be 0 for Electra, but was %s.",
                wrongAttestation.getData().getIndex()));
  }

  @Test
  public void shouldRejectSingleAttestationWithAttestationDataIndexNonZero() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData nonZeroIndexData =
        new AttestationData(
            correctAttestationData.getSlot(),
            UInt64.ONE,
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getSingleAttestationSchema()
            .create(UInt64.ONE, UInt64.ONE, nonZeroIndexData, attestation.getAggregateSignature());

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isNotEqualTo(UInt64.ZERO);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Attestation data index must be 0 for Electra, but was %s.",
                wrongAttestation.getData().getIndex()));
  }
}
