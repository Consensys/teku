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

package tech.pegasys.teku.api.schema.eip7732;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PayloadAttestationData {

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 beaconBlockRoot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @Schema(type = "string", format = "byte")
  public final Byte payloadStatus;

  @JsonCreator
  public PayloadAttestationData(
      @JsonProperty("beacon_block_root") final Bytes32 beaconBlockRoot,
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("index") final Byte payloadStatus) {
    this.beaconBlockRoot = beaconBlockRoot;
    this.slot = slot;
    this.payloadStatus = payloadStatus;
  }

  public PayloadAttestationData(
      final tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData data) {
    this.beaconBlockRoot = data.getBeaconBlockRoot();
    this.slot = data.getSlot();
    this.payloadStatus = data.getPayloadStatus();
  }

  public tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData
      asInternalPayloadAttestationData() {
    return tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData.SSZ_SCHEMA
        .create(beaconBlockRoot, slot, payloadStatus);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof final PayloadAttestationData that)) {
      return false;
    }
    return Objects.equals(beaconBlockRoot, that.beaconBlockRoot)
        && Objects.equals(slot, that.slot)
        && Objects.equals(payloadStatus, that.payloadStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(beaconBlockRoot, slot, payloadStatus);
  }
}
