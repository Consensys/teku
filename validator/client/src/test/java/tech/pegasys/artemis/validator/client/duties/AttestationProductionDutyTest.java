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

package tech.pegasys.artemis.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;
import static tech.pegasys.artemis.util.async.SafeFuture.failedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.Signer;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.ForkInfo;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.ssz.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.ForkProvider;
import tech.pegasys.artemis.validator.client.Validator;

class AttestationProductionDutyTest {

  private static final UnsignedLong SLOT = UnsignedLong.valueOf(1488);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final AttestationProductionDuty duty =
      new AttestationProductionDuty(SLOT, forkProvider, validatorApiChannel);

  @BeforeEach
  public void setUp() {
    when(forkProvider.getForkInfo()).thenReturn(completedFuture(fork));
  }

  @Test
  public void shouldNotProduceAnyAttestationsWhenNoValidatorsAdded() {
    assertThat(duty.performDuty()).isCompleted();

    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  public void shouldFailWhenUnsignedAttestationCanNotBeCreated() {
    final Validator validator = createValidator();
    when(validatorApiChannel.createUnsignedAttestation(SLOT, 0))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<Attestation>> attestationFuture = duty.addValidator(validator, 0, 5);
    final SafeFuture<?> result = duty.performDuty();

    assertThat(result).isCompletedExceptionally();
    assertThat(attestationFuture).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void shouldPublishProducedAttestationsWhenSomeUnsignedAttestationsCanNotBeCreated() {
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final int validator1CommitteeIndex = 0;
    final int validator1CommitteePosition = 5;
    final int validator2CommitteeIndex = 1;
    final int validator2CommitteePosition = 3;
    when(validatorApiChannel.createUnsignedAttestation(SLOT, validator1CommitteeIndex))
        .thenReturn(completedFuture(Optional.empty()));
    final Attestation unsignedAttestation =
        expectCreateUnsignedAttestation(validator2CommitteeIndex);
    final Attestation expectedAttestation =
        expectSignAttestation(validator2, validator2CommitteePosition, unsignedAttestation);

    final SafeFuture<Optional<Attestation>> attestationResult1 =
        duty.addValidator(validator1, validator1CommitteeIndex, validator1CommitteePosition);
    final SafeFuture<Optional<Attestation>> attestationResult2 =
        duty.addValidator(validator2, validator2CommitteeIndex, validator2CommitteePosition);

    assertThat(duty.performDuty()).isCompletedExceptionally();
    assertThat(attestationResult1).isCompletedWithValue(Optional.empty());
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);
  }

  @Test
  public void shouldPublishProducedAttestationsWhenSomeUnsignedAttestationsFail() {
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final int validator1CommitteeIndex = 0;
    final int validator1CommitteePosition = 5;
    final int validator2CommitteeIndex = 1;
    final int validator2CommitteePosition = 3;
    final RuntimeException failure = new RuntimeException("Golly gee");
    when(validatorApiChannel.createUnsignedAttestation(SLOT, validator1CommitteeIndex))
        .thenReturn(failedFuture(failure));
    final Attestation unsignedAttestation =
        expectCreateUnsignedAttestation(validator2CommitteeIndex);
    final Attestation expectedAttestation =
        expectSignAttestation(validator2, validator2CommitteePosition, unsignedAttestation);

    final SafeFuture<Optional<Attestation>> attestationResult1 =
        duty.addValidator(validator1, validator1CommitteeIndex, validator1CommitteePosition);
    final SafeFuture<Optional<Attestation>> attestationResult2 =
        duty.addValidator(validator2, validator2CommitteeIndex, validator2CommitteePosition);

    final SafeFuture<?> result = duty.performDuty();
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(failure);
    assertThat(attestationResult1).isCompletedExceptionally();
    assertThatThrownBy(attestationResult1::join).hasRootCause(failure);
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);
  }

