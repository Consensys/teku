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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.SszMutableList;
import tech.pegasys.teku.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.ssz.collections.SszUInt64List;
import tech.pegasys.teku.ssz.primitive.SszByte;

public interface MutableBeaconStateMerge extends MutableBeaconState, BeaconStateMerge {

  static MutableBeaconStateMerge required(final MutableBeaconState state) {
    return state
        .toMutableVersionMerge()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a merge state but got: " + state.getClass().getSimpleName()));
  }

  // Attestations
  @Override
  default SszMutableList<SszByte> getPreviousEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name());
    return getAnyByRef(fieldIndex);
  }

  default void setPreviousEpochParticipation(final SszList<SszByte> newValue) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name());
    set(fieldIndex, newValue);
  }

  @Override
  default SszMutableList<SszByte> getCurrentEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name());
    return getAnyByRef(fieldIndex);
  }

  default void setCurrentEpochParticipation(final SszList<SszByte> newValue) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name());
    set(fieldIndex, newValue);
  }

  @Override
  default SszMutableUInt64List getInactivityScores() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.INACTIVITY_SCORES.name());
    return getAnyByRef(fieldIndex);
  }

  default void setInactivityScores(SszUInt64List newValue) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.INACTIVITY_SCORES.name());
    set(fieldIndex, newValue);
  }

  default void setCurrentSyncCommittee(SyncCommittee currentSyncCommittee) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE.name());
    set(fieldIndex, currentSyncCommittee);
  }

  default void setNextSyncCommittee(SyncCommittee nextSyncCommittee) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE.name());
    set(fieldIndex, nextSyncCommittee);
  }

  // Execution
  default void setLatestExecutionPayloadHeader(ExecutionPayloadHeader executionPayloadHeader) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER.name());
    set(fieldIndex, executionPayloadHeader);
  }

  @Override
  BeaconStateMerge commitChanges();

  @Override
  default Optional<MutableBeaconStateMerge> toMutableVersionMerge() {
    return Optional.of(this);
  }
}
