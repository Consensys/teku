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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;

public class ValidateableSyncCommitteeMessage {
  private final SyncCommitteeMessage message;
  private final OptionalInt receivedSubnetId;
  private volatile Optional<SyncSubcommitteeAssignments> subcommitteeAssignments = Optional.empty();

  private ValidateableSyncCommitteeMessage(
      final SyncCommitteeMessage message, final OptionalInt receivedSubnetId) {
    this.message = message;
    this.receivedSubnetId = receivedSubnetId;
  }

  public static ValidateableSyncCommitteeMessage fromValidator(final SyncCommitteeMessage message) {
    return new ValidateableSyncCommitteeMessage(message, OptionalInt.empty());
  }

  public static ValidateableSyncCommitteeMessage fromNetwork(
      final SyncCommitteeMessage message, final int receivedSubnetId) {
    return new ValidateableSyncCommitteeMessage(message, OptionalInt.of(receivedSubnetId));
  }

  public SyncCommitteeMessage getMessage() {
    return message;
  }

  public OptionalInt getReceivedSubnetId() {
    return receivedSubnetId;
  }

  public Optional<SyncSubcommitteeAssignments> getSubcommitteeAssignments() {
    return subcommitteeAssignments;
  }

  public SyncSubcommitteeAssignments calculateAssignments(
      final Spec spec, final BeaconState state) {
    final Optional<SyncSubcommitteeAssignments> currentValue = this.subcommitteeAssignments;
    if (currentValue.isPresent()) {
      return currentValue.get();
    }
    final UInt64 messageSlot = message.getSlot();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(messageSlot);
    final SyncSubcommitteeAssignments assignments =
        syncCommitteeUtil.getSubcommitteeAssignments(
            state,
            syncCommitteeUtil.getEpochForDutiesAtSlot(messageSlot),
            message.getValidatorIndex());

    this.subcommitteeAssignments = Optional.of(assignments);
    return assignments;
  }

  @VisibleForTesting
  public void setSubcommitteeAssignments(final SyncSubcommitteeAssignments assignments) {
    this.subcommitteeAssignments = Optional.of(assignments);
  }

  public UInt64 getSlot() {
    return message.getSlot();
  }

  public Bytes32 getBeaconBlockRoot() {
    return message.getBeaconBlockRoot();
  }
}
