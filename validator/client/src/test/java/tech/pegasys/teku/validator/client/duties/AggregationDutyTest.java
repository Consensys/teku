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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

class AggregationDutyTest {

  public static final UInt64 SLOT = UInt64.valueOf(2832);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final Signer signer1 = mock(Signer.class);
  private final Signer signer2 = mock(Signer.class);
  private final Validator validator1 =
      new Validator(dataStructureUtil.randomPublicKey(), signer1, new FileBackedGraffitiProvider());
  private final Validator validator2 =
      new Validator(dataStructureUtil.randomPublicKey(), signer2, new FileBackedGraffitiProvider());
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private final AggregationDuty duty =
      new AggregationDuty(SLOT, validatorApiChannel, forkProvider, validatorLogger);

  @BeforeEach
  public void setUp() {
    when(forkProvider.getForkInfo()).thenReturn(SafeFuture.completedFuture(forkInfo));
  }

  @Test
  public void shouldReturnCorrectProducedType() {
    assertThat(duty.getProducedType()).isEqualTo("aggregate");
  }

  @Test
  public void shouldBeCompleteWhenNoValidatorsAdded() {
    performAndReportDuty();
    verifyNoInteractions(validatorApiChannel, validatorLogger);
  }

  @Test
  public void shouldProduceAggregateAndProof() {
    final int validatorIndex = 1;
    final int attestationCommitteeIndex = 2;
    final BLSSignature proof = dataStructureUtil.randomSignature();
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validatorIndex,
        proof,
        attestationCommitteeIndex,
        completedFuture(Optional.of(attestationData)));

    when(validatorApiChannel.createAggregate(SLOT, attestationData.hashTreeRoot()))
        .thenReturn(completedFuture(Optional.of(aggregate)));

    final AggregateAndProof expectedAggregateAndProof =
        new AggregateAndProof(UInt64.valueOf(validatorIndex), aggregate, proof);
    final BLSSignature aggregateSignature = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(expectedAggregateAndProof, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendAggregateAndProof(
            new SignedAggregateAndProof(expectedAggregateAndProof, aggregateSignature));
  }

  @Test
  public void shouldProduceAggregateAndProofForMultipleCommittees() {
    final int validator1Index = 1;
    final int validator1CommitteeIndex = 2;
    final BLSSignature validator1Proof = dataStructureUtil.randomSignature();

    final int validator2Index = 6;
    final int validator2CommitteeIndex = 0;
    final BLSSignature validator2Proof = dataStructureUtil.randomSignature();

    final AttestationData committee1AttestationData = dataStructureUtil.randomAttestationData();
    final AttestationData committee2AttestationData = dataStructureUtil.randomAttestationData();
    final Attestation committee1Aggregate = dataStructureUtil.randomAttestation();
    final Attestation committee2Aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validator1Index,
        validator1Proof,
        validator1CommitteeIndex,
        completedFuture(Optional.of(committee1AttestationData)));
    duty.addValidator(
        validator2,
        validator2Index,
        validator2Proof,
        validator2CommitteeIndex,
        completedFuture(Optional.of(committee2AttestationData)));

    when(validatorApiChannel.createAggregate(SLOT, committee1AttestationData.hashTreeRoot()))
        .thenReturn(completedFuture(Optional.of(committee1Aggregate)));
    when(validatorApiChannel.createAggregate(SLOT, committee2AttestationData.hashTreeRoot()))
        .thenReturn(completedFuture(Optional.of(committee2Aggregate)));

