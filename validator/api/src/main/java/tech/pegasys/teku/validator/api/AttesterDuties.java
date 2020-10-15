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
import java.util.Objects;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttesterDuties {

  private final BLSPublicKey publicKey;
  private final int validatorIndex;
  private final int committeeLength;
  /** the committee index of the committee that includes the specified validator */
  private final int committeeIndex;

  private final int commiteesAtSlot;
  // index of the validator in the committee
  private final int validatorCommitteeIndex;
  private final UInt64 slot;

  public AttesterDuties(
      final BLSPublicKey publicKey,
      final int validatorIndex,
      final int committeeLength,
      final int committeeIndex,
      final int commiteesAtSlot,
      final int validatorCommitteeIndex,
      final UInt64 slot) {
    this.publicKey = publicKey;
    this.validatorIndex = validatorIndex;
    this.committeeLength = committeeLength;
    this.committeeIndex = committeeIndex;
    this.commiteesAtSlot = commiteesAtSlot;
    this.validatorCommitteeIndex = validatorCommitteeIndex;
    this.slot = slot;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public int getCommitteeLength() {
    return committeeLength;
  }

  public int getCommitteeIndex() {
    return committeeIndex;
  }

  public int getCommiteesAtSlot() {
    return commiteesAtSlot;
  }

  public int getValidatorCommitteeIndex() {
    return validatorCommitteeIndex;
  }

  public UInt64 getSlot() {
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
    final AttesterDuties that = (AttesterDuties) o;
    return validatorIndex == that.validatorIndex
        && committeeLength == that.committeeLength
        && committeeIndex == that.committeeIndex
        && commiteesAtSlot == that.commiteesAtSlot
        && validatorCommitteeIndex == that.validatorCommitteeIndex
        && Objects.equals(publicKey, that.publicKey)
        && Objects.equals(slot, that.slot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        publicKey,
        validatorIndex,
        committeeLength,
        committeeIndex,
        commiteesAtSlot,
        validatorCommitteeIndex,
        slot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("validatorIndex", validatorIndex)
        .add("committeeLength", committeeLength)
        .add("committeeIndex", committeeIndex)
        .add("commiteesAtSlot", commiteesAtSlot)
        .add("validatorCommitteeIndex", validatorCommitteeIndex)
        .add("slot", slot)
        .toString();
  }
}
