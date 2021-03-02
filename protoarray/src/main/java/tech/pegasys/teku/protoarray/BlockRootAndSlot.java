/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.protoarray;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockRootAndSlot {

  private Bytes32 blockRoot;
  private UInt64 slot;

  public BlockRootAndSlot(final Bytes32 blockRoot, final UInt64 slot) {
    this.blockRoot = blockRoot;
    this.slot = slot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BlockRootAndSlot that = (BlockRootAndSlot) o;
    return Objects.equals(blockRoot, that.blockRoot) && Objects.equals(slot, that.slot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockRoot, slot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockRoot", blockRoot)
        .add("slot", slot)
        .toString();
  }
}
