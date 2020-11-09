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
import com.google.common.base.Objects;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Checkpoint {
  public static Checkpoint EMPTY = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);

  @Schema(type = "string", format = "uint64")
  public final UInt64 epoch;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 root;

  public Checkpoint(tech.pegasys.teku.datastructures.state.Checkpoint checkpoint) {
    this.epoch = checkpoint.getEpoch();
    this.root = checkpoint.getRoot();
  }

  @JsonCreator
  public Checkpoint(
      @JsonProperty("epoch") final UInt64 epoch, @JsonProperty("root") final Bytes32 root) {
    this.epoch = epoch;
    this.root = root;
  }

  public tech.pegasys.teku.datastructures.state.Checkpoint asInternalCheckpoint() {
    return new tech.pegasys.teku.datastructures.state.Checkpoint(epoch, root);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Checkpoint)) return false;
    Checkpoint that = (Checkpoint) o;
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
