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

package org.ethereum.beacon.consensus;

import java.util.function.Function;
import javax.annotation.Nullable;
import org.ethereum.beacon.consensus.transition.BeaconStateExImpl;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.spec.SpecConstants;
import tech.pegasys.artemis.ethereum.core.Hash32;

/** Class to hold additional state info which is not included to the canonical spec BeaconState */
public interface BeaconStateEx extends BeaconState {

  static BeaconStateEx getEmpty() {
    return new BeaconStateExImpl(BeaconState.getEmpty(), TransitionType.UNKNOWN);
  }

  static BeaconStateEx getEmpty(SpecConstants specConst) {
    return new BeaconStateExImpl(BeaconState.getEmpty(specConst), TransitionType.UNKNOWN);
  }

  TransitionType getTransition();

  default String toString(
      @Nullable SpecConstants constants, @Nullable Function<Object, Hash32> blockHasher) {
    return "BeaconStateEx[headBlock="
        + (blockHasher != null ? blockHasher.apply(getLatestBlockHeader()) : Hash32.ZERO)
            .toStringShort()
        + (getTransition() == TransitionType.UNKNOWN ? "" : ", lastTransition=" + getTransition())
        + ", "
        + toStringShort(constants);
  }
}
