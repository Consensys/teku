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

package org.ethereum.beacon.consensus.transition;

import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.TransitionType;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;

/**
 * Class to hold additional state info which is not included to the canonical spec BeaconState but
 * is wanted for state transitions
 */
@SSZSerializable(instanceGetter = "getDelegate")
public class BeaconStateExImpl extends DelegateBeaconState implements BeaconStateEx {

  private final TransitionType lastTransition;

  public BeaconStateExImpl(BeaconState canonicalState, TransitionType lastTransition) {
    super(canonicalState);
    this.lastTransition = lastTransition;
  }

  /** @param canonicalState regular BeaconState */
  public BeaconStateExImpl(BeaconState canonicalState) {
    this(canonicalState, TransitionType.UNKNOWN);
  }

  @Override
  public TransitionType getTransition() {
    return lastTransition;
  }

  @Override
  public String toString() {
    return toString(null, null);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BeaconStateExImpl that = (BeaconStateExImpl) o;
    return lastTransition == that.lastTransition;
  }
}
