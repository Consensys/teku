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

import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage.BeaconBlocksByRangeRequestMessageType;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container1;
import tech.pegasys.teku.ssz.backing.containers.ContainerType1;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class BeaconBlocksByRootRequestMessage extends
    Container1<BeaconBlocksByRootRequestMessage, ListViewRead<Bytes32View>> implements RpcRequest,
    SSZContainer {

  private static final ListViewType<Bytes32View> LIST_TYPE = new ListViewType<>(
      BasicViewTypes.BYTES32_TYPE, MAX_REQUEST_BLOCKS);

  public static class BeaconBlocksByRootRequestMessageType
      extends ContainerType1<
            BeaconBlocksByRootRequestMessage, ListViewRead<Bytes32View>> {

    public BeaconBlocksByRootRequestMessageType() {
      super(LIST_TYPE);
    }

    @Override
    public BeaconBlocksByRootRequestMessage createFromBackingNode(TreeNode node) {
      return new BeaconBlocksByRootRequestMessage(this, node);
    }
  }

  @SszTypeDescriptor
  public static final BeaconBlocksByRootRequestMessageType TYPE =
      new BeaconBlocksByRootRequestMessageType();


  public BeaconBlocksByRootRequestMessage(
      ContainerType1<BeaconBlocksByRootRequestMessage, ListViewRead<Bytes32View>> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlocksByRootRequestMessage(final List<Bytes32> blockRoots) {
    super(TYPE, ViewUtils.toListView(LIST_TYPE, blockRoots, Bytes32View::new));
  }

  public ListViewRead<Bytes32View> getBlockRoots() {
    return getField0();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("blockRoots", getBlockRoots()).toString();
  }

  @Override
  public int getMaximumRequestChunks() {
    return getBlockRoots().size();
  }
}
