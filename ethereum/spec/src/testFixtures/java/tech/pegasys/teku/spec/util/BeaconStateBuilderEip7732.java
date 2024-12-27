/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateSchemaEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.MutableBeaconStateEip7732;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;

public class BeaconStateBuilderEip7732
    extends AbstractBeaconStateBuilder<
        BeaconStateEip7732, MutableBeaconStateEip7732, BeaconStateBuilderEip7732> {
  private UInt64 nextWithdrawalIndex;
  private UInt64 nextWithdrawalValidatorIndex;

  private SszList<SszByte> previousEpochParticipation;
  private SszList<SszByte> currentEpochParticipation;
  private SszUInt64List inactivityScores;
  private SyncCommittee currentSyncCommittee;
  private SyncCommittee nextSyncCommittee;
  private ExecutionPayloadHeader latestExecutionPayloadHeader;

  private UInt64 depositRequestsStartIndex;
  private UInt64 depositBalanceToConsume;
  private UInt64 exitBalanceToConsume;
  private UInt64 earliestExitEpoch;

  private UInt64 consolidationBalanceToConsume;

  private UInt64 earliestConsolidationEpoch;

  private SszList<PendingDeposit> pendingDeposits;
  private SszList<PendingPartialWithdrawal> pendingPartialWithdrawals;
  private SszList<PendingConsolidation> pendingConsolidations;

  private Bytes32 latestBlockHash;
  private UInt64 latestFullSlot;
  private Bytes32 latestWithdrawalsRoot;

  protected BeaconStateBuilderEip7732(
      final SpecVersion spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    super(spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStateEip7732 getEmptyState() {
    return BeaconStateSchemaEip7732.create(
            spec.getConfig(), spec.getSchemaDefinitions().getSchemaRegistry())
        .createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStateEip7732 state) {
    state.setPreviousEpochParticipation(previousEpochParticipation);
    state.setCurrentEpochParticipation(currentEpochParticipation);
    state.setInactivityScores(inactivityScores);
    state.setCurrentSyncCommittee(currentSyncCommittee);
    state.setNextSyncCommittee(nextSyncCommittee);
    state.setLatestExecutionPayloadHeader(latestExecutionPayloadHeader);
    state.setNextWithdrawalIndex(nextWithdrawalIndex);
    state.setNextWithdrawalValidatorIndex(nextWithdrawalValidatorIndex);
    state.setDepositRequestsStartIndex(depositRequestsStartIndex);
    state.setDepositBalanceToConsume(depositBalanceToConsume);
    state.setExitBalanceToConsume(exitBalanceToConsume);
    state.setEarliestExitEpoch(earliestExitEpoch);
    state.setConsolidationBalanceToConsume(consolidationBalanceToConsume);
    state.setEarliestConsolidationEpoch(earliestConsolidationEpoch);
    state.setPendingDeposits(pendingDeposits);
    state.setPendingPartialWithdrawals(pendingPartialWithdrawals);
    state.setPendingConsolidations(pendingConsolidations);
    state.setLatestBlockHash(latestBlockHash);
    state.setLatestFullSlot(latestFullSlot);
    state.setLatestWithdrawalsRoot(latestWithdrawalsRoot);
  }

  public static BeaconStateBuilderEip7732 create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderEip7732(
        spec.forMilestone(SpecMilestone.EIP7732),
        dataStructureUtil,
        defaultValidatorCount,
        defaultItemsInSSZLists);
  }

  public BeaconStateBuilderEip7732 nextWithdrawalIndex(final UInt64 nextWithdrawalIndex) {
    checkNotNull(nextWithdrawalIndex);
    this.nextWithdrawalIndex = nextWithdrawalIndex;
    return this;
  }

  public BeaconStateBuilderEip7732 nextWithdrawalValidatorIndex(
      final UInt64 nextWithdrawalValidatorIndex) {
    checkNotNull(nextWithdrawalValidatorIndex);
    this.nextWithdrawalValidatorIndex = nextWithdrawalValidatorIndex;
    return this;
  }

  public BeaconStateBuilderEip7732 depositRequestsStartIndex(
      final UInt64 depositRequestsStartIndex) {
    checkNotNull(depositRequestsStartIndex);
    this.depositRequestsStartIndex = depositRequestsStartIndex;
    return this;
  }

  public BeaconStateBuilderEip7732 depositBalanceToConsume(final UInt64 depositBalanceToConsume) {
    checkNotNull(depositBalanceToConsume);
    this.depositBalanceToConsume = depositBalanceToConsume;
    return this;
  }

  private BeaconStateSchemaEip7732 getBeaconStateSchema() {
    return (BeaconStateSchemaEip7732) spec.getSchemaDefinitions().getBeaconStateSchema();
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    final BeaconStateSchemaEip7732 schema = getBeaconStateSchema();

    previousEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getPreviousEpochParticipationSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomSszByte);
    currentEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getCurrentEpochParticipationSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomSszByte);
    inactivityScores =
        dataStructureUtil.randomSszUInt64List(
            schema.getInactivityScoresSchema(), defaultItemsInSSZLists);
    currentSyncCommittee = dataStructureUtil.randomSyncCommittee();
    nextSyncCommittee = dataStructureUtil.randomSyncCommittee();
    latestExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader(
            dataStructureUtil.getSpec().forMilestone(SpecMilestone.EIP7732));

    this.nextWithdrawalIndex = UInt64.ZERO;
    this.nextWithdrawalValidatorIndex =
        defaultValidatorCount > 0
            ? dataStructureUtil.randomUInt64(defaultValidatorCount)
            : UInt64.ZERO;

    this.depositRequestsStartIndex = SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX;
    this.depositBalanceToConsume = UInt64.ZERO;
    this.exitBalanceToConsume = UInt64.ZERO;
    this.earliestExitEpoch = UInt64.ZERO;
    this.consolidationBalanceToConsume = UInt64.ZERO;
    this.earliestConsolidationEpoch = UInt64.ZERO;
    this.pendingDeposits = schema.getPendingDepositsSchema().createFromElements(List.of());
    this.pendingPartialWithdrawals =
        schema.getPendingPartialWithdrawalsSchema().createFromElements(List.of());
    this.pendingConsolidations =
        schema.getPendingConsolidationsSchema().createFromElements(List.of());

    this.latestBlockHash = Bytes32.ZERO;
    this.latestFullSlot = UInt64.ZERO;
    this.latestWithdrawalsRoot = Bytes32.ZERO;
  }
}
