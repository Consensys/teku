/*
 * Copyright Consensys Software Inc., 2025
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
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class PendingPartialWithdrawal {
  @JsonProperty("validator_index")
  public final int validatorIndex;

  @JsonProperty("amount")
  public final UInt64 amount;

  @JsonProperty("withdrawable_epoch")
  public final UInt64 withdrawableEpoch;

  public PendingPartialWithdrawal(
      final @JsonProperty("validator_index") int validatorIndex,
      final @JsonProperty("amount") UInt64 amount,
      final @JsonProperty("withdrawable_epoch") UInt64 withdrawableEpoch) {
    this.validatorIndex = validatorIndex;
    this.amount = amount;
    this.withdrawableEpoch = withdrawableEpoch;
  }

  public PendingPartialWithdrawal(
      final tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal
          pendingPartialWithdrawal) {
    this.validatorIndex = pendingPartialWithdrawal.getValidatorIndex();
    this.amount = pendingPartialWithdrawal.getAmount();
    this.withdrawableEpoch = pendingPartialWithdrawal.getWithdrawableEpoch();
  }

  public tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal
      asInternalPendingPartialWithdrawal(final SpecVersion spec) {
    final Optional<SchemaDefinitionsElectra> schemaDefinitionsElectra =
        spec.getSchemaDefinitions().toVersionElectra();
    if (schemaDefinitionsElectra.isEmpty()) {
      throw new IllegalArgumentException(
          "Could not create PendingPartialWithdrawal for pre-electra spec");
    }
    return schemaDefinitionsElectra
        .get()
        .getPendingPartialWithdrawalSchema()
        .create(
            SszUInt64.of(UInt64.valueOf(this.validatorIndex)),
            SszUInt64.of(this.amount),
            SszUInt64.of(this.withdrawableEpoch));
  }
}
