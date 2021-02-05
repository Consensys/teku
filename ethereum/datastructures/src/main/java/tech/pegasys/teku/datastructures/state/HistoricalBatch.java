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
import tech.pegasys.teku.ssz.backing.type.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class HistoricalBatch
    extends Container2<HistoricalBatch, SszVector<Bytes32View>, SszVector<Bytes32View>> {

  public static class HistoricalBatchType
      extends ContainerType2<
          HistoricalBatch, SszVector<Bytes32View>, SszVector<Bytes32View>> {

    public HistoricalBatchType() {
      super(
          "HistoricalBatch",
          namedType(
              "block_roots",
              new VectorViewType<>(
                  SszPrimitiveSchemas.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT)),
          namedType(
              "state_roots",
              new VectorViewType<>(
                  SszPrimitiveSchemas.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT)));
    }

    @Override
    public HistoricalBatch createFromBackingNode(TreeNode node) {
      return new HistoricalBatch(this, node);
    }

    public HistoricalBatch create(SSZVector<Bytes32> block_roots, SSZVector<Bytes32> state_roots) {
      return new HistoricalBatch(this, block_roots, state_roots);
    }

    public VectorViewType<Bytes32View> getBlockRootsType() {
      return (VectorViewType<Bytes32View>) getFieldType0();
    }

    public VectorViewType<Bytes32View> getStateRootsType() {
      return (VectorViewType<Bytes32View>) getFieldType1();
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
        SszUtils.toVectorView(type.getBlockRootsType(), block_roots, Bytes32View::new),
        SszUtils.toVectorView(type.getStateRootsType(), state_roots, Bytes32View::new));
  }

  public SSZVector<Bytes32> getBlockRoots() {
    return new SSZBackingVector<>(
        Bytes32.class, getField0(), Bytes32View::new, AbstractSszPrimitive::get);
  }

  public SSZVector<Bytes32> getStateRoots() {
    return new SSZBackingVector<>(
        Bytes32.class, getField1(), Bytes32View::new, AbstractSszPrimitive::get);
  }
}
