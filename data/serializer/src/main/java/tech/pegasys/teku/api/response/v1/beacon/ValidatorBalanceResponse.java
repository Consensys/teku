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

package tech.pegasys.teku.api.response.v1.beacon;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorBalanceResponse {
  @JsonProperty("index")
  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "Index of validator in validator registry.")
  public final UInt64 index;

  @JsonProperty("balance")
  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "Current validator balance in gwei.")
  public final UInt64 balance;

  @JsonCreator
  public ValidatorBalanceResponse(
      @JsonProperty("index") final UInt64 index, @JsonProperty("balance") final UInt64 balance) {
    this.index = index;
    this.balance = balance;
  }

  public static Optional<ValidatorBalanceResponse> fromState(
      final BeaconState state, final Integer index) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }
    return Optional.of(
        new ValidatorBalanceResponse(UInt64.valueOf(index), state.getBalances().get(index)));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ValidatorBalanceResponse)) return false;
    ValidatorBalanceResponse that = (ValidatorBalanceResponse) o;
    return Objects.equal(index, that.index) && Objects.equal(balance, that.balance);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(index, balance);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("index", index).add("balance", balance).toString();
  }
}
