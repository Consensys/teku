/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.chain.observer;

import com.google.common.base.Objects;
import javax.annotation.Nullable;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.core.BeaconBlock;

/** An observable chain state. */
public class ObservableBeaconState {
  private final BeaconBlock head;
  private final BeaconStateEx latestSlotState;
  private final PendingOperations pendingOperations;

  public ObservableBeaconState(
      BeaconBlock head, BeaconStateEx latestSlotState, PendingOperations pendingOperations) {
    this.head = head;
    this.latestSlotState = latestSlotState;
    this.pendingOperations = pendingOperations;
  }

  public BeaconBlock getHead() {
    return head;
  }

  public BeaconStateEx getLatestSlotState() {
    return latestSlotState;
  }

  public PendingOperations getPendingOperations() {
    return pendingOperations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ObservableBeaconState that = (ObservableBeaconState) o;
    return Objects.equal(head, that.head)
        && Objects.equal(latestSlotState, that.latestSlotState)
        && Objects.equal(pendingOperations, that.pendingOperations);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(head, latestSlotState, pendingOperations);
  }

  @Override
  public String toString() {
    return toString(null);
  }

  public String toString(@Nullable BeaconChainSpec spec) {

    String committee = "";
    if (spec != null) {
      committee =
          " Proposer/Committee: "
              + spec.get_beacon_proposer_index(getLatestSlotState())
              + " "
              + spec.get_crosslink_committees_at_slot(
                      getLatestSlotState(), getLatestSlotState().getSlot())
                  .get(0)
                  .getCommittee()
              + " ";
    }

    return "ObservableBeaconState[head="
        + (spec != null ? spec.signing_root(head).toStringShort() : head.toString(null, null, null))
        + ", latestState: "
        + committee
        + latestSlotState.toStringShort(spec == null ? null : spec.getConstants())
        + ", pendingOps: "
        + getPendingOperations().toStringMedium(spec == null ? null : spec.getConstants())
        + "]";
  }
}
