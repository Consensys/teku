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
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;

class AggregationDutyTest {

  public static final UnsignedLong SLOT = UnsignedLong.valueOf(2832);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final AggregationDuty duty = new AggregationDuty(SLOT, validatorApiChannel);

  @Test
  public void shouldBeCompleteWhenNoValidatorsAdded() {
    assertThat(duty.performDuty()).isCompleted();
  }

  @Test
  public void shouldSubscribeToCommitteeTopicWhenNewCommitteeAdded() {
    final int committeeIndex = 2;
    duty.addValidator(1, dataStructureUtil.randomSignature(), committeeIndex, new SafeFuture<>());
    verify(validatorApiChannel).subscribeToBeaconCommittee(committeeIndex, SLOT);
  }

  @Test
  public void shouldNotSubscribeToCommitteeTopicWhenAdditionalValidatorAdded() {
    final int committeeIndex = 2;
    duty.addValidator(1, dataStructureUtil.randomSignature(), committeeIndex, new SafeFuture<>());
    duty.addValidator(2, dataStructureUtil.randomSignature(), committeeIndex, new SafeFuture<>());

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
        validatorIndex,
        proof,
        attestationCommitteeIndex,
        completedFuture(Optional.of(unsignedAttestation)));

    when(validatorApiChannel.createAggregate(unsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(aggregate)));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendAggregateAndProof(
            new AggregateAndProof(UnsignedLong.valueOf(validatorIndex), proof, aggregate));
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
        validator1Index,
        validator1Proof,
        validator1CommitteeIndex,
        completedFuture(Optional.of(committee1UnsignedAttestation)));
    duty.addValidator(
        validator2Index,
        validator2Proof,
        validator2CommitteeIndex,
        completedFuture(Optional.of(committee2UnsignedAttestation)));

    when(validatorApiChannel.createAggregate(committee1UnsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(committee1Aggregate)));
    when(validatorApiChannel.createAggregate(committee2UnsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(committee2Aggregate)));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendAggregateAndProof(
            new AggregateAndProof(
                UnsignedLong.valueOf(validator1Index), validator1Proof, committee1Aggregate));

    verify(validatorApiChannel)
        .sendAggregateAndProof(
            new AggregateAndProof(
                UnsignedLong.valueOf(validator2Index), validator2Proof, committee2Aggregate));
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
        validator1Index,
        validator1Proof,
        committeeIndex,
        completedFuture(Optional.of(unsignedAttestation)));
    duty.addValidator(
        validator2Index,
        validator2Proof,
        committeeIndex,
        completedFuture(Optional.of(unsignedAttestation)));

    when(validatorApiChannel.createAggregate(unsignedAttestation.getData()))
        .thenReturn(completedFuture(Optional.of(aggregate)));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendAggregateAndProof(
            new AggregateAndProof(
                UnsignedLong.valueOf(validator1Index), validator1Proof, aggregate));
    // Only one proof should be sent.
    verify(validatorApiChannel, times(1)).sendAggregateAndProof(any());
  }

  @Test
  public void shouldFailWhenUnsignedAttestationNotCreated() {
    duty.addValidator(1, dataStructureUtil.randomSignature(), 2, completedFuture(Optional.empty()));
    verify(validatorApiChannel).subscribeToBeaconCommittee(anyInt(), any());

    assertThat(duty.performDuty()).isCompletedExceptionally();
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldFailWhenUnsignedAttestationCompletesExceptionally() {
    final RuntimeException exception = new RuntimeException("Doh!");
    duty.addValidator(1, dataStructureUtil.randomSignature(), 2, failedFuture(exception));

    final SafeFuture<?> result = duty.performDuty();
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(exception);
  }

  @Test
  public void shouldFailWhenAggregateNotCreated() {
    final Attestation unsignedAttestation = dataStructureUtil.randomAttestation();
    duty.addValidator(
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
