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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class AttestationDutyLoaderTest {

  private static final List<Integer> VALIDATOR_INDICES = List.of(1, 2, 3);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
      mock(BeaconCommitteeSubscriptions.class);
  private final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final Map<BLSPublicKey, Validator> validators = emptyMap();

  private final AttestationDutyLoader dutyLoader =
      new AttestationDutyLoader(
          validatorApiChannel,
          forkProvider,
          () -> scheduledDuties,
          validators,
          validatorIndexProvider,
          beaconCommitteeSubscriptions);

  @BeforeEach
  void setUp() {
    when(validatorIndexProvider.getValidatorIndices(any())).thenReturn(VALIDATOR_INDICES);
  }

  @Test
  void shouldSendSubscriptionRequestsWhenAllDutiesAreScheduled() {
    when(validatorApiChannel.getAttestationDuties(UInt64.ONE, VALIDATOR_INDICES))
        .thenReturn(SafeFuture.completedFuture(Optional.of(emptyList())));
    final SafeFuture<ScheduledDuties> result = dutyLoader.loadDutiesForEpoch(UInt64.ONE);

    assertThat(result).isCompleted();
    verify(beaconCommitteeSubscriptions).sendRequests();
  }
}
