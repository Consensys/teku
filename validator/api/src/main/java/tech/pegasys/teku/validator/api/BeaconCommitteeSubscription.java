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

package tech.pegasys.teku.validator.api;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Objects;

public class BeaconCommitteeSubscription {
  private final int committeeIndex;
  private final UnsignedLong slot;

  public BeaconCommitteeSubscription(final int committeeIndex, final UnsignedLong slot) {
    this.committeeIndex = committeeIndex;
    this.slot = slot;
  }

  public int getCommitteeIndex() {
    return committeeIndex;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconCommitteeSubscription that = (BeaconCommitteeSubscription) o;
    return committeeIndex == that.committeeIndex && Objects.equals(slot, that.slot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(committeeIndex, slot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("committeeIndex", committeeIndex)
        .add("slot", slot)
        .toString();
  }
}
