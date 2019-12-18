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

package tech.pegasys.artemis.statetransition.events;

import java.util.Objects;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;

/** This event is emitted when a new block is proposed locally */
public class BlockProposedEvent {
  private final BeaconBlock block;

  public BlockProposedEvent(final BeaconBlock block) {
    this.block = block;
  }

  public BeaconBlock getBlock() {
    return block;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BlockProposedEvent)) {
      return false;
    }
    final BlockProposedEvent that = (BlockProposedEvent) o;
    return Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block);
  }
}
