/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.metadata;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;

<<<<<<< HEAD
public record BlockContainerAndMetaData<T extends BlockContainer>(
    T blockContainer, SpecMilestone specMilestone, UInt256 executionPayloadValue) {}
=======
public class BlockContainerAndMetaData extends ObjectAndMetaData<BlockContainer> {

  private final UInt256 blockValue;

  public BlockContainerAndMetaData(
      final BlockContainer data,
      final SpecMilestone milestone,
      final boolean executionOptimistic,
      final boolean canonical,
      final boolean finalized,
      final UInt256 blockValue) {
    super(data, milestone, executionOptimistic, canonical, finalized);
    this.blockValue = blockValue;
  }

  public UInt256 getBlockValue() {
    return blockValue;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlockContainerAndMetaData that = (BlockContainerAndMetaData) o;
    return super.equals(o) && blockValue.equals(that.blockValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), blockValue);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("data", getData())
        .add("milestone", getMilestone())
        .add("executionOptimistic", isExecutionOptimistic())
        .add("canonical", isCanonical())
        .add("blockValue", blockValue.toDecimalString())
        .toString();
  }
}
>>>>>>> d9bcb3fd14 (Override equals)
