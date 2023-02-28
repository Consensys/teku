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

import static tech.pegasys.teku.spec.config.Constants.MAX_REQUEST_BLOCKS_DENEB;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlobSidecarsByRootRequestMessage extends SszListImpl<BlobIdentifier>
    implements SszList<BlobIdentifier>, RpcRequest {

  // size validation according to the spec (MAX_REQUEST_BLOCKS_DENEB * MAX_BLOBS_PER_BLOCK) is done
  // in the RPC handler
  public static final UInt64 MAX_LENGTH = MAX_REQUEST_BLOCKS_DENEB.times(128);

  public static class BlobSidecarsByRootRequestMessageSchema
      extends AbstractSszListSchema<BlobIdentifier, BlobSidecarsByRootRequestMessage> {

    public BlobSidecarsByRootRequestMessageSchema() {
      super(BlobIdentifier.SSZ_SCHEMA, MAX_LENGTH.longValue());
    }

    @Override
    public BlobSidecarsByRootRequestMessage createFromBackingNode(final TreeNode node) {
      return new BlobSidecarsByRootRequestMessage(this, node);
    }
  }

  public static final BlobSidecarsByRootRequestMessageSchema SSZ_SCHEMA =
      new BlobSidecarsByRootRequestMessageSchema();

  public BlobSidecarsByRootRequestMessage(final List<BlobIdentifier> blobIdentifiers) {
    super(SSZ_SCHEMA, SSZ_SCHEMA.createTreeFromElements(blobIdentifiers));
  }

  private BlobSidecarsByRootRequestMessage(
      final BlobSidecarsByRootRequestMessageSchema schema, final TreeNode node) {
    super(schema, node);
  }

  @Override
  public int getMaximumResponseChunks() {
    return size();
  }

  @Override
  public String toString() {
    return "BlobSidecarsByRootRequestMessage{" + super.toString() + "}";
  }
}
