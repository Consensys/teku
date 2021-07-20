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

package tech.pegasys.teku.api.schema.altair;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class SyncCommitteeMessage {
  @Schema(type = "string", format = "uint64")
  @JsonProperty("slot")
  public final UInt64 slot;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  @JsonProperty("beacon_block_root")
  public final Bytes32 beaconBlockRoot;

  @Schema(type = "string", format = "uint64")
  @JsonProperty("validator_index")
  public final UInt64 validatorIndex;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  @JsonProperty("signature")
  public final BLSSignature signature;

  @JsonCreator
  public SyncCommitteeMessage(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("beacon_block_root") final Bytes32 beaconBlockRoot,
      @JsonProperty("validator_index") final UInt64 validatorIndex,
      @JsonProperty("signature") final BLSSignature signature) {
    this.slot = slot;
    this.beaconBlockRoot = beaconBlockRoot;
    this.validatorIndex = validatorIndex;
    this.signature = signature;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SyncCommitteeMessage that = (SyncCommitteeMessage) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(beaconBlockRoot, that.beaconBlockRoot)
        && Objects.equals(validatorIndex, that.validatorIndex)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, beaconBlockRoot, validatorIndex, signature);
  }

  public Optional<
          tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage>
      asInternalCommitteeSignature(final Spec spec) {
    final Optional<SchemaDefinitionsAltair> maybeSchema =
        spec.atSlot(slot).getSchemaDefinitions().toVersionAltair();
    if (maybeSchema.isEmpty()) {
      final String message =
          String.format(
              "Could not create sync committee signature at phase0 slot %s for validator %s",
              slot, validatorIndex);
      throw new IllegalArgumentException(message);
    }
    return maybeSchema.map(
        schema ->
            schema
                .getSyncCommitteeMessageSchema()
                .create(slot, beaconBlockRoot, validatorIndex, signature.asInternalBLSSignature()));
  }
}
