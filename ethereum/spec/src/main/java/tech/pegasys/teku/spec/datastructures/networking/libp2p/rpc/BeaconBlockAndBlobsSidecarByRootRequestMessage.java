/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.spec.config.Constants.MAX_REQUEST_BLOBS_SIDECARS;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class BeaconBlockAndBlobsSidecarByRootRequestMessage extends SszListImpl<SszBytes32>
    implements SszList<SszBytes32>, RpcRequest {

  public static class BeaconBlockAndBlobsSidecarByRootRequestMessageSchema
      extends AbstractSszListSchema<SszBytes32, BeaconBlockAndBlobsSidecarByRootRequestMessage> {

    private BeaconBlockAndBlobsSidecarByRootRequestMessageSchema() {
      super(SszPrimitiveSchemas.BYTES32_SCHEMA, MAX_REQUEST_BLOBS_SIDECARS.longValue());
    }

    @Override
    public BeaconBlockAndBlobsSidecarByRootRequestMessage createFromBackingNode(
        final TreeNode node) {
      return new BeaconBlockAndBlobsSidecarByRootRequestMessage(node);
    }
  }

  public static final BeaconBlockAndBlobsSidecarByRootRequestMessageSchema SSZ_SCHEMA =
      new BeaconBlockAndBlobsSidecarByRootRequestMessageSchema();

  public BeaconBlockAndBlobsSidecarByRootRequestMessage(final List<Bytes32> roots) {
    super(
        SSZ_SCHEMA,
        SSZ_SCHEMA.createTreeFromElements(
            roots.stream().map(SszBytes32::of).collect(Collectors.toList())));
  }

  private BeaconBlockAndBlobsSidecarByRootRequestMessage(final TreeNode node) {
    super(SSZ_SCHEMA, node);
  }

  @Override
  public int getMaximumRequestChunks() {
    return size();
  }
}
