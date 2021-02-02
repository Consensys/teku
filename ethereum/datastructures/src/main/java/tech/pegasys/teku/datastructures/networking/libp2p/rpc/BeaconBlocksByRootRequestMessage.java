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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container1;
import tech.pegasys.teku.ssz.backing.containers.ContainerType1;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public class BeaconBlocksByRootRequestMessage
    extends Container1<BeaconBlocksByRootRequestMessage, ListViewRead<Bytes32View>>
    implements RpcRequest {

  public static class BeaconBlocksByRootRequestMessageType
      extends ContainerType1<BeaconBlocksByRootRequestMessage, ListViewRead<Bytes32View>> {

    public BeaconBlocksByRootRequestMessageType() {
      super(
          "BeaconBlocksByRootRequestMessage",
          namedType(
              "blockRoots", new ListViewType<>(BasicViewTypes.BYTES32_TYPE, MAX_REQUEST_BLOCKS)));
    }

    @Override
    public BeaconBlocksByRootRequestMessage createFromBackingNode(TreeNode node) {
      return new BeaconBlocksByRootRequestMessage(this, node);
    }

    public ListViewType<Bytes32View> getListType() {
      return (ListViewType<Bytes32View>) getFieldType0();
    }
  }

  public static final BeaconBlocksByRootRequestMessageType TYPE =
      new BeaconBlocksByRootRequestMessageType();

  private BeaconBlocksByRootRequestMessage(
      BeaconBlocksByRootRequestMessageType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlocksByRootRequestMessage(final List<Bytes32> blockRoots) {
    super(TYPE, ViewUtils.toListView(TYPE.getListType(), blockRoots, Bytes32View::new));
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
