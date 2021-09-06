/*
 * Copyright 2021 ConsenSys AG.
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.synccommittee.ChainHeadTracker;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class SyncCommitteeDutyLoaderTest {

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Validator validator1 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final Validator validator2 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final int validator1Index = 19;
  private final int validator2Index = 23;
  private final Set<Integer> validatorIndices = Set.of(validator1Index, validator2Index);
  private final OwnedValidators validators =
      new OwnedValidators(
          Map.of(validator1.getPublicKey(), validator1, validator2.getPublicKey(), validator2));
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ChainHeadTracker chainHeadTracker = mock(ChainHeadTracker.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);

  private final SyncCommitteeDutyLoader dutyLoader =
      new SyncCommitteeDutyLoader(
          validators,
          validatorIndexProvider,
          spec,
          validatorApiChannel,
          chainHeadTracker,
          forkProvider);

  @BeforeEach
  void setUp() {
    when(validatorIndexProvider.getValidatorIndices(validators.getPublicKeys()))
        .thenReturn(SafeFuture.completedFuture(validatorIndices));
  }

  @Test
  void shouldRetrieveDuties() {
    final UInt64 epoch = UInt64.valueOf(56);
    final UInt64 untilEpoch =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .computeFirstEpochOfNextSyncCommitteePeriod(epoch)
            .minusMinZero(1);

    when(validatorApiChannel.getSyncCommitteeDuties(epoch, validatorIndices))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new SyncCommitteeDuties(
                        List.of(
                            new SyncCommitteeDuty(
                                validator1.getPublicKey(), validator1Index, Set.of(1, 6, 25)),
                            new SyncCommitteeDuty(
                                validator2.getPublicKey(), validator2Index, Set.of(7, 50, 38)))))));

    final SyncCommitteeScheduledDuties duties = loadDuties(epoch);
    assertThat(duties.countDuties()).isEqualTo(2);
    // And should trigger subscription to subnets
    verify(validatorApiChannel)
        .subscribeToSyncCommitteeSubnets(
            Set.of(
                new SyncCommitteeSubnetSubscription(
                    validator1Index, Set.of(1, 6, 25), untilEpoch.increment()),
                new SyncCommitteeSubnetSubscription(
                    validator2Index, Set.of(7, 50, 38), untilEpoch.increment())));
  }

  private SyncCommitteeScheduledDuties loadDuties(final UInt64 epoch) {
    final SafeFuture<Optional<SyncCommitteeScheduledDuties>> result =
        dutyLoader.loadDutiesForEpoch(epoch);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    return result.join().orElseThrow();
  }
}
