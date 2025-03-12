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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class PendingDeposit {

  @JsonProperty("pubkey")
  private final BLSPublicKey publicKey;

  @JsonProperty("withdrawal_credentials")
  private final Bytes32 withdrawalCredentials;

  @JsonProperty("amount")
  public final UInt64 amount;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  @JsonProperty("slot")
  public final UInt64 slot;

  public PendingDeposit(
      final @JsonProperty("pubkey") BLSPublicKey publicKey,
      final @JsonProperty("withdrawal_credentials") Bytes32 withdrawalCredentials,
      final @JsonProperty("amount") UInt64 amount,
      final @JsonProperty("signature") BLSSignature signature,
      final @JsonProperty("slot") UInt64 slot) {
    this.publicKey = publicKey;
    this.withdrawalCredentials = withdrawalCredentials;
    this.amount = amount;
    this.signature = signature;
    this.slot = slot;
  }

  public PendingDeposit(
      final tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit
          internalPendingDeposit) {
    this.publicKey = internalPendingDeposit.getPublicKey();
    this.withdrawalCredentials = internalPendingDeposit.getWithdrawalCredentials();
    this.amount = internalPendingDeposit.getAmount();
    this.signature = new BLSSignature(internalPendingDeposit.getSignature());
    this.slot = internalPendingDeposit.getSlot();
  }

  public tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit
      asInternalPendingDeposit(final SpecVersion spec) {
    final Optional<SchemaDefinitionsElectra> schemaDefinitionsElectra =
        spec.getSchemaDefinitions().toVersionElectra();
    if (schemaDefinitionsElectra.isEmpty()) {
      throw new IllegalArgumentException("Could not create PendingDeposit for pre-electra spec");
    }
    return schemaDefinitionsElectra
        .get()
        .getPendingDepositSchema()
        .create(
            new SszPublicKey(publicKey),
            SszBytes32.of(withdrawalCredentials),
            SszUInt64.of(amount),
            new SszSignature(signature.asInternalBLSSignature()),
            SszUInt64.of(slot));
  }
}
