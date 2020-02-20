/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.test.acceptance.dsl.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class BeaconHead {

  private final UnsignedLong slot;

  @JsonProperty("block_root")
  private final Bytes32 blockRoot;

  @JsonProperty("state_root")
  private final Bytes32 stateRoot;

  public BeaconHead(final UnsignedLong slot, final Bytes32 blockRoot, final Bytes32 stateRoot) {
    this.slot = slot;
    this.blockRoot = blockRoot;
    this.stateRoot = stateRoot;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconHead that = (BeaconHead) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(blockRoot, that.blockRoot)
        && Objects.equals(stateRoot, that.stateRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, blockRoot, stateRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("blockRoot", blockRoot)
        .add("stateRoot", stateRoot)
        .toString();
  }
}
