/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;

public class BlindedBeaconBlockV1 {
  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 slot;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 proposerIndex;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 parentRoot;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 stateRoot;

  protected final BlindedBeaconBlockBodyV1 body;

  public BlindedBeaconBlockBodyV1 getBody() {
    return body;
  }

  @JsonCreator
  public BlindedBeaconBlockV1(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("proposerIndex") final UInt64 proposerIndex,
      @JsonProperty("parentRoot") final Bytes32 parentRoot,
      @JsonProperty("stateRoot") final Bytes32 stateRoot,
      @JsonProperty("body") final BlindedBeaconBlockBodyV1 body) {
    this.slot = slot;
    this.proposerIndex = proposerIndex;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.body = body;
  }

  public BeaconBlockSchema getBeaconBlockSchema(final SpecVersion spec) {
    return spec.getSchemaDefinitions().getBeaconBlockSchema();
  }

  public BlindedBeaconBlockV1(tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock message) {
    this.slot = message.getSlot();
    this.proposerIndex = message.getProposerIndex();
    this.parentRoot = message.getParentRoot();
    this.stateRoot = message.getStateRoot();
    this.body =
        new BlindedBeaconBlockBodyV1(message.getBody().toBlindedVersionBellatrix().orElseThrow());
  }

  public tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock asInternalBeaconBlock(
      final Spec spec) {
    final SpecVersion specVersion = spec.atSlot(slot);
    return getBeaconBlockSchema(spec.atSlot(slot))
        .create(
            slot,
            proposerIndex,
            parentRoot,
            stateRoot,
            body.asInternalBeaconBlockBody(specVersion));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BlindedBeaconBlockV1)) {
      return false;
    }
    BlindedBeaconBlockV1 that = (BlindedBeaconBlockV1) o;
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

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("proposerIndex", proposerIndex)
        .add("parentRoot", parentRoot)
        .add("stateRoot", stateRoot)
        .add("body", body)
        .toString();
  }
}
