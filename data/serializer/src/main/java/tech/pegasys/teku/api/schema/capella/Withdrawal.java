/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.schema.capella;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;

public class Withdrawal {

  @JsonProperty("index")
  private final UInt64 index;

  @JsonProperty("validator_index")
  private final UInt64 validatorIndex;

  @JsonProperty("address")
  private final Eth1Address address;

  @JsonProperty("amount")
  private final UInt64 amount;

  public Withdrawal(
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("validator_index") final UInt64 validatorIndex,
      @JsonProperty("address") final Eth1Address address,
      @JsonProperty("amount") final UInt64 amount) {
    this.index = index;
    this.validatorIndex = validatorIndex;
    this.address = address;
    this.amount = amount;
  }

  public Withdrawal(
      final tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal
          withdrawal) {
    this.index = withdrawal.getIndex();
    this.validatorIndex = withdrawal.getValidatorIndex();
    this.address = Eth1Address.fromBytes(withdrawal.getAddress().getWrappedBytes());
    this.amount = withdrawal.getAmount();
  }

  public tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal
      asInternalWithdrawal(final WithdrawalSchema schema) {
    return schema.create(index, validatorIndex, new Bytes20(address.getWrappedBytes()), amount);
  }
}
