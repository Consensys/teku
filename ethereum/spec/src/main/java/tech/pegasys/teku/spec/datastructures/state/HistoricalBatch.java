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

package tech.pegasys.teku.spec.datastructures.state;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.SpecDependent;

public class HistoricalBatch
    extends Container2<HistoricalBatch, SszBytes32Vector, SszBytes32Vector> {

  public static class HistoricalBatchSchema
      extends ContainerSchema2<HistoricalBatch, SszBytes32Vector, SszBytes32Vector> {

    public HistoricalBatchSchema() {
      super(
          "HistoricalBatch",
          namedSchema(
              "block_roots", SszBytes32VectorSchema.create(Constants.SLOTS_PER_HISTORICAL_ROOT)),
          namedSchema(
              "state_roots", SszBytes32VectorSchema.create(Constants.SLOTS_PER_HISTORICAL_ROOT)));
    }

    @Override
    public HistoricalBatch createFromBackingNode(TreeNode node) {
      return new HistoricalBatch(this, node);
    }

    public HistoricalBatch create(SszBytes32Vector block_roots, SszBytes32Vector state_roots) {
      return new HistoricalBatch(this, block_roots, state_roots);
    }

    public SszBytes32VectorSchema<?> getBlockRootsSchema() {
      return (SszBytes32VectorSchema<?>) getFieldSchema0();
    }

    public SszBytes32VectorSchema<?> getStateRootsSchema() {
      return (SszBytes32VectorSchema<?>) getFieldSchema1();
    }
  }

  public static HistoricalBatchSchema getSszSchema() {
    return SSZ_SCHEMA.get();
  }

  public static final SpecDependent<HistoricalBatchSchema> SSZ_SCHEMA =
      SpecDependent.of(HistoricalBatchSchema::new);

  private HistoricalBatch(HistoricalBatchSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated // Use the constructor with type
  public HistoricalBatch(SszBytes32Vector block_roots, SszBytes32Vector state_roots) {
    this(SSZ_SCHEMA.get(), block_roots, state_roots);
  }

  private HistoricalBatch(
      HistoricalBatchSchema type, SszBytes32Vector block_roots, SszBytes32Vector state_roots) {
    super(type, block_roots, state_roots);
  }

  public SszBytes32Vector getBlockRoots() {
    return getField0();
  }

  public SszBytes32Vector getStateRoots() {
    return getField1();
  }
}
