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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.AbstractDelegateSszSchema;
import tech.pegasys.teku.ssz.backing.type.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.type.SszListSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class BeaconBlocksByRootRequestMessage extends SszListImpl<SszBytes32>
    implements SszList<SszBytes32>, RpcRequest {

  private static final SszListSchema<SszBytes32> LIST_VIEW_TYPE =
      new SszListSchema<>(SszPrimitiveSchemas.BYTES32_SCHEMA, MAX_REQUEST_BLOCKS);

  public static class BeaconBlocksByRootRequestMessageType
      extends AbstractDelegateSszSchema<BeaconBlocksByRootRequestMessage> {

    private BeaconBlocksByRootRequestMessageType() {
      super(LIST_VIEW_TYPE);
    }

    @Override
    public BeaconBlocksByRootRequestMessage createFromBackingNode(TreeNode node) {
      return new BeaconBlocksByRootRequestMessage(node);
    }
  }

  public static final BeaconBlocksByRootRequestMessageType TYPE =
      new BeaconBlocksByRootRequestMessageType();

  public BeaconBlocksByRootRequestMessage(Iterable<Bytes32> roots) {
    super(SszUtils.toListView(LIST_VIEW_TYPE, roots, SszBytes32::new));
  }

  private BeaconBlocksByRootRequestMessage(TreeNode node) {
    super(LIST_VIEW_TYPE, node);
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
