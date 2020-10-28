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

package tech.pegasys.teku.datastructures.validator;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SubnetSubscription {

  private final int subnetId;
  private final UInt64 unsubscriptionSlot;

  public SubnetSubscription(int subnetId, UInt64 unsubscriptionSlot) {
    this.subnetId = subnetId;
    this.unsubscriptionSlot = unsubscriptionSlot;
  }

  public int getSubnetId() {
    return subnetId;
  }

  public UInt64 getUnsubscriptionSlot() {
    return unsubscriptionSlot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SubnetSubscription that = (SubnetSubscription) o;
    return subnetId == that.subnetId && Objects.equals(unsubscriptionSlot, that.unsubscriptionSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subnetId, unsubscriptionSlot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("subnetId", subnetId)
        .add("unsubscriptionSlot", unsubscriptionSlot)
        .toString();
  }
}
