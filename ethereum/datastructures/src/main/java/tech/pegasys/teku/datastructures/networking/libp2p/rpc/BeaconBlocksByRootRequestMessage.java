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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.ListViewReadImpl;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class BeaconBlocksByRootRequestMessage extends ListViewReadImpl<Bytes32View>
    implements ListViewRead<Bytes32View>, SimpleOffsetSerializable, RpcRequest {

  public static class BeaconBlocksByRootRequestMessageType extends ListViewType<Bytes32View> {

    private BeaconBlocksByRootRequestMessageType() {
      super(BasicViewTypes.BYTES32_TYPE, MAX_REQUEST_BLOCKS);
    }

    @Override
    public BeaconBlocksByRootRequestMessage sszDeserialize(Bytes ssz)
        throws SSZDeserializeException {
      return new BeaconBlocksByRootRequestMessage(
          this, sszDeserializeTree(SszReader.fromBytes(ssz)));
    }
  }

  @SszTypeDescriptor
  public static final BeaconBlocksByRootRequestMessageType TYPE =
      new BeaconBlocksByRootRequestMessageType();

  public BeaconBlocksByRootRequestMessage(Iterable<Bytes32> roots) {
    super(ViewUtils.toListView(TYPE, roots, Bytes32View::new));
  }

  private BeaconBlocksByRootRequestMessage(
      BeaconBlocksByRootRequestMessageType type, TreeNode node) {
    super(type, node);
  }

  @Override
  public BeaconBlocksByRootRequestMessageType getType() {
    return TYPE;
  }

  @Override
  public int getMaximumRequestChunks() {
    return size();
  }

  @Override
  public String toString() {
    return "BeaconBlocksByRootRequestMessage{" + super.toString() + "}";
  }
}