  @Test
  public void shouldPublishProducedAttestationsWhenSignerFailsForSomeAttestations() {
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final int committeeIndex = 0;
    final int validator1CommitteePosition = 5;
    final int validator2CommitteePosition = 3;
    final Attestation unsignedAttestation = expectCreateUnsignedAttestation(committeeIndex);
    final RuntimeException signingFailure = new RuntimeException("Gosh darn");
    when(validator1.getSigner().signAttestationData(unsignedAttestation.getData(), fork))
        .thenReturn(failedFuture(signingFailure));
    final Attestation expectedAttestation =
        expectSignAttestation(validator2, validator2CommitteePosition, unsignedAttestation);

    final SafeFuture<Optional<Attestation>> attestationResult1 =
        duty.addValidator(validator1, committeeIndex, validator1CommitteePosition);
    final SafeFuture<Optional<Attestation>> attestationResult2 =
        duty.addValidator(validator2, committeeIndex, validator2CommitteePosition);

    final SafeFuture<?> result = duty.performDuty();
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(signingFailure);
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(unsignedAttestation));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);
  }

  @Test
  public void shouldCreateAttestationForSingleValidator() {
    final int committeeIndex = 3;
    final int committeePosition = 6;
    final Validator validator = createValidator();

    final Attestation unsignedAttestation = expectCreateUnsignedAttestation(committeeIndex);
    final Attestation expectedAttestation =
        expectSignAttestation(validator, committeePosition, unsignedAttestation);

    final SafeFuture<Optional<Attestation>> attestationResult =
        duty.addValidator(validator, committeeIndex, committeePosition);
    assertThat(duty.performDuty()).isCompleted();
    assertThat(attestationResult).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);
  }

  @Test
  public void shouldCreateAttestationForMultipleValidatorsInSameCommittee() {
    final int committeeIndex = 3;
    final int validator1CommitteePosition = 6;
    final int validator2CommitteePosition = 2;
    final int validator3CommitteePosition = 5;
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final Validator validator3 = createValidator();

    final Attestation unsignedAttestation = expectCreateUnsignedAttestation(committeeIndex);
    final Attestation expectedAttestation1 =
        expectSignAttestation(validator1, validator1CommitteePosition, unsignedAttestation);
    final Attestation expectedAttestation2 =
        expectSignAttestation(validator2, validator2CommitteePosition, unsignedAttestation);
    final Attestation expectedAttestation3 =
        expectSignAttestation(validator3, validator3CommitteePosition, unsignedAttestation);

    final SafeFuture<Optional<Attestation>> attestationResult1 =
        duty.addValidator(validator1, committeeIndex, validator1CommitteePosition);
    final SafeFuture<Optional<Attestation>> attestationResult2 =
        duty.addValidator(validator2, committeeIndex, validator2CommitteePosition);
    final SafeFuture<Optional<Attestation>> attestationResult3 =
        duty.addValidator(validator3, committeeIndex, validator3CommitteePosition);
    assertThat(duty.performDuty()).isCompleted();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(unsignedAttestation));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));
    assertThat(attestationResult3).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation1);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation2);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation3);

    // Should have only needed to create one unsigned attestation and reused it for each validator
    verify(validatorApiChannel, times(1)).createUnsignedAttestation(any(), anyInt());
  }

  @Test
  public void shouldCreateAttestationForMultipleValidatorsInDifferentCommittees() {
    final int committeeIndex1 = 3;
    final int committeeIndex2 = 5;
    final int validator1CommitteePosition = 6;
    final int validator2CommitteePosition = 2;
    final int validator3CommitteePosition = 5;
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final Validator validator3 = createValidator();

    final Attestation unsignedAttestation1 = expectCreateUnsignedAttestation(committeeIndex1);
    final Attestation unsignedAttestation2 = expectCreateUnsignedAttestation(committeeIndex2);
    final Attestation expectedAttestation1 =
        expectSignAttestation(validator1, validator1CommitteePosition, unsignedAttestation1);
    final Attestation expectedAttestation2 =
        expectSignAttestation(validator2, validator2CommitteePosition, unsignedAttestation2);
    final Attestation expectedAttestation3 =
        expectSignAttestation(validator3, validator3CommitteePosition, unsignedAttestation1);

    final SafeFuture<Optional<Attestation>> attestationResult1 =
        duty.addValidator(validator1, committeeIndex1, validator1CommitteePosition);
    final SafeFuture<Optional<Attestation>> attestationResult2 =
        duty.addValidator(validator2, committeeIndex2, validator2CommitteePosition);
    final SafeFuture<Optional<Attestation>> attestationResult3 =
        duty.addValidator(validator3, committeeIndex1, validator3CommitteePosition);

    assertThat(duty.performDuty()).isCompleted();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(unsignedAttestation1));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation2));
    assertThat(attestationResult3).isCompletedWithValue(Optional.of(unsignedAttestation1));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation1);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation2);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation3);

    // Need to create an unsigned attestation for each committee
    verify(validatorApiChannel, times(2)).createUnsignedAttestation(any(), anyInt());
  }

  public Validator createValidator() {
    final Signer signer = mock(Signer.class);
    return new Validator(dataStructureUtil.randomPublicKey(), signer);
  }

  public Attestation expectSignAttestation(
      final Validator validator,
      final int committeePosition,
      final Attestation unsignedAttestation) {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    when(validator.getSigner().signAttestationData(unsignedAttestation.getData(), fork))
        .thenReturn(completedFuture(signature));
    return createExpectedAttestation(unsignedAttestation, committeePosition, signature);
  }

  public Attestation expectCreateUnsignedAttestation(final int committeeIndex) {
    final Attestation unsignedAttestation =
        new Attestation(
            new Bitlist(10, Constants.MAX_VALIDATORS_PER_COMMITTEE),
            dataStructureUtil.randomAttestationData(SLOT),
            BLSSignature.empty());

    when(validatorApiChannel.createUnsignedAttestation(SLOT, committeeIndex))
        .thenReturn(completedFuture(Optional.of(unsignedAttestation)));
    return unsignedAttestation;
  }

  public Attestation createExpectedAttestation(
      final Attestation unsignedAttestation,
      final int committeePosition,
      final BLSSignature signature) {
    final Bitlist expectedAggregationBits = new Bitlist(unsignedAttestation.getAggregation_bits());
    expectedAggregationBits.setBit(committeePosition);
    return new Attestation(expectedAggregationBits, unsignedAttestation.getData(), signature);
  }
}
