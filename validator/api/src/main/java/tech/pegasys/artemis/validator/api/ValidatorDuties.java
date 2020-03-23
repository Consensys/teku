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

package tech.pegasys.artemis.validator.api;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class ValidatorDuties {
  private final BLSPublicKey publicKey;
  private final Optional<Duties> duties;

  private ValidatorDuties(final BLSPublicKey publicKey, final Optional<Duties> duties) {
    this.publicKey = publicKey;
    this.duties = duties;
  }

  public static ValidatorDuties forKnownValidator(
      final BLSPublicKey publicKey,
      final int validatorIndex,
      final int attestationCommitteeIndex,
      final List<UnsignedLong> blockProposalSlots,
      final UnsignedLong attestationSlot) {
    return new ValidatorDuties(
        publicKey,
        Optional.of(
            new Duties(
                validatorIndex, attestationCommitteeIndex, blockProposalSlots, attestationSlot)));
  }

  public static ValidatorDuties forUnknownValidator(final BLSPublicKey publicKey) {
    return new ValidatorDuties(publicKey, Optional.empty());
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public Optional<Duties> getDuties() {
    return duties;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorDuties that = (ValidatorDuties) o;
    return Objects.equals(publicKey, that.publicKey) && Objects.equals(duties, that.duties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey, duties);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("duties", duties)
        .toString();
  }

  public static class Duties {
    private final int validatorIndex;
    private final int attestationCommitteeIndex;
    private final List<UnsignedLong> blockProposalSlots;
    private final UnsignedLong attestationSlot;

    public Duties(
        final int validatorIndex,
        final int attestationCommitteeIndex,
        final List<UnsignedLong> blockProposalSlots,
        final UnsignedLong attestationSlot) {
      this.validatorIndex = validatorIndex;
      this.attestationCommitteeIndex = attestationCommitteeIndex;
      this.blockProposalSlots = blockProposalSlots;
      this.attestationSlot = attestationSlot;
    }

    public int getValidatorIndex() {
      return validatorIndex;
    }

    public int getAttestationCommitteeIndex() {
      return attestationCommitteeIndex;
    }

    public List<UnsignedLong> getBlockProposalSlots() {
      return blockProposalSlots;
    }

    public UnsignedLong getAttestationSlot() {
      return attestationSlot;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Duties duties = (Duties) o;
      return validatorIndex == duties.validatorIndex
          && attestationCommitteeIndex == duties.attestationCommitteeIndex
          && Objects.equals(blockProposalSlots, duties.blockProposalSlots)
          && Objects.equals(attestationSlot, duties.attestationSlot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          validatorIndex, attestationCommitteeIndex, blockProposalSlots, attestationSlot);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("validatorIndex", validatorIndex)
          .add("attestationCommitteeIndex", attestationCommitteeIndex)
          .add("blockProposalSlots", blockProposalSlots)
          .add("attestationSlot", attestationSlot)
          .toString();
    }
  }
}
