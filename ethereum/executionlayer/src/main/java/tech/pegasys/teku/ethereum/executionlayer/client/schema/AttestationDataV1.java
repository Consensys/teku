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
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class AttestationDataV1 {
  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 slot;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 index;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 beaconBlockRoot;

  public final CheckpointV1 source;
  public final CheckpointV1 target;

  @JsonCreator
  public AttestationDataV1(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("beaconBlockRoot") final Bytes32 beaconBlockRoot,
      @JsonProperty("source") final CheckpointV1 source,
      @JsonProperty("target") final CheckpointV1 target) {
    this.slot = slot;
    this.index = index;
    this.beaconBlockRoot = beaconBlockRoot;
    this.source = source;
    this.target = target;
  }

  public AttestationDataV1(tech.pegasys.teku.spec.datastructures.operations.AttestationData data) {
    this.slot = data.getSlot();
    this.index = data.getIndex();
    this.beaconBlockRoot = data.getBeaconBlockRoot();
    this.source = new CheckpointV1(data.getSource());
    this.target = new CheckpointV1(data.getTarget());
  }

  public tech.pegasys.teku.spec.datastructures.operations.AttestationData
      asInternalAttestationData() {
    tech.pegasys.teku.spec.datastructures.state.Checkpoint src = source.asInternalCheckpoint();
    tech.pegasys.teku.spec.datastructures.state.Checkpoint tgt = target.asInternalCheckpoint();

    return new tech.pegasys.teku.spec.datastructures.operations.AttestationData(
        slot, index, beaconBlockRoot, src, tgt);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AttestationDataV1)) {
      return false;
    }
    AttestationDataV1 that = (AttestationDataV1) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(index, that.index)
        && Objects.equals(beaconBlockRoot, that.beaconBlockRoot)
        && Objects.equals(source, that.source)
        && Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, index, beaconBlockRoot, source, target);
  }
}
