/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.forkchoice;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class VoteTracker extends Container3<VoteTracker, Bytes32View, Bytes32View, UInt64View>
    implements SimpleOffsetSerializable {

  static class VoteTrackerType extends
      ContainerType3<VoteTracker, Bytes32View, Bytes32View, UInt64View> {

    public VoteTrackerType() {
      super(BasicViewTypes.BYTES32_TYPE, BasicViewTypes.BYTES32_TYPE, BasicViewTypes.UINT64_TYPE);
    }

    @Override
    public VoteTracker createFromBackingNode(TreeNode node) {
      return new VoteTracker(this, node);
    }
  }

  @SszTypeDescriptor
  public static final VoteTrackerType TYPE = new VoteTrackerType();
  public static final VoteTracker DEFAULT = new VoteTracker();


  private VoteTracker(
      ContainerType3<VoteTracker, Bytes32View, Bytes32View, UInt64View> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  private VoteTracker() {
    super(TYPE);
  }

  public VoteTracker(Bytes32 currentRoot, Bytes32 nextRoot, UInt64 nextEpoch) {
    super(TYPE, new Bytes32View(currentRoot), new Bytes32View(nextRoot), new UInt64View(nextEpoch));
  }

  @Override
  public String toString() {
    return "VoteTracker{"
        + "currentRoot="
        + getCurrentRoot()
        + ", nextRoot="
        + getNextRoot()
        + ", nextEpoch="
        + getNextEpoch()
        + '}';
  }

  public Bytes32 getCurrentRoot() {
    return getField0().get();
  }

  public Bytes32 getNextRoot() {
    return getField1().get();
  }

  public UInt64 getNextEpoch() {
    return getField2().get();
  }
}
