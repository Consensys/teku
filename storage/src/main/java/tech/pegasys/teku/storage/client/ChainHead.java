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

package tech.pegasys.teku.storage.client;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class ChainHead extends StateAndBlockSummary {
  private final UInt64 forkChoiceSlot;
  private final Spec spec;

  private ChainHead(
      BeaconBlockSummary block, BeaconState state, UInt64 forkChoiceSlot, final Spec spec) {
    super(block, state);
    this.forkChoiceSlot = forkChoiceSlot;
    this.spec = spec;
  }

  public static ChainHead create(
      StateAndBlockSummary blockAndState, UInt64 forkChoiceSlot, final Spec spec) {
    return new ChainHead(
        blockAndState.getBlockSummary(), blockAndState.getState(), forkChoiceSlot, spec);
  }

  /** @return The slot at which the chain head was calculated */
  public UInt64 getForkChoiceSlot() {
    return forkChoiceSlot;
  }

  public UInt64 getForkChoiceEpoch() {
    return spec.computeEpochAtSlot(forkChoiceSlot);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    final ChainHead chainHead = (ChainHead) o;
    return Objects.equals(forkChoiceSlot, chainHead.forkChoiceSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), forkChoiceSlot);
  }
}