    final AggregateAndProof aggregateAndProof1 =
        new AggregateAndProof(
            UInt64.valueOf(validator1Index), committee1Aggregate, validator1Proof);
    final AggregateAndProof aggregateAndProof2 =
        new AggregateAndProof(
            UInt64.valueOf(validator2Index), committee2Aggregate, validator2Proof);
    final BLSSignature aggregateSignature1 = dataStructureUtil.randomSignature();
    final BLSSignature aggregateSignature2 = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(aggregateAndProof1, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature1));
    when(signer2.signAggregateAndProof(aggregateAndProof2, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature2));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendAggregateAndProof(
            new SignedAggregateAndProof(aggregateAndProof1, aggregateSignature1));

    verify(validatorApiChannel)
        .sendAggregateAndProof(
            new SignedAggregateAndProof(aggregateAndProof2, aggregateSignature2));
  }

  @Test
  public void shouldProduceSingleAggregateAndProofWhenMultipleValidatorsAggregateSameCommittee() {
    final int committeeIndex = 2;

    final int validator1Index = 1;
    final BLSSignature validator1Proof = dataStructureUtil.randomSignature();

    final int validator2Index = 6;
    final BLSSignature validator2Proof = dataStructureUtil.randomSignature();

    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validator1Index,
        validator1Proof,
        committeeIndex,
        completedFuture(Optional.of(attestationData)));
    duty.addValidator(
        validator2,
        validator2Index,
        validator2Proof,
        committeeIndex,
        completedFuture(Optional.of(attestationData)));

    when(validatorApiChannel.createAggregate(SLOT, attestationData.hashTreeRoot()))
        .thenReturn(completedFuture(Optional.of(aggregate)));

    final AggregateAndProof aggregateAndProof =
        new AggregateAndProof(UInt64.valueOf(validator1Index), aggregate, validator1Proof);
    final BLSSignature aggregateSignature1 = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(aggregateAndProof, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature1));

    performAndReportDuty();

    verify(validatorApiChannel)
        .sendAggregateAndProof(new SignedAggregateAndProof(aggregateAndProof, aggregateSignature1));
    // Only one proof should be sent.
    verify(validatorApiChannel, times(1)).sendAggregateAndProof(any());
    verify(validatorLogger)
        .dutyCompleted(
            duty.getProducedType(), SLOT, 1, Set.of(aggregate.getData().getBeacon_block_root()));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldFailWhenAttestationDataNotCreated() {
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), 2, completedFuture(Optional.empty()));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyFailed(
            eq(duty.getProducedType()),
            eq(SLOT),
            eq(duty.getValidatorIdString()),
            any(IllegalStateException.class));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldFailWhenAttestationDataCompletesExceptionally() {
    final RuntimeException exception = new RuntimeException("Doh!");
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), 2, failedFuture(exception));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyFailed(duty.getProducedType(), SLOT, duty.getValidatorIdString(), exception);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldReportWhenAggregateNotCreated() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    duty.addValidator(
        validator1,
        1,
        dataStructureUtil.randomSignature(),
        2,
        completedFuture(Optional.of(attestationData)));
    when(validatorApiChannel.createAggregate(SLOT, attestationData.hashTreeRoot()))
        .thenReturn(completedFuture(Optional.empty()));

    assertThat(duty.performDuty()).isCompleted();
    verify(validatorApiChannel, never()).sendAggregateAndProof(any());
    verify(validatorLogger).aggregationSkipped(SLOT, 2);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldFailWhenAggregateFails() {
    final Exception exception = new RuntimeException("Whoops");
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    duty.addValidator(
        validator1,
        1,
        dataStructureUtil.randomSignature(),
        2,
        completedFuture(Optional.of(attestationData)));
    when(validatorApiChannel.createAggregate(SLOT, attestationData.hashTreeRoot()))
        .thenReturn(failedFuture(exception));

    performAndReportDuty();
    verify(validatorApiChannel, never()).sendAggregateAndProof(any());
    verify(validatorLogger)
        .dutyFailed(duty.getProducedType(), SLOT, duty.getValidatorIdString(), exception);
    verifyNoMoreInteractions(validatorLogger);
  }

  private void performAndReportDuty() {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    result
        .join()
        .report(duty.getProducedType(), SLOT, duty.getValidatorIdString(), validatorLogger);
  }
}
