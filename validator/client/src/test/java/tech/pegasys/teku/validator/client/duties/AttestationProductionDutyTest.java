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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;

class AttestationProductionDutyTest {

  private static final String TYPE = "attestation";
  private static final UInt64 SLOT = UInt64.valueOf(1488);

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private final AttestationProductionDuty duty =
      new AttestationProductionDuty(
          SLOT,
          forkProvider,
          validatorApiChannel,
          new BatchAttestationSendingStrategy<>(validatorApiChannel::sendSignedAttestations));

  @BeforeEach
  public void setUp() {
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(validatorApiChannel.sendSignedAttestations(any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
  }

  @Test
  public void shouldNotProduceAnyAttestationsWhenNoValidatorsAdded() {
    performAndReportDuty();

    verifyNoInteractions(validatorApiChannel, validatorLogger);
  }

  @Test
  public void shouldFailWhenUnsignedAttestationCanNotBeCreated() {
    final Validator validator = createValidator();
    when(validatorApiChannel.createAttestationData(SLOT, 0))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<AttestationData>> attestationFuture =
        duty.addValidator(validator, 0, 5, 10, 11);
    performAndReportDuty();

    assertThat(attestationFuture).isCompletedWithValue(Optional.empty());
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            any(IllegalStateException.class));
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
    final int validator2CommitteeSize = 8;
    when(validatorApiChannel.createAttestationData(SLOT, validator1CommitteeIndex))
        .thenReturn(completedFuture(Optional.empty()));
    final AttestationData attestationData = expectCreateAttestationData(validator2CommitteeIndex);
    final Attestation expectedAttestation =
        expectSignAttestation(
            validator2, validator2CommitteePosition, validator2CommitteeSize, attestationData);

    final SafeFuture<Optional<AttestationData>> attestationResult1 =
        duty.addValidator(
            validator1, validator1CommitteeIndex, validator1CommitteePosition, 10, 11);
    final SafeFuture<Optional<AttestationData>> attestationResult2 =
        duty.addValidator(
            validator2,
            validator2CommitteeIndex,
            validator2CommitteePosition,
            10,
            validator2CommitteeSize);

    performAndReportDuty();

    assertThat(attestationResult1).isCompletedWithValue(Optional.empty());
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(attestationData));

    verify(validatorApiChannel).sendSignedAttestations(List.of(expectedAttestation));
    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(attestationData.getBeacon_block_root()));
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator1.getPublicKey().toAbbreviatedString())),
            any(IllegalStateException.class));
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
    final int validator2CommitteeSize = 12;
    final RuntimeException failure = new RuntimeException("Golly gee");
    when(validatorApiChannel.createAttestationData(SLOT, validator1CommitteeIndex))
        .thenReturn(failedFuture(failure));
    final AttestationData attestationData = expectCreateAttestationData(validator2CommitteeIndex);
    final Attestation expectedAttestation =
        expectSignAttestation(
            validator2, validator2CommitteePosition, validator2CommitteeSize, attestationData);

    final SafeFuture<Optional<AttestationData>> attestationResult1 =
        duty.addValidator(
            validator1, validator1CommitteeIndex, validator1CommitteePosition, 10, 11);
    final SafeFuture<Optional<AttestationData>> attestationResult2 =
        duty.addValidator(
            validator2,
            validator2CommitteeIndex,
            validator2CommitteePosition,
            10,
            validator2CommitteeSize);

    performAndReportDuty();

    assertThat(attestationResult1).isCompletedExceptionally();
    assertThatThrownBy(attestationResult1::join).hasRootCause(failure);
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(attestationData));

    verify(validatorApiChannel).sendSignedAttestations(List.of(expectedAttestation));

    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(attestationData.getBeacon_block_root()));
    verify(validatorLogger)
        .dutyFailed(TYPE, SLOT, Set.of(validator1.getPublicKey().toAbbreviatedString()), failure);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldPublishProducedAttestationsWhenSignerFailsForSomeAttestations() {
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final int committeeIndex = 0;
    final int validator1CommitteePosition = 5;
    final int validator2CommitteePosition = 3;
    final int validator1CommitteeSize = 23;
    final int validator2CommitteeSize = 39;
    final AttestationData attestationData = expectCreateAttestationData(committeeIndex);
    final RuntimeException signingFailure = new RuntimeException("Gosh darn");
    when(validator1.getSigner().signAttestationData(attestationData, fork))
        .thenReturn(failedFuture(signingFailure));
    final Attestation expectedAttestation =
        expectSignAttestation(
            validator2, validator2CommitteePosition, validator2CommitteeSize, attestationData);

    final SafeFuture<Optional<AttestationData>> attestationResult1 =
        duty.addValidator(
            validator1, committeeIndex, validator1CommitteePosition, 10, validator1CommitteeSize);
    final SafeFuture<Optional<AttestationData>> attestationResult2 =
        duty.addValidator(
            validator2, committeeIndex, validator2CommitteePosition, 10, validator2CommitteeSize);

    performAndReportDuty();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(attestationData));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(attestationData));

    verify(validatorApiChannel).sendSignedAttestations(List.of(expectedAttestation));

    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(attestationData.getBeacon_block_root()));
    verify(validatorLogger)
        .dutyFailed(
            TYPE, SLOT, Set.of(validator1.getPublicKey().toAbbreviatedString()), signingFailure);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldCreateAttestationForSingleValidator() {
    final int committeeIndex = 3;
    final int committeePosition = 6;
    final int committeeSize = 22;
    final Validator validator = createValidator();

    final AttestationData attestationData = expectCreateAttestationData(committeeIndex);
    final Attestation expectedAttestation =
        expectSignAttestation(validator, committeePosition, committeeSize, attestationData);

    final SafeFuture<Optional<AttestationData>> attestationResult =
        duty.addValidator(validator, committeeIndex, committeePosition, 10, committeeSize);
    performAndReportDuty();
    assertThat(attestationResult).isCompletedWithValue(Optional.of(attestationData));

    verify(validatorApiChannel).sendSignedAttestations(List.of(expectedAttestation));
    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(attestationData.getBeacon_block_root()));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldReportFailureWhenAttestationIsInvalid() {
    final int committeeIndex = 3;
    final int committeePosition = 6;
    final int committeeSize = 22;
    final Validator validator = createValidator();

    final AttestationData attestationData = expectCreateAttestationData(committeeIndex);
    final Attestation expectedAttestation =
        expectSignAttestation(validator, committeePosition, committeeSize, attestationData);

    when(validatorApiChannel.sendSignedAttestations(List.of(expectedAttestation)))
        .thenReturn(
            SafeFuture.completedFuture(
                List.of(new SubmitDataError(UInt64.ZERO, "Naughty attestation"))));

    final SafeFuture<Optional<AttestationData>> attestationResult =
        duty.addValidator(validator, committeeIndex, committeePosition, 10, committeeSize);
    performAndReportDuty();
    assertThat(attestationResult).isCompletedWithValue(Optional.of(attestationData));

    verify(validatorApiChannel).sendSignedAttestations(List.of(expectedAttestation));
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            argThat(
                error ->
                    error instanceof RestApiReportedException
                        && error.getMessage().equals("Naughty attestation")));
    verifyNoMoreInteractions(validatorLogger);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateAttestationForMultipleValidatorsInSameCommittee() {
    final int committeeIndex = 3;
    final int committeeSize = 33;
    final int validator1CommitteePosition = 6;
    final int validator2CommitteePosition = 2;
    final int validator3CommitteePosition = 5;
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final Validator validator3 = createValidator();

    final AttestationData attestationData = expectCreateAttestationData(committeeIndex);
    final Attestation expectedAttestation1 =
        expectSignAttestation(
            validator1, validator1CommitteePosition, committeeSize, attestationData);
    final Attestation expectedAttestation2 =
        expectSignAttestation(
            validator2, validator2CommitteePosition, committeeSize, attestationData);
    final Attestation expectedAttestation3 =
        expectSignAttestation(
            validator3, validator3CommitteePosition, committeeSize, attestationData);

    final SafeFuture<Optional<AttestationData>> attestationResult1 =
        duty.addValidator(
            validator1, committeeIndex, validator1CommitteePosition, 10, committeeSize);
    final SafeFuture<Optional<AttestationData>> attestationResult2 =
        duty.addValidator(
            validator2, committeeIndex, validator2CommitteePosition, 10, committeeSize);
    final SafeFuture<Optional<AttestationData>> attestationResult3 =
        duty.addValidator(
            validator3, committeeIndex, validator3CommitteePosition, 10, committeeSize);
    performAndReportDuty();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(attestationData));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(attestationData));
    assertThat(attestationResult3).isCompletedWithValue(Optional.of(attestationData));

    ArgumentCaptor<List<Attestation>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(validatorApiChannel).sendSignedAttestations(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue())
        .containsExactlyInAnyOrder(
            expectedAttestation1, expectedAttestation2, expectedAttestation3);

    // Should have only needed to create one unsigned attestation and reused it for each validator
    verify(validatorApiChannel, times(1)).createAttestationData(any(), anyInt());
    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 3, Set.of(attestationData.getBeacon_block_root()));
    verifyNoMoreInteractions(validatorLogger);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateAttestationForMultipleValidatorsInDifferentCommittees() {
    final int committeeIndex1 = 3;
    final int committeeIndex2 = 5;
    final int committeeSize1 = 15;
    final int committeeSize2 = 20;
    final int validator1CommitteePosition = 6;
    final int validator2CommitteePosition = 2;
    final int validator3CommitteePosition = 5;
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final Validator validator3 = createValidator();

    final AttestationData unsignedAttestation1 = expectCreateAttestationData(committeeIndex1);
    final AttestationData unsignedAttestation2 = expectCreateAttestationData(committeeIndex2);
    final Attestation expectedAttestation1 =
        expectSignAttestation(
            validator1, validator1CommitteePosition, committeeSize1, unsignedAttestation1);
    final Attestation expectedAttestation2 =
        expectSignAttestation(
            validator2, validator2CommitteePosition, committeeSize2, unsignedAttestation2);
    final Attestation expectedAttestation3 =
        expectSignAttestation(
            validator3, validator3CommitteePosition, committeeSize1, unsignedAttestation1);

    final SafeFuture<Optional<AttestationData>> attestationResult1 =
        duty.addValidator(
            validator1, committeeIndex1, validator1CommitteePosition, 10, committeeSize1);
    final SafeFuture<Optional<AttestationData>> attestationResult2 =
        duty.addValidator(
            validator2, committeeIndex2, validator2CommitteePosition, 10, committeeSize2);
    final SafeFuture<Optional<AttestationData>> attestationResult3 =
        duty.addValidator(
            validator3, committeeIndex1, validator3CommitteePosition, 10, committeeSize1);

    performAndReportDuty();
    assertThat(attestationResult1).isCompletedWithValue(Optional.of(unsignedAttestation1));
    assertThat(attestationResult2).isCompletedWithValue(Optional.of(unsignedAttestation2));
    assertThat(attestationResult3).isCompletedWithValue(Optional.of(unsignedAttestation1));

    ArgumentCaptor<List<Attestation>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(validatorApiChannel).sendSignedAttestations(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue())
        .containsExactlyInAnyOrder(
            expectedAttestation1, expectedAttestation2, expectedAttestation3);

    // Need to create an unsigned attestation for each committee
    verify(validatorApiChannel, times(2)).createAttestationData(any(), anyInt());
    verify(validatorLogger)
        .dutyCompleted(
            TYPE,
            SLOT,
            3,
            Set.of(
                unsignedAttestation1.getBeacon_block_root(),
                unsignedAttestation2.getBeacon_block_root()));
    verifyNoMoreInteractions(validatorLogger);
  }

  public Validator createValidator() {
    final Signer signer = mock(Signer.class);
    return new Validator(
        dataStructureUtil.randomPublicKey(), signer, new FileBackedGraffitiProvider());
  }

  public Attestation expectSignAttestation(
      final Validator validator,
      final int committeePosition,
      final int committeeSize,
      final AttestationData attestationData) {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    when(validator.getSigner().signAttestationData(attestationData, fork))
        .thenReturn(completedFuture(signature));
    return createExpectedAttestation(attestationData, committeePosition, committeeSize, signature);
  }

  public AttestationData expectCreateAttestationData(final int committeeIndex) {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(SLOT);
    when(validatorApiChannel.createAttestationData(SLOT, committeeIndex))
        .thenReturn(completedFuture(Optional.of(attestationData)));
    return attestationData;
  }

  public Attestation createExpectedAttestation(
      final AttestationData attestationData,
      final int committeePosition,
      final int committeeSize,
      final BLSSignature signature) {
    final SszBitlist expectedAggregationBits =
        Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(committeeSize, committeePosition);
    return new Attestation(expectedAggregationBits, attestationData, signature);
  }

  private void performAndReportDuty() {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    result.join().report(TYPE, SLOT, validatorLogger);
  }
}
