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

package tech.pegasys.teku.api.schema.electra;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerWithdrawalRequestSchema;

public class ExecutionLayerWithdrawalRequest {

  @JsonProperty("source_address")
  private final Eth1Address sourceAddress;

  @JsonProperty("validator_pubkey")
  private final BLSPublicKey validatorPublicKey;

  @JsonProperty("amount")
  private final UInt64 amount;

  public ExecutionLayerWithdrawalRequest(
      @JsonProperty("source_address") final Eth1Address sourceAddress,
      @JsonProperty("validator_pubkey") final BLSPublicKey validatorPublicKey,
      @JsonProperty("amount") final UInt64 amount) {
    this.sourceAddress = sourceAddress;
    this.validatorPublicKey = validatorPublicKey;
    this.amount = amount;
  }

  public ExecutionLayerWithdrawalRequest(
      final tech.pegasys.teku.spec.datastructures.execution.versions.electra
              .ExecutionLayerWithdrawalRequest
          executionLayerWithdrawalRequest) {
    this.sourceAddress =
        Eth1Address.fromBytes(executionLayerWithdrawalRequest.getSourceAddress().getWrappedBytes());
    this.validatorPublicKey = executionLayerWithdrawalRequest.getValidatorPublicKey();
    this.amount = executionLayerWithdrawalRequest.getAmount();
  }

  public final tech.pegasys.teku.spec.datastructures.execution.versions.electra
          .ExecutionLayerWithdrawalRequest
      asInternalExecutionLayerWithdrawalRequest(
          final ExecutionLayerWithdrawalRequestSchema schema) {
    return schema.create(sourceAddress, validatorPublicKey, amount);
  }
}
