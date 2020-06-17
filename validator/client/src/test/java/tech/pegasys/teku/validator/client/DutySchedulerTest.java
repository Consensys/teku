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

package tech.pegasys.teku.validator.client;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.async.StubAsyncRunner;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorDuties;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.duties.BlockProductionDuty;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;

@SuppressWarnings("FutureReturnValueIgnored")
class DutySchedulerTest {
  private static final BLSPublicKey VALIDATOR1_KEY = BLSPublicKey.random(100);
  private static final BLSPublicKey VALIDATOR2_KEY = BLSPublicKey.random(200);
  private static final Collection<BLSPublicKey> VALIDATOR_KEYS =
      Set.of(VALIDATOR1_KEY, VALIDATOR2_KEY);
  private final Signer validator1Signer = mock(Signer.class);
  private final Signer validator2Signer = mock(Signer.class);
  private final Validator validator1 =
      new Validator(VALIDATOR1_KEY, validator1Signer, Optional.empty());
  private final Validator validator2 =
      new Validator(VALIDATOR2_KEY, validator2Signer, Optional.empty());

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorDutyFactory dutyFactory = mock(ValidatorDutyFactory.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final StableSubnetSubscriber stableSubnetSubscriber = mock(StableSubnetSubscriber.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final DutyScheduler dutyScheduler =
      new DutyScheduler(
          metricsSystem,
          new RetryingDutyLoader(
              asyncRunner,
              new ValidatorApiDutyLoader(
                  metricsSystem,
                  validatorApiChannel,
                  forkProvider,
                  () -> new ScheduledDuties(dutyFactory),
                  Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2))),
          stableSubnetSubscriber);

  @BeforeEach
  public void setUp() {
    when(validatorApiChannel.getDuties(any(), any()))
        .thenReturn(completedFuture(Optional.of(emptyList())));
    when(dutyFactory.createAttestationProductionDuty(any()))
        .thenReturn(mock(AttestationProductionDuty.class));
    when(forkProvider.getForkInfo()).thenReturn(completedFuture(fork));
    final SafeFuture<BLSSignature> rejectAggregationSignature =
        SafeFuture.failedFuture(new UnsupportedOperationException("This test ignores aggregation"));
    when(validator1Signer.signAggregationSlot(any(), any())).thenReturn(rejectAggregationSignature);
    when(validator2Signer.signAggregationSlot(any(), any())).thenReturn(rejectAggregationSignature);
  }

  @Test
  public void shouldFetchDutiesForCurrentAndNextEpoch() {
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    verify(validatorApiChannel).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);
  }

