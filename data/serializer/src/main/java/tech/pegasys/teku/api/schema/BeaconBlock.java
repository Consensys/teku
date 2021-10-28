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
import tech.pegasys.teku.api.schema.interfaces.UnsignedBlock;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;

public class BeaconBlock implements UnsignedBlock {
  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 proposerIndex;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 parentRoot;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 stateRoot;

  private final BeaconBlockBody body;

  public BeaconBlockBody getBody() {
    return body;
  }

  public BeaconBlock(tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock message) {
    this.slot = message.getSlot();
    this.proposerIndex = message.getProposerIndex();
    this.parentRoot = message.getParentRoot();
    this.stateRoot = message.getStateRoot();
    this.body = new BeaconBlockBody(message.getBody());
  }

  @JsonCreator
  public BeaconBlock(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("proposer_index") final UInt64 proposerIndex,
      @JsonProperty("parent_root") final Bytes32 parentRoot,
      @JsonProperty("state_root") final Bytes32 stateRoot,
      @JsonProperty("body") final BeaconBlockBody body) {
    this.slot = slot;
    this.proposerIndex = proposerIndex;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.body = body;
  }

  public tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock asInternalBeaconBlock(
      final Spec spec) {
    final SpecVersion specVersion = spec.atSlot(slot);
    return specVersion
        .getSchemaDefinitions()
        .getBeaconBlockSchema()
        .create(
            slot,
            proposerIndex,
            parentRoot,
            stateRoot,
            body.asInternalBeaconBlockBody(specVersion));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BeaconBlock)) return false;
    BeaconBlock that = (BeaconBlock) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(proposerIndex, that.proposerIndex)
        && Objects.equals(parentRoot, that.parentRoot)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, proposerIndex, parentRoot, stateRoot, body);
  }
}
