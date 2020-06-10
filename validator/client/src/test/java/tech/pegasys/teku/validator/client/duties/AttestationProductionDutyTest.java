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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.util.async.SafeFuture.failedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.logging.ValidatorLogger;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

class AttestationProductionDutyTest {

  private static final UnsignedLong SLOT = UnsignedLong.valueOf(1488);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private final AttestationProductionDuty duty =
      new AttestationProductionDuty(SLOT, forkProvider, validatorApiChannel);

  @BeforeEach
  public void setUp() {
    when(forkProvider.getForkInfo()).thenReturn(completedFuture(fork));
  }

  @Test
  public void shouldReportCorrectProducedType() {
    assertThat(duty.getProducedType()).isEqualTo("attestation");
  }

  @Test
  public void shouldNotProduceAnyAttestationsWhenNoValidatorsAdded() {
    performAndReportDuty();

    verifyNoInteractions(validatorApiChannel, validatorLogger);
  }

  @Test
  public void shouldFailWhenUnsignedAttestationCanNotBeCreated() {
    final Validator validator = createValidator();
    when(validatorApiChannel.createUnsignedAttestation(SLOT, 0))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<Attestation>> attestationFuture = duty.addValidator(validator, 0, 5);
    performAndReportDuty();

    assertThat(attestationFuture).isCompletedWithValue(Optional.empty());
    verify(validatorLogger)
        .dutyFailed(eq(duty.getProducedType()), eq(SLOT), any(IllegalStateException.class));
    verifyNoMoreInteractions(validatorLogger);
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

    performAndReportDuty();

    assertThat(attestationResult1).isCompletedWithValue(Optional.empty());
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);
    verify(validatorLogger)
        .dutyCompleted(
            duty.getProducedType(),
            SLOT,
            1,
            Set.of(unsignedAttestation.getData().getBeacon_block_root()));
    verify(validatorLogger)
        .dutyFailed(eq(duty.getProducedType()), eq(SLOT), any(IllegalStateException.class));
    verifyNoMoreInteractions(validatorLogger);
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

    performAndReportDuty();

    assertThat(attestationResult1).isCompletedExceptionally();
    assertThatThrownBy(attestationResult1::join).hasRootCause(failure);
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);

    verify(validatorLogger)
        .dutyCompleted(
            duty.getProducedType(),
            SLOT,
            1,
            Set.of(unsignedAttestation.getData().getBeacon_block_root()));
    verify(validatorLogger).dutyFailed(duty.getProducedType(), SLOT, failure);
    verifyNoMoreInteractions(validatorLogger);
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

    performAndReportDuty();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(unsignedAttestation));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);

    verify(validatorLogger)
        .dutyCompleted(
            duty.getProducedType(),
            SLOT,
            1,
            Set.of(unsignedAttestation.getData().getBeacon_block_root()));
    verify(validatorLogger).dutyFailed(duty.getProducedType(), SLOT, signingFailure);
    verifyNoMoreInteractions(validatorLogger);
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
    performAndReportDuty();
    assertThat(attestationResult).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation);
    verify(validatorLogger)
        .dutyCompleted(
            duty.getProducedType(),
            SLOT,
            1,
            Set.of(unsignedAttestation.getData().getBeacon_block_root()));
    verifyNoMoreInteractions(validatorLogger);
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
    performAndReportDuty();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(unsignedAttestation));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation));
    assertThat(attestationResult3).isCompletedWithValue(Optional.of(unsignedAttestation));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation1);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation2);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation3);

    // Should have only needed to create one unsigned attestation and reused it for each validator
    verify(validatorApiChannel, times(1)).createUnsignedAttestation(any(), anyInt());
    verify(validatorLogger)
        .dutyCompleted(
            duty.getProducedType(),
            SLOT,
            3,
            Set.of(unsignedAttestation.getData().getBeacon_block_root()));
    verifyNoMoreInteractions(validatorLogger);
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

    performAndReportDuty();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(unsignedAttestation1));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation2));
    assertThat(attestationResult3).isCompletedWithValue(Optional.of(unsignedAttestation1));

    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation1);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation2);
    verify(validatorApiChannel).sendSignedAttestation(expectedAttestation3);

    // Need to create an unsigned attestation for each committee
    verify(validatorApiChannel, times(2)).createUnsignedAttestation(any(), anyInt());
    verify(validatorLogger)
        .dutyCompleted(
            duty.getProducedType(),
            SLOT,
            3,
            Set.of(
                unsignedAttestation1.getData().getBeacon_block_root(),
                unsignedAttestation2.getData().getBeacon_block_root()));
    verifyNoMoreInteractions(validatorLogger);
  }

  public Validator createValidator() {
    final Signer signer = mock(Signer.class);
    return new Validator(dataStructureUtil.randomPublicKey(), signer, Optional.empty());
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

  private void performAndReportDuty() {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    result.join().report(duty.getProducedType(), SLOT, validatorLogger);
  }
}
