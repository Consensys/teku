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

package tech.pegasys.teku.validator.client;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.AttestationDutyLoader.SlotBatchingOptions;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class AttestationDutyLoaderTest {

  private static final IntList VALIDATOR_INDICES = IntList.of(1, 2, 3, 4, 5, 6, 7, 8);
  private static final SlotBatchingOptions SLOT_BATCHING_TEST_OPTIONS =
      new SlotBatchingOptions(5, 4, Duration.ofMillis(50), 1, Duration.ofMillis(50));

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
      mock(BeaconCommitteeSubscriptions.class);

  @SuppressWarnings("unchecked")
  private final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>
      scheduledDuties = mock(SlotBasedScheduledDuties.class);

  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final BLSPublicKey validatorKey = dataStructureUtil.randomPublicKey();
  private final Signer signer = mock(Signer.class);
  private final Validator validator =
      new Validator(validatorKey, signer, new FileBackedGraffitiProvider());
  private final Map<BLSPublicKey, Validator> validators = Map.of(validatorKey, validator);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private final AttestationDutyLoader dutyLoader =
      new AttestationDutyLoader(
          validatorApiChannel,
          forkProvider,
          dependentRoot -> scheduledDuties,
          new OwnedValidators(validators),
          validatorIndexProvider,
          beaconCommitteeSubscriptions,
          spec,
          false,
          asyncRunner,
          SLOT_BATCHING_TEST_OPTIONS);

  @BeforeEach
  void setUp() {
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(VALIDATOR_INDICES));
    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.completedFuture(forkInfo));
  }

  @Test
  void shouldSubscribeToSubnetWhenValidatorIsAggregator() {
    final UInt64 slot = UInt64.ONE;
    final int validatorIndex = VALIDATOR_INDICES.getInt(0);
    final int committeeLength = 1;
    final int committeeIndex = 3;
    final int committeesAtSlot = 4;
    final AttesterDuty duty =
        new AttesterDuty(
            validatorKey,
            validatorIndex,
            committeeLength,
            committeeIndex,
            committeesAtSlot,
            0,
            slot);
    when(validatorApiChannel.getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new AttesterDuties(false, dataStructureUtil.randomBytes32(), List.of(duty)))));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(slot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(UInt64.ONE);

    assertThat(result).isCompleted();
    verify(beaconCommitteeSubscriptions)
        .subscribeToBeaconCommittee(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, UInt64.valueOf(committeesAtSlot), slot, true));
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  @Test
  void shouldSubscribeToSubnetWhenValidatorIsNotAggregator() {
    final UInt64 slot = UInt64.ONE;
    final int validatorIndex = VALIDATOR_INDICES.getInt(0);
    final int committeeLength = 10000000;
    final int committeeIndex = 3;
    final int committeesAtSlot = 4;
    final AttesterDuty duty =
        new AttesterDuty(
            validatorKey,
            validatorIndex,
            committeeLength,
            committeeIndex,
            committeesAtSlot,
            0,
            slot);
    when(validatorApiChannel.getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new AttesterDuties(false, dataStructureUtil.randomBytes32(), List.of(duty)))));

    when(scheduledDuties.scheduleProduction(any(), any(), any())).thenReturn(new SafeFuture<>());
    when(signer.signAggregationSlot(slot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(UInt64.ONE);

    assertThat(result).isCompleted();
    verify(beaconCommitteeSubscriptions)
        .subscribeToBeaconCommittee(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, UInt64.valueOf(committeesAtSlot), slot, false));
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  @Test
  void shouldSendSubscriptionRequestsWhenAllDutiesAreScheduled() {
    when(validatorApiChannel.getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new AttesterDuties(false, dataStructureUtil.randomBytes32(), emptyList()))));
    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(UInt64.ONE);

    assertThat(result).isCompleted();
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  @Test
  void shouldBatchBySlotsWhenSchedulingEpochIsCurrent() {
    mockDutiesForBatchTesting(UInt64.ZERO);

    when(signer.signAggregationSlot(any(UInt64.class), eq(forkInfo)))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(UInt64.ZERO);

    // there should be 1 delay: 8 / 4 - 1 (no delay at start)
    asyncRunner.executeDueActions();
    assertThat(result).isNotCompleted();
    assertThat(asyncRunner.countDelayedActions()).isOne();
    timeProvider.advanceTimeBy(SLOT_BATCHING_TEST_OPTIONS.currentEpochSchedulingDelay());

    asyncRunner.executeDueActions();
    assertThat(result).isCompleted();

    verify(beaconCommitteeSubscriptions, times(8)).subscribeToBeaconCommittee(any());
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  @Test
  void shouldBatchBySlotsWhenSchedulingEpochIsInTheFuture() {
    // still in epoch 0
    dutyLoader.onSlot(UInt64.valueOf(3));
    mockDutiesForBatchTesting(UInt64.ONE);

    when(signer.signAggregationSlot(any(UInt64.class), eq(forkInfo)))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(UInt64.ONE);

    // there should be total of 7 delays: 8 - 1 (no delay at the start)
    for (int i = 0; i < 7; i++) {
      asyncRunner.executeDueActions();
      System.out.println(i);
      assertThat(result).isNotCompleted();
      assertThat(asyncRunner.countDelayedActions()).isOne();
      timeProvider.advanceTimeBy(SLOT_BATCHING_TEST_OPTIONS.futureEpochSchedulingDelay());
    }

    asyncRunner.executeDueActions();
    assertThat(result).isCompleted();

    verify(beaconCommitteeSubscriptions, times(8)).subscribeToBeaconCommittee(any());
    verify(beaconCommitteeSubscriptions).sendRequests();
  }

  private void mockDutiesForBatchTesting(final UInt64 epoch) {
    // 8 duties
    final List<AttesterDuty> duties =
        UInt64.range(
                spec.computeStartSlotAtEpoch(epoch),
                spec.computeStartSlotAtEpoch(epoch.increment()))
            .map(
                slot ->
                    new AttesterDuty(
                        validatorKey,
                        VALIDATOR_INDICES.getInt(slot.intValue() % 2),
                        1,
                        3,
                        4,
                        0,
                        slot))
            .toList();
    when(validatorApiChannel.getAttestationDuties(epoch, VALIDATOR_INDICES))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, dataStructureUtil.randomBytes32(), duties))));
  }
}
