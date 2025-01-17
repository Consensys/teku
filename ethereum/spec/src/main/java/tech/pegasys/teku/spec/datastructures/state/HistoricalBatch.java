/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class HistoricalBatch
    extends Container2<HistoricalBatch, SszBytes32Vector, SszBytes32Vector> {

  public static class HistoricalBatchSchema
      extends ContainerSchema2<HistoricalBatch, SszBytes32Vector, SszBytes32Vector> {

    public HistoricalBatchSchema(final int slotsPerHistoricalRoot) {
      super(
          "HistoricalBatch",
          namedSchema("block_roots", SszBytes32VectorSchema.create(slotsPerHistoricalRoot)),
          namedSchema("state_roots", SszBytes32VectorSchema.create(slotsPerHistoricalRoot)));
    }

    @Override
    public HistoricalBatch createFromBackingNode(final TreeNode node) {
      return new HistoricalBatch(this, node);
    }

    public HistoricalBatch create(
        final SszBytes32Vector blockRoots, final SszBytes32Vector stateRoots) {
      return new HistoricalBatch(this, blockRoots, stateRoots);
    }

    public SszBytes32VectorSchema<?> getBlockRootsSchema() {
      return (SszBytes32VectorSchema<?>) getFieldSchema0();
    }

    public SszBytes32VectorSchema<?> getStateRootsSchema() {
      return (SszBytes32VectorSchema<?>) getFieldSchema1();
    }
  }

  private HistoricalBatch(final HistoricalBatchSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private HistoricalBatch(
      final HistoricalBatchSchema type,
      final SszBytes32Vector blockRoots,
      final SszBytes32Vector stateRoots) {
    super(type, blockRoots, stateRoots);
  }

  public SszBytes32Vector getBlockRoots() {
    return getField0();
  }

  public SszBytes32Vector getStateRoots() {
    return getField1();
  }
}
