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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class BeaconBlocksByRootRequestMessage extends SszListImpl<SszBytes32>
    implements SszList<SszBytes32>, RpcRequest {

  public static class BeaconBlocksByRootRequestMessageSchema
      extends AbstractSszListSchema<SszBytes32, BeaconBlocksByRootRequestMessage> {

    private BeaconBlocksByRootRequestMessageSchema() {
      super(SszPrimitiveSchemas.BYTES32_SCHEMA, MAX_REQUEST_BLOCKS);
    }

    @Override
    public BeaconBlocksByRootRequestMessage createFromBackingNode(TreeNode node) {
      return new BeaconBlocksByRootRequestMessage(node);
    }
  }

  public static final BeaconBlocksByRootRequestMessageSchema SSZ_SCHEMA =
      new BeaconBlocksByRootRequestMessageSchema();

  public BeaconBlocksByRootRequestMessage(List<Bytes32> roots) {
    super(
        SSZ_SCHEMA,
        SSZ_SCHEMA.createTreeFromElements(
            roots.stream().map(SszBytes32::of).collect(Collectors.toList())));
  }

  private BeaconBlocksByRootRequestMessage(TreeNode node) {
    super(SSZ_SCHEMA, node);
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
