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

package tech.pegasys.teku.validator.beaconnode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class ForkAwareBasedEventAdapterTest {

  private final Spec specElectra = TestSpecFactory.createMinimalElectra();
  private final Spec specGloas = TestSpecFactory.createMinimalGloas();

  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  final int millisPerSlot = specElectra.getGenesisSpecConfig().getSlotDurationMillis();
  final int secondsPerSlot = millisPerSlot / 1000;

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RepeatingTaskScheduler repeatingTaskScheduler =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  @Test
  void shouldScheduleGloasEventWhenGloasIsScheduled() {
    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider,
            repeatingTaskScheduler,
            timeProvider,
            validatorTimingChannel,
            specGloas);
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 25;
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Payload attestation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));

    // But does fire 3/4 through the slot
    timeProvider.advanceTimeByMillis(millisPerSlot * 3L / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1))
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldNotScheduleGloasEventWhenGloasIsNotScheduled() {
    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider,
            repeatingTaskScheduler,
            timeProvider,
            validatorTimingChannel,
            specElectra);
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 25;
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Payload attestation should not be scheduled pre-GLOAS
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));

    // And it even does not fire 3/4 through the slot
    timeProvider.advanceTimeByMillis(millisPerSlot * 3L / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }
}
