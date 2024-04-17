/*
 * Copyright Consensys Software Inc., 2024
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

public class PendingBalanceDeposit {

  @JsonProperty("index")
  public final int index;

  @JsonProperty("amount")
  public final UInt64 amount;

  public PendingBalanceDeposit(
      @JsonProperty("index") int index, @JsonProperty("amount") UInt64 amount) {
    this.index = index;
    this.amount = amount;
  }

  public PendingBalanceDeposit(
      final tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit
          internalPendingBalanceDeposit) {
    this.index = internalPendingBalanceDeposit.getIndex();
    this.amount = internalPendingBalanceDeposit.getAmount();
  }

  public tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit
      asInternalPendingBalanceDeposit(final SpecVersion spec) {
    final Optional<SchemaDefinitionsElectra> schemaDefinitionsElectra =
        spec.getSchemaDefinitions().toVersionElectra();
    if (schemaDefinitionsElectra.isEmpty()) {
      throw new IllegalArgumentException(
          "Could not create PendingBalanceDeposit for pre-electra spec");
    }
    return schemaDefinitionsElectra
        .get()
        .getPendingBalanceDepositSchema()
        .create(SszUInt64.of(UInt64.valueOf(this.index)), SszUInt64.of(this.amount));
  }
}
