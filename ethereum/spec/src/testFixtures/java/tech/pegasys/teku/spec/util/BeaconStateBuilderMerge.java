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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateSchemaMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.MutableBeaconStateMerge;

public class BeaconStateBuilderMerge
    extends AbstractBeaconStateBuilder<
        BeaconStateMerge, MutableBeaconStateMerge, BeaconStateBuilderMerge> {

  private SszList<SszByte> previousEpochParticipation;
  private SszList<SszByte> currentEpochParticipation;
  private SszUInt64List inactivityScores;
  private SyncCommittee currentSyncCommittee;
  private SyncCommittee nextSyncCommittee;
  private ExecutionPayloadHeader latestExecutionPayloadHeader;

  private BeaconStateBuilderMerge(
      final SpecVersion spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    super(spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStateMerge getEmptyState() {
    return BeaconStateSchemaMerge.create(spec.getConfig()).createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStateMerge state) {
    state.setPreviousEpochParticipation(previousEpochParticipation);
    state.setCurrentEpochParticipation(currentEpochParticipation);
    state.setInactivityScores(inactivityScores);
    state.setCurrentSyncCommittee(currentSyncCommittee);
    state.setNextSyncCommittee(nextSyncCommittee);
    state.setLatestExecutionPayloadHeader(latestExecutionPayloadHeader);
  }

  public static BeaconStateBuilderMerge create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderMerge(
        spec.forMilestone(SpecMilestone.MERGE),
        dataStructureUtil,
        defaultValidatorCount,
        defaultItemsInSSZLists);
  }

  private BeaconStateSchemaMerge getBeaconStateSchema() {
    return (BeaconStateSchemaMerge) spec.getSchemaDefinitions().getBeaconStateSchema();
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    final BeaconStateSchemaMerge schema = getBeaconStateSchema();

    previousEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getPreviousEpochParticipationSchema(),
            defaultItemsInSSZLists,
            dataStructureUtil::randomSszByte);
    currentEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getCurrentEpochParticipationSchema(),
            defaultItemsInSSZLists,
            dataStructureUtil::randomSszByte);
    inactivityScores =
        dataStructureUtil.randomSszUInt64List(
            schema.getInactivityScoresSchema(), defaultItemsInSSZLists);
    currentSyncCommittee = dataStructureUtil.randomSyncCommittee();
    nextSyncCommittee = dataStructureUtil.randomSyncCommittee();
    latestExecutionPayloadHeader = dataStructureUtil.randomExecutionPayloadHeader();
  }

  public BeaconStateBuilderMerge previousEpochAttestations(final SszList<SszByte> value) {
    checkNotNull(value);
    this.previousEpochParticipation = value;
    return this;
  }

  public BeaconStateBuilderMerge currentEpochAttestations(final SszList<SszByte> value) {
    checkNotNull(value);
    this.currentEpochParticipation = value;
    return this;
  }

  public BeaconStateBuilderMerge inactivityScores(final SszUInt64List inactivityScores) {
    checkNotNull(inactivityScores);
    this.inactivityScores = inactivityScores;
    return this;
  }

  public BeaconStateBuilderMerge currentSyncCommittee(final SyncCommittee currentSyncCommittee) {
    checkNotNull(currentSyncCommittee);
    this.currentSyncCommittee = currentSyncCommittee;
    return this;
  }

  public BeaconStateBuilderMerge nextSyncCommittee(final SyncCommittee nextSyncCommittee) {
    checkNotNull(nextSyncCommittee);
    this.nextSyncCommittee = nextSyncCommittee;
    return this;
  }

  public BeaconStateBuilderMerge latestExecutionPayloadHeader(
      final ExecutionPayloadHeader latestExecutionPayloadHeader) {
    checkNotNull(latestExecutionPayloadHeader);
    this.latestExecutionPayloadHeader = latestExecutionPayloadHeader;
    return this;
  }
}
