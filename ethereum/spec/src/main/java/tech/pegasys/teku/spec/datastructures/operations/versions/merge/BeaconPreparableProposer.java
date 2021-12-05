/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.operations.versions.merge;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconPreparableProposer {
  private final UInt64 validatorIndex;
  private final Bytes20 feeRecipient;

  public BeaconPreparableProposer(UInt64 validatorIndex, Bytes20 feeRecipient) {
    this.validatorIndex = validatorIndex;
    this.feeRecipient = feeRecipient;
  }

  public UInt64 getValidatorIndex() {
    return validatorIndex;
  }

  public Bytes20 getFeeRecipient() {
    return feeRecipient;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconPreparableProposer that = (BeaconPreparableProposer) o;
    return Objects.equals(validatorIndex, that.validatorIndex)
        && Objects.equals(feeRecipient, that.feeRecipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, feeRecipient);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("validatorIndex", validatorIndex)
        .add("feeRecipient", feeRecipient)
        .toString();
  }
}
