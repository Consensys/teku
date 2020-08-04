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

package tech.pegasys.teku.core.signatures;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Objects;

class SignedAttestationRecord {
  private final UnsignedLong sourceEpoch;
  private final UnsignedLong targetEpoch;

  public SignedAttestationRecord(final UnsignedLong sourceEpoch, final UnsignedLong targetEpoch) {
    this.sourceEpoch = sourceEpoch;
    this.targetEpoch = targetEpoch;
  }

  public UnsignedLong getSourceEpoch() {
    return sourceEpoch;
  }

  public UnsignedLong getTargetEpoch() {
    return targetEpoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SignedAttestationRecord that = (SignedAttestationRecord) o;
    return Objects.equals(getSourceEpoch(), that.getSourceEpoch())
        && Objects.equals(getTargetEpoch(), that.getTargetEpoch());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getSourceEpoch(), getTargetEpoch());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("sourceEpoch", sourceEpoch)
        .add("targetEpoch", targetEpoch)
        .toString();
  }
}
