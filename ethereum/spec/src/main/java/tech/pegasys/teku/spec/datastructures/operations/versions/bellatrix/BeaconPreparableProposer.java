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

package tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES20_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconPreparableProposer {
  private static final DeserializableTypeDefinition<BeaconPreparableProposer>
      BEACON_PREPARABLE_PROPOSER_TYPE =
          DeserializableTypeDefinition.object(BeaconPreparableProposer.class, Builder.class)
              .name("BeaconPreparableProposer")
              .finisher(Builder::build)
              .initializer(BeaconPreparableProposer::builder)
              .description(
                  "The fee recipient that should be used by an associated validator index.")
              .withField(
                  "validator_index",
                  UINT64_TYPE,
                  BeaconPreparableProposer::getValidatorIndex,
                  Builder::validatorIndex)
              .withField(
                  "fee_recipient",
                  BYTES20_TYPE,
                  BeaconPreparableProposer::getFeeRecipient,
                  Builder::feeRecipient)
              .build();

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

  public static DeserializableTypeDefinition<BeaconPreparableProposer> getJsonTypeDefinition() {
    return BEACON_PREPARABLE_PROPOSER_TYPE;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private UInt64 validatorIndex;
    private Bytes20 feeRecipient;

    public Builder() {}

    public Builder feeRecipient(final Bytes20 feeRecipient) {
      this.feeRecipient = feeRecipient;
      return this;
    }

    public Builder validatorIndex(final UInt64 validatorIndex) {
      this.validatorIndex = validatorIndex;
      return this;
    }

    public BeaconPreparableProposer build() {
      return new BeaconPreparableProposer(validatorIndex, feeRecipient);
    }
  }
}
