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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;
import static tech.pegasys.artemis.util.async.SafeFuture.failedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.signatures.Signer;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.artemis.datastructures.state.ForkInfo;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.ForkProvider;
import tech.pegasys.artemis.validator.client.Validator;

class AggregationDutyTest {

  public static final UnsignedLong SLOT = UnsignedLong.valueOf(2832);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final Signer signer1 = mock(Signer.class);
  private final Signer signer2 = mock(Signer.class);
  private final Validator validator1 = new Validator(dataStructureUtil.randomPublicKey(), signer1);
  private final Validator validator2 = new Validator(dataStructureUtil.randomPublicKey(), signer2);

  private final AggregationDuty duty = new AggregationDuty(SLOT, validatorApiChannel, forkProvider);

  @BeforeEach
  public void setUp() {
    when(forkProvider.getForkInfo()).thenReturn(SafeFuture.completedFuture(forkInfo));
  }

  @Test
  public void shouldBeCompleteWhenNoValidatorsAdded() {
    assertThat(duty.performDuty()).isCompleted();
  }

  @Test
  public void shouldSubscribeToCommitteeTopicWhenNewCommitteeAdded() {
    final int committeeIndex = 2;
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), committeeIndex, new SafeFuture<>());
    verify(validatorApiChannel).subscribeToBeaconCommittee(committeeIndex, SLOT);
  }

  @Test
  public void shouldNotSubscribeToCommitteeTopicWhenAdditionalValidatorAdded() {
    final int committeeIndex = 2;
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), committeeIndex, new SafeFuture<>());
    duty.addValidator(
        validator2, 2, dataStructureUtil.randomSignature(), committeeIndex, new SafeFuture<>());

    verify(validatorApiChannel, times(1)).subscribeToBeaconCommittee(committeeIndex, SLOT);
  }

  @Test
  public void shouldProduceAggregateAndProof() {
    final int validatorIndex = 1;
    final int attestationCommitteeIndex = 2;
    final BLSSignature proof = dataStructureUtil.randomSignature();
    final Attestation unsignedAttestation = dataStructureUtil.randomAttestation();
    final Attestation aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validatorIndex,
        proof,
        attestationCommitteeIndex,
        completedFuture(Optional.of(unsignedAttestation)));

    when(validatorApiChannel.createAggregate(unsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(aggregate)));

    final AggregateAndProof expectedAggregateAndProof =
        new AggregateAndProof(UnsignedLong.valueOf(validatorIndex), aggregate, proof);
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

    final Attestation committee1UnsignedAttestation = dataStructureUtil.randomAttestation();
    final Attestation committee2UnsignedAttestation = dataStructureUtil.randomAttestation();
    final Attestation committee1Aggregate = dataStructureUtil.randomAttestation();
    final Attestation committee2Aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validator1Index,
        validator1Proof,
        validator1CommitteeIndex,
        completedFuture(Optional.of(committee1UnsignedAttestation)));
    duty.addValidator(
        validator2,
        validator2Index,
        validator2Proof,
        validator2CommitteeIndex,
        completedFuture(Optional.of(committee2UnsignedAttestation)));

    when(validatorApiChannel.createAggregate(committee1UnsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(committee1Aggregate)));
    when(validatorApiChannel.createAggregate(committee2UnsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(committee2Aggregate)));

    final AggregateAndProof aggregateAndProof1 =
        new AggregateAndProof(
            UnsignedLong.valueOf(validator1Index), committee1Aggregate, validator1Proof);
    final AggregateAndProof aggregateAndProof2 =
        new AggregateAndProof(
            UnsignedLong.valueOf(validator2Index), committee2Aggregate, validator2Proof);
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

    final Attestation unsignedAttestation = dataStructureUtil.randomAttestation();
    final Attestation aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validator1Index,
        validator1Proof,
        committeeIndex,
        completedFuture(Optional.of(unsignedAttestation)));
    duty.addValidator(
        validator2,
        validator2Index,
        validator2Proof,
        committeeIndex,
        completedFuture(Optional.of(unsignedAttestation)));

    when(validatorApiChannel.createAggregate(unsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(aggregate)));

    final AggregateAndProof aggregateAndProof =
        new AggregateAndProof(UnsignedLong.valueOf(validator1Index), aggregate, validator1Proof);
    final BLSSignature aggregateSignature1 = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(aggregateAndProof, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature1));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendAggregateAndProof(new SignedAggregateAndProof(aggregateAndProof, aggregateSignature1));
    // Only one proof should be sent.
    verify(validatorApiChannel, times(1)).sendAggregateAndProof(any());
  }

  @Test
  public void shouldFailWhenUnsignedAttestationNotCreated() {
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), 2, completedFuture(Optional.empty()));
    verify(validatorApiChannel).subscribeToBeaconCommittee(anyInt(), any());

    assertThat(duty.performDuty()).isCompletedExceptionally();
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldFailWhenUnsignedAttestationCompletesExceptionally() {
    final RuntimeException exception = new RuntimeException("Doh!");
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), 2, failedFuture(exception));

    final SafeFuture<?> result = duty.performDuty();
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(exception);
  }

  @Test
  public void shouldFailWhenAggregateNotCreated() {
    final Attestation unsignedAttestation = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        1,
        dataStructureUtil.randomSignature(),
        2,
        completedFuture(Optional.of(unsignedAttestation)));
    when(validatorApiChannel.createAggregate(unsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.empty()));

    assertThat(duty.performDuty()).isCompletedExceptionally();
    verify(validatorApiChannel, never()).sendAggregateAndProof(any());
  }

  @Test
  public void shouldFailWhenAggregateFails() {
    final Exception exception = new RuntimeException("Whoops");
    final Attestation unsignedAttestation = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        1,
        dataStructureUtil.randomSignature(),
        2,
        completedFuture(Optional.of(unsignedAttestation)));
    when(validatorApiChannel.createAggregate(unsignedAttestation.getData()))
        .thenReturn(failedFuture(exception));

    final SafeFuture<?> result = duty.performDuty();
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(exception);
    verify(validatorApiChannel, never()).sendAggregateAndProof(any());
  }
}
