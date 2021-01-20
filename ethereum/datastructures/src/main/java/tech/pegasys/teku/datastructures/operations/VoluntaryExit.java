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

package tech.pegasys.teku.datastructures.operations;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class VoluntaryExit extends
    Container2<VoluntaryExit, UInt64View, UInt64View> implements Merkleizable,
    SimpleOffsetSerializable, SSZContainer {

  @SszTypeDescriptor
  public static final ContainerType2<VoluntaryExit, UInt64View, UInt64View> TYPE = ContainerType2
      .create(
          BasicViewTypes.UINT64_TYPE, BasicViewTypes.UINT64_TYPE,
          VoluntaryExit::new);

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private UInt64 epoch;
  private UInt64 validator_index;

  public VoluntaryExit(
      ContainerType2<VoluntaryExit, UInt64View, UInt64View> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public VoluntaryExit(UInt64 epoch, UInt64 validator_index) {
    super(TYPE, new UInt64View(epoch), new UInt64View(validator_index));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(epoch.longValue()), SSZ.encodeUInt64(validator_index.longValue()));
  }

  public UInt64 getEpoch() {
    return getField0().get();
  }

  public UInt64 getValidator_index() {
    return getField1().get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
