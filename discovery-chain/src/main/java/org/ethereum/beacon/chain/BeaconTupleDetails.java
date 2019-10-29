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

package org.ethereum.beacon.chain;

import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.core.BeaconBlock;

public class BeaconTupleDetails extends BeaconTuple {

  private final BeaconStateEx postSlotState;
  private final BeaconStateEx postBlockState;

  public BeaconTupleDetails(
      @Nonnull BeaconBlock block,
      @Nullable BeaconStateEx postSlotState,
      @Nullable BeaconStateEx postBlockState,
      @Nonnull BeaconStateEx finalState) {

    super(block, finalState);
    this.postSlotState = postSlotState;
    this.postBlockState = postBlockState;
  }

  public BeaconTupleDetails(BeaconTuple tuple) {
    this(tuple.getBlock(), null, null, tuple.getState());
  }

  public Optional<BeaconStateEx> getPostSlotState() {
    return Optional.ofNullable(postSlotState);
  }

  public Optional<BeaconStateEx> getPostBlockState() {
    return Optional.ofNullable(postBlockState);
  }

  public BeaconStateEx getFinalState() {
    return getState();
  }

  @Override
  public String toString() {
    return getFinalState().toString();
  }
}
