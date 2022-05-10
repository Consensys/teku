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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CheckpointV1 {
  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 epoch;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 root;

  public CheckpointV1(tech.pegasys.teku.spec.datastructures.state.Checkpoint checkpoint) {
    this.epoch = checkpoint.getEpoch();
    this.root = checkpoint.getRoot();
  }

  @JsonCreator
  public CheckpointV1(
      @JsonProperty("epoch") final UInt64 epoch, @JsonProperty("root") final Bytes32 root) {
    this.epoch = epoch;
    this.root = root;
  }

  public tech.pegasys.teku.spec.datastructures.state.Checkpoint asInternalCheckpoint() {
    return new tech.pegasys.teku.spec.datastructures.state.Checkpoint(epoch, root);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CheckpointV1)) {
      return false;
    }
    CheckpointV1 that = (CheckpointV1) o;
    return Objects.equal(epoch, that.epoch) && Objects.equal(root, that.root);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(epoch, root);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("epoch", epoch).add("root", root).toString();
  }
}
