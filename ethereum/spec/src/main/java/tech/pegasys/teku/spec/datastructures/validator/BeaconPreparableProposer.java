/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.validator;

import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record BeaconPreparableProposer(UInt64 validatorIndex, Eth1Address feeRecipient) {

  public static final DeserializableTypeDefinition<BeaconPreparableProposer> SSZ_DATA =
      DeserializableTypeDefinition.object(
              BeaconPreparableProposer.class, BeaconPreparableProposer.Builder.class)
          .name("BeaconPreparableProposer")
          .finisher(BeaconPreparableProposer.Builder::build)
          .initializer(BeaconPreparableProposer::builder)
          .description("The fee recipient that should be used by an associated validator index.")
          .withField(
              "validator_index",
              UINT64_TYPE,
              BeaconPreparableProposer::validatorIndex,
              BeaconPreparableProposer.Builder::validatorIndex)
          .withField(
              "fee_recipient",
              ETH1ADDRESS_TYPE,
              BeaconPreparableProposer::feeRecipient,
              BeaconPreparableProposer.Builder::feeRecipient)
          .build();

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("validatorIndex", validatorIndex)
        .add("feeRecipient", feeRecipient)
        .toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private UInt64 validatorIndex;
    private Eth1Address feeRecipient;

    public Builder() {}

    public Builder feeRecipient(final Eth1Address feeRecipient) {
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
