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
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TotalAttestationReward {

  private final long validatorIndex;
  private final long head;
  private final long target;
  private final long source;
  private final Optional<UInt64> inclusionDelay;

  public TotalAttestationReward(
      long validatorIndex, long head, long target, long source, Optional<UInt64> inclusionDelay) {
    this.validatorIndex = validatorIndex;
    this.head = head;
    this.target = target;
    this.source = source;
    this.inclusionDelay = inclusionDelay;
  }

  public long getValidatorIndex() {
    return validatorIndex;
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

  public Optional<UInt64> getInclusionDelay() {
    return inclusionDelay;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TotalAttestationReward that = (TotalAttestationReward) o;
    return validatorIndex == that.validatorIndex
        && head == that.head
        && target == that.target
        && source == that.source
        && inclusionDelay.equals(that.inclusionDelay);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, head, target, source, inclusionDelay);
  }
}
