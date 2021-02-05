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

package tech.pegasys.teku.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.util.SpecDependent;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class HistoricalBatch
    extends Container2<HistoricalBatch, SszVector<SszBytes32>, SszVector<SszBytes32>> {

  public static class HistoricalBatchType
      extends ContainerType2<HistoricalBatch, SszVector<SszBytes32>, SszVector<SszBytes32>> {

    public HistoricalBatchType() {
      super(
          "HistoricalBatch",
          namedSchema(
              "block_roots",
              new SszVectorSchema<>(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.SLOTS_PER_HISTORICAL_ROOT)),
          namedSchema(
              "state_roots",
              new SszVectorSchema<>(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.SLOTS_PER_HISTORICAL_ROOT)));
    }

    @Override
    public HistoricalBatch createFromBackingNode(TreeNode node) {
      return new HistoricalBatch(this, node);
    }

    public HistoricalBatch create(SSZVector<Bytes32> block_roots, SSZVector<Bytes32> state_roots) {
      return new HistoricalBatch(this, block_roots, state_roots);
    }

    public SszVectorSchema<SszBytes32> getBlockRootsType() {
      return (SszVectorSchema<SszBytes32>) getFieldType0();
    }

    public SszVectorSchema<SszBytes32> getStateRootsType() {
      return (SszVectorSchema<SszBytes32>) getFieldType1();
    }
  }

  public static HistoricalBatchType getSszType() {
    return TYPE.get();
  }

  public static final SpecDependent<HistoricalBatchType> TYPE =
      SpecDependent.of(HistoricalBatchType::new);

  private HistoricalBatch(HistoricalBatchType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated // Use the constructor with type
  public HistoricalBatch(SSZVector<Bytes32> block_roots, SSZVector<Bytes32> state_roots) {
    this(TYPE.get(), block_roots, state_roots);
  }

  private HistoricalBatch(
      HistoricalBatchType type, SSZVector<Bytes32> block_roots, SSZVector<Bytes32> state_roots) {
    super(
        type,
        SszUtils.toVectorView(type.getBlockRootsType(), block_roots, SszBytes32::new),
        SszUtils.toVectorView(type.getStateRootsType(), state_roots, SszBytes32::new));
  }

  public SSZVector<Bytes32> getBlockRoots() {
    return new SSZBackingVector<>(
        Bytes32.class, getField0(), SszBytes32::new, AbstractSszPrimitive::get);
  }

  public SSZVector<Bytes32> getStateRoots() {
    return new SSZBackingVector<>(
        Bytes32.class, getField1(), SszBytes32::new, AbstractSszPrimitive::get);
  }
}
