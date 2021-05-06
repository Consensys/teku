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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;

class SyncCommitteeScheduledDutiesTest {

  private static final UInt64 PERIOD_END_EPOCH = UInt64.valueOf(429);
  private static final String TYPE = "type";
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ChainHeadTracker chainHeadTracker = mock(ChainHeadTracker.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final SyncCommitteeProductionDuty productionDuty =
      mock(SyncCommitteeProductionDuty.class);
  private final SyncCommitteeAggregationDuty aggregationDuty =
      mock(SyncCommitteeAggregationDuty.class);

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

  @Test
  void shouldNotProduceSignaturesWhenChainHeadIsEmpty() {
    final UInt64 slot = UInt64.valueOf(25);
    when(chainHeadTracker.getCurrentChainHead(slot)).thenReturn(Optional.empty());

    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final SyncCommitteeScheduledDuties duties =
        validBuilder()
            .committeeAssignment(validator1, 1, 1)
            .committeeAssignment(validator2, 2, 2)
            .build();
    final SafeFuture<DutyResult> result = duties.performProductionDuty(slot);
    reportDutyResult(slot, result);

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(slot),
            eq(
                Set.of(
                    validator1.getPublicKey().toAbbreviatedString(),
                    validator2.getPublicKey().toAbbreviatedString())),
            any(IllegalStateException.class));
  }

  @Test
  void shouldUseSameBlockRootForProductionAndAggregation() {
    final UInt64 slot = UInt64.valueOf(25);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(chainHeadTracker.getCurrentChainHead(slot))
        .thenReturn(Optional.of(blockRoot))
        // Will change the reported block on subsequent calls
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));

    final DutyResult expectedDutyResult = DutyResult.success(blockRoot);
    when(productionDuty.produceSignatures(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(expectedDutyResult));
    when(aggregationDuty.produceAggregates(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(expectedDutyResult));

    final SyncCommitteeScheduledDuties duties = createScheduledDutiesWithMocks();

    assertThat(duties.performProductionDuty(slot)).isCompletedWithValue(expectedDutyResult);
    assertThat(duties.performAggregationDuty(slot)).isCompletedWithValue(expectedDutyResult);

    verify(productionDuty).produceSignatures(slot, blockRoot);
    verify(aggregationDuty).produceAggregates(slot, blockRoot);
  }

  @Test
  void shouldNotProduceAggregatesIfSignaturesWereNotProduced() {
    final SyncCommitteeScheduledDuties duties = createScheduledDutiesWithMocks();

    final SafeFuture<DutyResult> result = duties.performAggregationDuty(UInt64.ZERO);
    reportDutyResult(UInt64.ZERO, result);

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(UInt64.ZERO),
            eq(Collections.emptySet()),
            any(IllegalStateException.class));
  }

  @Test
  void shouldNotProduceAggregatesIfNoBlockFoundForSignatures() {
    final SyncCommitteeScheduledDuties duties = createScheduledDutiesWithMocks();
    when(chainHeadTracker.getCurrentChainHead(any())).thenReturn(Optional.empty());

    assertThat(duties.performProductionDuty(UInt64.ZERO)).isCompleted();

    final SafeFuture<DutyResult> result = duties.performAggregationDuty(UInt64.ZERO);
    reportDutyResult(UInt64.ZERO, result);

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(UInt64.ZERO),
            eq(Collections.emptySet()),
            any(IllegalStateException.class));
  }

  @Test
  void shouldNotProduceAggregatesIfSignaturesLastProducedForEarlierSlot() {
    final SyncCommitteeScheduledDuties duties = createScheduledDutiesWithMocks();
    when(chainHeadTracker.getCurrentChainHead(any()))
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));

    assertThat(duties.performProductionDuty(UInt64.ZERO)).isCompleted();

    final SafeFuture<DutyResult> result = duties.performAggregationDuty(UInt64.ONE);
    reportDutyResult(UInt64.ZERO, result);

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(UInt64.ZERO),
            eq(Collections.emptySet()),
            any(IllegalStateException.class));
  }

  @Test
  void shouldNotProduceAggregatesIfSignaturesLastProducedForLaterSlot() {
    final SyncCommitteeScheduledDuties duties = createScheduledDutiesWithMocks();
    when(chainHeadTracker.getCurrentChainHead(any()))
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));

    assertThat(duties.performProductionDuty(UInt64.ONE)).isCompleted();

    final SafeFuture<DutyResult> result = duties.performAggregationDuty(UInt64.ZERO);
    reportDutyResult(UInt64.ZERO, result);

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(UInt64.ZERO),
            eq(Collections.emptySet()),
            any(IllegalStateException.class));
  }

  public SyncCommitteeScheduledDuties.Builder validBuilder() {
    return SyncCommitteeScheduledDuties.builder()
        .validatorLogger(validatorLogger)
        .chainHeadTracker(chainHeadTracker)
        .validatorApiChannel(validatorApiChannel)
        .spec(spec)
        .forkProvider(forkProvider)
        .lastEpochInCommitteePeriod(PERIOD_END_EPOCH);
  }

  private SyncCommitteeScheduledDuties createScheduledDutiesWithMocks() {
    return new SyncCommitteeScheduledDuties(
        productionDuty,
        aggregationDuty,
        chainHeadTracker,
        validatorApiChannel,
        Collections.emptyList(),
        UInt64.ZERO);
  }

  private void reportDutyResult(final UInt64 slot, final SafeFuture<DutyResult> result) {
    assertThat(result).isCompleted();
    result.join().report(TYPE, slot, validatorLogger);
  }

  private Validator createValidator() {
    return new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  }
}
