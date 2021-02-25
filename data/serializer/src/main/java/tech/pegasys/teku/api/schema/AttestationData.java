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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttestationData {
  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 index;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 beacon_block_root;

  public final Checkpoint source;
  public final Checkpoint target;

  @JsonCreator
  public AttestationData(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("beacon_block_root") final Bytes32 beacon_block_root,
      @JsonProperty("source") final Checkpoint source,
      @JsonProperty("target") final Checkpoint target) {
    this.slot = slot;
    this.index = index;
    this.beacon_block_root = beacon_block_root;
    this.source = source;
    this.target = target;
  }

  public AttestationData(tech.pegasys.teku.spec.datastructures.operations.AttestationData data) {
    this.slot = data.getSlot();
    this.index = data.getIndex();
    this.beacon_block_root = data.getBeacon_block_root();
    this.source = new Checkpoint(data.getSource());
    this.target = new Checkpoint(data.getTarget());
  }

  public tech.pegasys.teku.spec.datastructures.operations.AttestationData
      asInternalAttestationData() {
    tech.pegasys.teku.spec.datastructures.state.Checkpoint src = source.asInternalCheckpoint();
    tech.pegasys.teku.spec.datastructures.state.Checkpoint tgt = target.asInternalCheckpoint();

    return new tech.pegasys.teku.spec.datastructures.operations.AttestationData(
        slot, index, beacon_block_root, src, tgt);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AttestationData)) return false;
    AttestationData that = (AttestationData) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(index, that.index)
        && Objects.equals(beacon_block_root, that.beacon_block_root)
        && Objects.equals(source, that.source)
        && Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, index, beacon_block_root, source, target);
  }
}
