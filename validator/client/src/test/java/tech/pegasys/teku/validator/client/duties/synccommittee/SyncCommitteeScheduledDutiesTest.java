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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

class SyncCommitteeScheduledDutiesTest {

  private static final UInt64 PERIOD_END_EPOCH = UInt64.valueOf(429);
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ChainHeadTracker chainHeadTracker = mock(ChainHeadTracker.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);

  private final Validator validator1 = createValidator();
  private final Validator validator2 = createValidator();

  @Test
  void shouldSubscribeToSubnets() {
    final SyncCommitteeScheduledDuties duties =
        validBuilder()
            .committeeAssignment(validator1, 50, 1)
            .committeeAssignment(validator1, 50, 2)
            .committeeAssignment(validator1, 50, 3)
            .committeeAssignment(validator2, 70, 2)
            .committeeAssignment(validator2, 70, 6)
            .build();

    duties.subscribeToSubnets();

    verify(validatorApiChannel)
        .subscribeToSyncCommitteeSubnets(
            Set.of(
                new SyncCommitteeSubnetSubscription(50, Set.of(1, 2, 3), PERIOD_END_EPOCH),
                new SyncCommitteeSubnetSubscription(70, Set.of(2, 6), PERIOD_END_EPOCH)));
  }

  public SyncCommitteeScheduledDuties.Builder validBuilder() {
    return SyncCommitteeScheduledDuties.builder()
        .chainHeadTracker(chainHeadTracker)
        .validatorApiChannel(validatorApiChannel)
        .spec(spec)
        .forkProvider(forkProvider)
        .lastEpochInCommitteePeriod(PERIOD_END_EPOCH);
  }

  private Validator createValidator() {
    return new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  }
}
