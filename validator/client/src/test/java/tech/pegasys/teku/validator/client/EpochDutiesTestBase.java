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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

abstract class EpochDutiesTestBase<D extends EpochDuties<S>, S extends ScheduledDuties> {

  protected static final UInt64 EPOCH = UInt64.valueOf(10);
  protected final SafeFuture<Optional<S>> scheduledDutiesFuture = new SafeFuture<>();

  @SuppressWarnings("unchecked")
  protected final DutyLoader<S> dutyLoader = mock(DutyLoader.class);

  protected final S scheduledDuties;
  protected final Optional<S> scheduledDutiesOptional;

  protected D duties;

  protected EpochDutiesTestBase(final S scheduledDutiesMock) {
    this.scheduledDuties = scheduledDutiesMock;
    scheduledDutiesOptional = Optional.of(scheduledDuties);
  }

  @BeforeEach
  void setUp() {
    when(dutyLoader.loadDutiesForEpoch(EPOCH)).thenReturn(scheduledDutiesFuture);
    duties = calculateDuties(dutyLoader, EPOCH);
    verify(dutyLoader).loadDutiesForEpoch(EPOCH);
  }

  protected abstract D calculateDuties(final DutyLoader<S> dutyLoader, final UInt64 epoch);

  @Test
  void cancel_shouldCancelFuture() {
    duties.cancel();
    assertThat(scheduledDutiesFuture).isCancelled();
  }

  @Test
  void shouldRecalculateDutiesIfNewDependentRootDoesNotMatch() {
    when(scheduledDuties.getDependentRoot()).thenReturn(Bytes32.ZERO);
    scheduledDutiesFuture.complete(scheduledDutiesOptional);

    duties.onHeadUpdate(Bytes32.fromHexString("0x1234"));

    verify(dutyLoader, times(2)).loadDutiesForEpoch(EPOCH);
  }

  @Test
  void shouldNotRecalculateDutiesIfNewDependentRootMatches() {
    when(scheduledDuties.getDependentRoot()).thenReturn(Bytes32.ZERO);
    scheduledDutiesFuture.complete(scheduledDutiesOptional);

    duties.onHeadUpdate(Bytes32.ZERO);

    verifyNoMoreInteractions(dutyLoader);
  }

  @Test
  void shouldRecalculateDutiesIfNonMatchingHeadUpdateReceivedWhileLoadingDuties() {
    duties.onHeadUpdate(Bytes32.fromHexString("0x1234"));

    when(scheduledDuties.getDependentRoot()).thenReturn(Bytes32.ZERO);
    scheduledDutiesFuture.complete(scheduledDutiesOptional);

    verify(dutyLoader, times(2)).loadDutiesForEpoch(EPOCH);
  }

  @Test
  void shouldNotRecalculateDutiesIfMatchingHeadUpdateReceivedWhileLoadingDuties() {
    duties.onHeadUpdate(Bytes32.ZERO);

    when(scheduledDuties.getDependentRoot()).thenReturn(Bytes32.ZERO);
    scheduledDutiesFuture.complete(scheduledDutiesOptional);

    verify(dutyLoader, times(1)).loadDutiesForEpoch(EPOCH);
  }
}
