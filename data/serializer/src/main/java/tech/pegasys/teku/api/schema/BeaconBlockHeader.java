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
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconBlockHeader {
  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 proposer_index;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 parent_root;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 state_root;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 body_root;

  @JsonCreator
  public BeaconBlockHeader(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("proposer_index") final UInt64 proposer_index,
      @JsonProperty("parent_root") final Bytes32 parent_root,
      @JsonProperty("state_root") final Bytes32 state_root,
      @JsonProperty("body_root") final Bytes32 body_root) {
    this.slot = slot;
    this.proposer_index = proposer_index;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.body_root = body_root;
  }

  public BeaconBlockHeader(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader header) {
    this.slot = header.getSlot();
    this.proposer_index = header.getProposerIndex();
    this.parent_root = header.getParentRoot();
    this.state_root = header.getStateRoot();
    this.body_root = header.getBodyRoot();
  }

  public tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader
      asInternalBeaconBlockHeader() {
    return new tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader(
        slot, proposer_index, parent_root, state_root, body_root);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BeaconBlockHeader that = (BeaconBlockHeader) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(proposer_index, that.proposer_index)
        && Objects.equals(parent_root, that.parent_root)
        && Objects.equals(state_root, that.state_root)
        && Objects.equals(body_root, that.body_root);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, proposer_index, parent_root, state_root, body_root);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("proposer_index", proposer_index)
        .add("parent_root", parent_root)
        .add("state_root", state_root)
        .add("body_root", body_root)
        .toString();
  }
}
