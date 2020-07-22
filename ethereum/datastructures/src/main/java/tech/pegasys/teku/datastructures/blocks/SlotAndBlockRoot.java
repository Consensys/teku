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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class SlotAndBlockRoot {
  private final UnsignedLong slot;
  private final Bytes32 blockRoot;

  public SlotAndBlockRoot(final UnsignedLong slot, final Bytes32 blockRoot) {
    this.slot = slot;
    this.blockRoot = blockRoot;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SlotAndBlockRoot that = (SlotAndBlockRoot) o;
    return Objects.equals(slot, that.slot) && Objects.equals(blockRoot, that.blockRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, blockRoot);
  }
}
