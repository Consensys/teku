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
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractBasicView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class HistoricalBatch
    extends Container2<HistoricalBatch, VectorViewRead<Bytes32View>, VectorViewRead<Bytes32View>>
    implements SimpleOffsetSerializable, SSZContainer {

  static class HistoricalBatchType
      extends ContainerType2<
          HistoricalBatch, VectorViewRead<Bytes32View>, VectorViewRead<Bytes32View>> {

    private final VectorViewType<Bytes32View> blockRootsType;
    private final VectorViewType<Bytes32View> stateRootsType;

    public HistoricalBatchType(
        VectorViewType<Bytes32View> blockRootsType, VectorViewType<Bytes32View> stateRootsType) {
      super(blockRootsType, stateRootsType);
      this.blockRootsType = blockRootsType;
      this.stateRootsType = stateRootsType;
    }

    @Override
    public HistoricalBatch createFromBackingNode(TreeNode node) {
      return new HistoricalBatch(this, node);
    }

    public HistoricalBatch create(SSZVector<Bytes32> block_roots, SSZVector<Bytes32> state_roots) {
      return new HistoricalBatch(this, block_roots, state_roots);
    }

    public VectorViewType<Bytes32View> getBlockRootsType() {
      return blockRootsType;
    }

    public VectorViewType<Bytes32View> getStateRootsType() {
      return stateRootsType;
    }
  }

  @SszTypeDescriptor
  public static HistoricalBatchType getSszType() {
    return TYPE.get();
  }

  public static final SpecDependent<HistoricalBatchType> TYPE =
      SpecDependent.of(
          () ->
              new HistoricalBatchType(
                  new VectorViewType<>(
                      BasicViewTypes.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT),
                  new VectorViewType<>(
                      BasicViewTypes.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT)));

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
        ViewUtils.toVectorView(type.getBlockRootsType(), block_roots, Bytes32View::new),
        ViewUtils.toVectorView(type.getStateRootsType(), state_roots, Bytes32View::new));
  }

  public SSZVector<Bytes32> getBlockRoots() {
    return new SSZBackingVector<>(
        Bytes32.class, getField0(), Bytes32View::new, AbstractBasicView::get);
  }

  public SSZVector<Bytes32> getStateRoots() {
    return new SSZBackingVector<>(
        Bytes32.class, getField1(), Bytes32View::new, AbstractBasicView::get);
  }
}
