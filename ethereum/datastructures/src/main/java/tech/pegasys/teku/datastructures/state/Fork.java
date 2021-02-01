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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes4View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class Fork extends Container3<Fork, Bytes4View, Bytes4View, UInt64View>
    implements SimpleOffsetSerializable, SSZContainer {

  static class ForkType extends ContainerType3<Fork, Bytes4View, Bytes4View, UInt64View> {

    public ForkType() {
      super(
          "Fork",
          namedType("previous_version", BasicViewTypes.BYTES4_TYPE),
          namedType("current_version", BasicViewTypes.BYTES4_TYPE),
          namedType("epoch", BasicViewTypes.UINT64_TYPE));
    }

    @Override
    public Fork createFromBackingNode(TreeNode node) {
      return new Fork(this, node);
    }
  }

  @SszTypeDescriptor public static final ForkType TYPE = new ForkType();

  private Fork(ForkType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Fork(Bytes4 previous_version, Bytes4 current_version, UInt64 epoch) {
    super(
        TYPE,
        new Bytes4View(previous_version),
        new Bytes4View(current_version),
        new UInt64View(epoch));
  }

  public Bytes4 getPrevious_version() {
    return getField0().get();
  }

  public Bytes4 getCurrent_version() {
    return getField1().get();
  }

  public UInt64 getEpoch() {
    return getField2().get();
  }
}
