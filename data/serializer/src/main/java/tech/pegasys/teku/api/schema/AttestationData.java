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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes32;

public class AttestationData {
  @Schema(type = "string", format = "uint64")
  public final UnsignedLong slot;

  @Schema(type = "string", format = "uint64")
  public final UnsignedLong index;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 beacon_block_root;

  public final Checkpoint source;
  public final Checkpoint target;

  @JsonCreator
  public AttestationData(
      @JsonProperty("slot") final UnsignedLong slot,
      @JsonProperty("index") final UnsignedLong index,
      @JsonProperty("beacon_block_root") final Bytes32 beacon_block_root,
      @JsonProperty("source") final Checkpoint source,
      @JsonProperty("target") final Checkpoint target) {
    this.slot = slot;
    this.index = index;
    this.beacon_block_root = beacon_block_root;
    this.source = source;
    this.target = target;
  }

  public AttestationData(tech.pegasys.teku.datastructures.operations.AttestationData data) {
    this.slot = data.getSlot();
    this.index = data.getIndex();
    this.beacon_block_root = data.getBeacon_block_root();
    this.source = new Checkpoint(data.getSource());
    this.target = new Checkpoint(data.getTarget());
  }

  public tech.pegasys.teku.datastructures.operations.AttestationData asInternalAttestationData() {
    tech.pegasys.teku.datastructures.state.Checkpoint src = source.asInternalCheckpoint();
    tech.pegasys.teku.datastructures.state.Checkpoint tgt = target.asInternalCheckpoint();

    return new tech.pegasys.teku.datastructures.operations.AttestationData(
        slot, index, beacon_block_root, src, tgt);
  }
}
