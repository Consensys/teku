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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.Copyable;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractBasicView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class HistoricalBatch extends
    Container2<HistoricalBatch, VectorViewRead<Bytes32View>, VectorViewRead<Bytes32View>>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  private static final VectorViewType<Bytes32View> LIST_VIEW_TYPE =
      new VectorViewType<>(BasicViewTypes.BYTES32_TYPE,
          Constants.SLOTS_PER_HISTORICAL_ROOT);

  @SszTypeDescriptor
  public static final ContainerType2<HistoricalBatch, VectorViewRead<Bytes32View>, VectorViewRead<Bytes32View>> TYPE = ContainerType2
      .create(
          LIST_VIEW_TYPE,
          LIST_VIEW_TYPE,
          HistoricalBatch::new);

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private SSZVector<Bytes32> block_roots; // Vector bounded by SLOTS_PER_HISTORICAL_ROOT
  private SSZVector<Bytes32> state_roots; // Vector bounded by SLOTS_PER_HISTORICAL_ROOT

  private HistoricalBatch(
      ContainerType2<HistoricalBatch, VectorViewRead<Bytes32View>, VectorViewRead<Bytes32View>> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public HistoricalBatch(SSZVector<Bytes32> block_roots, SSZVector<Bytes32> state_roots) {
    super(TYPE, ViewUtils.toVectorView(LIST_VIEW_TYPE, block_roots, Bytes32View::new),
        ViewUtils.toVectorView(LIST_VIEW_TYPE, state_roots, Bytes32View::new));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytesVector(block_roots.asList())),
        SSZ.encode(writer -> writer.writeFixedBytesVector(state_roots.asList())));
  }

  public SSZVector<Bytes32> getBlockRoots() {
    return new SSZBackingVector<>(
        Bytes32.class,
        getField0(),
        Bytes32View::new,
        AbstractBasicView::get);
  }

  public SSZVector<Bytes32> getStateRoots() {
    return new SSZBackingVector<>(
        Bytes32.class,
        getField1(),
        Bytes32View::new,
        AbstractBasicView::get);
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
