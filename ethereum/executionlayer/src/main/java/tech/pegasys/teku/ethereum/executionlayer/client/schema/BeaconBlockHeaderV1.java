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

package tech.pegasys.teku.ethereum.executionlayer.client.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class BeaconBlockHeaderV1 {
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

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 bodyRoot;

  @JsonCreator
  public BeaconBlockHeaderV1(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("proposerIndex") final UInt64 proposerIndex,
      @JsonProperty("parentRoot") final Bytes32 parentRoot,
      @JsonProperty("stateRoot") final Bytes32 stateRoot,
      @JsonProperty("bodyRoot") final Bytes32 bodyRoot) {
    this.slot = slot;
    this.proposerIndex = proposerIndex;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.bodyRoot = bodyRoot;
  }

  public BeaconBlockHeaderV1(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader header) {
    this.slot = header.getSlot();
    this.proposerIndex = header.getProposerIndex();
    this.parentRoot = header.getParentRoot();
    this.stateRoot = header.getStateRoot();
    this.bodyRoot = header.getBodyRoot();
  }

  public tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader
      asInternalBeaconBlockHeader() {
    return new tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader(
        slot, proposerIndex, parentRoot, stateRoot, bodyRoot);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconBlockHeaderV1 that = (BeaconBlockHeaderV1) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(proposerIndex, that.proposerIndex)
        && Objects.equals(parentRoot, that.parentRoot)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(bodyRoot, that.bodyRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, proposerIndex, parentRoot, stateRoot, bodyRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("proposerIndex", proposerIndex)
        .add("parentRoot", parentRoot)
        .add("stateRoot", stateRoot)
        .add("bodyRoot", bodyRoot)
        .toString();
  }
}
