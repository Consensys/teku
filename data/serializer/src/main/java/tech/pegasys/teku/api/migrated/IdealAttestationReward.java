/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.api.migrated;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class IdealAttestationReward {

  private final UInt64 effectiveBalance;
  private final long head;
  private final long target;
  private final long source;

  public IdealAttestationReward(UInt64 effectiveBalance, long head, long target, long source) {
    this.effectiveBalance = effectiveBalance;
    this.head = head;
    this.target = target;
    this.source = source;
  }

  public UInt64 getEffectiveBalance() {
    return effectiveBalance;
  }

  public long getHead() {
    return head;
  }

  public long getTarget() {
    return target;
  }

  public long getSource() {
    return source;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    IdealAttestationReward that = (IdealAttestationReward) o;
    return effectiveBalance == that.effectiveBalance
        && head == that.head
        && target == that.target
        && source == that.source;
  }

  @Override
  public int hashCode() {
    return Objects.hash(effectiveBalance, head, target, source);
  }
}
