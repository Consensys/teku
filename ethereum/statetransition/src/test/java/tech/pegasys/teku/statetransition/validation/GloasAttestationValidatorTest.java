/*
 * Copyright Consensys Software Inc., 2025
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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.function.Consumer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

public class GloasAttestationValidatorTest extends ElectraAttestationValidatorTest {

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalGloas(configAdapter);
  }

  @Override
  @Test
  @Disabled(
      "This validation is applicable to Electra only. In Gloas, an index of 0 is a valid payload status.")
  public void shouldRejectAggregateWithAttestationDataIndexNonZero() {}

  @Override
  @Test
  @Disabled(
      "This validation is applicable to Electra only. In Gloas, an index of 0 is a valid payload status.")
  public void shouldRejectSingleAttestationWithAttestationDataIndexNonZero() {}

  @Test
  public void shouldRejectAggregateWithAttestationDataWithInvalidPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
        new AttestationData(
            correctAttestationData.getSlot(),
            UInt64.valueOf(2),
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .create(
                attestation.getAggregationBits(),
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature(),
                attestation::getCommitteeBitsRequired);

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isGreaterThan(UInt64.ONE);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Attestation data index must be 0 or 1 for Gloas, but was %s.",
                wrongAttestation.getData().getIndex()));
  }

  @Test
  public void shouldRejectSingleAttestationWithInvalidPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
        new AttestationData(
            correctAttestationData.getSlot(),
            UInt64.valueOf(2),
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .toVersionGloas()
            .orElseThrow()
            .getSingleAttestationSchema()
            .create(
                UInt64.ONE,
                UInt64.ONE,
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature());

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isGreaterThan(UInt64.ONE);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Attestation data index must be 0 or 1 for Gloas, but was %s.",
                wrongAttestation.getData().getIndex()));
  }

  @Test
  public void shouldRejectSingleAttestationWithIncorrectPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());
    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
        new AttestationData(
            correctAttestationData.getSlot(),
            ONE,
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .toVersionGloas()
            .orElseThrow()
            .getSingleAttestationSchema()
            .create(
                UInt64.ONE,
                UInt64.ONE,
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature());

    assertThat(wrongAttestation.getData().getIndex()).isNotEqualTo(ZERO);
    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Payload status must be 0, but was %s.", wrongAttestation.getData().getIndex()));
  }

  @Test
  public void shouldRejectAggregateAttestationWithIncorrectPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
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
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature(),
                attestation::getCommitteeBitsRequired);

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isNotEqualTo(UInt64.ZERO);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Payload status must be 0, but was %s.", wrongAttestation.getData().getIndex()));
  }
}