  @Test
  public void shouldFetchDutiesForSecondEpochWhenFirstEpochReached() {
    dutyScheduler.onSlot(ZERO);

    verify(validatorApiChannel).getDuties(ZERO, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);

    // Process each slot up to the start of epoch 1
    final UnsignedLong epoch1Start = compute_start_slot_at_epoch(UnsignedLong.ONE);
    for (int slot = 0; slot <= epoch1Start.intValue(); slot++) {
      dutyScheduler.onSlot(UnsignedLong.valueOf(slot));
    }
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);
  }

  @Test
  public void shouldNotRefetchDutiesWhichHaveAlreadyBeenRetrieved() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    verify(validatorApiChannel).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);

    dutyScheduler.onSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)));

    // Requests the next epoch, but not the current one because we already have that
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(3), VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotRefetchDutiesWhichHaveAlreadyBeenRetrievedDuringFirstEpoch() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(ZERO);

    verify(validatorApiChannel).getDuties(ZERO, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(ONE, VALIDATOR_KEYS);

    // Second slot in epoch 0
    dutyScheduler.onSlot(ONE);

    // Shouldn't request any more duties
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldRetryWhenRequestingDutiesFails() {
    final SafeFuture<Optional<List<ValidatorDuties>>> request1 = new SafeFuture<>();
    final SafeFuture<Optional<List<ValidatorDuties>>> request2 = new SafeFuture<>();
    when(validatorApiChannel.getDuties(UnsignedLong.ONE, VALIDATOR_KEYS))
        .thenReturn(request1)
        .thenReturn(request2);

    dutyScheduler.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));
    verify(validatorApiChannel, times(1)).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);

    request1.completeExceptionally(new RuntimeException("Nope"));
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    // Should retry request
    verify(validatorApiChannel, times(2)).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);

    // And not have any more retries scheduled
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldRefetchDutiesAfterReorg() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    verify(validatorApiChannel).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);

    dutyScheduler.onChainReorg(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)));

    // Re-requests epoch 2 and also requests epoch 3 as the new chain is far enough along for that
    verify(validatorApiChannel, times(2)).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(3), VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldRefetchDutiesAfterBlockImportedFromTwoOrMoreEpochsBefore() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(5)));

    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(5), VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(6), VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);

    dutyScheduler.onBlockImportedForSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(4)));

    // Duties are invalidated but not yet re-requested as we might be importing a batch of blocks
    verifyNoMoreInteractions(validatorApiChannel);

    dutyScheduler.onSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(5)).plus(ONE));
    // Re-requests epoch 6 which may have been changed by the new block
    verify(validatorApiChannel, times(2)).getDuties(UnsignedLong.valueOf(6), VALIDATOR_KEYS);
    // Epoch 5 is unchanged so not re-requested
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldScheduleBlockProposalDuty() {
    final UnsignedLong blockProposerSlot = UnsignedLong.valueOf(5);
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY, 5, 3, 6, 0, List.of(blockProposerSlot), UnsignedLong.valueOf(7));
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties))));

    final BlockProductionDuty blockCreationDuty = mock(BlockProductionDuty.class);
    when(blockCreationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createBlockProductionDuty(blockProposerSlot, validator1))
        .thenReturn(blockCreationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Execute
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).performDuty();
  }

  @Test
  public void shouldDelayExecutingDutiesUntilSchedulingIsComplete() {
    final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();
    final ValidatorTimingChannel dutyScheduler =
        new DutyScheduler(
            metricsSystem,
            new RetryingDutyLoader(
                asyncRunner,
                new ValidatorApiDutyLoader(
                    metricsSystem,
                    validatorApiChannel,
                    forkProvider,
                    () -> scheduledDuties,
                    Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2))),
            stableSubnetSubscriber);
    final SafeFuture<Optional<List<ValidatorDuties>>> epoch0Duties = new SafeFuture<>();

    when(validatorApiChannel.getDuties(eq(ZERO), any())).thenReturn(epoch0Duties);
    when(validatorApiChannel.getDuties(eq(ONE), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(emptyList())));
    dutyScheduler.onSlot(ZERO);

    dutyScheduler.onBlockProductionDue(ZERO);
    dutyScheduler.onAttestationCreationDue(ZERO);
    dutyScheduler.onAttestationAggregationDue(ZERO);
    // Duties haven't been loaded yet.
    verify(scheduledDuties, never()).produceBlock(ZERO);
    verify(scheduledDuties, never()).produceAttestations(ZERO);
    verify(scheduledDuties, never()).performAggregation(ZERO);

    epoch0Duties.complete(Optional.of(emptyList()));
    verify(scheduledDuties).produceBlock(ZERO);
    verify(scheduledDuties).produceAttestations(ZERO);
    verify(scheduledDuties).performAggregation(ZERO);
  }

  @Test
  public void shouldNotPerformDutiesForSameSlotTwice() {
    final UnsignedLong blockProposerSlot = UnsignedLong.valueOf(5);
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY, 5, 3, 6, 0, List.of(blockProposerSlot), UnsignedLong.valueOf(7));
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties))));

    final BlockProductionDuty blockCreationDuty = mock(BlockProductionDuty.class);
    when(blockCreationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createBlockProductionDuty(blockProposerSlot, validator1))
        .thenReturn(blockCreationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Execute
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).performDuty();

    // Somehow we triggered the same slot again.
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    // But shouldn't produce another block and get ourselves slashed.
    verifyNoMoreInteractions(blockCreationDuty);
  }

  @Test
  public void shouldScheduleAttestationDuties() {
    final UnsignedLong attestationSlot = UnsignedLong.valueOf(5);
    final int validator1Index = 5;
    final int validator1Committee = 3;
    final int validator1CommitteePosition = 9;
    final int validator2Index = 6;
    final int validator2Committee = 4;
    final int validator2CommitteePosition = 8;
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY,
            validator1Index,
            validator1Committee,
            validator1CommitteePosition,
            0,
            emptyList(),
            attestationSlot);
    final ValidatorDuties validator2Duties =
        ValidatorDuties.withDuties(
            VALIDATOR2_KEY,
            validator2Index,
            validator2Committee,
            validator2CommitteePosition,
            0,
            emptyList(),
            attestationSlot);
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties, validator2Duties))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createAttestationProductionDuty(attestationSlot)).thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty)
        .addValidator(validator1, validator1Committee, validator1CommitteePosition);
    verify(attestationDuty)
        .addValidator(validator2, validator2Committee, validator2CommitteePosition);

    // Execute
    dutyScheduler.onAttestationCreationDue(attestationSlot);
    verify(attestationDuty).performDuty();
  }

  @Test
  public void shouldScheduleAggregationDuties() {
    final UnsignedLong attestationSlot = UnsignedLong.valueOf(13);
    final int validator1Index = 5;
    final int validator1Committee = 3;
    final int validator1CommitteePosition = 9;
    final int validator2Index = 6;
    final int validator2Committee = 4;
    final int validator2CommitteePosition = 8;
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY,
            validator1Index,
            validator1Committee,
            validator1CommitteePosition,
            1, // Guaranteed to be an aggregator
            emptyList(),
            attestationSlot);
    final ValidatorDuties validator2Duties =
        ValidatorDuties.withDuties(
            VALIDATOR2_KEY,
            validator2Index,
            validator2Committee,
            validator2CommitteePosition,
            100000, // Won't be an aggregator
            emptyList(),
            attestationSlot);
    when(validatorApiChannel.getDuties(eq(ONE), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties, validator2Duties))));

    final BLSSignature validator1Signature = dataStructureUtil.randomSignature();
    when(validator1.getSigner().signAggregationSlot(attestationSlot, fork))
        .thenReturn(completedFuture(validator1Signature));
    when(validator2.getSigner().signAggregationSlot(attestationSlot, fork))
        .thenReturn(completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<Optional<Attestation>> unsignedAttestationFuture = new SafeFuture<>();
    final AggregationDuty aggregationDuty = mock(AggregationDuty.class);
    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(dutyFactory.createAttestationProductionDuty(attestationSlot)).thenReturn(attestationDuty);
    when(dutyFactory.createAggregationDuty(attestationSlot)).thenReturn(aggregationDuty);
    when(aggregationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDuty.addValidator(validator1, validator1Committee, validator1CommitteePosition))
        .thenReturn(unsignedAttestationFuture);

    // Load duties
    final UnsignedLong epochStartSlot = compute_start_slot_at_epoch(ONE);
    dutyScheduler.onSlot(epochStartSlot);

    // Only validator1 should have had an aggregation duty created for it
    verify(dutyFactory).createAggregationDuty(attestationSlot);
    // And should have added validator1 to each duty
    verify(aggregationDuty)
        .addValidator(
            validator1,
            validator1Index,
            validator1Signature,
            validator1Committee,
            unsignedAttestationFuture);
    verifyNoMoreInteractions(aggregationDuty);

    // Perform the duties
    dutyScheduler.onAttestationAggregationDue(attestationSlot);
    verify(aggregationDuty).performDuty();
  }
}
