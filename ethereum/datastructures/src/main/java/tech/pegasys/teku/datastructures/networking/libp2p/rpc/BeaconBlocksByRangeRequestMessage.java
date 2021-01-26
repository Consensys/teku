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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public final class BeaconBlocksByRangeRequestMessage
    extends Container3<BeaconBlocksByRangeRequestMessage, UInt64View, UInt64View, UInt64View>
    implements RpcRequest, SimpleOffsetSerializable, SSZContainer {

  public static class BeaconBlocksByRangeRequestMessageType
      extends ContainerType3<
          BeaconBlocksByRangeRequestMessage, UInt64View, UInt64View, UInt64View> {

    public BeaconBlocksByRangeRequestMessageType() {
      super(BasicViewTypes.UINT64_TYPE, BasicViewTypes.UINT64_TYPE, BasicViewTypes.UINT64_TYPE);
    }

    @Override
    public BeaconBlocksByRangeRequestMessage createFromBackingNode(TreeNode node) {
      return new BeaconBlocksByRangeRequestMessage(this, node);
    }
  }

  @SszTypeDescriptor
  public static final BeaconBlocksByRangeRequestMessageType TYPE =
      new BeaconBlocksByRangeRequestMessageType();

  public BeaconBlocksByRangeRequestMessage(
      ContainerType3<BeaconBlocksByRangeRequestMessage, UInt64View, UInt64View, UInt64View> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlocksByRangeRequestMessage(
      final UInt64 startSlot, final UInt64 count, final UInt64 step) {
    super(TYPE, new UInt64View(startSlot), new UInt64View(count), new UInt64View(step));
  }

  public UInt64 getStartSlot() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  public UInt64 getStep() {
    return getField2().get();
  }

  @Override
  public int getMaximumRequestChunks() {
    return Math.toIntExact(getCount().longValue());
  }
}
